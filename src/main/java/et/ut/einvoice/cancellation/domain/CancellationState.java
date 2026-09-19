package et.ut.einvoice.cancellation.domain;

public enum CancellationState {
    REQUESTED,
    EVIDENCE_REQUIRED,
    SUBMITTED_TO_AUTHORITY,
    APPROVED,
    REJECTED,
    CANCELLED_CONFIRMED,
    SLA_EXPIRED
}
