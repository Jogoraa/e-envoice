-- ==============================================================================
-- Flyway Migration V22: Provider Compliance Tiers & Guarantee Level Engine
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 14(6)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS provider_compliance_tiers (
    id UUID PRIMARY KEY,
    tier_level INT NOT NULL UNIQUE,
    min_active_taxpayers INT NOT NULL,
    max_active_taxpayers INT NOT NULL,
    min_annual_sales_volume NUMERIC(18, 2) NOT NULL,
    max_annual_sales_volume NUMERIC(18, 2) NOT NULL,
    required_guarantee_amount_usd NUMERIC(18, 2) NOT NULL,
    required_technical_staffing INT NOT NULL,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    source_article VARCHAR(64) NOT NULL DEFAULT 'Directive 1142/2026 Art. 14(6)',
    version VARCHAR(32) NOT NULL DEFAULT '2026.1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tier_level CHECK (tier_level BETWEEN 1 AND 10)
);

CREATE TABLE IF NOT EXISTS provider_tier_status_history (
    id UUID PRIMARY KEY,
    assessment_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_taxpayer_count INT NOT NULL,
    annual_sales_volume NUMERIC(18, 2) NOT NULL,
    current_level INT NOT NULL,
    projected_level INT NOT NULL,
    status VARCHAR(64) NOT NULL,
    proximity_percentage NUMERIC(5, 2) NOT NULL,
    alert_message TEXT,
    notified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tier_status CHECK (status IN ('CURRENT_LEVEL', 'THRESHOLD_APPROACHING', 'LEVEL_CHANGE_PENDING_NOTIFICATION', 'LEVEL_CHANGE_NOTIFIED', 'CONFIRMED'))
);

CREATE INDEX IF NOT EXISTS idx_provider_tier_history_time ON provider_tier_status_history(assessment_time DESC);

-- Seed Levels 1 through 10 pursuant to Directive Art. 14(6) Table
INSERT INTO provider_compliance_tiers (
    id, tier_level, min_active_taxpayers, max_active_taxpayers,
    min_annual_sales_volume, max_annual_sales_volume,
    required_guarantee_amount_usd, required_technical_staffing,
    effective_from, source_article, version
) VALUES
('b1000001-0000-0000-0000-000000000001', 1, 0, 500, 0.00, 10000000.00, 0.00, 2, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000002', 2, 501, 1000, 10000000.01, 25000000.00, 10000.00, 2, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000003', 3, 1001, 2500, 25000000.01, 50000000.00, 25000.00, 3, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000004', 4, 2501, 5000, 50000000.01, 100000000.00, 50000.00, 4, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000005', 5, 5001, 10000, 100000000.01, 250000000.00, 75000.00, 4, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000006', 6, 10001, 15000, 250000000.01, 500000000.00, 100000.00, 5, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000007', 7, 15001, 20000, 500000000.01, 1000000000.00, 125000.00, 6, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000008', 8, 20001, 30000, 1000000000.01, 2000000000.00, 150000.00, 6, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000009', 9, 30001, 40000, 2000000000.01, 5000000000.00, 200000.00, 7, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1'),
('b1000001-0000-0000-0000-000000000010', 10, 40001, 999999999, 5000000000.01, 999999999999.00, 250000.00, 8, '2026-01-01', 'Directive 1142/2026 Art. 14(6)', '2026.1')
ON CONFLICT (tier_level) DO NOTHING;
