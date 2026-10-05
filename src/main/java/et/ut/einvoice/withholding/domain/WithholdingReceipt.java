package et.ut.einvoice.withholding.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "withholding_receipts")
public class WithholdingReceipt {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "receipt_number", nullable = false, length = 64)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "withholding_type", nullable = false, length = 32)
    private WithholdingType withholdingType;

    @Column(name = "related_invoice_id")
    private UUID relatedInvoiceId;

    @Column(name = "related_invoice_irn", length = 128)
    private String relatedInvoiceIrn;

    @Column(name = "withholding_agent_tin", nullable = false, length = 32)
    private String withholdingAgentTin;

    @Column(name = "withholding_agent_name", nullable = false)
    private String withholdingAgentName;

    @Column(name = "taxpayer_tin", nullable = false, length = 32)
    private String taxpayerTin;

    @Column(name = "taxpayer_name", nullable = false)
    private String taxpayerName;

    @Column(name = "tax_base_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxBaseAmount;

    @Column(name = "withheld_tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal withheldTaxRate;

    @Column(name = "withheld_tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal withheldTaxAmount;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "issue_date", nullable = false)
    private Instant issueDate = Instant.now();

    @Column(name = "rrn", nullable = false, length = 128, unique = true)
    private String rrn;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "REGISTERED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public WithholdingReceipt() {}

    public WithholdingReceipt(UUID id, UUID tenantId, String receiptNumber, WithholdingType withholdingType,
                              UUID relatedInvoiceId, String relatedInvoiceIrn, String withholdingAgentTin,
                              String withholdingAgentName, String taxpayerTin, String taxpayerName,
                              BigDecimal taxBaseAmount, BigDecimal withheldTaxRate, BigDecimal withheldTaxAmount,
                              String paymentReference, Instant issueDate, String rrn, String qrCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.receiptNumber = receiptNumber;
        this.withholdingType = withholdingType;
        this.relatedInvoiceId = relatedInvoiceId;
        this.relatedInvoiceIrn = relatedInvoiceIrn;
        this.withholdingAgentTin = withholdingAgentTin;
        this.withholdingAgentName = withholdingAgentName;
        this.taxpayerTin = taxpayerTin;
        this.taxpayerName = taxpayerName;
        this.taxBaseAmount = taxBaseAmount;
        this.withheldTaxRate = withheldTaxRate;
        this.withheldTaxAmount = withheldTaxAmount;
        this.paymentReference = paymentReference;
        this.issueDate = issueDate != null ? issueDate : Instant.now();
        this.rrn = rrn;
        this.qrCode = qrCode;
        this.status = "REGISTERED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getReceiptNumber() { return receiptNumber; }
    public WithholdingType getWithholdingType() { return withholdingType; }
    public UUID getRelatedInvoiceId() { return relatedInvoiceId; }
    public String getRelatedInvoiceIrn() { return relatedInvoiceIrn; }
    public String getWithholdingAgentTin() { return withholdingAgentTin; }
    public String getWithholdingAgentName() { return withholdingAgentName; }
    public String getTaxpayerTin() { return taxpayerTin; }
    public String getTaxpayerName() { return taxpayerName; }
    public BigDecimal getTaxBaseAmount() { return taxBaseAmount; }
    public BigDecimal getWithheldTaxRate() { return withheldTaxRate; }
    public BigDecimal getWithheldTaxAmount() { return withheldTaxAmount; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getIssueDate() { return issueDate; }
    public String getRrn() { return rrn; }
    public String getQrCode() { return qrCode; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
