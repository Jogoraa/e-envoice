package et.ut.einvoice.purchasevoucher.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_vouchers")
public class PurchaseVoucher {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "voucher_number", nullable = false, length = 64)
    private String voucherNumber;

    @Column(name = "buyer_taxpayer_id", nullable = false)
    private UUID buyerTaxpayerId;

    @Column(name = "buyer_tin", nullable = false, length = 32)
    private String buyerTin;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "supplier_tin", length = 32)
    private String supplierTin;

    @Column(name = "supplier_id_number", length = 64)
    private String supplierIdNumber;

    @Column(name = "supplier_id_type", length = 32)
    private String supplierIdType;

    @Column(name = "supplier_phone", length = 32)
    private String supplierPhone;

    @Column(name = "supplier_address", columnDefinition = "TEXT")
    private String supplierAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "unavailable_reason", nullable = false, length = 64)
    private UnavailableReceiptReason unavailableReason;

    @Column(name = "reason_description")
    private String reasonDescription;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate = Instant.now();

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalTaxAmount = BigDecimal.ZERO;

    @Column(name = "withholding_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal withholdingAmount = BigDecimal.ZERO;

    @Column(name = "net_payable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal netPayableAmount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "ETB";

    @Column(name = "attachment_reference")
    private String attachmentReference;

    @Column(name = "rrn", nullable = false, length = 128, unique = true)
    private String rrn;

    @Column(name = "irn", length = 128)
    private String irn;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "REGISTERED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PurchaseVoucherLine> lines = new ArrayList<>();

    public PurchaseVoucher() {}

    public PurchaseVoucher(UUID id, UUID tenantId, String voucherNumber, UUID buyerTaxpayerId, String buyerTin,
                           String supplierName, String supplierTin, String supplierIdNumber, String supplierIdType,
                           String supplierPhone, String supplierAddress, UnavailableReceiptReason unavailableReason,
                           String reasonDescription, Instant transactionDate, BigDecimal totalAmount,
                           BigDecimal totalTaxAmount, BigDecimal withholdingAmount, BigDecimal netPayableAmount,
                           String currency, String attachmentReference, String rrn, String irn, String qrCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.voucherNumber = voucherNumber;
        this.buyerTaxpayerId = buyerTaxpayerId;
        this.buyerTin = buyerTin;
        this.supplierName = supplierName;
        this.supplierTin = supplierTin;
        this.supplierIdNumber = supplierIdNumber;
        this.supplierIdType = supplierIdType;
        this.supplierPhone = supplierPhone;
        this.supplierAddress = supplierAddress;
        this.unavailableReason = unavailableReason;
        this.reasonDescription = reasonDescription;
        this.transactionDate = transactionDate != null ? transactionDate : Instant.now();
        this.totalAmount = totalAmount;
        this.totalTaxAmount = totalTaxAmount != null ? totalTaxAmount : BigDecimal.ZERO;
        this.withholdingAmount = withholdingAmount != null ? withholdingAmount : BigDecimal.ZERO;
        this.netPayableAmount = netPayableAmount;
        this.currency = currency != null ? currency : "ETB";
        this.attachmentReference = attachmentReference;
        this.rrn = rrn;
        this.irn = irn;
        this.qrCode = qrCode;
        this.status = "REGISTERED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getVoucherNumber() { return voucherNumber; }
    public UUID getBuyerTaxpayerId() { return buyerTaxpayerId; }
    public String getBuyerTin() { return buyerTin; }
    public String getSupplierName() { return supplierName; }
    public String getSupplierTin() { return supplierTin; }
    public String getSupplierIdNumber() { return supplierIdNumber; }
    public String getSupplierIdType() { return supplierIdType; }
    public String getSupplierPhone() { return supplierPhone; }
    public String getSupplierAddress() { return supplierAddress; }
    public UnavailableReceiptReason getUnavailableReason() { return unavailableReason; }
    public String getReasonDescription() { return reasonDescription; }
    public Instant getTransactionDate() { return transactionDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
    public BigDecimal getWithholdingAmount() { return withholdingAmount; }
    public BigDecimal getNetPayableAmount() { return netPayableAmount; }
    public String getCurrency() { return currency; }
    public String getAttachmentReference() { return attachmentReference; }
    public String getRrn() { return rrn; }
    public String getIrn() { return irn; }
    public String getQrCode() { return qrCode; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PurchaseVoucherLine> getLines() { return lines; }

    public void addLine(PurchaseVoucherLine line) {
        line.setVoucher(this);
        this.lines.add(line);
    }
}
