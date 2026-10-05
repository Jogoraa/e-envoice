package et.ut.einvoice.purchasevoucher.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_voucher_lines")
public class PurchaseVoucherLine {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private PurchaseVoucher voucher;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "item_description", nullable = false)
    private String itemDescription;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_of_measure", nullable = false, length = 16)
    private String unitOfMeasure;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    public PurchaseVoucherLine() {}

    public PurchaseVoucherLine(UUID id, UUID tenantId, PurchaseVoucher voucher, int lineNumber,
                               String itemDescription, BigDecimal quantity, String unitOfMeasure,
                               BigDecimal unitPrice, BigDecimal totalPrice, BigDecimal taxRate, BigDecimal taxAmount) {
        this.id = id;
        this.tenantId = tenantId;
        this.voucher = voucher;
        this.lineNumber = lineNumber;
        this.itemDescription = itemDescription;
        this.quantity = quantity;
        this.unitOfMeasure = unitOfMeasure;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.taxRate = taxRate != null ? taxRate : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public PurchaseVoucher getVoucher() { return voucher; }
    public int getLineNumber() { return lineNumber; }
    public String getItemDescription() { return itemDescription; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }

    public void setVoucher(PurchaseVoucher voucher) {
        this.voucher = voucher;
    }
}
