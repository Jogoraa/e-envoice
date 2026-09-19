package et.ut.einvoice.government.domain;

public enum GovernmentSubmissionStatus {
    QUEUED,
    IN_FLIGHT,
    ACCEPTED,
    REJECTED,
    UNKNOWN,
    NEEDS_RECONCILIATION
}
