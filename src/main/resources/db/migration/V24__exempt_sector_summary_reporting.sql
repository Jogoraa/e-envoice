-- V24__exempt_sector_summary_reporting.sql
-- Directive No. 1142/2026 Art. 20
-- High-Volume Exempt-Sector Summary Reporting
-- Authorized sectors: banking, securities, digital payments, telecom (Art. 20(1))
-- May be extended to other sectors by Ministry decision (Art. 20(2))
--
-- Rules:
--   - B2C consumer transactions only, without direct EIRS connection (Art. 20(1))
--   - B2B transactions MUST use direct EIRS (Art. 20(5)) — excluded from summary
--   - Report summary daily/weekly/monthly per Authority decision (Art. 20(3)(d))
--   - Each invoice must retain unique number + QR code (Art. 20(3)(f))
--   - Invoice data retained for statutory period, producible on demand (Art. 20(3)(e))

-- ============================================================
-- Exempt-sector authorization grants
-- ============================================================
CREATE TABLE IF NOT EXISTS exempt_sector_authorizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    sector_code VARCHAR(32) NOT NULL REFERENCES business_sectors(sector_code),
    authorized_by VARCHAR(128) NOT NULL,             -- platform admin who granted
    authorization_reference VARCHAR(128) NOT NULL,   -- MoR letter/decision reference
    reporting_frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',  -- DAILY|WEEKLY|MONTHLY
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE|SUSPENDED|REVOKED
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,                     -- NULL = open-ended
    revocation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exempt_authorization_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_exempt_reporting_frequency CHECK (reporting_frequency IN ('DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT chk_exempt_status CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED'))
);

CREATE INDEX idx_exempt_auth_tenant ON exempt_sector_authorizations(tenant_id);
CREATE INDEX idx_exempt_auth_status ON exempt_sector_authorizations(status);

-- Extend business_sectors to flag Art. 20 eligible sectors
ALTER TABLE business_sectors
    ADD COLUMN IF NOT EXISTS is_eligible_for_exempt_eirs BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark Art. 20(1) explicitly eligible sectors
UPDATE business_sectors SET is_eligible_for_exempt_eirs = TRUE
WHERE sector_code IN (
    'SEC-BANKING', 'SEC-SECURITIES', 'SEC-DIGITAL-PAYMENTS', 'SEC-TELECOM'
);

-- Seed the Art. 20(1) exempt-eligible sectors if not present
INSERT INTO business_sectors (sector_code, name_en, name_am, is_mandatory_offline_continuity, is_eligible_for_exempt_eirs)
VALUES
('SEC-BANKING',         'Banking Services',                      'የባንክ አገልግሎቶች',                 FALSE, TRUE),
('SEC-SECURITIES',      'Securities Market Activities',          'የዋስትና ገበያ ሥራዎች',               FALSE, TRUE),
('SEC-DIGITAL-PAYMENTS','Digital Payment Processing Services',   'የዲጂታል ክፍያ አሳላጭ አገልግሎቶች',     FALSE, TRUE),
('SEC-TELECOM',         'Telecommunications Services',           'የቴሌኮሙኒኬሽን አገልግሎቶች',           FALSE, TRUE)
ON CONFLICT (sector_code) DO UPDATE SET is_eligible_for_exempt_eirs = TRUE;

