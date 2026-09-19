package et.ut.einvoice.adjustments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(name = "irn", length = 128, unique = true)
    private String irn;

    @Column(name = "ack_date", length = 64)
    private String ackDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "REGISTERED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public TaxAdjustment() {}

    public TaxAdjustment(UUID id, UUID tenantId, NoteType noteType, UUID originalInvoiceId, String originalIrn,
                         String adjustmentReason, BigDecimal adjustedPreTax, BigDecimal adjustedTax, BigDecimal adjustedTotal) {
        this.id = id;
        this.tenantId = tenantId;
        this.noteType = noteType;
        this.originalInvoiceId = originalInvoiceId;
        this.originalIrn = originalIrn;
        this.adjustmentReason = adjustmentReason;
        this.adjustedPreTax = adjustedPreTax;
        this.adjustedTax = adjustedTax;
        this.adjustedTotal = adjustedTotal;
        this.status = "REGISTERED";
        this.createdAt = Instant.now();
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
    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }
    public String getAckDate() { return ackDate; }
    public void setAckDate(String ackDate) { this.ackDate = ackDate; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
