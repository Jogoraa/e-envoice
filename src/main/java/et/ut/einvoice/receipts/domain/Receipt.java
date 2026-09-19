package et.ut.einvoice.receipts.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_type", nullable = false, length = 32)
    private ReceiptType receiptType;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "invoice_irn", nullable = false, length = 128)
    private String invoiceIrn;

    @Column(name = "rrn", nullable = false, length = 128, unique = true)
    private String rrn;

    @Column(name = "receipt_number", nullable = false, length = 64)
    private String receiptNumber;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "withholding_amount", precision = 18, scale = 2)
    private BigDecimal withholdingAmount = BigDecimal.ZERO;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "REGISTERED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Receipt() {}

    public Receipt(UUID id, UUID tenantId, ReceiptType receiptType, UUID invoiceId, String invoiceIrn,
                   String receiptNumber, BigDecimal amount, BigDecimal withholdingAmount, String rrn, String qrCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.receiptType = receiptType;
        this.invoiceId = invoiceId;
        this.invoiceIrn = invoiceIrn;
        this.receiptNumber = receiptNumber;
        this.amount = amount;
        this.withholdingAmount = withholdingAmount != null ? withholdingAmount : BigDecimal.ZERO;
        this.rrn = rrn;
        this.qrCode = qrCode;
        this.status = "REGISTERED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public ReceiptType getReceiptType() { return receiptType; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getInvoiceIrn() { return invoiceIrn; }
    public String getRrn() { return rrn; }
    public String getReceiptNumber() { return receiptNumber; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getWithholdingAmount() { return withholdingAmount; }
    public String getQrCode() { return qrCode; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
