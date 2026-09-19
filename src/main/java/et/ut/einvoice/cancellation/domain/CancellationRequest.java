package et.ut.einvoice.cancellation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "sla_deadline_at", nullable = false)
    private Instant slaDeadlineAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancellation_ref", length = 128)
    private String cancellationRef;

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
        // 48-Hour SLA deadline per Directive Art. 26(3)
        this.slaDeadlineAt = Instant.now().plus(48, ChronoUnit.HOURS);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getIrn() { return irn; }
    public String getReasonCategory() { return reasonCategory; }
    public String getDetailedReason() { return detailedReason; }
    public CancellationState getState() { return state; }
    public Instant getSlaDeadlineAt() { return slaDeadlineAt; }
    public String getCancellationRef() { return cancellationRef; }

    public void approve(String cancellationRef) {
        this.state = CancellationState.APPROVED;
        this.cancellationRef = cancellationRef;
        this.approvedAt = Instant.now();
    }

    public void reject(String reason) {
        this.state = CancellationState.REJECTED;
        this.detailedReason = this.detailedReason + " [REJECTION REASON: " + reason + "]";
    }
}
