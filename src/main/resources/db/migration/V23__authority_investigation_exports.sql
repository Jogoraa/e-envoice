-- ==============================================================================
-- Flyway Migration V23: Tax Authority Investigation Exports & Customer Inspection
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 15(5)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS authority_investigation_exports (
    id UUID PRIMARY KEY,
    case_reference VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    tenant_id UUID,
    customer_tin VARCHAR(32),
    date_from TIMESTAMPTZ,
    date_to TIMESTAMPTZ,
    invoice_range_start VARCHAR(64),
    invoice_range_end VARCHAR(64),
    transaction_type VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    record_count INT NOT NULL DEFAULT 0,
    payload_encrypted_base64 TEXT,
    encryption_algorithm VARCHAR(64) NOT NULL DEFAULT 'AES/GCM/NoPadding',
    sha256_checksum VARCHAR(64),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_auth_export_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_auth_export_case ON authority_investigation_exports(case_reference);
CREATE INDEX IF NOT EXISTS idx_auth_export_created ON authority_investigation_exports(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_auth_export_tenant ON authority_investigation_exports(tenant_id);
