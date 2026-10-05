-- ==============================================================================
-- Flyway Migration V19: Device Offline Pre-Allocated Document Ranges
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 4(4), Art. 22
-- ==============================================================================
-- 1. Create table if it doesn't exist at all
CREATE TABLE IF NOT EXISTS device_offline_allocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    allocation_id VARCHAR(64) NOT NULL,
    range_start BIGINT NOT NULL,
    range_end BIGINT NOT NULL,
    next_value BIGINT NOT NULL,
    allocated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ALLOCATED',
    authority_registration_ref VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_allocation_id UNIQUE (tenant_id, allocation_id),
    CONSTRAINT chk_range_order CHECK (range_start <= range_end),
    CONSTRAINT chk_next_value CHECK (
        next_value >= range_start
        AND next_value <= range_end + 1
    ),
    CONSTRAINT chk_allocation_status CHECK (
        status IN (
            'ALLOCATED',
            'ACTIVE',
            'EXHAUSTED',
            'EXPIRED',
            'REVOKED',
            'RECONCILED'
        )
    )
);
-- 2. Safely add missing columns if the table was partially created previously
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS allocation_id VARCHAR(64);
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS range_start BIGINT;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS range_end BIGINT;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS next_value BIGINT;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS allocated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'ALLOCATED';
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS authority_registration_ref VARCHAR(128);
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE device_offline_allocations
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;
-- 3. Create indexes safely
CREATE INDEX IF NOT EXISTS idx_device_alloc_tenant_dev ON device_offline_allocations(tenant_id, device_id, status);
CREATE INDEX IF NOT EXISTS idx_device_alloc_range ON device_offline_allocations(tenant_id, range_start, range_end);
-- 4. RLS Enforcement
ALTER TABLE device_offline_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_offline_allocations FORCE ROW LEVEL SECURITY;
DO $$ BEGIN IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE tablename = 'device_offline_allocations'
        AND policyname = 'tenant_isolation_device_offline_allocations'
) THEN CREATE POLICY tenant_isolation_device_offline_allocations ON device_offline_allocations FOR ALL USING (
    tenant_id = NULLIF(
        current_setting('app.current_tenant_id', true),
        ''
    )::UUID
    OR NULLIF(
        current_setting('app.current_user_role', true),
        ''
    ) IN (
        'ROLE_PLATFORM_ADMIN',
        'ROLE_SAAS_ADMIN',
        'ROLE_AUTHORITY_AUDITOR'
    )
) WITH CHECK (
    tenant_id = NULLIF(
        current_setting('app.current_tenant_id', true),
        ''
    )::UUID
    OR NULLIF(
        current_setting('app.current_user_role', true),
        ''
    ) IN (
        'ROLE_PLATFORM_ADMIN',
        'ROLE_SAAS_ADMIN',
        'ROLE_AUTHORITY_AUDITOR'
    )
);
END IF;
END $$;