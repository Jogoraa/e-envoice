-- V4__production_hardening.sql
-- Production Hardening Schema: Outbox, Inbox, Idempotency, Government Submissions, Versioned Tax Rules, Webhooks, Usage, Documents, Sharding, Device Trust

-- 1. Distributed Idempotency Records (Scoped to Tenant + Client)
CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT_CLIENT',
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    status VARCHAR(32) NOT NULL, -- 'PROCESSING', 'COMPLETED', 'FAILED'
    response_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tenant_client_idempotency UNIQUE (tenant_id, client_id, idempotency_key)
);

CREATE INDEX idx_idempotency_lookup ON idempotency_records(tenant_id, client_id, idempotency_key);
CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);

-- 2. First-Class Government Submissions State Machine
CREATE TABLE IF NOT EXISTS government_submissions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    provider_version VARCHAR(32) NOT NULL,
    submission_id VARCHAR(128) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED', -- 'QUEUED', 'IN_FLIGHT', 'ACCEPTED', 'REJECTED', 'UNKNOWN', 'NEEDS_RECONCILIATION'
    attempt_count INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    government_reference VARCHAR(128),
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gov_submissions_status ON government_submissions(status, next_retry_at);
CREATE INDEX idx_gov_submissions_invoice ON government_submissions(tenant_id, invoice_id);

-- 3. Transactional Outbox Pattern for Non-Blocking Gateway Dispatch
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED', 'DEAD_LETTER'
    last_error TEXT
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_events(status, next_attempt_at);
CREATE INDEX idx_outbox_tenant_aggregate ON outbox_events(tenant_id, aggregate_type, aggregate_id);

-- 4. Inbound Event Deduplication Inbox
CREATE TABLE IF NOT EXISTS inbox_events (
    id UUID PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    CONSTRAINT uk_source_event UNIQUE (source, event_id)
);

CREATE INDEX idx_inbox_source_event ON inbox_events(source, event_id);

-- 5. Versioned Tax Rules Engine
CREATE TABLE IF NOT EXISTS tax_rules (
    id UUID PRIMARY KEY,
    tax_code VARCHAR(32) NOT NULL,
    tax_type VARCHAR(32) NOT NULL, -- 'VAT', 'TOT', 'EXCISE', 'WITHHOLDING'
    rate NUMERIC(8, 4) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tax_rules_lookup ON tax_rules(tax_code, is_active, effective_from);

-- Seed Ethiopian Standard Tax Rules
INSERT INTO tax_rules (id, tax_code, tax_type, rate, effective_from, is_active, version)
VALUES
    ('c1111111-1111-1111-1111-111111111101', 'VAT15', 'VAT', 0.1500, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111102', 'VAT0', 'VAT', 0.0000, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111103', 'VATEX', 'VAT', 0.0000, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111104', 'TOT_2', 'TOT', 0.0200, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111105', 'TOT_10', 'TOT', 0.1000, '2020-01-01 00:00:00+00', TRUE, 1)
ON CONFLICT (id) DO NOTHING;

-- 6. Webhook Subscriptions
CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_url VARCHAR(512) NOT NULL,
    secret_key VARCHAR(128) NOT NULL,
    subscribed_events VARCHAR(512) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_subs_tenant ON webhook_subscriptions(tenant_id, is_active);

-- 7. SaaS Usage Metering
CREATE TABLE IF NOT EXISTS usage_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    metric_type VARCHAR(64) NOT NULL,
    quantity NUMERIC(14, 2) NOT NULL DEFAULT 1.00,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resource_id VARCHAR(128)
);

CREATE INDEX idx_usage_tenant_time ON usage_records(tenant_id, recorded_at DESC);

-- 8. Document Metadata & Object Storage Tracking
CREATE TABLE IF NOT EXISTS stored_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    invoice_id UUID,
    document_type VARCHAR(32) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stored_docs_tenant_inv ON stored_documents(tenant_id, invoice_id);

-- 9. Shard Assignment & Scale Routing Control Plane
CREATE TABLE IF NOT EXISTS tenant_shards (
    id UUID PRIMARY KEY,
    shard_name VARCHAR(64) NOT NULL UNIQUE,
    cluster_url VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    max_tenants INTEGER NOT NULL DEFAULT 10000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tenant_shards (id, shard_name, cluster_url, is_active, max_tenants)
VALUES
    ('a1111111-1111-1111-1111-111111111101', 'shared_cluster', 'jdbc:postgresql://localhost:5432/ut_einvoice_db', TRUE, 500000),
    ('a1111111-1111-1111-1111-111111111102', 'enterprise_shard_01', 'jdbc:postgresql://localhost:5432/ut_enterprise_01', TRUE, 10000)
ON CONFLICT (shard_name) DO NOTHING;

-- 10. Device Trust & Revocation
CREATE TABLE IF NOT EXISTS device_revocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID NOT NULL,
    revocation_reason VARCHAR(255) NOT NULL,
    revoked_by VARCHAR(64) NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_revocations_lookup ON device_revocations(tenant_id, device_id);

-- 11. Asynchronous Data Portability Export Jobs
CREATE TABLE IF NOT EXISTS export_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    artifact_url VARCHAR(512),
    artifact_checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_export_jobs_tenant ON export_jobs(tenant_id, status);

-- 12. Scoped Audit Stream Column
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS stream_id VARCHAR(64) NOT NULL DEFAULT 'MAIN';
CREATE INDEX IF NOT EXISTS idx_audit_stream ON audit_events(tenant_id, stream_id, timestamp DESC);

-- 13. Offline Buffer Protocol Enhancements
ALTER TABLE offline_transaction_buffer ADD COLUMN IF NOT EXISTS offline_session_id VARCHAR(64);
ALTER TABLE offline_transaction_buffer ADD COLUMN IF NOT EXISTS client_transaction_id VARCHAR(128);
ALTER TABLE offline_transaction_buffer ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'UPLOADED';
