package et.ut.einvoice.government.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "government_submissions")
public class GovernmentSubmission {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "provider_version", nullable = false, length = 32)
    private String providerVersion;

    @Column(name = "submission_id", nullable = false, length = 128, unique = true)
    private String submissionId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GovernmentSubmissionStatus status = GovernmentSubmissionStatus.QUEUED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "government_reference", length = 128)
    private String governmentReference;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public GovernmentSubmission() {}

    public GovernmentSubmission(UUID id, UUID tenantId, UUID invoiceId, String provider,
                                String providerVersion, String submissionId, String requestHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.provider = provider;
        this.providerVersion = providerVersion;
        this.submissionId = submissionId;
        this.requestHash = requestHash;
        this.status = GovernmentSubmissionStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getProvider() { return provider; }
    public String getProviderVersion() { return providerVersion; }
    public String getSubmissionId() { return submissionId; }
    public String getRequestHash() { return requestHash; }
    public GovernmentSubmissionStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String getGovernmentReference() { return governmentReference; }
    public String getGovernmentIrn() { return governmentReference; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getNextRetryAt() { return nextRetryAt; }

    public void markInFlight() {
        this.status = GovernmentSubmissionStatus.IN_FLIGHT;
        this.attemptCount++;
        this.submittedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markAccepted(String reference) {
        this.status = GovernmentSubmissionStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
        this.governmentReference = reference;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markUnknown(String errorCode, String message) {
        this.status = GovernmentSubmissionStatus.UNKNOWN;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = message;
        this.updatedAt = Instant.now();
    }

    public void markRejected(String errorCode, String message) {
        this.status = GovernmentSubmissionStatus.REJECTED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = message;
        this.updatedAt = Instant.now();
    }

    public void markNeedsReconciliation(String reason) {
        this.status = GovernmentSubmissionStatus.NEEDS_RECONCILIATION;
        this.lastErrorMessage = reason;
        this.updatedAt = Instant.now();
    }
}
