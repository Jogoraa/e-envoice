package et.ut.einvoice.adjustments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tax_adjustment_lines")
public class TaxAdjustmentLine {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private TaxAdjustment adjustment;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "item_code", length = 64)
    private String itemCode;

    @Column(name = "product_description", nullable = false)
    private String productDescription;

    @Column(name = "adjusted_quantity", precision = 18, scale = 4)
    private BigDecimal adjustedQuantity;

    @Column(name = "unit_price", precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "pre_tax_adjustment", nullable = false, precision = 18, scale = 2)
    private BigDecimal preTaxAdjustment;

    @Column(name = "tax_adjustment", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAdjustment;

    @Column(name = "total_adjustment", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAdjustment;

    public TaxAdjustmentLine() {}

    public TaxAdjustmentLine(UUID id, UUID tenantId, TaxAdjustment adjustment, int lineNumber,
                             String itemCode, String productDescription, BigDecimal adjustedQuantity,
                             BigDecimal unitPrice, BigDecimal preTaxAdjustment, BigDecimal taxAdjustment,
                             BigDecimal totalAdjustment) {
        this.id = id;
        this.tenantId = tenantId;
        this.adjustment = adjustment;
        this.lineNumber = lineNumber;
        this.itemCode = itemCode;
        this.productDescription = productDescription;
        this.adjustedQuantity = adjustedQuantity;
        this.unitPrice = unitPrice;
        this.preTaxAdjustment = preTaxAdjustment;
        this.taxAdjustment = taxAdjustment;
        this.totalAdjustment = totalAdjustment;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public TaxAdjustment getAdjustment() { return adjustment; }
    public int getLineNumber() { return lineNumber; }
    public String getItemCode() { return itemCode; }
    public String getProductDescription() { return productDescription; }
    public BigDecimal getAdjustedQuantity() { return adjustedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getPreTaxAdjustment() { return preTaxAdjustment; }
    public BigDecimal getTaxAdjustment() { return taxAdjustment; }
    public BigDecimal getTotalAdjustment() { return totalAdjustment; }

    public void setAdjustment(TaxAdjustment adjustment) {
        this.adjustment = adjustment;
    }
}
