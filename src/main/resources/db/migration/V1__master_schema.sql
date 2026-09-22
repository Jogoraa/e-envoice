-- ==============================================================================
-- UT INVOICE — CANONICAL MASTER DATABASE MIGRATION
-- Migration: V1__master_schema.sql
-- Classification: Production Canonical Master Schema
-- Authoritative Compliance: FDRE Ministry of Revenue Directive No. 1142/2026
-- ==============================================================================

-- 0. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- 1. CONTROL PLANE & TENANCY LAYER
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    tin VARCHAR(16) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ONBOARDING',
    subscription_status VARCHAR(32) NOT NULL DEFAULT 'SUBSCRIPTION_ACTIVE',
    government_status VARCHAR(32) NOT NULL DEFAULT 'GOVERNMENT_ACTIVE',
    tenant_type VARCHAR(32) NOT NULL DEFAULT 'SME',
    database_shard VARCHAR(64) DEFAULT 'shared_cluster',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ
);

CREATE INDEX idx_tenants_tin ON tenants(tin);
CREATE INDEX idx_tenants_status ON tenants(status);

CREATE TABLE IF NOT EXISTS platform_users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    role VARCHAR(64) NOT NULL DEFAULT 'ROLE_SAAS_ADMIN',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ
);

CREATE INDEX idx_platform_users_username ON platform_users(username);
CREATE INDEX idx_platform_users_email ON platform_users(email);
CREATE INDEX idx_platform_users_role ON platform_users(role);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_code VARCHAR(32) NOT NULL DEFAULT 'SME_STANDARD',
    billing_cycle VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    max_monthly_invoices INTEGER NOT NULL DEFAULT 5000,
    rate_limit_rps INTEGER NOT NULL DEFAULT 20,
    offline_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    geofence_enforced BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subscriptions_tenant UNIQUE (tenant_id)
);

CREATE TABLE IF NOT EXISTS api_clients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    client_id VARCHAR(64) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(255) NOT NULL,
    client_name VARCHAR(128) NOT NULL,
    client_type VARCHAR(32) NOT NULL DEFAULT 'EXTERNAL_ERP',
    scopes VARCHAR(512) NOT NULL DEFAULT 'invoice:read invoice:create',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_api_clients_tenant ON api_clients(tenant_id);

CREATE TABLE IF NOT EXISTS tenant_users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(128) NOT NULL,
    phone VARCHAR(32),
    full_name VARCHAR(128),
    role VARCHAR(32) NOT NULL DEFAULT 'CASHIER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, username)
);

CREATE INDEX idx_tenant_users_tenant ON tenant_users(tenant_id);

CREATE TABLE IF NOT EXISTS delegated_tenant_sessions (
    session_id UUID PRIMARY KEY,
    master_user_id VARCHAR(128) NOT NULL,
    target_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_branch_id UUID,
    access_type VARCHAR(32) NOT NULL DEFAULT 'TESTING',
    reason VARCHAR(512),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(128)
);

CREATE INDEX idx_delegated_sessions_lookup ON delegated_tenant_sessions(session_id, is_revoked, expires_at);
CREATE INDEX idx_delegated_sessions_tenant ON delegated_tenant_sessions(target_tenant_id, is_revoked);

