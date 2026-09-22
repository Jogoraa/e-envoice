package et.ut.einvoice.notifications.domain;

/**
 * Lifecycle states of an invoice notification delivery attempt.
 * Strictly decoupled from authoritative fiscal invoice status.
 */
public enum NotificationStatus {
    PENDING,
    IN_FLIGHT,
    SUBMITTED,
    SUBMISSION_UNKNOWN,
    RECONCILING,
    DELIVERED,
    DELIVERY_FAILED,
    RETRY_SCHEDULED,
    PERMANENTLY_FAILED,
    SUBMISSION_UNKNOWN_FINAL
}
