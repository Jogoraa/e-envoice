package et.ut.einvoice.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable outbox entity for transactional electronic invoice SMS notifications.
 * Strictly decoupled from authoritative fiscal invoice status.
 */
@Entity
@Table(name = "invoice_notification_outbox")
public class InvoiceNotificationOutbox {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "recipient_party_id", nullable = false, length = 128)
    private String recipientPartyId;

    @Column(name = "recipient_phone_snapshot", nullable = false, length = 32)
    private String recipientPhoneSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType notificationType;

    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    @Column(name = "template_version", nullable = false, length = 16)
    private String templateVersion;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "template_params", columnDefinition = "jsonb")
    private String templateParams;

    @Column(name = "rendered_message", columnDefinition = "TEXT")
    private String renderedMessage;

    @Column(name = "rendered_message_hash", nullable = false, length = 64)
    private String renderedMessageHash;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_classification", length = 64)
    private FailureClassification failureClassification;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider = "MOCK";

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    public InvoiceNotificationOutbox() {}

    public InvoiceNotificationOutbox(
            UUID id,
            UUID tenantId,
            UUID invoiceId,
            String recipientPartyId,
            String recipientPhoneSnapshot,
            NotificationType notificationType,
            String templateId,
            String templateVersion,
            String templateParams,
            String renderedMessage,
            String renderedMessageHash,
            String idempotencyKey,
            String provider,
            String correlationId
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.recipientPartyId = recipientPartyId;
        this.recipientPhoneSnapshot = recipientPhoneSnapshot;
        this.notificationType = notificationType;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.templateParams = templateParams;
        this.renderedMessage = renderedMessage;
        this.renderedMessageHash = renderedMessageHash;
        this.idempotencyKey = idempotencyKey;
        this.provider = provider;
        this.correlationId = correlationId;
        this.status = NotificationStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = 5;
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getRecipientPartyId() { return recipientPartyId; }
    public String getRecipientPhoneSnapshot() { return recipientPhoneSnapshot; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getTemplateId() { return templateId; }
    public String getTemplateVersion() { return templateVersion; }
    public String getTemplateParams() { return templateParams; }
    public String getRenderedMessage() { return renderedMessage; }
    public String getRenderedMessageHash() { return renderedMessageHash; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public NotificationStatus getStatus() { return status; }
    public FailureClassification getFailureClassification() { return failureClassification; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLockedAt() { return lockedAt; }
    public String getLockedBy() { return lockedBy; }
    public String getProvider() { return provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getFailedAt() { return failedAt; }
    public String getCorrelationId() { return correlationId; }

    public void markInFlight(String workerId) {
        this.status = NotificationStatus.IN_FLIGHT;
        this.lockedAt = Instant.now();
        this.lockedBy = workerId;
        this.attemptCount++;
    }

    public void markSubmitted(String providerMessageId) {
        this.status = NotificationStatus.SUBMITTED;
        this.providerMessageId = providerMessageId;
        this.submittedAt = Instant.now();
        this.lockedAt = null;
        this.lockedBy = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    public void markSubmissionUnknown(String errorCode, String errorMessage) {
        this.status = NotificationStatus.SUBMISSION_UNKNOWN;
        this.failureClassification = FailureClassification.PROVIDER_UNKNOWN_OUTCOME;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public void markReconciling() {
        this.status = NotificationStatus.RECONCILING;
    }

    public void markSubmissionUnknownFinal() {
        this.status = NotificationStatus.SUBMISSION_UNKNOWN_FINAL;
        this.failedAt = Instant.now();
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public void markDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void markDeliveryFailed(String reason) {
        this.status = NotificationStatus.DELIVERY_FAILED;
        this.lastErrorMessage = reason;
        this.failedAt = Instant.now();
    }

    public void scheduleRetry(Instant nextRetryAt, String errorCode, String errorMessage, FailureClassification classification) {
        if (this.attemptCount >= this.maxAttempts) {
            markPermanentlyFailed(errorCode, errorMessage, classification);
        } else {
            this.status = NotificationStatus.RETRY_SCHEDULED;
            this.nextAttemptAt = nextRetryAt;
            this.lastErrorCode = errorCode;
            this.lastErrorMessage = errorMessage;
            this.failureClassification = classification;
            this.lockedAt = null;
            this.lockedBy = null;
        }
    }

    public void markPermanentlyFailed(String errorCode, String errorMessage, FailureClassification classification) {
        this.status = NotificationStatus.PERMANENTLY_FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.failureClassification = classification;
        this.failedAt = Instant.now();
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public void releaseLock() {
        this.lockedAt = null;
        this.lockedBy = null;
        if (this.status == NotificationStatus.IN_FLIGHT) {
            this.status = NotificationStatus.PENDING;
        }
    }
}
