package et.ut.einvoice.compliance.domain;

/**
 * Migration status of an individual taxpayer tenant during provider cessation under Art. 17.
 */
public enum TenantTransitionStatus {
    NOTIFIED,
    DATA_RETRIEVED,
    MIGRATING,
    MIGRATED,
    ALTERNATIVE_SYSTEM_ACTIVE
}
