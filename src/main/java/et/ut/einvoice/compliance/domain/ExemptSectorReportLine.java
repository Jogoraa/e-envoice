package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line item within an Art. 20 ExemptSectorSummaryReport.
 *
 * Directive No. 1142/2026 Art. 20(3)(c):
 *   Summarized info includes: service/goods type, quantity, unit price,
 *   total price, tax type, tax rate, tax amount, and grand total.
 */
@Entity
@Table(name = "exempt_sector_report_lines")
public class ExemptSectorReportLine {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "nature_of_supplies", length = 16, nullable = false)
    private String natureOfSupplies = "services";

    @Column(name = "service_or_goods_description", length = 255, nullable = false)
    private String serviceOrGoodsDescription;

    @Column(name = "quantity", precision = 22, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "unit", length = 16, nullable = false)
    private String unit = "TXN";

    @Column(name = "unit_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "total_price", precision = 22, scale = 2, nullable = false)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "tax_code", length = 16, nullable = false)
    private String taxCode = "VAT15";

    @Column(name = "tax_rate", precision = 6, scale = 4, nullable = false)
    private BigDecimal taxRate = new BigDecimal("0.1500");

    @Column(name = "tax_amount", precision = 22, scale = 2, nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", precision = 22, scale = 2, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "invoice_count", nullable = false)
    private Long invoiceCount = 0L;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    public ExemptSectorReportLine() {}

    public ExemptSectorReportLine(UUID id, UUID reportId, UUID tenantId, Integer lineNumber,
                                  String natureOfSupplies, String serviceOrGoodsDescription,
                                  BigDecimal quantity, String unit, BigDecimal unitPrice,
                                  BigDecimal totalPrice, String taxCode, BigDecimal taxRate,
                                  BigDecimal taxAmount, BigDecimal grandTotal, Long invoiceCount) {
        this.id = id;
        this.reportId = reportId;
        this.tenantId = tenantId;
        this.lineNumber = lineNumber;
        this.natureOfSupplies = natureOfSupplies;
        this.serviceOrGoodsDescription = serviceOrGoodsDescription;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.taxCode = taxCode;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.grandTotal = grandTotal;
        this.invoiceCount = invoiceCount;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getNatureOfSupplies() { return natureOfSupplies; }
    public void setNatureOfSupplies(String natureOfSupplies) { this.natureOfSupplies = natureOfSupplies; }

    public String getServiceOrGoodsDescription() { return serviceOrGoodsDescription; }
    public void setServiceOrGoodsDescription(String serviceOrGoodsDescription) { this.serviceOrGoodsDescription = serviceOrGoodsDescription; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public Long getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Long invoiceCount) { this.invoiceCount = invoiceCount; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
}
