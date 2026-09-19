package et.ut.einvoice.invoicing.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnore
    private Invoice invoice;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "product_description", nullable = false)
    private String productDescription;

    @Column(name = "nature_of_supplies", nullable = false, length = 16)
    private String natureOfSupplies = "goods";

    @Column(name = "unit", nullable = false, length = 16)
    private String unit = "PCS";

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "pre_tax_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal preTaxValue;

    @Column(name = "tax_code", nullable = false, length = 16)
    private String taxCode = "VAT15";

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.1500");

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "excise_tax_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal exciseTaxValue = BigDecimal.ZERO;

    @Column(name = "total_line_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalLineAmount;

    public InvoiceLine() {}

    public InvoiceLine(UUID id, UUID tenantId, Integer lineNumber, String itemCode, String productDescription,
                       String natureOfSupplies, String unit, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal discount, BigDecimal preTaxValue, String taxCode, BigDecimal taxRate,
                       BigDecimal taxAmount, BigDecimal exciseTaxValue, BigDecimal totalLineAmount) {
        this.id = id;
        this.tenantId = tenantId;
        this.lineNumber = lineNumber;
        this.itemCode = itemCode;
        this.productDescription = productDescription;
        this.natureOfSupplies = natureOfSupplies != null ? natureOfSupplies : "goods";
        this.unit = unit != null ? unit : "PCS";
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discount = discount != null ? discount : BigDecimal.ZERO;
        this.preTaxValue = preTaxValue;
        this.taxCode = taxCode != null ? taxCode : "VAT15";
        this.taxRate = taxRate != null ? taxRate : new BigDecimal("0.1500");
        this.taxAmount = taxAmount;
        this.exciseTaxValue = exciseTaxValue != null ? exciseTaxValue : BigDecimal.ZERO;
        this.totalLineAmount = totalLineAmount;
    }

    public UUID getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public UUID getTenantId() { return tenantId; }
    public Integer getLineNumber() { return lineNumber; }
    public String getItemCode() { return itemCode; }
    public String getProductDescription() { return productDescription; }
    public String getNatureOfSupplies() { return natureOfSupplies; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getPreTaxValue() { return preTaxValue; }
    public String getTaxCode() { return taxCode; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getExciseTaxValue() { return exciseTaxValue; }
    public BigDecimal getTotalLineAmount() { return totalLineAmount; }
}
