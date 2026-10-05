-- =============================================================================
-- V25: Provider Exit Strategy & Transition Governance
-- Directive No. 1142/2026 (2018 E.C.) Article 17 (Termination of Service)
--
-- Statutory Enforcements:
-- 1. Art. 17(1): 6 months mandatory advance notice to Authority & user taxpayers for voluntary termination.
-- 2. Art. 17(2): 10 days mandatory notice to taxpayers if accreditation is canceled under Art. 16.
-- 3. Art. 17(3): Formal Exit Strategy submission and approval by the Authority.
-- 4. Art. 17(4): 6 months data retrieval and transfer window for all user taxpayers.
-- 5. Art. 17(5): Confirmation of cessation of service ONLY upon 100% tenant migration & certificate surrender.
-- =============================================================================

CREATE TABLE IF NOT EXISTS provider_exit_plans (
    id UUID PRIMARY KEY,
    exit_reason VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    notice_period_months INT NOT NULL DEFAULT 6,
    announcement_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_exit_date TIMESTAMPTZ NOT NULL,
    data_retrieval_deadline TIMESTAMPTZ NOT NULL,
    strategy_document_reference VARCHAR(255),
    strategy_submitted_at TIMESTAMPTZ,
    authority_approval_reference VARCHAR(128),
    authority_approved_at TIMESTAMPTZ,
    authority_approved_by VARCHAR(128),
    taxpayers_notified_at TIMESTAMPTZ,
    total_active_tenants INT NOT NULL DEFAULT 0,
    migrated_tenants_count INT NOT NULL DEFAULT 0,
    surrendered_certificate_reference VARCHAR(128),
    cessation_confirmation_reference VARCHAR(128),
    cessation_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_provider_exit_plans_status ON provider_exit_plans(status);

CREATE TABLE IF NOT EXISTS provider_tenant_transitions (
    id UUID PRIMARY KEY,
    exit_plan_id UUID NOT NULL REFERENCES provider_exit_plans(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tenant_tin VARCHAR(32) NOT NULL,
    tenant_legal_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NOTIFIED',
    notification_sent_at TIMESTAMPTZ,
    data_retrieval_completed_at TIMESTAMPTZ,
    destination_provider_name VARCHAR(255),
    destination_system_number VARCHAR(128),
    migration_confirmed_at TIMESTAMPTZ,
    migration_evidence_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_provider_tenant_transition UNIQUE (exit_plan_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_provider_tenant_transitions_plan ON provider_tenant_transitions(exit_plan_id);
CREATE INDEX IF NOT EXISTS idx_provider_tenant_transitions_status ON provider_tenant_transitions(status);
