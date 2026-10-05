package et.ut.einvoice.compliance.domain;

/**
 * Status lifecycle of a Provider Exit Strategy under Directive No. 1142/2026 Art. 17.
 */
public enum ProviderExitStatus {
    PLANNED,
    STRATEGY_SUBMITTED,
    AUTHORITY_APPROVED,
    TAXPAYERS_NOTIFIED,
    IN_TRANSITION,
    MIGRATION_COMPLETED,
    CESSATION_CONFIRMED
}
