-- V2__init_transactional_schema.sql
-- Transactional Schema: Taxpayer Profiles, Branches, Invoices, Lines, Adjustments, Cancellations, Offline

CREATE TABLE IF NOT EXISTS taxpayer_profiles (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE RESTRICT,
    tin VARCHAR(16) NOT NULL UNIQUE,
    vat_number VARCHAR(32),
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    region VARCHAR(32) NOT NULL,
    woreda VARCHAR(32) NOT NULL,
    sub_city VARCHAR(64),
    kebele VARCHAR(32),
    house_number VARCHAR(32),
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    system_number VARCHAR(32) NOT NULL,
    system_type VARCHAR(16) NOT NULL DEFAULT 'POS',
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS branches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_code VARCHAR(32) NOT NULL,
    branch_name VARCHAR(128) NOT NULL,
    region VARCHAR(32) NOT NULL,
    woreda VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_branch_code UNIQUE (tenant_id, branch_code)
);

CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    device_serial VARCHAR(64) NOT NULL,
    device_type VARCHAR(32) NOT NULL DEFAULT 'MPOS',
    system_number VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_latitude NUMERIC(10, 7),
    last_longitude NUMERIC(10, 7),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_device_serial UNIQUE (tenant_id, device_serial)
);

CREATE TABLE IF NOT EXISTS geofences (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    fence_name VARCHAR(64) NOT NULL,
    polygon_geojson TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    branch_id UUID REFERENCES branches(id),
    device_id UUID REFERENCES devices(id),
    document_number VARCHAR(64) NOT NULL,
    invoice_counter BIGINT NOT NULL,
    invoice_date TIMESTAMPTZ NOT NULL,
    transaction_type VARCHAR(16) NOT NULL DEFAULT 'B2C',
    payment_mode VARCHAR(32) NOT NULL DEFAULT 'CASH',
    payment_term VARCHAR(32) NOT NULL DEFAULT 'IMMEDIATE',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    
    -- Financial Totals
    pre_tax_total NUMERIC(18, 2) NOT NULL,
    tax_total NUMERIC(18, 2) NOT NULL,
    excise_total NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    grand_total NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'ETB',
    
    -- Buyer Information Snapshot
    buyer_legal_name VARCHAR(255),
    buyer_tin VARCHAR(16),
    buyer_id_number VARCHAR(64),
    buyer_id_type VARCHAR(16),
    buyer_phone VARCHAR(32),
    buyer_email VARCHAR(128),
    buyer_region VARCHAR(32),
    buyer_woreda VARCHAR(32),
    
    -- MoR EIRS Government Acknowledgments
    irn VARCHAR(128) UNIQUE,
    previous_irn VARCHAR(128),
    rrn VARCHAR(128),
    ack_date VARCHAR(64),
    signed_qr TEXT,
    signed_invoice TEXT,
    
    -- Metadata & Auditing
    idempotency_key VARCHAR(128),
    reprint_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_tenant_document_number UNIQUE (tenant_id, document_number)
);

CREATE INDEX idx_invoices_tenant_date ON invoices(tenant_id, invoice_date DESC);
CREATE INDEX idx_invoices_irn ON invoices(irn);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_idempotency ON invoices(tenant_id, idempotency_key);

CREATE TABLE IF NOT EXISTS invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    product_description VARCHAR(255) NOT NULL,
    nature_of_supplies VARCHAR(16) NOT NULL DEFAULT 'goods',
    unit VARCHAR(16) NOT NULL DEFAULT 'PCS',
    quantity NUMERIC(14, 4) NOT NULL,
    unit_price NUMERIC(14, 2) NOT NULL,
    discount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    pre_tax_value NUMERIC(18, 2) NOT NULL,
    tax_code VARCHAR(16) NOT NULL DEFAULT 'VAT15',
    tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.1500,
    tax_amount NUMERIC(18, 2) NOT NULL,
    excise_tax_value NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    total_line_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT uk_invoice_line_number UNIQUE (invoice_id, line_number)
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines(invoice_id);

CREATE TABLE IF NOT EXISTS tax_adjustments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    note_type VARCHAR(16) NOT NULL, -- 'CREDIT_NOTE' or 'DEBIT_NOTE'
    original_invoice_id UUID NOT NULL REFERENCES invoices(id),
    original_irn VARCHAR(128) NOT NULL,
    adjustment_reason VARCHAR(255) NOT NULL,
    adjusted_pre_tax NUMERIC(18, 2) NOT NULL,
    adjusted_tax NUMERIC(18, 2) NOT NULL,
    adjusted_total NUMERIC(18, 2) NOT NULL,
    irn VARCHAR(128) UNIQUE,
    ack_date VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cancellation_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    irn VARCHAR(128) NOT NULL,
    reason_category VARCHAR(64) NOT NULL,
    detailed_reason TEXT NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    evidence_data TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sla_deadline_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    cancellation_ref VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cancellations_sla ON cancellation_requests(state, sla_deadline_at);

CREATE TABLE IF NOT EXISTS offline_transaction_buffer (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id),
    offline_seq_no BIGINT NOT NULL,
    payload_json TEXT NOT NULL,
    device_signature TEXT NOT NULL,
    buffered_at TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    irn VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_device_offline_seq UNIQUE (tenant_id, device_id, offline_seq_no)
);

CREATE TABLE IF NOT EXISTS device_offline_allocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    start_seq BIGINT NOT NULL,
    end_seq BIGINT NOT NULL,
    current_seq BIGINT NOT NULL,
    allocated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
