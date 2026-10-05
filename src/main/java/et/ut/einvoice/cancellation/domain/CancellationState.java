package et.ut.einvoice.cancellation.domain;

/**
 * Statutory state machine for fiscal invoice cancellation.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 26.
 */
public enum CancellationState {
    REQUESTED,
    SUBMITTED_TO_MOR,
    UNDER_REVIEW,
    EVIDENCE_REQUESTED,
    EVIDENCE_SUBMITTED,
    APPROVED,
    REJECTED,
    EXPIRED,
    UNKNOWN_OUTCOME,

    // Backward-compatible aliases for legacy test fixtures
    EVIDENCE_REQUIRED,
    SUBMITTED_TO_AUTHORITY,
    CANCELLED_CONFIRMED,
    SLA_EXPIRED
}
