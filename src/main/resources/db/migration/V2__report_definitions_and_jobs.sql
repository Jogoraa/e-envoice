-- ============================================================================
-- V2__report_definitions_and_jobs.sql
-- Report Definitions Catalog & Scoped Report Generation Jobs (Directive No. 1142/2026)
-- ============================================================================

CREATE TABLE IF NOT EXISTS report_definitions (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    amharic_title VARCHAR(255),
    description TEXT,
    icon_name VARCHAR(64) NOT NULL DEFAULT 'assessment',
    category VARCHAR(64) NOT NULL DEFAULT 'COMPLIANCE',
    export_formats VARCHAR(128) NOT NULL DEFAULT 'PDF,EXCEL,CSV,JSON',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_defs_active ON report_definitions(is_active, display_order);

CREATE TABLE IF NOT EXISTS report_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    report_definition_id VARCHAR(64) REFERENCES report_definitions(id) ON DELETE SET NULL,
    report_title VARCHAR(255) NOT NULL,
    date_range VARCHAR(128),
    format VARCHAR(16) NOT NULL DEFAULT 'PDF',
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    record_count INT NOT NULL DEFAULT 0,
    artifact_url VARCHAR(512),
    report_content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_report_jobs_tenant ON report_jobs(tenant_id, created_at DESC);

-- Enable RLS for report_jobs
ALTER TABLE report_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_jobs FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_report_jobs_rls_policy ON report_jobs;
CREATE POLICY tenant_report_jobs_rls_policy ON report_jobs
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- Seed Baseline Statutory Reports
INSERT INTO report_definitions (id, title, amharic_title, description, icon_name, category, export_formats, is_active, display_order)
VALUES
('vat_sales_ledger', 'Monthly VAT Sales Ledger', 'ወርሃዊ የተጨማሪ እሴት ታክስ የሽያጭ መዝገብ', 'Official Ministry of Revenues compliant VAT ledger with IRN, buyer TIN, pre-tax, and 15% VAT breakdowns.', 'receipt_long', 'COMPLIANCE', 'PDF,EXCEL,CSV,JSON', TRUE, 1),
('daily_z_report', 'Daily Z-Report & Cashier Summary', 'ዕለታዊ የሽያጭ ማጠቃለያ (Z-ሪፖርት)', 'End-of-day fiscal totals grouped by payment mode (Cash, telebirr, CBE Birr, Bank transfer).', 'point_of_sale', 'OPERATIONAL', 'PDF,EXCEL,CSV,JSON', TRUE, 2),
('product_sales_velocity', 'Product & Service Sales Velocity', 'የዕቃዎችና አገልግሎቶች የሽያጭ ፍጥነት', 'Sales quantity, revenue, and tax distribution across registered catalog products and services.', 'trending_up', 'COMMERCIAL', 'PDF,EXCEL,CSV,JSON', TRUE, 3),
('offline_sync_audit', 'Offline Synchronization Audit Log', 'ከመስመር ውጭ የተከናወኑ ተግባራት መዝገብ', 'Detailed audit of offline buffered invoices, timestamps, 72-hour compliance, and reconciliation results.', 'cloud_sync', 'AUDIT', 'PDF,EXCEL,CSV,JSON', TRUE, 4),
('withholding_tax_summary', 'Withholding Tax Summary', 'የተቀናሽ ታክስ ማጠቃለያ መዝገብ', 'Withholding tax deductions schedule for qualifying transactions under MoR regulations.', 'account_balance', 'COMPLIANCE', 'PDF,EXCEL,CSV,JSON', TRUE, 5)
ON CONFLICT (id) DO NOTHING;
