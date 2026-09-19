-- V7__immutable_audit_hardening.sql
-- Development-stage hardening for immutable audit evidence, streams, checkpoints, and outbox

-- 1. Upgrade audit_events table
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS sequence_number BIGINT NOT NULL DEFAULT 1;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS schema_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS application_version VARCHAR(32) NOT NULL DEFAULT '1.0.0-RELEASE';

CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_stream_seq ON audit_events(tenant_id, stream_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_audit_events_hash ON audit_events(event_hash);

-- 2. Audit Streams for atomic per-stream concurrency control
CREATE TABLE IF NOT EXISTS audit_streams (
    tenant_id UUID NOT NULL,
    stream_id VARCHAR(64) NOT NULL,
    last_sequence_number BIGINT NOT NULL DEFAULT 0,
    last_event_hash VARCHAR(64) NOT NULL,
    last_event_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_streams PRIMARY KEY (tenant_id, stream_id)
);

-- 3. Transactional Audit Outbox
CREATE TABLE IF NOT EXISTS audit_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    audit_event_id UUID NOT NULL,
    stream_id VARCHAR(64) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    canonical_payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_audit_outbox_status_next ON audit_outbox_events(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_audit_outbox_tenant_stream ON audit_outbox_events(tenant_id, stream_id);

-- 4. Audit Checkpoints
CREATE TABLE IF NOT EXISTS audit_checkpoints (
    checkpoint_id UUID PRIMARY KEY,
    tenant_id UUID,
    stream_id VARCHAR(64) NOT NULL,
    first_event_sequence BIGINT NOT NULL,
    last_event_sequence BIGINT NOT NULL,
    event_count BIGINT NOT NULL,
    first_event_hash VARCHAR(64) NOT NULL,
    last_event_hash VARCHAR(64) NOT NULL,
    chain_state_hash VARCHAR(64) NOT NULL,
    previous_checkpoint_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    schema_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_audit_checkpoints_lookup ON audit_checkpoints(tenant_id, stream_id, last_event_sequence);

-- 5. Evidence Manifests
CREATE TABLE IF NOT EXISTS evidence_manifests (
    artifact_id UUID PRIMARY KEY,
    tenant_id UUID,
    artifact_type VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    previous_artifact_hash VARCHAR(64) NOT NULL,
    producer_version VARCHAR(32) NOT NULL DEFAULT '1.0.0-RELEASE',
    schema_version INTEGER NOT NULL DEFAULT 1,
    signature_metadata VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evidence_manifests_tenant ON evidence_manifests(tenant_id, artifact_type);

-- 6. Enforce Append-Only Immutability on audit_events
CREATE OR REPLACE FUNCTION enforce_audit_events_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'AUDIT_TRAIL_IMMUTABLE: Historical audit events cannot be modified once persisted.';
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'AUDIT_TRAIL_IMMUTABLE: Historical audit events cannot be deleted once persisted.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_events_immutability ON audit_events;
CREATE TRIGGER trg_audit_events_immutability
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION enforce_audit_events_immutability();
