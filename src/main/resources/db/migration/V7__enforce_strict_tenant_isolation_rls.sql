-- ==============================================================================
-- UT INVOICE — CANONICAL DATABASE MIGRATION
-- Migration: V7__enforce_strict_tenant_isolation_rls.sql
-- Classification: Production Tenant Isolation & RLS Enforcement
-- Authoritative Compliance: FDRE Ministry of Revenue Directive No. 1142/2026
-- ==============================================================================

-- 1. Ensure unprivileged runtime application role exists without SUPERUSER or BYPASSRLS
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ut_app_user') THEN
        CREATE ROLE ut_app_user WITH LOGIN PASSWORD 'appuserpassword' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END $$;

GRANT CONNECT ON DATABASE ut_einvoice_db TO ut_app_user;
GRANT USAGE ON SCHEMA public TO ut_app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ut_app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ut_app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ut_app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ut_app_user;

-- 2. Enforce Row Level Security (RLS) on all tenant-owned tables
-- Helper macro: RLS expression permits row if:
--   a) tenant_id matches the session variable 'app.current_tenant_id'
--   OR
--   b) the session has authorized platform admin privileges ('app.is_platform_admin' = 'true')

-- Table: invoices
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_invoices_rls_policy ON invoices;
CREATE POLICY tenant_invoices_rls_policy ON invoices
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: invoice_lines
ALTER TABLE invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_lines FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_invoice_lines_rls_policy ON invoice_lines;
CREATE POLICY tenant_invoice_lines_rls_policy ON invoice_lines
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: customers
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_customers_rls_policy ON customers;
CREATE POLICY tenant_customers_rls_policy ON customers
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: receipts
ALTER TABLE receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE receipts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_receipts_rls_policy ON receipts;
CREATE POLICY tenant_receipts_rls_policy ON receipts
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: tax_adjustments
ALTER TABLE tax_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE tax_adjustments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_tax_adjustments_rls_policy ON tax_adjustments;
CREATE POLICY tenant_tax_adjustments_rls_policy ON tax_adjustments
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: cancellation_requests
ALTER TABLE cancellation_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE cancellation_requests FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_cancellations_rls_policy ON cancellation_requests;
CREATE POLICY tenant_cancellations_rls_policy ON cancellation_requests
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: audit_events
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_audit_events_rls_policy ON audit_events;
CREATE POLICY tenant_audit_events_rls_policy ON audit_events
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: offline_transaction_buffer
ALTER TABLE offline_transaction_buffer ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline_transaction_buffer FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_offline_buffer_rls_policy ON offline_transaction_buffer;
CREATE POLICY tenant_offline_buffer_rls_policy ON offline_transaction_buffer
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: stored_documents
ALTER TABLE stored_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE stored_documents FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_stored_documents_rls_policy ON stored_documents;
CREATE POLICY tenant_stored_documents_rls_policy ON stored_documents
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: device_revocations
ALTER TABLE device_revocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_revocations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_device_revocations_rls_policy ON device_revocations;
CREATE POLICY tenant_device_revocations_rls_policy ON device_revocations
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: export_jobs
ALTER TABLE export_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE export_jobs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_export_jobs_rls_policy ON export_jobs;
CREATE POLICY tenant_export_jobs_rls_policy ON export_jobs
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: webhook_subscriptions
ALTER TABLE webhook_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_subscriptions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_webhooks_rls_policy ON webhook_subscriptions;
CREATE POLICY tenant_webhooks_rls_policy ON webhook_subscriptions
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: outbound_webhook_deliveries
ALTER TABLE outbound_webhook_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbound_webhook_deliveries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_webhook_deliveries_rls_policy ON outbound_webhook_deliveries;
CREATE POLICY tenant_webhook_deliveries_rls_policy ON outbound_webhook_deliveries
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: usage_records
ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_records FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_usage_records_rls_policy ON usage_records;
CREATE POLICY tenant_usage_records_rls_policy ON usage_records
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: tenant_invoice_sequences
ALTER TABLE tenant_invoice_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_invoice_sequences FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_invoice_sequences_rls_policy ON tenant_invoice_sequences;
CREATE POLICY tenant_invoice_sequences_rls_policy ON tenant_invoice_sequences
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: tenant_configuration_overrides
ALTER TABLE tenant_configuration_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_configuration_overrides FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_config_overrides_rls_policy ON tenant_configuration_overrides;
CREATE POLICY tenant_config_overrides_rls_policy ON tenant_configuration_overrides
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: tenant_feature_flags
ALTER TABLE tenant_feature_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_feature_flags FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_feature_flags_rls_policy ON tenant_feature_flags;
CREATE POLICY tenant_feature_flags_rls_policy ON tenant_feature_flags
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: categories
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_categories_rls_policy ON categories;
CREATE POLICY tenant_categories_rls_policy ON categories
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: products
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_products_rls_policy ON products;
CREATE POLICY tenant_products_rls_policy ON products
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: services
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE services FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_services_rls_policy ON services;
CREATE POLICY tenant_services_rls_policy ON services
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: branches
ALTER TABLE branches ENABLE ROW LEVEL SECURITY;
ALTER TABLE branches FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_branches_rls_policy ON branches;
CREATE POLICY tenant_branches_rls_policy ON branches
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: devices
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE devices FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_devices_rls_policy ON devices;
CREATE POLICY tenant_devices_rls_policy ON devices
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: taxpayer_profiles
ALTER TABLE taxpayer_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayer_profiles FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_taxpayer_profiles_rls_policy ON taxpayer_profiles;
CREATE POLICY tenant_taxpayer_profiles_rls_policy ON taxpayer_profiles
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: geofences
ALTER TABLE geofences ENABLE ROW LEVEL SECURITY;
ALTER TABLE geofences FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_geofences_rls_policy ON geofences;
CREATE POLICY tenant_geofences_rls_policy ON geofences
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: api_clients
ALTER TABLE api_clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_clients FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_api_clients_rls_policy ON api_clients;
CREATE POLICY tenant_api_clients_rls_policy ON api_clients
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: tenant_users
ALTER TABLE tenant_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_users FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_users_rls_policy ON tenant_users;
CREATE POLICY tenant_users_rls_policy ON tenant_users
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: subscriptions
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_subscriptions_rls_policy ON subscriptions;
CREATE POLICY tenant_subscriptions_rls_policy ON subscriptions
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: delegated_tenant_sessions (target_tenant_id)
ALTER TABLE delegated_tenant_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE delegated_tenant_sessions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_delegated_sessions_rls_policy ON delegated_tenant_sessions;
CREATE POLICY tenant_delegated_sessions_rls_policy ON delegated_tenant_sessions
    FOR ALL
    USING (
        target_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        target_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Table: report_jobs (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'report_jobs') THEN
        EXECUTE 'ALTER TABLE report_jobs ENABLE ROW LEVEL SECURITY';
        EXECUTE 'ALTER TABLE report_jobs FORCE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_report_jobs_rls_policy ON report_jobs';
        EXECUTE 'CREATE POLICY tenant_report_jobs_rls_policy ON report_jobs FOR ALL USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'') WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'')';
    END IF;
END $$;

-- Table: invoice_notification_outbox (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'invoice_notification_outbox') THEN
        EXECUTE 'ALTER TABLE invoice_notification_outbox ENABLE ROW LEVEL SECURITY';
        EXECUTE 'ALTER TABLE invoice_notification_outbox FORCE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_notif_outbox_rls_policy ON invoice_notification_outbox';
        EXECUTE 'CREATE POLICY tenant_notif_outbox_rls_policy ON invoice_notification_outbox FOR ALL USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'') WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'')';
    END IF;
END $$;

-- Table: notification_opt_outs (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'notification_opt_outs') THEN
        EXECUTE 'ALTER TABLE notification_opt_outs ENABLE ROW LEVEL SECURITY';
        EXECUTE 'ALTER TABLE notification_opt_outs FORCE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_notification_opt_outs_rls_policy ON notification_opt_outs';
        EXECUTE 'CREATE POLICY tenant_notification_opt_outs_rls_policy ON notification_opt_outs FOR ALL USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'') WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'')';
    END IF;
END $$;

-- Table: tenant_sms_quotas (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'tenant_sms_quotas') THEN
        EXECUTE 'ALTER TABLE tenant_sms_quotas ENABLE ROW LEVEL SECURITY';
        EXECUTE 'ALTER TABLE tenant_sms_quotas FORCE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_sms_quotas_rls_policy ON tenant_sms_quotas';
        EXECUTE 'CREATE POLICY tenant_sms_quotas_rls_policy ON tenant_sms_quotas FOR ALL USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'') WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'')';
    END IF;
END $$;

-- Table: audit_outbox_events (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'audit_outbox_events') THEN
        EXECUTE 'ALTER TABLE audit_outbox_events ENABLE ROW LEVEL SECURITY';
        EXECUTE 'ALTER TABLE audit_outbox_events FORCE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_audit_outbox_rls_policy ON audit_outbox_events';
        EXECUTE 'CREATE POLICY tenant_audit_outbox_rls_policy ON audit_outbox_events FOR ALL USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'') WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'')';
    END IF;
END $$;

-- Table: government_submissions (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'government_submissions') THEN
        EXECUTE 'ALTER TABLE government_submissions ENABLE ROW LEVEL SECURITY';
        EXECUTE 'ALTER TABLE government_submissions FORCE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_government_submissions_rls_policy ON government_submissions';
        EXECUTE 'CREATE POLICY tenant_government_submissions_rls_policy ON government_submissions FOR ALL USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'') WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.is_platform_admin'', true) = ''true'')';
    END IF;
END $$;

