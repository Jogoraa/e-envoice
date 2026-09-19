package et.ut.einvoice.tenancy.config.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.tenancy.config.domain.ConfigSafetyClassification;
import et.ut.einvoice.tenancy.config.domain.FeatureFlagCategory;
import et.ut.einvoice.tenancy.config.domain.TenantConfigurationOverride;
import et.ut.einvoice.tenancy.config.domain.TenantFeatureFlag;
import et.ut.einvoice.tenancy.config.repository.TenantConfigurationOverrideRepository;
import et.ut.einvoice.tenancy.config.repository.TenantFeatureFlagRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, authoritative Tenant Configuration & Feature Flag Service.
 * Implements: GLOBAL DEFAULT -> TENANT OVERRIDE -> EFFECTIVE CONFIGURATION pipeline.
 * Strictly protects IMMUTABLE_REGULATORY controls and SECURITY_CONTROL feature flags.
 */
@Service
public class TenantConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigurationService.class);

    private final TenantConfigurationOverrideRepository configRepository;
    private final TenantFeatureFlagRepository featureFlagRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final StringRedisTemplate redisTemplate;

    // In-memory cache fallback for effective configuration (Tenant-scoped)
    private final Map<String, Map<String, String>> localConfigCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> localFeatureCache = new ConcurrentHashMap<>();

    // Authoritative Definition of Global Configuration Keys & Safety Classifications
    public record ConfigMetadata(String key, String defaultValue, ConfigSafetyClassification classification, String description) {}
    public record FeatureMetadata(String key, boolean defaultEnabled, FeatureFlagCategory category, String description) {}

    private static final Map<String, ConfigMetadata> GLOBAL_CONFIG_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, FeatureMetadata> GLOBAL_FEATURE_REGISTRY = new LinkedHashMap<>();

    static {
        // IMMUTABLE_REGULATORY: Tenants are prohibited from weakening or disabling these
        registerConfig("audit.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "Mandatory Directive Art. 4(2)(b) & 27(2) audit trail");
        registerConfig("tenantIsolation.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "Directive Art. 4(2)(a) & 15(6) tenant data isolation");
        registerConfig("invoice.serverTaxCalculation.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "Server-authoritative tax calculation");
        registerConfig("invoice.immutability.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "Registered invoice immutability");
        registerConfig("offline.signatureVerification.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "ECDSA signature validation");
        registerConfig("idempotency.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "Financial transaction idempotency");
        registerConfig("governmentSubmission.validation.enabled", "true", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "MoR gateway schema validation");
        registerConfig("invoice.rounding.mode", "HALF_UP", ConfigSafetyClassification.IMMUTABLE_REGULATORY, "Directive half-up rounding");

        // TENANT_OVERRIDABLE / TENANT_RUNTIME
        registerConfig("invoice.default.currency", "ETB", ConfigSafetyClassification.TENANT_OVERRIDABLE, "Default currency code");
        registerConfig("invoice.pdf.template", "STANDARD_A4", ConfigSafetyClassification.TENANT_OVERRIDABLE, "Invoice PDF rendering template");
        registerConfig("invoice.notification.email.enabled", "true", ConfigSafetyClassification.TENANT_OVERRIDABLE, "Buyer email notification dispatch");
        registerConfig("invoice.notification.sms.enabled", "false", ConfigSafetyClassification.TENANT_OVERRIDABLE, "Buyer SMS notification dispatch");
        registerConfig("invoice.payment.terms.days", "30", ConfigSafetyClassification.TENANT_OVERRIDABLE, "Default credit payment term in days");
        registerConfig("webhook.retry.max-attempts", "3", ConfigSafetyClassification.TENANT_OVERRIDABLE, "Outbound webhook maximum delivery retries");
        registerConfig("rateLimit.perMinute", "120", ConfigSafetyClassification.TENANT_ADMIN_ONLY, "API rate limit requests per minute");

        // Features
        registerFeature("FEATURE_AUDIT_TRAIL", true, FeatureFlagCategory.SECURITY_CONTROL, "Immutable cryptographic audit trail");
        registerFeature("FEATURE_OFFLINE_SYNC", true, FeatureFlagCategory.REGULATORY_FEATURE, "Directive Art. 4(4) & 23(4) offline continuity sync");
        registerFeature("FEATURE_B2B_TIN_VALIDATION", true, FeatureFlagCategory.REGULATORY_FEATURE, "Mandatory B2B buyer TIN validation");
        registerFeature("FEATURE_NOTIFICATIONS", true, FeatureFlagCategory.OPTIONAL_PRODUCT_FEATURE, "Email & SMS buyer notification dispatching");
        registerFeature("FEATURE_CUSTOM_PDF_LAYOUT", false, FeatureFlagCategory.UI_WORKFLOW_FEATURE, "Custom branded PDF invoice layouts");
        registerFeature("FEATURE_BETA_INSIGHTS", false, FeatureFlagCategory.BETA_FEATURE, "Predictive sales tax insights");
    }

    private static void registerConfig(String key, String def, ConfigSafetyClassification cat, String desc) {
        GLOBAL_CONFIG_REGISTRY.put(key, new ConfigMetadata(key, def, cat, desc));
    }

    private static void registerFeature(String key, boolean def, FeatureFlagCategory cat, String desc) {
        GLOBAL_FEATURE_REGISTRY.put(key, new FeatureMetadata(key, def, cat, desc));
    }

    @Autowired
    public TenantConfigurationService(
            TenantConfigurationOverrideRepository configRepository,
            TenantFeatureFlagRepository featureFlagRepository,
            TenantRepository tenantRepository,
            AuditService auditService,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.configRepository = configRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Resolves the effective configuration for a tenant:
     * EffectiveConfig = merge(GlobalDefaults, TenantOverrides)
     */
    public Map<String, String> getEffectiveConfiguration(UUID tenantId) {
        validateTenantActive(tenantId);

        // Check cache
        String cacheKey = "config:" + tenantId;
        if (localConfigCache.containsKey(cacheKey)) {
            return Collections.unmodifiableMap(localConfigCache.get(cacheKey));
        }

        Map<String, String> effective = new LinkedHashMap<>();
        // 1. Seed with Global Defaults
        for (var entry : GLOBAL_CONFIG_REGISTRY.entrySet()) {
            effective.put(entry.getKey(), entry.getValue().defaultValue());
        }

        // 2. Overlay Tenant Overrides from Database
        List<TenantConfigurationOverride> overrides = configRepository.findByTenantId(tenantId);
        for (TenantConfigurationOverride override : overrides) {
            effective.put(override.getConfigKey(), override.getConfigValue());
        }

        localConfigCache.put(cacheKey, effective);
        return Collections.unmodifiableMap(effective);
    }

    /**
     * Resolves a single effective configuration value.
     */
    public String getEffectiveValue(UUID tenantId, String configKey) {
        Map<String, String> config = getEffectiveConfiguration(tenantId);
        if (!config.containsKey(configKey)) {
            throw new BusinessException("UNKNOWN_CONFIGURATION_KEY", "Configuration key is not registered: " + configKey);
        }
        return config.get(configKey);
    }

    /**
     * Sets or updates a tenant configuration override.
     */
    @Transactional
    public TenantConfigurationOverride setTenantOverride(UUID tenantId, String configKey, String newValue, Long expectedVersion, String actor) {
        validateTenantActive(tenantId);

        ConfigMetadata metadata = GLOBAL_CONFIG_REGISTRY.get(configKey);
        if (metadata == null) {
            throw new BusinessException(
                    "UNKNOWN_CONFIGURATION_KEY",
                    "Configuration key '" + configKey + "' is not recognized by the platform.",
                    HttpStatus.BAD_REQUEST
            );
        }

        // Strict Enforcement: IMMUTABLE_REGULATORY and SYSTEM_ONLY controls cannot be overridden
        if (metadata.classification() == ConfigSafetyClassification.IMMUTABLE_REGULATORY) {
            throw new BusinessException(
                    "PROTECTED_REGULATORY_CONTROL",
                    "Configuration property '" + configKey + "' is an IMMUTABLE_REGULATORY control and cannot be modified by tenant.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (metadata.classification() == ConfigSafetyClassification.SYSTEM_ONLY) {
            throw new BusinessException(
                    "SYSTEM_ONLY_CONFIGURATION",
                    "Configuration property '" + configKey + "' is SYSTEM_ONLY and cannot be altered via tenant scope.",
                    HttpStatus.FORBIDDEN
            );
        }

        Optional<TenantConfigurationOverride> existingOpt = configRepository.findByTenantIdAndConfigKey(tenantId, configKey);
        TenantConfigurationOverride record;
        String oldValue;

        if (existingOpt.isPresent()) {
            record = existingOpt.get();
            oldValue = record.getConfigValue();

            // Optimistic concurrency check
            if (expectedVersion != null && !expectedVersion.equals(record.getVersion())) {
                throw new BusinessException(
                        "CONFIGURATION_VERSION_CONFLICT",
                        "Configuration was modified concurrently. Expected version " + expectedVersion + " but was " + record.getVersion(),
                        HttpStatus.CONFLICT
                );
            }
            record.updateValue(newValue, actor);
        } else {
            oldValue = metadata.defaultValue();
            record = new TenantConfigurationOverride(UUID.randomUUID(), tenantId, configKey, newValue, metadata.classification(), actor);
        }

        TenantConfigurationOverride saved = configRepository.save(record);
        invalidateCache(tenantId);

        // Audit Event Generation
        auditService.recordEvent(
                tenantId,
                actor != null ? actor : "SYSTEM",
                "UPDATE_TENANT_CONFIG",
                "CONFIGURATION",
                saved.getId().toString(),
                "Key: " + configKey + ", Old: " + oldValue + ", New: " + newValue
        );

        return saved;
    }

    /**
     * Evaluates whether a feature flag is enabled for the specified tenant.
     * Evaluates server-side: Security and Regulatory controls CANNOT be disabled.
     */
    public boolean isFeatureEnabled(UUID tenantId, String featureKey) {
        validateTenantActive(tenantId);

        FeatureMetadata metadata = GLOBAL_FEATURE_REGISTRY.get(featureKey);
        if (metadata == null) {
            throw new BusinessException(
                    "UNKNOWN_FEATURE_FLAG",
                    "Feature flag '" + featureKey + "' is not recognized by the platform.",
                    HttpStatus.BAD_REQUEST
            );
        }

        // Security controls and mandatory regulatory features can NEVER be turned off
        if (metadata.category() == FeatureFlagCategory.SECURITY_CONTROL ||
            metadata.category() == FeatureFlagCategory.REGULATORY_FEATURE) {
            return true;
        }

        String cacheKey = "feat:" + tenantId + ":" + featureKey;
        if (localFeatureCache.containsKey(cacheKey)) {
            return localFeatureCache.get(cacheKey);
        }

        Optional<TenantFeatureFlag> flagOpt = featureFlagRepository.findByTenantIdAndFeatureKey(tenantId, featureKey);
        boolean result = flagOpt.map(TenantFeatureFlag::isEnabled).orElse(metadata.defaultEnabled());

        localFeatureCache.put(cacheKey, result);
        return result;
    }

    /**
     * Sets or updates a tenant feature flag override.
     */
    @Transactional
    public TenantFeatureFlag setFeatureFlag(UUID tenantId, String featureKey, boolean enabled, Long expectedVersion, String actor) {
        validateTenantActive(tenantId);

        FeatureMetadata metadata = GLOBAL_FEATURE_REGISTRY.get(featureKey);
        if (metadata == null) {
            throw new BusinessException("UNKNOWN_FEATURE_FLAG", "Feature flag '" + featureKey + "' is not recognized.", HttpStatus.BAD_REQUEST);
        }

        // Protective Guard: Cannot disable security controls or regulatory features
        if (!enabled && (metadata.category() == FeatureFlagCategory.SECURITY_CONTROL || metadata.category() == FeatureFlagCategory.REGULATORY_FEATURE)) {
            throw new BusinessException(
                    "PROTECTED_REGULATORY_FEATURE",
                    "Feature '" + featureKey + "' is a protected " + metadata.category() + " and cannot be disabled.",
                    HttpStatus.FORBIDDEN
            );
        }

        Optional<TenantFeatureFlag> existingOpt = featureFlagRepository.findByTenantIdAndFeatureKey(tenantId, featureKey);
        TenantFeatureFlag flag;
        boolean oldVal;

        if (existingOpt.isPresent()) {
            flag = existingOpt.get();
            oldVal = flag.isEnabled();

            if (expectedVersion != null && !expectedVersion.equals(flag.getVersion())) {
                throw new BusinessException(
                        "CONFIGURATION_VERSION_CONFLICT",
                        "Feature flag was modified concurrently. Expected version " + expectedVersion + " but was " + flag.getVersion(),
                        HttpStatus.CONFLICT
                );
            }
            flag.setEnabled(enabled, actor);
        } else {
            oldVal = metadata.defaultEnabled();
            flag = new TenantFeatureFlag(UUID.randomUUID(), tenantId, featureKey, enabled, metadata.category(), actor);
        }

        TenantFeatureFlag saved = featureFlagRepository.save(flag);
        invalidateCache(tenantId);

        auditService.recordEvent(
                tenantId,
                actor != null ? actor : "SYSTEM",
                "UPDATE_FEATURE_FLAG",
                "FEATURE_FLAG",
                saved.getId().toString(),
                "Feature: " + featureKey + ", Old: " + oldVal + ", New: " + enabled
        );

        return saved;
    }

    public void invalidateCache(UUID tenantId) {
        localConfigCache.remove("config:" + tenantId);
        // Clear all cached feature flags for this tenant
        localFeatureCache.keySet().removeIf(k -> k.startsWith("feat:" + tenantId + ":"));

        if (redisTemplate != null) {
            try {
                redisTemplate.delete("config:" + tenantId);
                Set<String> keys = redisTemplate.keys("feat:" + tenantId + ":*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception ex) {
                log.warn("Redis cache invalidation warning: {}", ex.getMessage());
            }
        }
    }

    private void validateTenantActive(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant identity is mandatory.", HttpStatus.BAD_REQUEST);
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant " + tenantId + " does not exist.", HttpStatus.NOT_FOUND));

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new BusinessException("TENANT_SUSPENDED", "Tenant is suspended.", HttpStatus.FORBIDDEN);
        }
        if (tenant.getStatus() == TenantStatus.DEACTIVATED || tenant.getStatus() == TenantStatus.ARCHIVED) {
            throw new BusinessException("TENANT_DEACTIVATED", "Tenant is inactive or deactivated.", HttpStatus.FORBIDDEN);
        }
    }

    public Map<String, ConfigMetadata> getGlobalConfigRegistry() {
        return Collections.unmodifiableMap(GLOBAL_CONFIG_REGISTRY);
    }

    public Map<String, FeatureMetadata> getGlobalFeatureRegistry() {
        return Collections.unmodifiableMap(GLOBAL_FEATURE_REGISTRY);
    }
}
