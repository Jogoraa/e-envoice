-- V8__customer_master_schema.sql
-- Customer Master Data Schema & Expanded Invoice Buyer Snapshot

CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    tin VARCHAR(16),
    vat_number VARCHAR(32),
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    phone VARCHAR(32),
    email VARCHAR(128),
    country VARCHAR(8) NOT NULL DEFAULT 'ET',
    region VARCHAR(32),
    city VARCHAR(64),
    zone VARCHAR(64),
    woreda VARCHAR(32),
    kebele VARCHAR(32),
    house_number VARCHAR(32),
    buyer_id_type VARCHAR(32) NOT NULL DEFAULT 'TIN',
    buyer_id_number VARCHAR(64),
    is_vat_registered BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraint on tenant_id + tin where tin is provided
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_customer_tin 
    ON customers(tenant_id, tin) 
    WHERE tin IS NOT NULL AND tin <> '';

CREATE INDEX IF NOT EXISTS idx_customers_tenant_search 
    ON customers(tenant_id, legal_name, phone);

CREATE INDEX IF NOT EXISTS idx_customers_tenant_status 
    ON customers(tenant_id, status);

-- Add expanded snapshot columns to invoices table if not present
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_vat_number VARCHAR(32);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_country VARCHAR(8);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_city VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_zone VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_kebele VARCHAR(32);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_house_no VARCHAR(32);
