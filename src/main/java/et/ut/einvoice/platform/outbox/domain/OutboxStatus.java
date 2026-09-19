package et.ut.einvoice.platform.outbox.domain;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    PUBLISHED,
    FAILED,
    DEAD_LETTER
}
