-- ==============================================================================
-- Migration: V5__row_level_security.sql
-- Description: Defense-in-Depth PostgreSQL Row-Level Security (RLS) Policies
-- Authoritative Compliance: Directive No. 1142/2026 Multi-Tenant Isolation
-- ==============================================================================

-- 1. Invoices & Lines
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_invoices_rls_policy ON invoices
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE invoice_lines ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_invoice_lines_rls_policy ON invoice_lines
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 2. Official Receipts
ALTER TABLE receipts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_receipts_rls_policy ON receipts
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 3. Tax Adjustments (Credit & Debit Notes)
ALTER TABLE tax_adjustments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_tax_adjustments_rls_policy ON tax_adjustments
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 4. Cancellation Requests
ALTER TABLE cancellation_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_cancellations_rls_policy ON cancellation_requests
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 5. Audit Events
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_audit_events_rls_policy ON audit_events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 6. Offline Transaction Buffer
ALTER TABLE offline_transaction_buffer ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_offline_buffer_rls_policy ON offline_transaction_buffer
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 7. Stored Documents Catalog
ALTER TABLE stored_documents ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_stored_documents_rls_policy ON stored_documents
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 8. Device Revocations
ALTER TABLE device_revocations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_device_revocations_rls_policy ON device_revocations
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 9. Export Jobs
ALTER TABLE export_jobs ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_export_jobs_rls_policy ON export_jobs
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 10. Webhooks & Usage
ALTER TABLE webhook_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_webhooks_rls_policy ON webhook_subscriptions
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE outbound_webhook_deliveries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_webhook_deliveries_rls_policy ON outbound_webhook_deliveries
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_usage_records_rls_policy ON usage_records
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
