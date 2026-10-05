-- V15__marketplace_compliance.sql
-- Directive No. 1142/2026 Art. 14(4) & Art. 6
-- Optional E-Commerce / Digital Marketplace Compliance Module:
-- Merchant Registry, Multi-Seller Fiscal Splitting, Authority Suspension

CREATE TABLE IF NOT EXISTS marketplace_merchants (
    id UUID PRIMARY KEY,
    marketplace_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    merchant_tin VARCHAR(16) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    merchant_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED_BY_AUTHORITY, REINSTATED_BY_AUTHORITY, TERMINATED
    authority_notification_state VARCHAR(32) NOT NULL DEFAULT 'ACKNOWLEDGED',
    authority_suspension_reason TEXT,
    suspended_at TIMESTAMPTZ,
    reinstated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_marketplace_merchant UNIQUE (marketplace_tenant_id, merchant_tin)
);

CREATE INDEX idx_mkt_merchant_tenant ON marketplace_merchants(marketplace_tenant_id);
CREATE INDEX idx_mkt_merchant_tin ON marketplace_merchants(merchant_tin);
CREATE INDEX idx_mkt_merchant_status ON marketplace_merchants(merchant_status);

ALTER TABLE marketplace_merchants ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace_merchants FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS marketplace_merchants_rls_policy ON marketplace_merchants;
CREATE POLICY marketplace_merchants_rls_policy ON marketplace_merchants
    FOR ALL
    USING (
        marketplace_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        marketplace_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

CREATE TABLE IF NOT EXISTS marketplace_orders (
    id UUID PRIMARY KEY,
    marketplace_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    order_number VARCHAR(64) NOT NULL,
    buyer_tin VARCHAR(16),
    buyer_name VARCHAR(255) NOT NULL,
    total_order_amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'ETB',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_marketplace_order_number UNIQUE (marketplace_tenant_id, order_number)
);

CREATE INDEX idx_mkt_order_tenant ON marketplace_orders(marketplace_tenant_id);

ALTER TABLE marketplace_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace_orders FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS marketplace_orders_rls_policy ON marketplace_orders;
CREATE POLICY marketplace_orders_rls_policy ON marketplace_orders
    FOR ALL
    USING (
        marketplace_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        marketplace_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );

-- Audit table for authority-directed commands on marketplace
CREATE TABLE IF NOT EXISTS marketplace_authority_commands (
    id UUID PRIMARY KEY,
    marketplace_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    merchant_id UUID NOT NULL REFERENCES marketplace_merchants(id) ON DELETE RESTRICT,
    command_type VARCHAR(32) NOT NULL, -- SUSPEND, REINSTATE
    authority_reference VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL,
    executed_by VARCHAR(128) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mkt_auth_cmd_tenant ON marketplace_authority_commands(marketplace_tenant_id);

ALTER TABLE marketplace_authority_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace_authority_commands FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS marketplace_auth_cmd_rls_policy ON marketplace_authority_commands;
CREATE POLICY marketplace_auth_cmd_rls_policy ON marketplace_authority_commands
    FOR ALL
    USING (
        marketplace_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        marketplace_tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );
