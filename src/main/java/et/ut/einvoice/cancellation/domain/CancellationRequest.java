package et.ut.einvoice.cancellation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Statutory Fiscal Cancellation Request.
 * Implements FDRE MoR Directive No. 1142/2026 Art. 26.
 */
@Entity
@Table(name = "cancellation_requests")
public class CancellationRequest {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "irn", nullable = false, length = 128)
    private String irn;

    @Column(name = "reason_category", nullable = false, length = 64)
    private String reasonCategory;

    @Column(name = "detailed_reason", nullable = false, columnDefinition = "TEXT")
    private String detailedReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private CancellationState state = CancellationState.REQUESTED;

    @Column(name = "evidence_data", columnDefinition = "TEXT")
    private String evidenceData;

    @Column(name = "authority_evidence_requested_at")
    private Instant authorityEvidenceRequestedAt;

    @Column(name = "evidence_deadline")
    private Instant evidenceDeadline;

    @Column(name = "evidence_submitted_at")
    private Instant evidenceSubmittedAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "sla_deadline_at", nullable = false)
    private Instant slaDeadlineAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancellation_ref", length = 128)
    private String cancellationRef;

    @Column(name = "mor_submission_id", length = 128)
    private String morSubmissionId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CancellationRequest() {}

    public CancellationRequest(UUID id, UUID tenantId, UUID invoiceId, String irn, String reasonCategory, String detailedReason) {
        this.id = id;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.irn = irn;
        this.reasonCategory = reasonCategory;
        this.detailedReason = detailedReason;
        this.state = CancellationState.REQUESTED;
        this.requestedAt = Instant.now();
        // Baseline statutory review window (e.g. 30 days)
        this.slaDeadlineAt = this.requestedAt.plus(30, ChronoUnit.DAYS);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }

    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }

    public String getReasonCategory() { return reasonCategory; }
    public void setReasonCategory(String reasonCategory) { this.reasonCategory = reasonCategory; }

    public String getDetailedReason() { return detailedReason; }
    public void setDetailedReason(String detailedReason) { this.detailedReason = detailedReason; }

    public CancellationState getState() { return state; }
    public void setState(CancellationState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public String getEvidenceData() { return evidenceData; }
    public void setEvidenceData(String evidenceData) { this.evidenceData = evidenceData; }

    public Instant getAuthorityEvidenceRequestedAt() { return authorityEvidenceRequestedAt; }
    public void setAuthorityEvidenceRequestedAt(Instant authorityEvidenceRequestedAt) { this.authorityEvidenceRequestedAt = authorityEvidenceRequestedAt; }

    public Instant getEvidenceDeadline() { return evidenceDeadline; }
    public void setEvidenceDeadline(Instant evidenceDeadline) { this.evidenceDeadline = evidenceDeadline; }

    public Instant getEvidenceSubmittedAt() { return evidenceSubmittedAt; }
    public void setEvidenceSubmittedAt(Instant evidenceSubmittedAt) { this.evidenceSubmittedAt = evidenceSubmittedAt; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getSlaDeadlineAt() { return slaDeadlineAt; }
    public void setSlaDeadlineAt(Instant slaDeadlineAt) { this.slaDeadlineAt = slaDeadlineAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public String getCancellationRef() { return cancellationRef; }
    public void setCancellationRef(String cancellationRef) { this.cancellationRef = cancellationRef; }

    public String getMorSubmissionId() { return morSubmissionId; }
    public void setMorSubmissionId(String morSubmissionId) { this.morSubmissionId = morSubmissionId; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Authority demands additional evidence under Art. 26(3).
     * The statutory 48-hour clock begins NOW.
     */
    public void requestEvidence(Instant deadline) {
        this.state = CancellationState.EVIDENCE_REQUESTED;
        this.authorityEvidenceRequestedAt = Instant.now();
        this.evidenceDeadline = deadline != null ? deadline : this.authorityEvidenceRequestedAt.plus(48, ChronoUnit.HOURS);
        this.updatedAt = Instant.now();
    }

    /**
     * Taxpayer submits evidence attachments within the 48-hour window.
     */
    public void submitEvidence(String evidenceData) {
        this.state = CancellationState.EVIDENCE_SUBMITTED;
        this.evidenceData = evidenceData;
        this.evidenceSubmittedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void approve(String cancellationRef) {
        this.state = CancellationState.APPROVED;
        this.cancellationRef = cancellationRef;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        this.state = CancellationState.REJECTED;
        this.rejectionReason = reason;
        this.detailedReason = this.detailedReason + " [REJECTION REASON: " + reason + "]";
        this.updatedAt = Instant.now();
    }

    public void expire() {
        this.state = CancellationState.EXPIRED;
        this.updatedAt = Instant.now();
    }
}
