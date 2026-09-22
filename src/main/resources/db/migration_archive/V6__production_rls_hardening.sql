-- ==============================================================================
-- Migration: V6__production_rls_hardening.sql
-- Description: Concurrency-Safe Sequences, Immutability Triggers, RLS FORCE,
--              and Tenant Configuration & Feature Flags Schema
-- Authoritative Compliance: Directive No. 1142/2026 Concurrency & Isolation Gates
-- ==============================================================================

-- 1. Tenant-Specific Atomic Invoice Sequences
CREATE TABLE IF NOT EXISTS tenant_invoice_sequences (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    current_counter BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Database Constraint: Guarantee Unique Invoice Counter Per Tenant
ALTER TABLE invoices ADD CONSTRAINT uk_tenant_invoice_counter UNIQUE (tenant_id, invoice_counter);

-- 3. Tenant Configuration Overrides
CREATE TABLE IF NOT EXISTS tenant_configuration_overrides (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    safety_classification VARCHAR(32) NOT NULL DEFAULT 'TENANT_OVERRIDABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uk_tenant_config_key UNIQUE (tenant_id, config_key)
);

CREATE INDEX idx_tenant_config_lookup ON tenant_configuration_overrides(tenant_id, config_key);

-- 4. Tenant Feature Flags
CREATE TABLE IF NOT EXISTS tenant_feature_flags (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feature_key VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(32) NOT NULL DEFAULT 'OPTIONAL_PRODUCT_FEATURE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uk_tenant_feature_key UNIQUE (tenant_id, feature_key)
);

CREATE INDEX idx_tenant_feature_lookup ON tenant_feature_flags(tenant_id, feature_key);

-- 5. Outbox Worker Locking Index & Column
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS locked_by VARCHAR(64);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_outbox_events_poll ON outbox_events(status, next_attempt_at) WHERE status IN ('PENDING', 'FAILED');

-- 6. Apply FORCE ROW LEVEL SECURITY on all tenant-isolated tables
ALTER TABLE invoices FORCE ROW LEVEL SECURITY;
ALTER TABLE invoice_lines FORCE ROW LEVEL SECURITY;
ALTER TABLE tax_adjustments FORCE ROW LEVEL SECURITY;
ALTER TABLE cancellation_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
ALTER TABLE offline_transaction_buffer FORCE ROW LEVEL SECURITY;
ALTER TABLE stored_documents FORCE ROW LEVEL SECURITY;
ALTER TABLE device_revocations FORCE ROW LEVEL SECURITY;
ALTER TABLE export_jobs FORCE ROW LEVEL SECURITY;
ALTER TABLE webhook_subscriptions FORCE ROW LEVEL SECURITY;
ALTER TABLE outbound_webhook_deliveries FORCE ROW LEVEL SECURITY;
ALTER TABLE usage_records FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_invoice_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_invoice_sequences FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_configuration_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_configuration_overrides FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_feature_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_feature_flags FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_invoice_sequences_rls_policy ON tenant_invoice_sequences
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_config_overrides_rls_policy ON tenant_configuration_overrides
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_feature_flags_rls_policy ON tenant_feature_flags
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 7. Database-Level Immutability Functions and Triggers
CREATE OR REPLACE FUNCTION enforce_invoice_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IN ('REGISTERED', 'CANCELLED') THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'CANNOT_DELETE_OFFICIAL_INVOICE: Registered or cancelled invoices are permanently immutable';
        END IF;
        IF TG_OP = 'UPDATE' THEN
            IF OLD.pre_tax_total <> NEW.pre_tax_total OR
               OLD.tax_total <> NEW.tax_total OR
               OLD.excise_total <> NEW.excise_total OR
               OLD.grand_total <> NEW.grand_total OR
               OLD.invoice_counter <> NEW.invoice_counter OR
               OLD.document_number <> NEW.document_number OR
               OLD.irn <> NEW.irn THEN
                RAISE EXCEPTION 'FINANCIAL_MUTATION_FORBIDDEN: Registered or cancelled invoices are financially immutable';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_invoice_immutability ON invoices;
CREATE TRIGGER trg_invoice_immutability
BEFORE UPDATE OR DELETE ON invoices
FOR EACH ROW
EXECUTE FUNCTION enforce_invoice_immutability();

CREATE OR REPLACE FUNCTION enforce_audit_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' OR TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'AUDIT_TRAIL_IMMUTABLE: Audit events cannot be modified or deleted';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_immutability ON audit_events;
CREATE TRIGGER trg_audit_immutability
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION enforce_audit_immutability();