CREATE TABLE IF NOT EXISTS tenant_shards (
    id UUID PRIMARY KEY,
    shard_name VARCHAR(64) NOT NULL UNIQUE,
    cluster_url VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    max_tenants INTEGER NOT NULL DEFAULT 10000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tenant_shards (id, shard_name, cluster_url, is_active, max_tenants)
VALUES
    ('a1111111-1111-1111-1111-111111111101', 'shared_cluster', 'jdbc:postgresql://localhost:5432/ut_einvoice_db', TRUE, 500000),
    ('a1111111-1111-1111-1111-111111111102', 'enterprise_shard_01', 'jdbc:postgresql://localhost:5432/ut_enterprise_01', TRUE, 10000)
ON CONFLICT (shard_name) DO NOTHING;

-- ==============================================================================
-- 2. TAXPAYER PROFILES, BRANCHES, DEVICES & GEOFENCES
-- ==============================================================================

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

CREATE TABLE IF NOT EXISTS device_revocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID NOT NULL,
    revocation_reason VARCHAR(255) NOT NULL,
    revoked_by VARCHAR(64) NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_revocations_lookup ON device_revocations(tenant_id, device_id);

-- ==============================================================================
-- 3. CUSTOMER MASTER DATA & INVOICING CORE
-- ==============================================================================

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

CREATE UNIQUE INDEX idx_customers_tenant_tin ON customers(tenant_id, tin) WHERE tin IS NOT NULL AND tin <> '';
CREATE INDEX idx_customers_tenant_search ON customers(tenant_id, legal_name, phone);
CREATE INDEX idx_customers_tenant_status ON customers(tenant_id, status);

CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCT',
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

CREATE TABLE IF NOT EXISTS tenant_invoice_sequences (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    current_counter BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    buyer_vat_number VARCHAR(32),
    buyer_id_number VARCHAR(64),
    buyer_id_type VARCHAR(16),
    buyer_phone VARCHAR(32),
    buyer_email VARCHAR(128),
    buyer_country VARCHAR(8) DEFAULT 'ET',
    buyer_region VARCHAR(32),
    buyer_city VARCHAR(64),
    buyer_zone VARCHAR(64),
    buyer_woreda VARCHAR(32),
    buyer_kebele VARCHAR(32),
    buyer_house_no VARCHAR(32),
    
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
    
    CONSTRAINT uk_tenant_document_number UNIQUE (tenant_id, document_number),
    CONSTRAINT uk_tenant_invoice_counter UNIQUE (tenant_id, invoice_counter)
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
CREATE INDEX idx_invoice_lines_tenant ON invoice_lines(tenant_id);

CREATE TABLE IF NOT EXISTS receipts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_type VARCHAR(32) NOT NULL,
    invoice_id UUID NOT NULL,
    invoice_irn VARCHAR(128) NOT NULL,
    rrn VARCHAR(128) NOT NULL UNIQUE,
    receipt_number VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    withholding_amount NUMERIC(18, 2) DEFAULT 0.00,
    qr_code TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_receipts_tenant_inv ON receipts(tenant_id, invoice_id);
CREATE INDEX idx_receipts_rrn ON receipts(rrn);

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

CREATE INDEX idx_tax_adjustments_tenant ON tax_adjustments(tenant_id);
CREATE INDEX idx_tax_adjustments_irn ON tax_adjustments(irn);

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
CREATE INDEX idx_cancellations_tenant ON cancellation_requests(tenant_id);

-- ==============================================================================
-- 4. OFFLINE TRANSACTIONS & BUFFER PROTOCOL
-- ==============================================================================

CREATE TABLE IF NOT EXISTS offline_transaction_buffer (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id),
    offline_seq_no BIGINT NOT NULL,
    offline_session_id VARCHAR(64),
    client_transaction_id VARCHAR(128),
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
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

CREATE INDEX idx_offline_buffer_tenant_sync ON offline_transaction_buffer(tenant_id, sync_status);

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

CREATE INDEX idx_device_offline_alloc ON device_offline_allocations(tenant_id, device_id, is_active);

-- ==============================================================================
-- 5. IDEMPOTENCY, OUTBOX & INBOX INFRASTRUCTURE
-- ==============================================================================

CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT_CLIENT',
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    status VARCHAR(32) NOT NULL, -- 'PROCESSING', 'COMPLETED', 'FAILED'
    response_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tenant_client_idempotency UNIQUE (tenant_id, client_id, idempotency_key)
);

CREATE INDEX idx_idempotency_lookup ON idempotency_records(tenant_id, client_id, idempotency_key);
CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    locked_by VARCHAR(64),
    locked_at TIMESTAMPTZ,
    last_error TEXT
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_events(status, next_attempt_at);
CREATE INDEX idx_outbox_tenant_aggregate ON outbox_events(tenant_id, aggregate_type, aggregate_id);

CREATE TABLE IF NOT EXISTS inbox_events (
    id UUID PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    CONSTRAINT uk_source_event UNIQUE (source, event_id)
);

CREATE INDEX idx_inbox_source_event ON inbox_events(source, event_id);

CREATE TABLE IF NOT EXISTS government_submissions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    provider_version VARCHAR(32) NOT NULL,
    submission_id VARCHAR(128) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    government_reference VARCHAR(128),
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gov_submissions_status ON government_submissions(status, next_retry_at);
CREATE INDEX idx_gov_submissions_invoice ON government_submissions(tenant_id, invoice_id);

-- ==============================================================================
-- 6. TAX ENGINE, METRIC METERING, DOCUMENTS & CONFIGURATION
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tax_rules (
    id UUID PRIMARY KEY,
    tax_code VARCHAR(32) NOT NULL,
    tax_type VARCHAR(32) NOT NULL,
    rate NUMERIC(8, 4) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tax_rules_lookup ON tax_rules(tax_code, is_active, effective_from);

INSERT INTO tax_rules (id, tax_code, tax_type, rate, effective_from, is_active, version)
VALUES
    ('c1111111-1111-1111-1111-111111111101', 'VAT15', 'VAT', 0.1500, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111102', 'VAT0', 'VAT', 0.0000, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111103', 'VATEX', 'VAT', 0.0000, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111104', 'TOT_2', 'TOT', 0.0200, '2020-01-01 00:00:00+00', TRUE, 1),
    ('c1111111-1111-1111-1111-111111111105', 'TOT_10', 'TOT', 0.1000, '2020-01-01 00:00:00+00', TRUE, 1)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_url VARCHAR(512) NOT NULL,
    secret_key VARCHAR(128) NOT NULL,
    subscribed_events VARCHAR(512) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_subs_tenant ON webhook_subscriptions(tenant_id, is_active);

CREATE TABLE IF NOT EXISTS outbound_webhook_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    target_url VARCHAR(512) NOT NULL,
    payload_json TEXT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_attempt_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_deliveries_status ON outbound_webhook_deliveries(status);
CREATE INDEX idx_webhook_deliveries_tenant ON outbound_webhook_deliveries(tenant_id);

CREATE TABLE IF NOT EXISTS usage_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    metric_type VARCHAR(64) NOT NULL,
    quantity NUMERIC(14, 2) NOT NULL DEFAULT 1.00,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resource_id VARCHAR(128)
);

CREATE INDEX idx_usage_tenant_time ON usage_records(tenant_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS stored_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    invoice_id UUID,
    document_type VARCHAR(32) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stored_docs_tenant_inv ON stored_documents(tenant_id, invoice_id);

CREATE TABLE IF NOT EXISTS export_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    artifact_url VARCHAR(512),
    artifact_checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_export_jobs_tenant ON export_jobs(tenant_id, status);

CREATE TABLE IF NOT EXISTS tenant_configuration_overrides (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    safety_classification VARCHAR(32) NOT NULL DEFAULT 'TENANT_OVERRIDABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uk_tenant_config_key UNIQUE (tenant_id, config_key)
);

CREATE INDEX idx_tenant_config_lookup ON tenant_configuration_overrides(tenant_id, config_key);

CREATE TABLE IF NOT EXISTS tenant_feature_flags (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feature_key VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(32) NOT NULL DEFAULT 'OPTIONAL_PRODUCT_FEATURE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uk_tenant_feature_key UNIQUE (tenant_id, feature_key)
);

CREATE INDEX idx_tenant_feature_lookup ON tenant_feature_flags(tenant_id, feature_key);

-- ==============================================================================
-- 7. AUDIT TRAIL, EVIDENCE CHAINS & COMPLIANCE RELEASES
-- ==============================================================================

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    stream_id VARCHAR(64) NOT NULL DEFAULT 'MAIN',
    sequence_number BIGINT NOT NULL DEFAULT 1,
    schema_version INTEGER NOT NULL DEFAULT 1,
    actor_id VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    client_id VARCHAR(64),
    device_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    client_ip VARCHAR(45),
    user_agent VARCHAR(255),
    correlation_id VARCHAR(64),
    trace_id VARCHAR(64),
    application_version VARCHAR(32) NOT NULL DEFAULT '1.0.0-RELEASE',
    payload_hash VARCHAR(64) NOT NULL,
    payload_json TEXT,
    previous_event_hash VARCHAR(64) NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_events_tenant_time ON audit_events(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_events_tenant_stream_seq ON audit_events(tenant_id, stream_id, sequence_number);
CREATE INDEX idx_audit_events_resource ON audit_events(resource_type, resource_id);
CREATE INDEX idx_audit_events_hash ON audit_events(event_hash);

CREATE TABLE IF NOT EXISTS audit_streams (
    tenant_id UUID NOT NULL,
    stream_id VARCHAR(64) NOT NULL,
    last_sequence_number BIGINT NOT NULL DEFAULT 0,
    last_event_hash VARCHAR(64) NOT NULL,
    last_event_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_streams PRIMARY KEY (tenant_id, stream_id)
);

CREATE TABLE IF NOT EXISTS audit_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    audit_event_id UUID NOT NULL,
    stream_id VARCHAR(64) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    canonical_payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1024)
);

CREATE INDEX idx_audit_outbox_status_next ON audit_outbox_events(status, next_attempt_at);
CREATE INDEX idx_audit_outbox_tenant_stream ON audit_outbox_events(tenant_id, stream_id);

CREATE TABLE IF NOT EXISTS audit_checkpoints (
    checkpoint_id UUID PRIMARY KEY,
    tenant_id UUID,
    stream_id VARCHAR(64) NOT NULL,
    first_event_sequence BIGINT NOT NULL,
    last_event_sequence BIGINT NOT NULL,
    event_count BIGINT NOT NULL,
    first_event_hash VARCHAR(64) NOT NULL,
    last_event_hash VARCHAR(64) NOT NULL,
    chain_state_hash VARCHAR(64) NOT NULL,
    previous_checkpoint_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    schema_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_audit_checkpoints_lookup ON audit_checkpoints(tenant_id, stream_id, last_event_sequence);

CREATE TABLE IF NOT EXISTS evidence_manifests (
    artifact_id UUID PRIMARY KEY,
    tenant_id UUID,
    artifact_type VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    previous_artifact_hash VARCHAR(64) NOT NULL,
    producer_version VARCHAR(32) NOT NULL DEFAULT '1.0.0-RELEASE',
    schema_version INTEGER NOT NULL DEFAULT 1,
    signature_metadata VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evidence_manifests_tenant ON evidence_manifests(tenant_id, artifact_type);

CREATE TABLE IF NOT EXISTS compliance_releases (
    id UUID PRIMARY KEY,
    system_version VARCHAR(32) NOT NULL,
    build_version VARCHAR(64) NOT NULL,
    git_commit VARCHAR(64) NOT NULL,
    artifact_checksum VARCHAR(64) NOT NULL,
    insa_cert_ref VARCHAR(128) NOT NULL,
    deployed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_by VARCHAR(64) NOT NULL
);

-- ==============================================================================
-- 8. IMMUTABILITY FUNCTIONS & TRIGGERS
-- ==============================================================================

CREATE OR REPLACE FUNCTION enforce_invoice_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IN ('REGISTERED', 'CANCELLED') THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'CANNOT_DELETE_OFFICIAL_INVOICE: Registered or cancelled invoices are permanently immutable';
        END IF;
        IF TG_OP = 'UPDATE' THEN
            IF OLD.pre_tax_total <> NEW.pre_tax_total OR
               OLD.tax_total <> NEW.tax_total OR
               OLD.excise_total <> NEW.excise_total OR
               OLD.grand_total <> NEW.grand_total OR
               OLD.invoice_counter <> NEW.invoice_counter OR
               OLD.document_number <> NEW.document_number OR
               OLD.irn <> NEW.irn THEN
                RAISE EXCEPTION 'FINANCIAL_MUTATION_FORBIDDEN: Registered or cancelled invoices are financially immutable';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_invoice_immutability ON invoices;
CREATE TRIGGER trg_invoice_immutability
BEFORE UPDATE OR DELETE ON invoices
FOR EACH ROW
EXECUTE FUNCTION enforce_invoice_immutability();

CREATE OR REPLACE FUNCTION enforce_audit_events_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'AUDIT_TRAIL_IMMUTABLE: Historical audit events cannot be modified once persisted.';
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'AUDIT_TRAIL_IMMUTABLE: Historical audit events cannot be deleted once persisted.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_events_immutability ON audit_events;
CREATE TRIGGER trg_audit_events_immutability
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION enforce_audit_events_immutability();

-- ==============================================================================
-- 9. DEFENSE-IN-DEPTH POSTGRESQL ROW LEVEL SECURITY (RLS)
-- ==============================================================================

-- Apply Row Level Security and FORCE RLS on all 17 tenant-isolated tables
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_invoices_rls_policy ON invoices;
CREATE POLICY tenant_invoices_rls_policy ON invoices
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_lines FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_invoice_lines_rls_policy ON invoice_lines;
CREATE POLICY tenant_invoice_lines_rls_policy ON invoice_lines
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_customers_rls_policy ON customers;
CREATE POLICY tenant_customers_rls_policy ON customers
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE receipts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_receipts_rls_policy ON receipts;
CREATE POLICY tenant_receipts_rls_policy ON receipts
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE tax_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE tax_adjustments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_tax_adjustments_rls_policy ON tax_adjustments;
CREATE POLICY tenant_tax_adjustments_rls_policy ON tax_adjustments
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE cancellation_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE cancellation_requests FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_cancellations_rls_policy ON cancellation_requests;
CREATE POLICY tenant_cancellations_rls_policy ON cancellation_requests
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_audit_events_rls_policy ON audit_events;
CREATE POLICY tenant_audit_events_rls_policy ON audit_events
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE offline_transaction_buffer ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline_transaction_buffer FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_offline_buffer_rls_policy ON offline_transaction_buffer;
CREATE POLICY tenant_offline_buffer_rls_policy ON offline_transaction_buffer
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE stored_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE stored_documents FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_stored_documents_rls_policy ON stored_documents;
CREATE POLICY tenant_stored_documents_rls_policy ON stored_documents
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE device_revocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_revocations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_device_revocations_rls_policy ON device_revocations;
CREATE POLICY tenant_device_revocations_rls_policy ON device_revocations
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE export_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE export_jobs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_export_jobs_rls_policy ON export_jobs;
CREATE POLICY tenant_export_jobs_rls_policy ON export_jobs
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE webhook_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_subscriptions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_webhooks_rls_policy ON webhook_subscriptions;
CREATE POLICY tenant_webhooks_rls_policy ON webhook_subscriptions
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE outbound_webhook_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbound_webhook_deliveries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_webhook_deliveries_rls_policy ON outbound_webhook_deliveries;
CREATE POLICY tenant_webhook_deliveries_rls_policy ON outbound_webhook_deliveries
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_records FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_usage_records_rls_policy ON usage_records;
CREATE POLICY tenant_usage_records_rls_policy ON usage_records
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE tenant_invoice_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_invoice_sequences FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_invoice_sequences_rls_policy ON tenant_invoice_sequences;
CREATE POLICY tenant_invoice_sequences_rls_policy ON tenant_invoice_sequences
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE tenant_configuration_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_configuration_overrides FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_config_overrides_rls_policy ON tenant_configuration_overrides;
CREATE POLICY tenant_config_overrides_rls_policy ON tenant_configuration_overrides
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE tenant_feature_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_feature_flags FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_feature_flags_rls_policy ON tenant_feature_flags;
CREATE POLICY tenant_feature_flags_rls_policy ON tenant_feature_flags
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

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

