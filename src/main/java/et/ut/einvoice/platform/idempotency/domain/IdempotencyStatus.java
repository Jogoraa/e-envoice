package et.ut.einvoice.platform.idempotency.domain;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
