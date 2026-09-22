package et.ut.einvoice.platform.config.service;

import et.ut.einvoice.platform.config.domain.ConfigurationClassification;
import et.ut.einvoice.platform.config.domain.ConfigurationScope;
import et.ut.einvoice.platform.config.domain.ConfigurationValueType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Authoritative Allowlist Definition Registry.
 * Enforces strict boundary checks: only explicitly allowlisted keys with predefined
 * data types, sensitivity tiers, and validation boundaries can be configured or manipulated.
 * Prevents arbitrary environment variable injection and arbitrary filesystem manipulation.
 */
@Component
public class ConfigurationDefinitionRegistry {

    public record ConfigurationDefinition(
            String keyName,
            ConfigurationScope scope,
            ConfigurationValueType valueType,
            ConfigurationClassification classification,
            boolean isSecret,
            boolean isRuntimeMutable,
            boolean requiresRestart,
            String description,
            List<String> allowedValues,
            Long minValue,
            Long maxValue
    ) {}

    private final Map<String, ConfigurationDefinition> definitions = new LinkedHashMap<>();

    public ConfigurationDefinitionRegistry() {
        registerAll();
    }

    private void registerAll() {
        // APPLICATION SCOPE
        register(new ConfigurationDefinition(
                "SERVER_PORT", ConfigurationScope.APPLICATION, ConfigurationValueType.INTEGER,
                ConfigurationClassification.LOW, false, false, true,
                "HTTP server listening port for platform container", null, 1024L, 65535L
        ));
        register(new ConfigurationDefinition(
                "LOG_LEVEL", ConfigurationScope.APPLICATION, ConfigurationValueType.ENUM,
                ConfigurationClassification.LOW, false, true, false,
                "Root logging verbosity level", List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR"), null, null
        ));
        register(new ConfigurationDefinition(
                "PLATFORM_SYSTEM_TYPE", ConfigurationScope.APPLICATION, ConfigurationValueType.ENUM,
                ConfigurationClassification.LOW, false, true, false,
                "Authoritative statutory system deployment classification (Directive Art. 11)", List.of("ERP", "POS", "BILLING"), null, null
        ));
        register(new ConfigurationDefinition(
                "PLATFORM_DEFAULT_CURRENCY", ConfigurationScope.APPLICATION, ConfigurationValueType.STRING,
                ConfigurationClassification.LOW, false, true, false,
                "Sovereign accounting currency default ISO-4217 code", List.of("ETB", "USD", "EUR"), null, null
        ));
        register(new ConfigurationDefinition(
                "OFFLINE_BUFFER_MAX_AGE_HOURS", ConfigurationScope.APPLICATION, ConfigurationValueType.INTEGER,
                ConfigurationClassification.HIGH, false, true, false,
                "Mandatory statutory offline buffer synchronization ceiling before audit flagging", null, 1L, 168L
        ));
        register(new ConfigurationDefinition(
                "CANCELLATION_SLA_HOURS", ConfigurationScope.APPLICATION, ConfigurationValueType.INTEGER,
                ConfigurationClassification.HIGH, false, true, false,
                "Legal invoice cancellation approval statutory time window", null, 1L, 72L
        ));

        // DATABASE SCOPE
        register(new ConfigurationDefinition(
                "DATABASE_URL", ConfigurationScope.DATABASE, ConfigurationValueType.URL,
                ConfigurationClassification.CRITICAL, false, false, true,
                "Primary PostgreSQL cluster JDBC connection URL", null, null, null
        ));
        register(new ConfigurationDefinition(
                "DATABASE_USERNAME", ConfigurationScope.DATABASE, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, false, true,
                "Database connection master user principal", null, null, null
        ));
        register(new ConfigurationDefinition(
                "DATABASE_PASSWORD", ConfigurationScope.DATABASE, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, false, true,
                "Database master authentication secret", null, null, null
        ));
        register(new ConfigurationDefinition(
                "DATABASE_POOL_SIZE", ConfigurationScope.DATABASE, ConfigurationValueType.INTEGER,
                ConfigurationClassification.HIGH, false, true, true,
                "HikariCP connection pool maximum active connections", null, 5L, 100L
        ));
        register(new ConfigurationDefinition(
                "DATABASE_CONNECTION_TIMEOUT_MS", ConfigurationScope.DATABASE, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, true,
                "HikariCP database connection timeout in milliseconds", null, 1000L, 60000L
        ));

        // REDIS SCOPE
        register(new ConfigurationDefinition(
                "REDIS_HOST", ConfigurationScope.REDIS, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, false, true,
                "Redis cluster hostname for token and cache distribution", null, null, null
        ));
        register(new ConfigurationDefinition(
                "REDIS_PORT", ConfigurationScope.REDIS, ConfigurationValueType.INTEGER,
                ConfigurationClassification.HIGH, false, false, true,
                "Redis listening port", null, 1L, 65535L
        ));
        register(new ConfigurationDefinition(
                "REDIS_PASSWORD", ConfigurationScope.REDIS, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, false, true,
                "Redis cluster authentication token", null, null, null
        ));
        register(new ConfigurationDefinition(
                "REDIS_TIMEOUT_MS", ConfigurationScope.REDIS, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Redis operation socket read timeout in milliseconds", null, 100L, 30000L
        ));

        // SECURITY SCOPE
        register(new ConfigurationDefinition(
                "CORS_ALLOWED_ORIGINS", ConfigurationScope.SECURITY, ConfigurationValueType.STRING,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Comma-separated list of approved browser origin domains for CORS headers", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MFA_HARDWARE_REQUIRED", ConfigurationScope.SECURITY, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.HIGH, false, true, false,
                "Enforce hardware/TOTP MFA ceremony for Master Admin sessions", null, null, null
        ));
        register(new ConfigurationDefinition(
                "ENFORCE_SINGLE_FLIGHT_REFRESH", ConfigurationScope.SECURITY, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Prevent concurrent duplicate OAuth token refresh races across microservices", null, null, null
        ));

        // AUTHENTICATION SCOPE
        register(new ConfigurationDefinition(
                "JWT_ISSUER", ConfigurationScope.AUTHENTICATION, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, true, false,
                "Expected JWT issuer claim string for all platform security contexts", null, null, null
        ));
        register(new ConfigurationDefinition(
                "JWT_AUDIENCE", ConfigurationScope.AUTHENTICATION, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, true, false,
                "Default audience claim identifier for issued tokens", null, null, null
        ));
        register(new ConfigurationDefinition(
                "JWT_ACCESS_TOKEN_TTL_SECONDS", ConfigurationScope.AUTHENTICATION, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Lifetime duration for tenant user access JWT in seconds", null, 300L, 604800L
        ));
        register(new ConfigurationDefinition(
                "JWT_SECRET", ConfigurationScope.AUTHENTICATION, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, true, false,
                "HMAC-SHA256 signing secret key (minimum 256-bit entropy)", null, null, null
        ));

        // MOEIRS / EIRS SCOPE
        register(new ConfigurationDefinition(
                "MOR_GATEWAY_URL", ConfigurationScope.MOEIRS, ConfigurationValueType.URL,
                ConfigurationClassification.CRITICAL, false, true, false,
                "FDRE Ministry of Revenues core EIRS gateway base URL (strict SSRF checks enforced)", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MOR_CLIENT_ID", ConfigurationScope.MOEIRS, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, true, false,
                "MoR registered software vendor client ID", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MOR_CLIENT_SECRET", ConfigurationScope.MOEIRS, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, true, false,
                "MoR registered software vendor OAuth client secret", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MOR_API_KEY", ConfigurationScope.MOEIRS, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, true, false,
                "MoR fiscal gateway API authorization key", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MOR_SELLER_TIN", ConfigurationScope.MOEIRS, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, true, false,
                "Vendor taxpayer identification number for platform gateway registration", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MOR_SYSTEM_NUMBER", ConfigurationScope.MOEIRS, ConfigurationValueType.STRING,
                ConfigurationClassification.HIGH, false, true, false,
                "MoR certified platform equipment registration number", null, null, null
        ));
        register(new ConfigurationDefinition(
                "MOR_TIMEOUT_MS", ConfigurationScope.MOEIRS, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "HTTP connect and read timeout for MoR gateway endpoints in milliseconds", null, 1000L, 60000L
        ));
        register(new ConfigurationDefinition(
                "MOR_INTEGRATION_ENABLED", ConfigurationScope.MOEIRS, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.CRITICAL, false, true, false,
                "EMERGENCY KILL SWITCH: Live MoR invoice dispatching toggle switch", null, null, null
        ));

        // SMS SCOPE
        register(new ConfigurationDefinition(
                "SMS_ENABLED", ConfigurationScope.SMS, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.HIGH, false, true, false,
                "EMERGENCY KILL SWITCH: Platform-wide customer SMS dispatch toggle switch", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMS_PROVIDER", ConfigurationScope.SMS, ConfigurationValueType.ENUM,
                ConfigurationClassification.HIGH, false, false, false,
                "Designated SMS gateway provider implementation (GeezSMS is mock-only)", List.of("MOCK_GEEZSMS"), null, null
        ));
        register(new ConfigurationDefinition(
                "SMS_LIVE_INTEGRATION_BLOCKED", ConfigurationScope.SMS, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.CRITICAL, false, false, false,
                "STATUTORY SAFETY LOCK: GeezSMS live third-party egress permanently blocked", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMS_RECONCILIATION_SLA_HOURS", ConfigurationScope.SMS, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Reconciliation timeout SLA for unknown/pending SMS message states", null, 1L, 72L
        ));
        register(new ConfigurationDefinition(
                "SMS_DAILY_LIMIT", ConfigurationScope.SMS, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Platform maximum aggregate SMS dispatch quota limit per calendar day", null, 100L, 1000000L
        ));
        register(new ConfigurationDefinition(
                "ETHIO_TELECOM_SMS_KEY", ConfigurationScope.SMS, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, true, false,
                "Ethio Telecom transactional SMS gateway authentication API key / bearer token", null, null, null
        ));
        register(new ConfigurationDefinition(
                "ETHIO_TELECOM_SMS_URL", ConfigurationScope.SMS, ConfigurationValueType.URL,
                ConfigurationClassification.HIGH, false, true, false,
                "Ethio Telecom transactional SMS gateway HTTP/REST endpoint URL", null, null, null
        ));
        register(new ConfigurationDefinition(
                "ETHIO_TELECOM_SMS_SENDER_ID", ConfigurationScope.SMS, ConfigurationValueType.STRING,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Ethio Telecom approved alphanumeric SMS Sender ID header (e.g. MoR-EIRS)", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMS_API_KEY", ConfigurationScope.SMS, ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, true, true, false,
                "Primary transactional SMS provider API Key / Bearer credential", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMS_GATEWAY_URL", ConfigurationScope.SMS, ConfigurationValueType.URL,
                ConfigurationClassification.HIGH, false, true, false,
                "Primary transactional SMS gateway endpoint URL", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMS_SENDER_ID", ConfigurationScope.SMS, ConfigurationValueType.STRING,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Approved alphanumeric SMS Sender Header ID (e.g. MoR-EIRS)", null, null, null
        ));

        // EMAIL SCOPE
        register(new ConfigurationDefinition(
                "SMTP_HOST", ConfigurationScope.EMAIL, ConfigurationValueType.STRING,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Outgoing SMTP server hostname", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMTP_PORT", ConfigurationScope.EMAIL, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Outgoing SMTP server port (STARTTLS)", null, 1L, 65535L
        ));
        register(new ConfigurationDefinition(
                "SMTP_USERNAME", ConfigurationScope.EMAIL, ConfigurationValueType.STRING,
                ConfigurationClassification.MEDIUM, false, true, false,
                "SMTP authentication username / account email address", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMTP_FROM", ConfigurationScope.EMAIL, ConfigurationValueType.STRING,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Authoritative from-address for platform notification emails", null, null, null
        ));
        register(new ConfigurationDefinition(
                "SMTP_PASSWORD", ConfigurationScope.EMAIL, ConfigurationValueType.SECRET,
                ConfigurationClassification.HIGH, true, true, false,
                "SMTP authentication password secret", null, null, null
        ));
        register(new ConfigurationDefinition(
                "EMAIL_DELIVERY_ENABLED", ConfigurationScope.EMAIL, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.HIGH, false, true, false,
                "EMERGENCY KILL SWITCH: Platform-wide email notification dispatch toggle", null, null, null
        ));

        // STORAGE SCOPE
        register(new ConfigurationDefinition(
                "STORAGE_MAX_FILE_SIZE_MB", ConfigurationScope.STORAGE, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Maximum allowed binary PDF and fiscal export upload size in megabytes", null, 1L, 100L
        ));

        // OBSERVABILITY SCOPE
        register(new ConfigurationDefinition(
                "METRICS_ENABLED", ConfigurationScope.OBSERVABILITY, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.LOW, false, true, false,
                "Enable Prometheus telemetry metrics export endpoint", null, null, null
        ));
        register(new ConfigurationDefinition(
                "HEALTH_CHECK_SHOW_DETAILS", ConfigurationScope.OBSERVABILITY, ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Disclose detailed subsystem health check traces to unauthenticated probes", null, null, null
        ));

        // RATE LIMITING SCOPE
        register(new ConfigurationDefinition(
                "RATE_LIMIT_REQUESTS_PER_MINUTE", ConfigurationScope.RATE_LIMITING, ConfigurationValueType.INTEGER,
                ConfigurationClassification.MEDIUM, false, true, false,
                "Standard per-client API gateway ingress throttle bucket capacity", null, 10L, 10000L
        ));
    }

    private void register(ConfigurationDefinition def) {
        definitions.put(def.keyName().toUpperCase(), def);
    }

    public Optional<ConfigurationDefinition> getDefinition(String keyName) {
        if (keyName == null) return Optional.empty();
        return Optional.ofNullable(definitions.get(keyName.trim().toUpperCase()));
    }

    public boolean isAllowlisted(String keyName) {
        if (keyName == null) return false;
        return definitions.containsKey(keyName.trim().toUpperCase());
    }

    public Collection<ConfigurationDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }
}
