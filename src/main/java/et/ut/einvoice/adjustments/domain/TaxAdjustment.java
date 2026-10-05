package et.ut.einvoice.adjustments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_adjustments")
public class TaxAdjustment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 16)
    private NoteType noteType;

    @Column(name = "original_invoice_id", nullable = false)
    private UUID originalInvoiceId;

    @Column(name = "original_irn", nullable = false, length = 128)
    private String originalIrn;

    @Column(name = "adjustment_reason", nullable = false)
    private String adjustmentReason;

    @Column(name = "adjusted_pre_tax", nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustedPreTax;

    @Column(name = "adjusted_tax", nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustedTax;

    @Column(name = "adjusted_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustedTotal;

    @Column(name = "original_grand_total", precision = 18, scale = 2)
    private BigDecimal originalGrandTotal;

    @Column(name = "new_grand_total", precision = 18, scale = 2)
    private BigDecimal newGrandTotal;

    @Column(name = "irn", length = 128, unique = true)
    private String irn;

    @Column(name = "rrn", length = 128)
    private String rrn;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "ack_date", length = 64)
    private String ackDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Enumerated(EnumType.STRING)
    @Column(name = "mor_status", nullable = false, length = 32)
    private AdjustmentMoRStatus morStatus = AdjustmentMoRStatus.PENDING;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "submission_attempts", nullable = false)
    private int submissionAttempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 32)
    private AdjustmentReconciliationStatus reconciliationStatus = AdjustmentReconciliationStatus.NONE;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "signed_payload", columnDefinition = "TEXT")
    private String signedPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TaxAdjustmentLine> lines = new ArrayList<>();

    public TaxAdjustment() {}

    public TaxAdjustment(UUID id, UUID tenantId, NoteType noteType, UUID originalInvoiceId, String originalIrn,
                         String adjustmentReason, BigDecimal adjustedPreTax, BigDecimal adjustedTax, BigDecimal adjustedTotal,
                         BigDecimal originalGrandTotal, BigDecimal newGrandTotal, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.noteType = noteType;
        this.originalInvoiceId = originalInvoiceId;
        this.originalIrn = originalIrn;
        this.adjustmentReason = adjustmentReason;
        this.adjustedPreTax = adjustedPreTax;
        this.adjustedTax = adjustedTax;
        this.adjustedTotal = adjustedTotal;
        this.originalGrandTotal = originalGrandTotal;
        this.newGrandTotal = newGrandTotal;
        this.idempotencyKey = idempotencyKey;
        this.status = "PENDING";
        this.morStatus = AdjustmentMoRStatus.PENDING;
        this.reconciliationStatus = AdjustmentReconciliationStatus.NONE;
        this.submissionAttempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public NoteType getNoteType() { return noteType; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public String getOriginalIrn() { return originalIrn; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public BigDecimal getAdjustedPreTax() { return adjustedPreTax; }
    public BigDecimal getAdjustedTax() { return adjustedTax; }
    public BigDecimal getAdjustedTotal() { return adjustedTotal; }
    public BigDecimal getOriginalGrandTotal() { return originalGrandTotal; }
    public BigDecimal getNewGrandTotal() { return newGrandTotal; }
    public String getIrn() { return irn; }
    public String getRrn() { return rrn; }
    public String getQrCode() { return qrCode; }
    public String getAckDate() { return ackDate; }
    public String getStatus() { return status; }
    public AdjustmentMoRStatus getMorStatus() { return morStatus; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getSubmissionAttempts() { return submissionAttempts; }
    public AdjustmentReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public String getErrorMessage() { return errorMessage; }
    public String getSignedPayload() { return signedPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<TaxAdjustmentLine> getLines() { return lines; }

    public void addLine(TaxAdjustmentLine line) {
        line.setAdjustment(this);
        this.lines.add(line);
    }

    public void markSubmitted() {
        this.morStatus = AdjustmentMoRStatus.SUBMITTED;
        this.submissionAttempts++;
        this.updatedAt = Instant.now();
    }

    public void markRegistered(String irn, String rrn, String ackDate, String qrCode, String signedPayload) {
        this.irn = irn;
        this.rrn = rrn;
        this.ackDate = ackDate != null ? ackDate : Instant.now().toString();
        this.qrCode = qrCode;
        this.signedPayload = signedPayload;
        this.status = "REGISTERED";
        this.morStatus = AdjustmentMoRStatus.REGISTERED;
        this.reconciliationStatus = AdjustmentReconciliationStatus.NONE;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.morStatus = AdjustmentMoRStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void markUnknown(String reason) {
        this.morStatus = AdjustmentMoRStatus.UNKNOWN;
        this.reconciliationStatus = AdjustmentReconciliationStatus.PENDING;
        this.errorMessage = reason;
        this.updatedAt = Instant.now();
    }

    public void setIrn(String irn) { this.irn = irn; }
    public void setAckDate(String ackDate) { this.ackDate = ackDate; }
    public void setStatus(String status) { this.status = status; }
}
