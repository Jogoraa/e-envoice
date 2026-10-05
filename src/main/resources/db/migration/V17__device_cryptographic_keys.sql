-- ==============================================================================
-- Flyway Migration V17: Offline Device Cryptographic Identity & Public Keys
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 4(6)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS device_public_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    key_version INT NOT NULL DEFAULT 1,
    algorithm VARCHAR(32) NOT NULL DEFAULT 'RSA-2048',
    public_key_pem TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    CONSTRAINT uk_tenant_device_key_ver UNIQUE (tenant_id, device_id, key_version)
);

CREATE INDEX IF NOT EXISTS idx_device_keys_lookup ON device_public_keys(tenant_id, device_id, status);

-- Enable and FORCE Row Level Security on device_public_keys
ALTER TABLE device_public_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_public_keys FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_device_public_keys_rls_policy ON device_public_keys;
CREATE POLICY tenant_device_public_keys_rls_policy ON device_public_keys
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );
