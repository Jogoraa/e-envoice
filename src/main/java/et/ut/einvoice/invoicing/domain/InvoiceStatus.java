package et.ut.einvoice.invoicing.domain;

public enum InvoiceStatus {
    DRAFT,
    VALIDATED,
    PENDING_REGISTRATION,
    SUBMISSION_PENDING,
    REGISTERED,
    OFFLINE_BUFFERED,
    SUBMISSION_FAILED,
    CANCELLED
}
