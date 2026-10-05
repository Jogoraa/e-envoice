package et.ut.einvoice.portability.domain;

public enum TenantExitStatus {
    REQUESTED,
    FROZEN,
    EXPORT_GENERATED,
    ARCHIVE_VERIFIED,
    TENANT_CONFIRMED,
    RETENTION_EVALUATED,
    PURGED,
    MIGRATED,
    CANCELLED
}
