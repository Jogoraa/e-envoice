-- V3__init_audit_and_compliance.sql
-- Audit & Compliance Schema: Immutable Operation Audit Log, Releases, Outbound Webhooks

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor_id VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    client_id VARCHAR(64),
    device_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    client_ip VARCHAR(45),
    user_agent VARCHAR(255),
    payload_hash VARCHAR(64) NOT NULL,
    previous_event_hash VARCHAR(64) NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_events_tenant_time ON audit_events(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_events_resource ON audit_events(resource_type, resource_id);

CREATE TABLE IF NOT EXISTS compliance_releases (
    id UUID PRIMARY KEY,
    system_version VARCHAR(32) NOT NULL,
    build_version VARCHAR(64) NOT NULL,
    git_commit VARCHAR(64) NOT NULL,
    artifact_checksum VARCHAR(64) NOT NULL,
    insa_cert_ref VARCHAR(128) NOT NULL,
    deployed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_by VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS outbound_webhook_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    target_url VARCHAR(512) NOT NULL,
    payload_json TEXT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_attempt_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_deliveries_status ON outbound_webhook_deliveries(status);
