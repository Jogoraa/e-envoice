package et.ut.einvoice.platform.config.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "configuration_entries")
public class ConfigurationEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private ConfigurationScope scope;

    @Column(name = "key_name", nullable = false, unique = true, length = 128)
    private String keyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 32)
    private ConfigurationValueType valueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 32)
    private ConfigurationClassification classification;

    @Column(name = "current_value", columnDefinition = "TEXT")
    private String currentValue;

    @Column(name = "encrypted_secret_payload", columnDefinition = "TEXT")
    private String encryptedSecretPayload;

    @Column(name = "secret_fingerprint", length = 64)
    private String secretFingerprint;

    @Column(name = "is_secret", nullable = false)
    private boolean isSecret;

    @Column(name = "is_runtime_mutable", nullable = false)
    private boolean runtimeMutable;

    @Column(name = "requires_restart", nullable = false)
    private boolean requiresRestart;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // DRAFT, SAVED, APPLY_PENDING, APPLIED, APPLY_FAILED

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    @Column(name = "validation_pattern", length = 256)
    private String validationPattern;

    @Column(name = "min_value")
    private Long minValue;

    @Column(name = "max_value")
    private Long maxValue;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ConfigurationEntry() {}

    public ConfigurationEntry(
            UUID id,
            ConfigurationScope scope,
            String keyName,
            ConfigurationValueType valueType,
            ConfigurationClassification classification,
            String currentValue,
            boolean isSecret,
            boolean runtimeMutable,
            boolean requiresRestart,
            String description,
            String allowedValues,
            Long minValue,
            Long maxValue,
            String updatedBy
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.scope = scope;
        this.keyName = keyName;
        this.valueType = valueType;
        this.classification = classification;
        this.currentValue = currentValue;
        this.isSecret = isSecret;
        this.runtimeMutable = runtimeMutable;
        this.requiresRestart = requiresRestart;
        this.status = "APPLIED";
        this.version = 1L;
        this.description = description;
        this.allowedValues = allowedValues;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ConfigurationScope getScope() {
        return scope;
    }

    public void setScope(ConfigurationScope scope) {
        this.scope = scope;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public ConfigurationValueType getValueType() {
        return valueType;
    }

    public void setValueType(ConfigurationValueType valueType) {
        this.valueType = valueType;
    }

    public ConfigurationClassification getClassification() {
        return classification;
    }

    public void setClassification(ConfigurationClassification classification) {
        this.classification = classification;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }

    public String getEncryptedSecretPayload() {
        return encryptedSecretPayload;
    }

    public void setEncryptedSecretPayload(String encryptedSecretPayload) {
        this.encryptedSecretPayload = encryptedSecretPayload;
    }

    public String getSecretFingerprint() {
        return secretFingerprint;
    }

    public void setSecretFingerprint(String secretFingerprint) {
        this.secretFingerprint = secretFingerprint;
    }

    public boolean isSecret() {
        return isSecret;
    }

    public void setSecret(boolean secret) {
        isSecret = secret;
    }

    public boolean isRuntimeMutable() {
        return runtimeMutable;
    }

    public void setRuntimeMutable(boolean runtimeMutable) {
        this.runtimeMutable = runtimeMutable;
    }

    public boolean isRequiresRestart() {
        return requiresRestart;
    }

    public void setRequiresRestart(boolean requiresRestart) {
        this.requiresRestart = requiresRestart;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(String allowedValues) {
        this.allowedValues = allowedValues;
    }

    public String getValidationPattern() {
        return validationPattern;
    }

    public void setValidationPattern(String validationPattern) {
        this.validationPattern = validationPattern;
    }

    public Long getMinValue() {
        return minValue;
    }

    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }

    public Long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
