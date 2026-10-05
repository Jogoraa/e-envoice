-- ==============================================================================
-- Flyway Migration V18: Software Build Checksum & Version Integrity Evidence
-- Target Directive: FDRE MoR Directive No. 1142/2026 Art. 10(9), 11(2), 12(3)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS software_build_checksums (
    id UUID PRIMARY KEY,
    version VARCHAR(32) NOT NULL,
    git_sha VARCHAR(64) NOT NULL,
    backend_sha256 VARCHAR(128) NOT NULL,
    frontend_build_sha256 VARCHAR(128),
    container_digest VARCHAR(128),
    database_schema_version VARCHAR(32) NOT NULL,
    sbom_hash VARCHAR(128),
    registered_checksum VARCHAR(128),
    authority_signature TEXT,
    build_timestamp TIMESTAMPTZ NOT NULL,
    is_active_release BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_software_version UNIQUE (version)
);

CREATE INDEX IF NOT EXISTS idx_build_checksums_ver ON software_build_checksums(version, is_active_release);
