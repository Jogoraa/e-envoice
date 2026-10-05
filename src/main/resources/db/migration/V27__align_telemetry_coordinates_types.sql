-- ==============================================================================
-- Flyway Migration V27: Align Device Telemetry & Geolocation GPS Column Types
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 4(5)
-- Converts GPS coordinates and accuracy to DOUBLE PRECISION to match IEEE 754 floating point standard and Hibernate entity mapping
-- ==============================================================================

ALTER TABLE device_telemetry_logs
    ALTER COLUMN latitude TYPE DOUBLE PRECISION,
    ALTER COLUMN longitude TYPE DOUBLE PRECISION,
    ALTER COLUMN accuracy TYPE DOUBLE PRECISION;

ALTER TABLE devices
    ALTER COLUMN last_latitude TYPE DOUBLE PRECISION,
    ALTER COLUMN last_longitude TYPE DOUBLE PRECISION,
    ALTER COLUMN last_accuracy TYPE DOUBLE PRECISION;

ALTER TABLE invoices
    ALTER COLUMN latitude TYPE DOUBLE PRECISION,
    ALTER COLUMN longitude TYPE DOUBLE PRECISION,
    ALTER COLUMN gps_accuracy TYPE DOUBLE PRECISION;
