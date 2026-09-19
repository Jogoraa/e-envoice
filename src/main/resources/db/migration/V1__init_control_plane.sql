-- V1__init_control_plane.sql
-- Control Plane Schema: Tenants, Subscriptions, API Clients, Users

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    tin VARCHAR(16) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ONBOARDING',
    tenant_type VARCHAR(32) NOT NULL DEFAULT 'SME',
    database_shard VARCHAR(64) DEFAULT 'shared_cluster',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ
);

CREATE INDEX idx_tenants_tin ON tenants(tin);
CREATE INDEX idx_tenants_status ON tenants(status);

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_subscriptions_tenant ON subscriptions(tenant_id);

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
    role VARCHAR(32) NOT NULL DEFAULT 'CASHIER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, username)
);
