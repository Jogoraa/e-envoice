-- ==============================================================================
-- UT INVOICE — CANONICAL DATABASE MIGRATION
-- Migration: V3__catalog_master_data.sql
-- Classification: Tenant Master-Data Architecture (Categories, Products, Services)
-- Authoritative Compliance: FDRE Ministry of Revenue Directive No. 1142/2026 Art. 4(1)(a) & Art. 7
-- ==============================================================================

-- 1. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCT', -- 'PRODUCT', 'SERVICE', 'ALL'
    description TEXT,
    parent_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_category_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_categories_tenant ON categories(tenant_id);
CREATE INDEX IF NOT EXISTS idx_categories_status ON categories(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_categories_type ON categories(tenant_id, category_type);

-- 2. PRODUCTS TABLE
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    item_code VARCHAR(64) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    barcode VARCHAR(64),
    description VARCHAR(255) NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    category_code VARCHAR(64),
    unit VARCHAR(32) NOT NULL DEFAULT 'PCS',
    unit_price NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    tax_classification VARCHAR(32) NOT NULL DEFAULT 'VAT15',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    track_stock BOOLEAN NOT NULL DEFAULT TRUE,
    stock_quantity NUMERIC(14, 4) NOT NULL DEFAULT 0.0000,
    min_stock_level NUMERIC(14, 4) NOT NULL DEFAULT 5.0000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_product_code UNIQUE (tenant_id, item_code)
);

CREATE INDEX IF NOT EXISTS idx_products_tenant ON products(tenant_id);
CREATE INDEX IF NOT EXISTS idx_products_branch ON products(tenant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(tenant_id, sku);
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(tenant_id, barcode);

-- 3. SERVICES TABLE
CREATE TABLE IF NOT EXISTS services (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    service_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    category_code VARCHAR(64),
    unit VARCHAR(32) NOT NULL DEFAULT 'SERVICE',
    unit_price NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    tax_classification VARCHAR(32) NOT NULL DEFAULT 'VAT15',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_service_code UNIQUE (tenant_id, service_code)
);

CREATE INDEX IF NOT EXISTS idx_services_tenant ON services(tenant_id);
CREATE INDEX IF NOT EXISTS idx_services_branch ON services(tenant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_services_category ON services(tenant_id, category_id);

-- 4. ROW LEVEL SECURITY (RLS) FOR CATALOG MASTER DATA
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_categories_rls_policy ON categories;
CREATE POLICY tenant_categories_rls_policy ON categories
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_products_rls_policy ON products;
CREATE POLICY tenant_products_rls_policy ON products
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE services FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_services_rls_policy ON services;
CREATE POLICY tenant_services_rls_policy ON services
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
