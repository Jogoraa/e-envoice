-- V13__tenant_lifecycle_notifications.sql
-- Directive No. 1142/2026 Art. 15(8)
-- Taxpayer Tenant Commencement and Termination Notification Workflow

CREATE TABLE IF NOT EXISTS tenant_lifecycle_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    event_type VARCHAR(32) NOT NULL, -- COMMENCEMENT, TERMINATION
    tin VARCHAR(16) NOT NULL,
    taxpayer_name VARCHAR(255) NOT NULL,
    system_number VARCHAR(128) NOT NULL,
    sector_code VARCHAR(32),
    effective_date TIMESTAMPTZ NOT NULL,
    notification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, SUBMITTED, ACKNOWLEDGED, FAILED, UNKNOWN, RECONCILIATION_REQUIRED
    mor_acknowledgement_reference VARCHAR(128),
    mor_response_payload TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_lifecycle_tenant ON tenant_lifecycle_events(tenant_id);
CREATE INDEX idx_tenant_lifecycle_status ON tenant_lifecycle_events(notification_status);
CREATE INDEX idx_tenant_lifecycle_type ON tenant_lifecycle_events(event_type);

ALTER TABLE tenant_lifecycle_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_lifecycle_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_lifecycle_events_rls_policy ON tenant_lifecycle_events;
CREATE POLICY tenant_lifecycle_events_rls_policy ON tenant_lifecycle_events
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );
