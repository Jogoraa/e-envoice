package et.ut.einvoice.invoicing.domain;

import et.ut.einvoice.platform.exception.BusinessException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "document_number", nullable = false, length = 64)
    private String documentNumber;

    @Column(name = "invoice_counter", nullable = false)
    private Long invoiceCounter;

    @Column(name = "invoice_date", nullable = false)
    private Instant invoiceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16)
    private TransactionType transactionType = TransactionType.B2C;

    @Column(name = "payment_mode", nullable = false, length = 32)
    private String paymentMode = "CASH";

    @Column(name = "payment_term", nullable = false, length = 32)
    private String paymentTerm = "IMMEDIATE";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "pre_tax_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal preTaxTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "excise_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal exciseTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "ETB";

    // Buyer Information Snapshot
    @Column(name = "buyer_legal_name")
    private String buyerLegalName;

    @Column(name = "buyer_tin", length = 16)
    private String buyerTin;

    @Column(name = "buyer_id_number", length = 64)
    private String buyerIdNumber;

    @Column(name = "buyer_id_type", length = 16)
    private String buyerIdType;

    @Column(name = "buyer_phone", length = 32)
    private String buyerPhone;

    @Column(name = "buyer_email", length = 128)
    private String buyerEmail;

    @Column(name = "buyer_region", length = 32)
    private String buyerRegion;

    @Column(name = "buyer_woreda", length = 32)
    private String buyerWoreda;

    // MoR EIRS Government Acknowledgments
    @Column(name = "irn", length = 128, unique = true)
    private String irn;

    @Column(name = "previous_irn", length = 128)
    private String previousIrn;

    @Column(name = "rrn", length = 128)
    private String rrn;

    @Column(name = "ack_date", length = 64)
    private String ackDate;

    @Column(name = "signed_qr", columnDefinition = "TEXT")
    private String signedQr;

    @Column(name = "signed_invoice", columnDefinition = "TEXT")
    private String signedInvoice;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "reprint_count", nullable = false)
    private int reprintCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InvoiceLine> lines = new ArrayList<>();

    public Invoice() {}

    public Invoice(UUID id, UUID tenantId, String documentNumber, Long invoiceCounter, Instant invoiceDate,
                   TransactionType transactionType, String paymentMode, String paymentTerm) {
        this.id = id;
        this.tenantId = tenantId;
        this.documentNumber = documentNumber;
        this.invoiceCounter = invoiceCounter;
        this.invoiceDate = invoiceDate != null ? invoiceDate : Instant.now();
        this.transactionType = transactionType != null ? transactionType : TransactionType.B2C;
        this.paymentMode = paymentMode != null ? paymentMode : "CASH";
        this.paymentTerm = paymentTerm != null ? paymentTerm : "IMMEDIATE";
        this.status = InvoiceStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @Transient
    private BigDecimal initialGrandTotal;

    @PostLoad
    public void onPostLoad() {
        this.initialGrandTotal = this.grandTotal;
    }

    @PreUpdate
    public void onPreUpdate() {
        if ((this.status == InvoiceStatus.REGISTERED || this.status == InvoiceStatus.CANCELLED)
                && initialGrandTotal != null && initialGrandTotal.compareTo(this.grandTotal != null ? this.grandTotal : BigDecimal.ZERO) != 0) {
            throw new BusinessException(
                    "FINANCIAL_MUTATION_FORBIDDEN",
                    "Registered or cancelled tax invoices are financially immutable. Direct updates to invoice amounts are forbidden.",
                    "የተመዘገበ ወይም የተሰረዘ ህጋዊ ደረሰኝ የገንዘብ መጠን ማሻሻል የተከለከለ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void checkNotRegistered() {
        if (this.status == InvoiceStatus.REGISTERED) {
            throw new BusinessException(
                    "FINANCIAL_MUTATION_FORBIDDEN",
                    "Registered tax invoices are financially immutable. Adjustments must be made via Credit/Debit Notes or Cancellation pursuant to Directive No. 1142/2026 Art. 25 & 26.",
                    "የተመዘገበ ህጋዊ ደረሰኝ በቀጥታ ማሻሻል የተከለከለ ነው። ማስተካከያ በክሬዲት/ዴቢት ኖት ወይም በስረዛ ብቻ መከናወን አለበት።",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public void addLine(InvoiceLine line) {
        checkNotRegistered();
        lines.add(line);
        line.setInvoice(this);
    }

    public void recalculateTotals() {
        checkNotRegistered();
        BigDecimal preTax = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal excise = BigDecimal.ZERO;

        for (InvoiceLine l : lines) {
            preTax = preTax.add(l.getPreTaxValue());
            tax = tax.add(l.getTaxAmount());
            excise = excise.add(l.getExciseTaxValue() != null ? l.getExciseTaxValue() : BigDecimal.ZERO);
        }

        this.preTaxTotal = preTax;
        this.taxTotal = tax;
        this.exciseTotal = excise;
        this.grandTotal = preTax.add(tax).add(excise);
    }

    public void markRegistered(String irn, String rrn, String ackDate, String signedQr, String signedInvoice) {
        this.irn = irn;
        this.rrn = rrn;
        this.ackDate = ackDate;
        this.signedQr = signedQr;
        this.signedInvoice = signedInvoice;
        this.status = InvoiceStatus.REGISTERED;
        this.updatedAt = Instant.now();
    }

    public void markOfflineBuffered() {
        this.status = InvoiceStatus.OFFLINE_BUFFERED;
        this.updatedAt = Instant.now();
    }

    public void setOfflineQr(String qrBase64) {
        this.signedQr = qrBase64;
        this.updatedAt = Instant.now();
    }

    public void markCancelled() {
        if (this.status != InvoiceStatus.REGISTERED) {
            throw new BusinessException("INVOICE_NOT_REGISTERED", "Only registered invoices can be cancelled.");
        }
        this.status = InvoiceStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void recordReprint() {
        this.reprintCount++;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public Long getInvoiceCounter() { return invoiceCounter; }
    public void setInvoiceCounter(Long invoiceCounter) { this.invoiceCounter = invoiceCounter; }
    public Instant getInvoiceDate() { return invoiceDate; }
    public TransactionType getTransactionType() { return transactionType; }
    public String getPaymentMode() { return paymentMode; }
    public String getPaymentTerm() { return paymentTerm; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public BigDecimal getPreTaxTotal() { return preTaxTotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getExciseTotal() { return exciseTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) {
        checkNotRegistered();
        this.grandTotal = grandTotal;
    }
    public String getCurrency() { return currency; }
    public String getBuyerLegalName() { return buyerLegalName; }
    public void setBuyerLegalName(String buyerLegalName) { this.buyerLegalName = buyerLegalName; }
    public String getBuyerTin() { return buyerTin; }
    public void setBuyerTin(String buyerTin) { this.buyerTin = buyerTin; }
    public String getBuyerIdNumber() { return buyerIdNumber; }
    public void setBuyerIdNumber(String buyerIdNumber) { this.buyerIdNumber = buyerIdNumber; }
    public String getBuyerIdType() { return buyerIdType; }
    public void setBuyerIdType(String buyerIdType) { this.buyerIdType = buyerIdType; }
    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String buyerPhone) { this.buyerPhone = buyerPhone; }
    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }
    public String getBuyerRegion() { return buyerRegion; }
    public void setBuyerRegion(String buyerRegion) { this.buyerRegion = buyerRegion; }
    public String getBuyerWoreda() { return buyerWoreda; }
    public void setBuyerWoreda(String buyerWoreda) { this.buyerWoreda = buyerWoreda; }
    public String getIrn() { return irn; }
    public String getPreviousIrn() { return previousIrn; }
    public void setPreviousIrn(String previousIrn) { this.previousIrn = previousIrn; }
    public String getRrn() { return rrn; }
    public String getAckDate() { return ackDate; }
    public String getSignedQr() { return signedQr; }
    public String getSignedInvoice() { return signedInvoice; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public int getReprintCount() { return reprintCount; }
    public List<InvoiceLine> getLines() { return lines; }
}
