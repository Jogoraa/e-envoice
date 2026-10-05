-- V11__tenant_government_credentials.sql
-- Directive No. 1142/2026 Art. 19(5)
-- Per-Tenant Government (MoR / EIRS / INSA) Credentials Vault with RLS and Audit

CREATE TABLE IF NOT EXISTS tenant_government_credentials (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    mor_system_number VARCHAR(128) NOT NULL,
    seller_tin VARCHAR(16) NOT NULL,
    taxpayer_registration_identity VARCHAR(128),
    encrypted_client_id TEXT NOT NULL,
    encrypted_client_secret TEXT NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    key_vault_version INT NOT NULL DEFAULT 1,
    insa_certificate_reference VARCHAR(256),
    certificate_serial VARCHAR(128),
    certificate_expiry TIMESTAMPTZ,
    credential_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, EXPIRED, REVOKED
    last_validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_gov_cred UNIQUE (tenant_id)
);

CREATE INDEX idx_tenant_gov_cred_tenant ON tenant_government_credentials(tenant_id);
CREATE INDEX idx_tenant_gov_cred_tin ON tenant_government_credentials(seller_tin);
CREATE INDEX idx_tenant_gov_cred_status ON tenant_government_credentials(credential_status);

ALTER TABLE tenant_government_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_government_credentials FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_gov_credentials_rls_policy ON tenant_government_credentials;
CREATE POLICY tenant_gov_credentials_rls_policy ON tenant_government_credentials
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );
