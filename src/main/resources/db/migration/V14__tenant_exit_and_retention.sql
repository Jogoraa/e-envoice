-- V14__tenant_exit_and_retention.sql
-- Directive No. 1142/2026 Art. 5(3), 14(3)(e), 17(4)
-- Data Portability, Dual-Authorization Tenant Exit, and Statutory Retention Classification

CREATE TABLE IF NOT EXISTS tenant_exit_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    requested_by VARCHAR(128) NOT NULL,
    authorized_by VARCHAR(128),
    exit_status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, FROZEN, EXPORT_GENERATED, ARCHIVE_VERIFIED, TENANT_CONFIRMED, RETENTION_EVALUATED, PURGED, MIGRATED, CANCELLED
    export_job_id UUID,
    archive_checksum VARCHAR(128),
    destination_provider VARCHAR(255),
    reason TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    frozen_at TIMESTAMPTZ,
    export_generated_at TIMESTAMPTZ,
    archive_verified_at TIMESTAMPTZ,
    tenant_confirmed_at TIMESTAMPTZ,
    purge_executed_at TIMESTAMPTZ,
    purge_certificate_number VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_exit_tenant ON tenant_exit_requests(tenant_id);
CREATE INDEX idx_tenant_exit_status ON tenant_exit_requests(exit_status);

ALTER TABLE tenant_exit_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_exit_requests FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_exit_requests_rls_policy ON tenant_exit_requests;
CREATE POLICY tenant_exit_requests_rls_policy ON tenant_exit_requests
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table for immutable purge audit certificates
CREATE TABLE IF NOT EXISTS purge_audit_certificates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    exit_request_id UUID NOT NULL REFERENCES tenant_exit_requests(id) ON DELETE RESTRICT,
    certificate_number VARCHAR(128) NOT NULL UNIQUE,
    records_purged_count BIGINT NOT NULL DEFAULT 0,
    statutory_records_retained_count BIGINT NOT NULL DEFAULT 0,
    purged_categories TEXT NOT NULL,
    retention_justification TEXT NOT NULL,
    authorized_by_tenant_admin VARCHAR(128) NOT NULL,
    authorized_by_platform_admin VARCHAR(128) NOT NULL,
    certificate_hash VARCHAR(128) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purge_cert_tenant ON purge_audit_certificates(tenant_id);

ALTER TABLE purge_audit_certificates ENABLE ROW LEVEL SECURITY;
ALTER TABLE purge_audit_certificates FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS purge_audit_certificates_rls_policy ON purge_audit_certificates;
CREATE POLICY purge_audit_certificates_rls_policy ON purge_audit_certificates
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Trigger to prevent any deletion or mutation of purge certificates
CREATE OR REPLACE FUNCTION enforce_purge_certificate_immutability()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'PURGE_CERTIFICATE_IMMUTABLE: Purge audit certificates cannot be updated or deleted by law';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_purge_cert_immutability ON purge_audit_certificates;
CREATE TRIGGER trg_purge_cert_immutability
BEFORE UPDATE OR DELETE ON purge_audit_certificates
FOR EACH ROW EXECUTE FUNCTION enforce_purge_certificate_immutability();
