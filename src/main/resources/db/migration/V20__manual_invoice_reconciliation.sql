-- ==============================================================================
-- Flyway Migration V20: Manual Paper/QR Fallback & Reconciliation
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 22
-- ==============================================================================

CREATE TABLE IF NOT EXISTS manual_fiscal_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    manual_document_number VARCHAR(64) NOT NULL,
    manual_book VARCHAR(64) NOT NULL,
    original_issue_time TIMESTAMPTZ NOT NULL,
    entered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    customer_tin VARCHAR(16),
    customer_name VARCHAR(255),
    total_amount NUMERIC(18, 2) NOT NULL,
    subtotal NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL,
    items_json TEXT NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    outage_reference VARCHAR(128) NOT NULL,
    reconciliation_deadline TIMESTAMPTZ NOT NULL,
    eirs_registration_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    irn VARCHAR(128),
    duplicate_reprint_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_manual_doc UNIQUE (tenant_id, manual_book, manual_document_number),
    CONSTRAINT chk_manual_reprint_count CHECK (duplicate_reprint_count >= 0),
    CONSTRAINT chk_manual_eirs_state CHECK (eirs_registration_state IN ('PENDING', 'REGISTERED', 'FAILED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_manual_docs_tenant ON manual_fiscal_documents(tenant_id, eirs_registration_state);
CREATE INDEX IF NOT EXISTS idx_manual_docs_deadline ON manual_fiscal_documents(tenant_id, reconciliation_deadline);
CREATE INDEX IF NOT EXISTS idx_manual_docs_book ON manual_fiscal_documents(tenant_id, manual_book, manual_document_number);

-- RLS Enforcement
ALTER TABLE manual_fiscal_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE manual_fiscal_documents FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'manual_fiscal_documents' AND policyname = 'tenant_isolation_manual_fiscal_documents'
    ) THEN
        CREATE POLICY tenant_isolation_manual_fiscal_documents ON manual_fiscal_documents
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
