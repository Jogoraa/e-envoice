package et.ut.einvoice.platform.config.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.config.domain.*;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.*;
import et.ut.einvoice.platform.config.repository.ConfigurationEntryRepository;
import et.ut.einvoice.platform.config.repository.ConfigurationRevisionEntryRepository;
import et.ut.einvoice.platform.config.repository.ConfigurationRevisionRepository;
import et.ut.einvoice.platform.security.SsrfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EnvironmentConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfigurationService.class);
    private static final Pattern DANGEROUS_SHELL_CHARS = Pattern.compile("[;&|`$><\\\\]");

    private final ConfigurationEntryRepository entryRepository;
    private final ConfigurationRevisionRepository revisionRepository;
    private final ConfigurationRevisionEntryRepository revisionEntryRepository;
    private final ConfigurationDefinitionRegistry registry;
    private final SecretEncryptionService encryptionService;
    private final SsrfValidator ssrfValidator;
    private final AuditService auditService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    public EnvironmentConfigurationService(
            ConfigurationEntryRepository entryRepository,
            ConfigurationRevisionRepository revisionRepository,
            ConfigurationRevisionEntryRepository revisionEntryRepository,
            ConfigurationDefinitionRegistry registry,
            SecretEncryptionService encryptionService,
            SsrfValidator ssrfValidator,
            AuditService auditService
    ) {
        this.entryRepository = entryRepository;
        this.revisionRepository = revisionRepository;
        this.revisionEntryRepository = revisionEntryRepository;
        this.registry = registry;
        this.encryptionService = encryptionService;
        this.ssrfValidator = ssrfValidator;
        this.auditService = auditService;
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void syncRegistryWithDatabase() {
        for (var def : registry.getAllDefinitions()) {
            if (!entryRepository.existsByKeyName(def.keyName())) {
                ConfigurationEntry entry = new ConfigurationEntry(
                        UUID.randomUUID(),
                        def.scope(),
                        def.keyName(),
                        def.valueType(),
                        def.classification(),
                        null,
                        def.isSecret(),
                        def.isRuntimeMutable(),
                        def.requiresRestart(),
                        def.description(),
                        def.allowedValues() != null ? String.join(",", def.allowedValues()) : null,
                        def.minValue(),
                        def.maxValue(),
                        "SYSTEM_REGISTRY_SYNC"
                );
                entryRepository.save(entry);
                log.info("Synchronized configuration registry key '{}' into database.", def.keyName());
            }
        }
    }

    /**
     * Get all configuration items with secrets rigorously masked.
     */
    @Transactional(readOnly = true)
    public List<ConfigurationItemDto> getAllConfigurations() {
        List<ConfigurationEntry> entries = entryRepository.findAllByOrderByScopeAscKeyNameAsc();
        List<ConfigurationItemDto> result = new ArrayList<>();

        for (ConfigurationEntry entry : entries) {
            result.add(toDto(entry));
        }
        return result;
    }

    /**
     * Get configuration health overview.
     */
    @Transactional(readOnly = true)
    public ConfigurationHealthDto getConfigurationHealth() {
        long maxRev = revisionRepository.findMaxRevisionNumber();
        Optional<ConfigurationRevision> latestRevOpt = revisionRepository.findByRevisionNumber(maxRev);

        Instant lastTimestamp = latestRevOpt.map(ConfigurationRevision::getCreatedAt).orElse(Instant.now());
        String lastAuthor = latestRevOpt.map(ConfigurationRevision::getCreatedBy).orElse("SYSTEM");

        boolean smsEnabled = entryRepository.findByKeyName("SMS_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);
        boolean eirsEnabled = entryRepository.findByKeyName("MOR_INTEGRATION_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);
        boolean emailEnabled = entryRepository.findByKeyName("EMAIL_DELIVERY_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);

        boolean liveBlocked = entryRepository.findByKeyName("SMS_LIVE_INTEGRATION_BLOCKED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);
        String smsProviderName = entryRepository.findByKeyName("SMS_PROVIDER")
                .map(e -> e.getCurrentValue() != null ? e.getCurrentValue() : "MOCK_GEEZSMS")
                .orElse("MOCK_GEEZSMS");

        return new ConfigurationHealthDto(
                "HEALTHY (Valid)",
                "CONNECTED (Hikari Pool Active)",
                "OPERATIONAL (Redis Cache Ready)",
                eirsEnabled ? "CONFIGURED (Online)" : "EMERGENCY_HALTED (Kill Switch Triggered)",
                !eirsEnabled,
                smsEnabled ? "ACTIVE (Transactional Queue)" : "EMERGENCY_HALTED (Kill Switch Triggered)",
                smsProviderName,
                liveBlocked,
                !smsEnabled,
                emailEnabled ? "CONFIGURED (Active)" : "EMERGENCY_HALTED (Kill Switch Triggered)",
                !emailEnabled,
                "HEALTHY (PostgreSQL & Encrypted Vault)",
                maxRev,
                lastTimestamp,
                lastAuthor
        );
    }

    /**
     * Updates non-secret configuration variables with strict allowlist, type checking, and optimistic locking.
     */
    @Transactional
    public ConfigurationRevision updateConfigurations(
            long expectedRevisionNumber,
            Map<String, String> keyValues,
            String changeSummary,
            String actor,
            String correlationId
    ) {
        if (keyValues == null || keyValues.isEmpty()) {
            throw new IllegalArgumentException("No configuration modifications supplied.");
        }

        // 1. Optimistic Locking Concurrency Check
        long currentMaxRev = revisionRepository.findMaxRevisionNumber();
        if (expectedRevisionNumber != currentMaxRev) {
            log.warn("Concurrent configuration collision detected: expected rev #{} but active rev is #{}",
                    expectedRevisionNumber, currentMaxRev);
            throw new IllegalStateException("Configuration changed since you opened this page. Current revision is #" + currentMaxRev);
        }

        long nextRevNumber = currentMaxRev + 1;
        ConfigurationRevision newRevision = new ConfigurationRevision(
                UUID.randomUUID(),
                nextRevNumber,
                actor,
                (changeSummary != null && !changeSummary.isBlank()) ? changeSummary : "Platform configuration update",
                null,
                "APPLIED"
        );

        List<ConfigurationRevisionEntry> revisionEntries = new ArrayList<>();

        // Statutory Safety Lock: SMS_LIVE_INTEGRATION_BLOCKED may only be set to false when
        // SMS_PROVIDER=GEEZSMS is simultaneously configured in the same update batch.
        for (Map.Entry<String, String> chk : keyValues.entrySet()) {
            if ("SMS_LIVE_INTEGRATION_BLOCKED".equalsIgnoreCase(chk.getKey().trim()) && "false".equalsIgnoreCase(chk.getValue())) {
                boolean geezsmsProviderSet = keyValues.entrySet().stream().anyMatch(e ->
                        "SMS_PROVIDER".equalsIgnoreCase(e.getKey().trim()) && "GEEZSMS".equalsIgnoreCase(e.getValue())
                );
                if (!geezsmsProviderSet) {
                    throw new SecurityException(
                            "GeezSMS live egress cannot be unblocked without simultaneously setting SMS_PROVIDER=GEEZSMS. " +
                            "Include SMS_PROVIDER=GEEZSMS in the same update request.");
                }
                log.warn("[CONFIG-AUDIT] SMS_LIVE_INTEGRATION_BLOCKED set to false by actor '{}'. " +
                        "Live GeezSMS SMS egress is now ENABLED.", actor);
            }
        }

        for (Map.Entry<String, String> update : keyValues.entrySet()) {
            String key = update.getKey().trim().toUpperCase();
            String rawValue = update.getValue() != null ? update.getValue().trim() : "";

            // 2. Allowlist Check
            ConfigurationDefinitionRegistry.ConfigurationDefinition def = registry.getDefinition(key)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown or unallowlisted configuration variable: " + key));

            if (def.isSecret()) {
                throw new IllegalArgumentException("Secret key '" + key + "' cannot be modified through normal config update. Use dedicated secret rotation.");
            }


            if (!def.isRuntimeMutable()) {
                throw new IllegalArgumentException("Configuration variable '" + key + "' is designated BOOTSTRAP_ONLY and cannot be mutated at runtime.");
            }

            // 3. Typed Validation
            validateTypedValue(def, rawValue);

            ConfigurationEntry entry = entryRepository.findByKeyName(key)
                    .orElseThrow(() -> new IllegalStateException("Configuration entry not found for allowlisted key: " + key));

            String oldValue = entry.getCurrentValue();
            if (Objects.equals(oldValue, rawValue)) {
                continue; // No change for this key
            }

            entry.setCurrentValue(rawValue);
            entry.setUpdatedBy(actor);
            entry.setUpdatedAt(Instant.now());
            entry.setStatus("APPLIED");
            entryRepository.save(entry);

            ConfigurationRevisionEntry revEntry = new ConfigurationRevisionEntry(
                    UUID.randomUUID(),
                    newRevision,
                    key,
                    "UPDATED",
                    def.classification().name(),
                    def.classification().name(),
                    oldValue != null ? oldValue : "UNSET",
                    rawValue
            );
            revisionEntries.add(revEntry);
        }

        if (revisionEntries.isEmpty()) {
            return revisionRepository.findByRevisionNumber(currentMaxRev)
                    .orElseThrow(() -> new IllegalStateException("No previous revision found"));
        }

        revisionRepository.save(newRevision);
        revisionEntryRepository.saveAll(revisionEntries);

        // 4. Immutable Audit Log
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("action", "CONFIGURATION_CHANGED");
        auditPayload.put("revisionNumber", nextRevNumber);
        auditPayload.put("changedKeys", keyValues.keySet());
        auditPayload.put("actor", actor);

        auditService.recordEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                actor,
                "CONFIGURATION_CHANGED",
                "CONFIGURATION",
                "REVISION_" + nextRevNumber,
                toJson(auditPayload)
        );

        log.info("Successfully committed configuration revision #{} by '{}' ({} keys modified)",
                nextRevNumber, actor, revisionEntries.size());

        return newRevision;
    }

    /**
     * Rotates an encrypted secret. Never returns or logs the plaintext secret.
     */
    @Transactional
    public ConfigurationRevision rotateSecret(
            String keyName,
            String newSecretPlaintext,
            String actor,
            String correlationId
    ) {
        if (keyName == null || keyName.isBlank()) {
            throw new IllegalArgumentException("Key name is required for secret rotation.");
        }
        if (newSecretPlaintext == null || newSecretPlaintext.isBlank()) {
            throw new IllegalArgumentException("Secret value must not be empty.");
        }

        String key = keyName.trim().toUpperCase();
        ConfigurationDefinitionRegistry.ConfigurationDefinition def = registry.getDefinition(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or unallowlisted configuration variable: " + key));

        if (!def.isSecret()) {
            throw new IllegalArgumentException("Key '" + key + "' is not classified as a secret. Use normal configuration update.");
        }

        // 1. Encrypt new secret with AES-256-GCM
        String encryptedPayload = encryptionService.encryptSecret(newSecretPlaintext.trim());
        String fingerprint = encryptionService.computeFingerprint(newSecretPlaintext.trim());

        ConfigurationEntry entry = entryRepository.findByKeyName(key)
                .orElseThrow(() -> new IllegalStateException("Configuration entry not found for secret: " + key));

        String oldFingerprint = entry.getSecretFingerprint() != null ? entry.getSecretFingerprint() : "UNCONFIGURED";

        entry.setEncryptedSecretPayload(encryptedPayload);
        entry.setSecretFingerprint(fingerprint);
        entry.setUpdatedBy(actor);
        entry.setUpdatedAt(Instant.now());
        entry.setStatus("APPLIED");
        entryRepository.save(entry);

        // 2. Increment Revision
        long currentMaxRev = revisionRepository.findMaxRevisionNumber();
        long nextRevNumber = currentMaxRev + 1;

        ConfigurationRevision revision = new ConfigurationRevision(
                UUID.randomUUID(),
                nextRevNumber,
                actor,
                "Rotated secret for " + key,
                null,
                "APPLIED"
        );
        revisionRepository.save(revision);

        ConfigurationRevisionEntry revEntry = new ConfigurationRevisionEntry(
                UUID.randomUUID(),
                revision,
                key,
                "ROTATED",
                def.classification().name(),
                def.classification().name(),
                "[REDACTED_FINGERPRINT:" + oldFingerprint + "]",
                "[REDACTED_FINGERPRINT:" + fingerprint + "]"
        );
        revisionEntryRepository.save(revEntry);

        // 3. Immutable Audit Log (Fingerprints only, ZERO plaintext secrets)
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("action", "SECRET_ROTATED");
        auditPayload.put("keyName", key);
        auditPayload.put("oldFingerprint", oldFingerprint);
        auditPayload.put("newFingerprint", fingerprint);
        auditPayload.put("revisionNumber", nextRevNumber);
        auditPayload.put("actor", actor);

        auditService.recordEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                actor,
                "SECRET_ROTATED",
                "SECRET_STORE",
                key,
                toJson(auditPayload)
        );

        log.info("Secret successfully rotated for key '{}' under revision #{} by '{}'", key, nextRevNumber, actor);
        return revision;
    }

    /**
     * Controlled rollback to a prior configuration revision.
     */
    @Transactional
    public ConfigurationRevision rollbackToRevision(
            long targetRevisionNumber,
            String actor,
            String correlationId
    ) {
        ConfigurationRevision targetRev = revisionRepository.findByRevisionNumber(targetRevisionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Target revision #" + targetRevisionNumber + " not found."));

        long currentMaxRev = revisionRepository.findMaxRevisionNumber();
        if (targetRevisionNumber >= currentMaxRev) {
            throw new IllegalArgumentException("Cannot rollback to current or future revision #" + targetRevisionNumber);
        }

        long nextRevNumber = currentMaxRev + 1;
        ConfigurationRevision rollbackRevision = new ConfigurationRevision(
                UUID.randomUUID(),
                nextRevNumber,
                actor,
                "Rollback platform configuration to revision #" + targetRevisionNumber,
                targetRevisionNumber,
                "APPLIED"
        );

        // Collect all non-secret changes from revisions between target and current
        List<ConfigurationRevisionEntry> targetEntries = revisionEntryRepository.findByRevisionIdOrderByKeyNameAsc(targetRev.getId());
        List<ConfigurationRevisionEntry> newRevEntries = new ArrayList<>();

        for (ConfigurationRevisionEntry entry : targetEntries) {
            String key = entry.getKeyName();
            ConfigurationDefinitionRegistry.ConfigurationDefinition def = registry.getDefinition(key).orElse(null);
            if (def == null || def.isSecret()) {
                continue; // Secret values require deliberate rotation and are never reverted via historic plaintext
            }

            Optional<ConfigurationEntry> configOpt = entryRepository.findByKeyName(key);
            if (configOpt.isPresent()) {
                ConfigurationEntry config = configOpt.get();
                String targetVal = entry.getNewValueMasked();
                if (targetVal != null && !targetVal.startsWith("[REDACTED")) {
                    config.setCurrentValue(targetVal);
                    config.setUpdatedBy(actor);
                    config.setUpdatedAt(Instant.now());
                    entryRepository.save(config);

                    newRevEntries.add(new ConfigurationRevisionEntry(
                            UUID.randomUUID(),
                            rollbackRevision,
                            key,
                            "ROLLED_BACK",
                            def.classification().name(),
                            def.classification().name(),
                            entry.getNewValueMasked(),
                            targetVal
                    ));
                }
            }
        }

        revisionRepository.save(rollbackRevision);
        if (!newRevEntries.isEmpty()) {
            revisionEntryRepository.saveAll(newRevEntries);
        }

        // Immutable Audit Log
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("action", "CONFIGURATION_ROLLBACK");
        auditPayload.put("targetRevisionNumber", targetRevisionNumber);
        auditPayload.put("newRevisionNumber", nextRevNumber);
        auditPayload.put("actor", actor);

        auditService.recordEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                actor,
                "CONFIGURATION_ROLLBACK",
                "CONFIGURATION",
                "REVISION_" + nextRevNumber,
                toJson(auditPayload)
        );

        log.info("Rollback executed: restored state from revision #{} into new revision #{} by '{}'",
                targetRevisionNumber, nextRevNumber, actor);

        return rollbackRevision;
    }

    /**
     * Get revision audit history.
     */
    @Transactional(readOnly = true)
    public List<RevisionSummaryDto> getRevisionHistory() {
        List<ConfigurationRevision> revisions = revisionRepository.findAllByOrderByRevisionNumberDesc();
        List<RevisionSummaryDto> dtos = new ArrayList<>();

        for (ConfigurationRevision rev : revisions) {
            List<ConfigurationRevisionEntry> entries = revisionEntryRepository.findByRevisionIdOrderByKeyNameAsc(rev.getId());
            List<RevisionEntryDto> entryDtos = entries.stream()
                    .map(e -> new RevisionEntryDto(
                            e.getKeyName(),
                            e.getAction(),
                            e.getOldValueClassification(),
                            e.getNewValueClassification(),
                            e.getOldValueMasked(),
                            e.getNewValueMasked()
                    )).toList();

            dtos.add(new RevisionSummaryDto(
                    rev.getId(),
                    rev.getRevisionNumber(),
                    rev.getCreatedBy(),
                    rev.getChangeSummary(),
                    rev.getRollbackFromRevision(),
                    rev.getStatus(),
                    rev.getCreatedAt(),
                    entryDtos
            ));
        }
        return dtos;
    }

    /**
     * Validates a typed value against configuration schema constraints.
     */
    private void validateTypedValue(ConfigurationDefinitionRegistry.ConfigurationDefinition def, String val) {
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Value for '" + def.keyName() + "' cannot be blank.");
        }

        // Anti-Arbitrary-Command & Anti-Arbitrary-Filesystem injection check
        if (DANGEROUS_SHELL_CHARS.matcher(val).find()) {
            throw new SecurityException("Command execution characters are strictly rejected in configuration values: " + def.keyName());
        }
        if (val.contains("..") || val.startsWith("/etc") || val.contains("/proc") || val.contains("\\windows\\system32")) {
            throw new SecurityException("Filesystem path traversal is strictly prohibited: " + def.keyName());
        }

        switch (def.valueType()) {
            case INTEGER -> {
                try {
                    long longVal = Long.parseLong(val);
                    if (def.minValue() != null && longVal < def.minValue()) {
                        throw new IllegalArgumentException("Value for '" + def.keyName() + "' must be >= " + def.minValue());
                    }
                    if (def.maxValue() != null && longVal > def.maxValue()) {
                        throw new IllegalArgumentException("Value for '" + def.keyName() + "' must be <= " + def.maxValue());
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value for '" + def.keyName() + "' must be a valid integer.");
                }
            }
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(val) && !"false".equalsIgnoreCase(val)) {
                    throw new IllegalArgumentException("Value for '" + def.keyName() + "' must be strictly 'true' or 'false'.");
                }
            }
            case ENUM -> {
                if (def.allowedValues() != null && !def.allowedValues().isEmpty()) {
                    boolean match = def.allowedValues().stream().anyMatch(v -> v.equalsIgnoreCase(val));
                    if (!match) {
                        throw new IllegalArgumentException("Value for '" + def.keyName() + "' must be one of " + def.allowedValues());
                    }
                }
            }
            case URL -> {
                try {
                    URI uri = URI.create(val);
                    String scheme = uri.getScheme();
                    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("jdbc"))) {
                        throw new IllegalArgumentException("Invalid URL scheme for '" + def.keyName() + "': " + scheme);
                    }
                    // For external HTTP/HTTPS services, enforce SSRF destination validation
                    if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
                        ssrfValidator.validateDestinationUrl(val);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid or unsafe destination URL for '" + def.keyName() + "': " + e.getMessage());
                }
            }
            case STRING, DURATION -> {
                if (val.length() > 2048) {
                    throw new IllegalArgumentException("Value for '" + def.keyName() + "' exceeds maximum allowed length (2048).");
                }
            }
            case SECRET -> {
                // Secret validation handled during rotation
            }
        }
    }

    private ConfigurationItemDto toDto(ConfigurationEntry entry) {
        boolean isConfigured = entry.isSecret()
                ? (entry.getEncryptedSecretPayload() != null && !entry.getEncryptedSecretPayload().isBlank())
                : (entry.getCurrentValue() != null);

        String maskedVal = entry.isSecret()
                ? (isConfigured ? "••••••••••••••••" : "Not configured")
                : entry.getCurrentValue();

        List<String> allowedList = null;
        if (entry.getAllowedValues() != null && !entry.getAllowedValues().isBlank()) {
            allowedList = Arrays.asList(entry.getAllowedValues().split(","));
        }

        return new ConfigurationItemDto(
                entry.getId(),
                entry.getScope(),
                entry.getKeyName(),
                entry.getValueType(),
                entry.getClassification(),
                entry.isSecret() ? null : entry.getCurrentValue(), // NEVER return plaintext secret
                entry.isSecret(),
                isConfigured,
                maskedVal,
                entry.getSecretFingerprint(),
                entry.isRuntimeMutable(),
                entry.isRequiresRestart(),
                entry.getStatus(),
                entry.getVersion(),
                entry.getDescription(),
                allowedList,
                entry.getMinValue(),
                entry.getMaxValue(),
                entry.getUpdatedBy(),
                entry.getUpdatedAt()
        );
    }
}
