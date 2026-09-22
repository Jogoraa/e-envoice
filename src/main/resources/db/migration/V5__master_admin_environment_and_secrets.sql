-- ==============================================================================
-- UT INVOICE — CANONICAL DATABASE MIGRATION
-- Migration: V5__master_admin_environment_and_secrets.sql
-- Classification: Master Admin Secure Environment & Encrypted Secrets Subsystem
-- Authoritative Security: NIST SP 800-63B Step-Up MFA, AES-256-GCM Cryptographic Storage,
--                          Strict Allowlisting, Immutability & Anti-Tamper Revisions
-- ==============================================================================

-- 1. SHORT-LIVED PRIVILEGED CONFIGURATION SESSIONS (MFA STEP-UP CEREMONY)
CREATE TABLE IF NOT EXISTS privileged_configuration_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    username VARCHAR(64) NOT NULL,
    mfa_verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revocation_reason VARCHAR(128),
    ip_address VARCHAR(64),
    user_agent VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_priv_sessions_lookup ON privileged_configuration_sessions(id, revoked, expires_at);
CREATE INDEX IF NOT EXISTS idx_priv_sessions_user ON privileged_configuration_sessions(user_id);

-- 2. CONFIGURATION REVISIONS TABLE (IMMUTABLE AUDIT TRAIL OF CONFIGURATION SETS)
CREATE TABLE IF NOT EXISTS configuration_revisions (
    id UUID PRIMARY KEY,
    revision_number BIGINT UNIQUE NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    change_summary TEXT NOT NULL,
    rollback_from_revision BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_revisions_number ON configuration_revisions(revision_number DESC);

-- 3. MASTER ALLOWLISTED CONFIGURATION ENTRIES TABLE
CREATE TABLE IF NOT EXISTS configuration_entries (
    id UUID PRIMARY KEY,
    scope VARCHAR(32) NOT NULL,
    key_name VARCHAR(128) UNIQUE NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    current_value TEXT,
    encrypted_secret_payload TEXT,
    secret_fingerprint VARCHAR(64),
    is_secret BOOLEAN NOT NULL DEFAULT FALSE,
    is_runtime_mutable BOOLEAN NOT NULL DEFAULT TRUE,
    requires_restart BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    version BIGINT NOT NULL DEFAULT 1,
    description TEXT,
    allowed_values TEXT,
    validation_pattern VARCHAR(256),
    min_value BIGINT,
    max_value BIGINT,
    updated_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM_BOOTSTRAP',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_entries_scope ON configuration_entries(scope);
CREATE INDEX IF NOT EXISTS idx_config_entries_key ON configuration_entries(key_name);

-- 4. CONFIGURATION REVISION ENTRIES TABLE (DELTA HISTORY PER REVISION)
CREATE TABLE IF NOT EXISTS configuration_revision_entries (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES configuration_revisions(id) ON DELETE CASCADE,
    key_name VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    old_value_classification VARCHAR(32),
    new_value_classification VARCHAR(32),
    old_value_masked TEXT,
    new_value_masked TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_rev_entries_rev ON configuration_revision_entries(revision_id);

-- 5. INITIAL BASELINE REVISION #1
INSERT INTO configuration_revisions (id, revision_number, created_by, change_summary, status, created_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    1,
    'SYSTEM_BOOTSTRAP',
    'Initial certified production environment configuration baseline',
    'APPLIED',
    CURRENT_TIMESTAMP
) ON CONFLICT (revision_number) DO NOTHING;

-- 6. SEED AUTHORITATIVE ALLOWLISTED CONFIGURATION DEFINITIONS AND BASELINE VALUES
INSERT INTO configuration_entries (
    id, scope, key_name, value_type, classification, current_value, is_secret,
    is_runtime_mutable, requires_restart, status, version, description,
    allowed_values, min_value, max_value, updated_by
) VALUES
-- APPLICATION SCOPE
(
    'a1000000-0000-0000-0000-000000000001', 'APPLICATION', 'SERVER_PORT', 'INTEGER', 'LOW',
    '8081', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'HTTP server listening port for platform container', NULL, 1024, 65535, 'SYSTEM_BOOTSTRAP'
),
(
    'a1000000-0000-0000-0000-000000000002', 'APPLICATION', 'LOG_LEVEL', 'ENUM', 'LOW',
    'INFO', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Root logging verbosity level', 'TRACE,DEBUG,INFO,WARN,ERROR', NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a1000000-0000-0000-0000-000000000003', 'APPLICATION', 'PLATFORM_SYSTEM_TYPE', 'ENUM', 'LOW',
    'ERP', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Authoritative statutory system deployment classification (Directive Art. 11)', 'ERP,POS,BILLING', NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a1000000-0000-0000-0000-000000000004', 'APPLICATION', 'PLATFORM_DEFAULT_CURRENCY', 'STRING', 'LOW',
    'ETB', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Sovereign accounting currency default ISO-4217 code', 'ETB,USD,EUR', NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a1000000-0000-0000-0000-000000000005', 'APPLICATION', 'OFFLINE_BUFFER_MAX_AGE_HOURS', 'INTEGER', 'HIGH',
    '72', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Mandatory statutory offline buffer synchronization ceiling before audit flagging', NULL, 1, 168, 'SYSTEM_BOOTSTRAP'
),
(
    'a1000000-0000-0000-0000-000000000006', 'APPLICATION', 'CANCELLATION_SLA_HOURS', 'INTEGER', 'HIGH',
    '48', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Legal invoice cancellation approval statutory time window', NULL, 1, 72, 'SYSTEM_BOOTSTRAP'
),

-- DATABASE SCOPE
(
    'a2000000-0000-0000-0000-000000000001', 'DATABASE', 'DATABASE_URL', 'URL', 'CRITICAL',
    'jdbc:postgresql://localhost:5435/ut_einvoice_db', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'Primary PostgreSQL cluster JDBC connection URL', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a2000000-0000-0000-0000-000000000002', 'DATABASE', 'DATABASE_USERNAME', 'STRING', 'HIGH',
    'postgres', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'Database connection master user principal', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a2000000-0000-0000-0000-000000000003', 'DATABASE', 'DATABASE_PASSWORD', 'SECRET', 'CRITICAL',
    NULL, TRUE, FALSE, TRUE, 'APPLIED', 1,
    'Database master authentication secret', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a2000000-0000-0000-0000-000000000004', 'DATABASE', 'DATABASE_POOL_SIZE', 'INTEGER', 'HIGH',
    '20', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'HikariCP connection pool maximum active connections', NULL, 5, 100, 'SYSTEM_BOOTSTRAP'
),
(
    'a2000000-0000-0000-0000-000000000005', 'DATABASE', 'DATABASE_CONNECTION_TIMEOUT_MS', 'INTEGER', 'MEDIUM',
    '20000', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'HikariCP database connection timeout in milliseconds', NULL, 1000, 60000, 'SYSTEM_BOOTSTRAP'
),

-- REDIS SCOPE
(
    'a3000000-0000-0000-0000-000000000001', 'REDIS', 'REDIS_HOST', 'STRING', 'HIGH',
    'localhost', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'Redis cluster hostname for token and cache distribution', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a3000000-0000-0000-0000-000000000002', 'REDIS', 'REDIS_PORT', 'INTEGER', 'HIGH',
    '6379', FALSE, FALSE, TRUE, 'APPLIED', 1,
    'Redis listening port', NULL, 1, 65535, 'SYSTEM_BOOTSTRAP'
),
(
    'a3000000-0000-0000-0000-000000000003', 'REDIS', 'REDIS_PASSWORD', 'SECRET', 'CRITICAL',
    NULL, TRUE, FALSE, TRUE, 'APPLIED', 1,
    'Redis cluster authentication token', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a3000000-0000-0000-0000-000000000004', 'REDIS', 'REDIS_TIMEOUT_MS', 'INTEGER', 'MEDIUM',
    '2000', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Redis operation socket read timeout in milliseconds', NULL, 100, 30000, 'SYSTEM_BOOTSTRAP'
),

-- SECURITY SCOPE
(
    'a4000000-0000-0000-0000-000000000001', 'SECURITY', 'CORS_ALLOWED_ORIGINS', 'STRING', 'MEDIUM',
    'http://localhost:3000,http://localhost:8080', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Comma-separated list of approved browser origin domains for CORS headers', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a4000000-0000-0000-0000-000000000002', 'SECURITY', 'MFA_HARDWARE_REQUIRED', 'BOOLEAN', 'HIGH',
    'true', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Enforce hardware/TOTP MFA ceremony for Master Admin sessions', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a4000000-0000-0000-0000-000000000003', 'SECURITY', 'ENFORCE_SINGLE_FLIGHT_REFRESH', 'BOOLEAN', 'MEDIUM',
    'true', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Prevent concurrent duplicate OAuth token refresh races across microservices', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),

-- AUTHENTICATION SCOPE
(
    'a5000000-0000-0000-0000-000000000001', 'AUTHENTICATION', 'JWT_ISSUER', 'STRING', 'HIGH',
    'ut-einvoice-platform', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Expected JWT issuer claim string for all platform security contexts', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a5000000-0000-0000-0000-000000000002', 'AUTHENTICATION', 'JWT_AUDIENCE', 'STRING', 'HIGH',
    'ut-invoice-tenant', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Default audience claim identifier for issued tokens', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a5000000-0000-0000-0000-000000000003', 'AUTHENTICATION', 'JWT_ACCESS_TOKEN_TTL_SECONDS', 'INTEGER', 'MEDIUM',
    '86400', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Lifetime duration for tenant user access JWT in seconds', NULL, 300, 604800, 'SYSTEM_BOOTSTRAP'
),
(
    'a5000000-0000-0000-0000-000000000004', 'AUTHENTICATION', 'JWT_SECRET', 'SECRET', 'CRITICAL',
    NULL, TRUE, TRUE, FALSE, 'APPLIED', 1,
    'HMAC-SHA256 signing secret key (minimum 256-bit entropy)', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),

-- MOEIRS / EIRS SCOPE
(
    'a6000000-0000-0000-0000-000000000001', 'MOEIRS', 'MOR_GATEWAY_URL', 'URL', 'CRITICAL',
    'http://core.mor.gov.et', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'FDRE Ministry of Revenues core EIRS gateway base URL (strict SSRF checks enforced)', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000002', 'MOEIRS', 'MOR_CLIENT_ID', 'STRING', 'HIGH',
    'UT_PLATFORM_OPERATOR_001', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'MoR registered software vendor client ID', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000003', 'MOEIRS', 'MOR_CLIENT_SECRET', 'SECRET', 'CRITICAL',
    NULL, TRUE, TRUE, FALSE, 'APPLIED', 1,
    'MoR registered software vendor OAuth client secret', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000004', 'MOEIRS', 'MOR_API_KEY', 'SECRET', 'CRITICAL',
    NULL, TRUE, TRUE, FALSE, 'APPLIED', 1,
    'MoR fiscal gateway API authorization key', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000005', 'MOEIRS', 'MOR_SELLER_TIN', 'STRING', 'HIGH',
    '0011223344', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Vendor taxpayer identification number for platform gateway registration', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000006', 'MOEIRS', 'MOR_SYSTEM_NUMBER', 'STRING', 'HIGH',
    '8EFBBDD7FF', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'MoR certified platform equipment registration number', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000007', 'MOEIRS', 'MOR_TIMEOUT_MS', 'INTEGER', 'MEDIUM',
    '10000', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'HTTP connect and read timeout for MoR gateway endpoints in milliseconds', NULL, 1000, 60000, 'SYSTEM_BOOTSTRAP'
),
(
    'a6000000-0000-0000-0000-000000000008', 'MOEIRS', 'MOR_INTEGRATION_ENABLED', 'BOOLEAN', 'CRITICAL',
    'true', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'EMERGENCY KILL SWITCH: Live MoR invoice dispatching toggle switch', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),

-- SMS SCOPE
(
    'a7000000-0000-0000-0000-000000000001', 'SMS', 'SMS_ENABLED', 'BOOLEAN', 'HIGH',
    'true', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'EMERGENCY KILL SWITCH: Platform-wide customer SMS dispatch toggle switch', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a7000000-0000-0000-0000-000000000002', 'SMS', 'SMS_PROVIDER', 'ENUM', 'HIGH',
    'MOCK_GEEZSMS', FALSE, FALSE, FALSE, 'APPLIED', 1,
    'Designated SMS gateway provider implementation (GeezSMS is mock-only)', 'MOCK_GEEZSMS', NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a7000000-0000-0000-0000-000000000003', 'SMS', 'SMS_LIVE_INTEGRATION_BLOCKED', 'BOOLEAN', 'CRITICAL',
    'true', FALSE, FALSE, FALSE, 'APPLIED', 1,
    'STATUTORY SAFETY LOCK: GeezSMS live third-party egress permanently blocked', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a7000000-0000-0000-0000-000000000004', 'SMS', 'SMS_RECONCILIATION_SLA_HOURS', 'INTEGER', 'MEDIUM',
    '24', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Reconciliation timeout SLA for unknown/pending SMS message states', NULL, 1, 72, 'SYSTEM_BOOTSTRAP'
),
(
    'a7000000-0000-0000-0000-000000000005', 'SMS', 'SMS_DAILY_LIMIT', 'INTEGER', 'MEDIUM',
    '50000', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Platform maximum aggregate SMS dispatch quota limit per calendar day', NULL, 100, 1000000, 'SYSTEM_BOOTSTRAP'
),

-- EMAIL SCOPE
(
    'a8000000-0000-0000-0000-000000000001', 'EMAIL', 'SMTP_HOST', 'STRING', 'MEDIUM',
    'mail.utsolutionsplc.com', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Outgoing SMTP server hostname', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a8000000-0000-0000-0000-000000000002', 'EMAIL', 'SMTP_PORT', 'INTEGER', 'MEDIUM',
    '587', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Outgoing SMTP server port (STARTTLS)', NULL, 1, 65535, 'SYSTEM_BOOTSTRAP'
),
(
    'a8000000-0000-0000-0000-000000000003', 'EMAIL', 'SMTP_FROM', 'STRING', 'MEDIUM',
    'no-reply@ut-invoice.et', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Authoritative from-address for platform notification emails', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a8000000-0000-0000-0000-000000000004', 'EMAIL', 'SMTP_PASSWORD', 'SECRET', 'HIGH',
    NULL, TRUE, TRUE, FALSE, 'APPLIED', 1,
    'SMTP authentication password secret', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'a8000000-0000-0000-0000-000000000005', 'EMAIL', 'EMAIL_DELIVERY_ENABLED', 'BOOLEAN', 'HIGH',
    'true', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'EMERGENCY KILL SWITCH: Platform-wide email notification dispatch toggle', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),

-- STORAGE SCOPE
(
    'a9000000-0000-0000-0000-000000000001', 'STORAGE', 'STORAGE_MAX_FILE_SIZE_MB', 'INTEGER', 'MEDIUM',
    '10', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Maximum allowed binary PDF and fiscal export upload size in megabytes', NULL, 1, 100, 'SYSTEM_BOOTSTRAP'
),

-- OBSERVABILITY SCOPE
(
    'aa000000-0000-0000-0000-000000000001', 'OBSERVABILITY', 'METRICS_ENABLED', 'BOOLEAN', 'LOW',
    'true', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Enable Prometheus telemetry metrics export endpoint', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),
(
    'aa000000-0000-0000-0000-000000000002', 'OBSERVABILITY', 'HEALTH_CHECK_SHOW_DETAILS', 'BOOLEAN', 'MEDIUM',
    'false', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Disclose detailed subsystem health check traces to unauthenticated probes', NULL, NULL, NULL, 'SYSTEM_BOOTSTRAP'
),

-- RATE LIMITING SCOPE
(
    'ab000000-0000-0000-0000-000000000001', 'RATE_LIMITING', 'RATE_LIMIT_REQUESTS_PER_MINUTE', 'INTEGER', 'MEDIUM',
    '120', FALSE, TRUE, FALSE, 'APPLIED', 1,
    'Standard per-client API gateway ingress throttle bucket capacity', NULL, 10, 10000, 'SYSTEM_BOOTSTRAP'
)
ON CONFLICT (key_name) DO NOTHING;
