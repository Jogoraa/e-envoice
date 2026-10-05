-- ==============================================================================
-- Flyway Migration V16: mPOS Geolocation Compliance, Device Registration & Telemetry
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 4(5)
-- ==============================================================================

-- 1. Extend devices table with registration status, public key, geofence, and telemetry state
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS registration_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS public_key TEXT,
    ADD COLUMN IF NOT EXISTS authorized_geofence_id UUID REFERENCES geofences(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS last_accuracy NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS last_heartbeat TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_telemetry_status VARCHAR(32) DEFAULT 'OK';

-- 2. Extend invoices table with transaction geolocation capture fields
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS gps_accuracy NUMERIC(10, 2);

-- 3. Device Telemetry & Geolocation Heartbeat Logs
CREATE TABLE IF NOT EXISTS device_telemetry_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    accuracy NUMERIC(10, 2) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    battery_level INT,
    is_inside_geofence BOOLEAN NOT NULL DEFAULT TRUE,
    telemetry_source VARCHAR(32) NOT NULL DEFAULT 'HEARTBEAT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_telemetry_lookup ON device_telemetry_logs(tenant_id, device_id, captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_devices_geofence ON devices(tenant_id, authorized_geofence_id);

-- 4. Enable and FORCE Row Level Security on device_telemetry_logs
ALTER TABLE device_telemetry_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_telemetry_logs FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_device_telemetry_logs_rls_policy ON device_telemetry_logs;
CREATE POLICY tenant_device_telemetry_logs_rls_policy ON device_telemetry_logs
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.is_platform_admin', true) = 'true'
    );
