package et.ut.einvoice.cashreceipt.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cash_receipts")
public class CashReceipt {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "receipt_number", nullable = false, length = 64)
    private String receiptNumber;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "payer_tin", length = 32)
    private String payerTin;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "ETB";

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 64)
    private CashReceiptPurpose purpose;

    @Column(name = "purpose_description")
    private String purposeDescription;

    @Column(name = "related_invoice_id")
    private UUID relatedInvoiceId;

    @Column(name = "related_credit_account_id", length = 64)
    private String relatedCreditAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private CashReceiptPaymentMethod paymentMethod = CashReceiptPaymentMethod.CASH;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "rrn", nullable = false, length = 128, unique = true)
    private String rrn;

    @Column(name = "irn", length = 128)
    private String irn;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CashReceiptStatus status = CashReceiptStatus.REGISTERED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CashReceipt() {}

    public CashReceipt(UUID id, UUID tenantId, String receiptNumber, String payerName, String payerTin,
                       BigDecimal amount, String currency, CashReceiptPurpose purpose, String purposeDescription,
                       UUID relatedInvoiceId, String relatedCreditAccountId, CashReceiptPaymentMethod paymentMethod,
                       String referenceNumber, Instant receivedAt, String rrn, String irn, String qrCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.receiptNumber = receiptNumber;
        this.payerName = payerName;
        this.payerTin = payerTin;
        this.amount = amount;
        this.currency = currency != null ? currency : "ETB";
        this.purpose = purpose;
        this.purposeDescription = purposeDescription;
        this.relatedInvoiceId = relatedInvoiceId;
        this.relatedCreditAccountId = relatedCreditAccountId;
        this.paymentMethod = paymentMethod != null ? paymentMethod : CashReceiptPaymentMethod.CASH;
        this.referenceNumber = referenceNumber;
        this.receivedAt = receivedAt != null ? receivedAt : Instant.now();
        this.rrn = rrn;
        this.irn = irn;
        this.qrCode = qrCode;
        this.status = CashReceiptStatus.REGISTERED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getPayerName() { return payerName; }
    public String getPayerTin() { return payerTin; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CashReceiptPurpose getPurpose() { return purpose; }
    public String getPurposeDescription() { return purposeDescription; }
    public UUID getRelatedInvoiceId() { return relatedInvoiceId; }
    public String getRelatedCreditAccountId() { return relatedCreditAccountId; }
    public CashReceiptPaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getReferenceNumber() { return referenceNumber; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getRrn() { return rrn; }
    public String getIrn() { return irn; }
    public String getQrCode() { return qrCode; }
    public CashReceiptStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void cancel() {
        this.status = CashReceiptStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