-- ============================================================
-- Exempt-sector summary sales reports
-- Art. 20(3)(b)-(d): reported to Authority within prescribed deadline
-- ============================================================
CREATE TABLE IF NOT EXISTS exempt_sector_summary_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    authorization_id UUID NOT NULL REFERENCES exempt_sector_authorizations(id),
    report_period_label VARCHAR(32) NOT NULL,        -- e.g. '2026-10-05', '2026-W40', '2026-10'
    reporting_frequency VARCHAR(16) NOT NULL,
    period_from TIMESTAMPTZ NOT NULL,
    period_to   TIMESTAMPTZ NOT NULL,
    total_invoice_count BIGINT NOT NULL DEFAULT 0,
    -- Aggregate financial fields (Art. 20(3)(c))
    total_gross_amount NUMERIC(22, 2) NOT NULL DEFAULT 0,
    total_tax_amount   NUMERIC(22, 2) NOT NULL DEFAULT 0,
    total_grand_total  NUMERIC(22, 2) NOT NULL DEFAULT 0,
    -- Submission lifecycle
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',    -- DRAFT|SUBMITTED|ACCEPTED|REJECTED
    submitted_at TIMESTAMPTZ,
    accepted_at  TIMESTAMPTZ,
    rejection_reason TEXT,
    submitted_by VARCHAR(128),
    -- Integrity
    sha256_checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_report_status CHECK (status IN ('DRAFT','SUBMITTED','ACCEPTED','REJECTED')),
    CONSTRAINT chk_report_frequency CHECK (reporting_frequency IN ('DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT uk_summary_report_period UNIQUE (tenant_id, report_period_label, reporting_frequency)
);

CREATE INDEX idx_exempt_reports_tenant ON exempt_sector_summary_reports(tenant_id);
CREATE INDEX idx_exempt_reports_status ON exempt_sector_summary_reports(status);
CREATE INDEX idx_exempt_reports_period ON exempt_sector_summary_reports(tenant_id, period_from, period_to);

-- ============================================================
-- Summary report line items — aggregated by service/goods type and tax code
-- Art. 20(3)(c): per-line detail within each summary
-- ============================================================
CREATE TABLE IF NOT EXISTS exempt_sector_report_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES exempt_sector_summary_reports(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    -- Art. 20(3)(c) mandatory fields
    nature_of_supplies VARCHAR(16) NOT NULL DEFAULT 'services',  -- 'goods'|'services'
    service_or_goods_description VARCHAR(255) NOT NULL,
    quantity NUMERIC(22, 4) NOT NULL DEFAULT 0,
    unit VARCHAR(16) NOT NULL DEFAULT 'TXN',
    unit_price NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_price NUMERIC(22, 2) NOT NULL DEFAULT 0,
    tax_code VARCHAR(16) NOT NULL DEFAULT 'VAT15',
    tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.1500,
    tax_amount NUMERIC(22, 2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(22, 2) NOT NULL DEFAULT 0,
    invoice_count BIGINT NOT NULL DEFAULT 0,
    line_number INTEGER NOT NULL,
    CONSTRAINT uk_report_line_number UNIQUE (report_id, line_number)
);

CREATE INDEX idx_exempt_report_lines_report ON exempt_sector_report_lines(report_id);
CREATE INDEX idx_exempt_report_lines_tenant ON exempt_sector_report_lines(tenant_id);

-- RLS: tenants see only their own authorizations and reports
ALTER TABLE exempt_sector_authorizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE exempt_sector_summary_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE exempt_sector_report_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY exempt_auth_tenant_isolation ON exempt_sector_authorizations
    USING (tenant_id::text = current_setting('app.tenant_id', TRUE));

CREATE POLICY exempt_reports_tenant_isolation ON exempt_sector_summary_reports
    USING (tenant_id::text = current_setting('app.tenant_id', TRUE));

CREATE POLICY exempt_lines_tenant_isolation ON exempt_sector_report_lines
    USING (tenant_id::text = current_setting('app.tenant_id', TRUE));

-- Platform admin bypass (superuser / ut_superuser)
CREATE POLICY exempt_auth_admin_bypass ON exempt_sector_authorizations
    USING (current_user IN ('postgres', 'ut_superuser'));

CREATE POLICY exempt_reports_admin_bypass ON exempt_sector_summary_reports
    USING (current_user IN ('postgres', 'ut_superuser'));

CREATE POLICY exempt_lines_admin_bypass ON exempt_sector_report_lines
    USING (current_user IN ('postgres', 'ut_superuser'));
