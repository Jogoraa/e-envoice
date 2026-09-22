-- ==============================================================================
-- UT INVOICE — CANONICAL DATABASE MIGRATION
-- Migration: V4__invoice_notifications_and_sms_outbox.sql
-- Classification: Transactional SMS Notification Subsystem & Durable Outbox
-- Authoritative Compliance: FDRE Ministry of Revenues Directive No. 1142/2026 Art. 4(1)(i) & Art. 26(5)
-- ==============================================================================

-- 1. EXTEND INVOICES TABLE WITH PUBLIC VERIFICATION TOKEN
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS public_verification_token VARCHAR(64) UNIQUE;
CREATE INDEX IF NOT EXISTS idx_invoices_verification_token ON invoices(public_verification_token);

-- 2. EXTEND CUSTOMERS MASTER TABLE WITH NOTIFICATION PREFERENCES
ALTER TABLE customers ADD COLUMN IF NOT EXISTS sms_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(8) NOT NULL DEFAULT 'am';

-- 3. WALK-IN / B2C NOTIFICATION OPT-OUTS TABLE
CREATE TABLE IF NOT EXISTS notification_opt_outs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    phone VARCHAR(32) NOT NULL,
    opt_out_type VARCHAR(32) NOT NULL DEFAULT 'TRANSACTIONAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_opt_outs UNIQUE (tenant_id, phone, opt_out_type)
);

CREATE INDEX IF NOT EXISTS idx_notification_opt_outs_lookup ON notification_opt_outs(tenant_id, phone);

-- 4. TENANT SMS DAILY QUOTA TRACKER
CREATE TABLE IF NOT EXISTS tenant_sms_quotas (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    daily_limit INT NOT NULL DEFAULT 1000,
    daily_units_used INT NOT NULL DEFAULT 0,
    daily_messages_used INT NOT NULL DEFAULT 0,
    reset_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. INVOICE NOTIFICATION DURABLE OUTBOX TABLE
CREATE TABLE IF NOT EXISTS invoice_notification_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    recipient_party_id VARCHAR(128) NOT NULL,
    recipient_phone_snapshot VARCHAR(32) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version VARCHAR(16) NOT NULL,
    template_params JSONB,
    rendered_message TEXT,
    rendered_message_hash VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_classification VARCHAR(64),
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(128),
    provider VARCHAR(64) NOT NULL DEFAULT 'MOCK',
    provider_message_id VARCHAR(128),
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    correlation_id VARCHAR(128),
    CONSTRAINT uk_invoice_notif_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_notif_outbox_poll ON invoice_notification_outbox(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_notif_outbox_tenant_inv ON invoice_notification_outbox(tenant_id, invoice_id);
CREATE INDEX IF NOT EXISTS idx_notif_outbox_provider_msg ON invoice_notification_outbox(provider, provider_message_id);

-- 6. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE notification_opt_outs ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_opt_outs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_notification_opt_outs_rls_policy ON notification_opt_outs;
CREATE POLICY tenant_notification_opt_outs_rls_policy ON notification_opt_outs
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE tenant_sms_quotas ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_sms_quotas FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_sms_quotas_rls_policy ON tenant_sms_quotas;
CREATE POLICY tenant_sms_quotas_rls_policy ON tenant_sms_quotas
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE invoice_notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_notification_outbox FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_notif_outbox_rls_policy ON invoice_notification_outbox;
CREATE POLICY tenant_notif_outbox_rls_policy ON invoice_notification_outbox
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
