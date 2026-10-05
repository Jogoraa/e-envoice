-- ==============================================================================
-- Flyway Migration V21: Statutory Cancellation State Machine & Evidence Workflow
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 26
-- ==============================================================================

ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS authority_evidence_requested_at TIMESTAMPTZ;
ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS evidence_deadline TIMESTAMPTZ;
ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS evidence_submitted_at TIMESTAMPTZ;
ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS mor_submission_id VARCHAR(128);
ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Create immutable evidence attachments table for cancellation proofs
CREATE TABLE IF NOT EXISTS cancellation_evidence_attachments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cancellation_request_id UUID NOT NULL REFERENCES cancellation_requests(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_checksum VARCHAR(128) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    description TEXT,
    uploaded_by VARCHAR(64) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_evidence_file_size CHECK (file_size > 0 AND file_size <= 20971520) -- 20MB max
);

CREATE INDEX IF NOT EXISTS idx_cancel_evidence_req ON cancellation_evidence_attachments(tenant_id, cancellation_request_id);

-- RLS Enforcement
ALTER TABLE cancellation_evidence_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE cancellation_evidence_attachments FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'cancellation_evidence_attachments' AND policyname = 'tenant_isolation_cancellation_evidence_attachments'
    ) THEN
        CREATE POLICY tenant_isolation_cancellation_evidence_attachments ON cancellation_evidence_attachments
            FOR ALL
            USING (
                tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
                OR NULLIF(current_setting('app.current_user_role', true), '') IN ('ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_ADMIN', 'ROLE_AUTHORITY_AUDITOR')
            )
            WITH CHECK (
                tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
                OR NULLIF(current_setting('app.current_user_role', true), '') IN ('ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_ADMIN', 'ROLE_AUTHORITY_AUDITOR')
            );
    END IF;
END $$;
