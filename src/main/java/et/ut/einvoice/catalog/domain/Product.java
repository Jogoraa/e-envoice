package et.ut.einvoice.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_code", length = 64)
    private String categoryCode;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit = "PCS";

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "tax_classification", nullable = false, length = 32)
    private String taxClassification = "VAT15";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "track_stock", nullable = false)
    private boolean trackStock = true;

    @Column(name = "stock_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "min_stock_level", nullable = false, precision = 14, scale = 4)
    private BigDecimal minStockLevel = new BigDecimal("5.0000");

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Product() {}

    public Product(UUID id, UUID tenantId, UUID branchId, String itemCode, String sku, String barcode,
                   String description, UUID categoryId, String categoryCode, String unit,
                   BigDecimal unitPrice, String taxClassification, boolean trackStock, BigDecimal stockQuantity) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.itemCode = itemCode != null ? itemCode.trim().toUpperCase() : "";
        this.sku = sku != null ? sku.trim().toUpperCase() : "";
        this.barcode = barcode != null && !barcode.isBlank() ? barcode.trim() : null;
        this.description = description != null ? description.trim() : "";
        this.categoryId = categoryId;
        this.categoryCode = categoryCode;
        this.unit = unit != null ? unit.trim().toUpperCase() : "PCS";
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        this.taxClassification = taxClassification != null ? taxClassification.trim().toUpperCase() : "VAT15";
        this.isActive = true;
        this.trackStock = trackStock;
        this.stockQuantity = stockQuantity != null ? stockQuantity : BigDecimal.ZERO;
        this.minStockLevel = new BigDecimal("5.0000");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getTaxClassification() { return taxClassification; }
    public void setTaxClassification(String taxClassification) { this.taxClassification = taxClassification; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public boolean isTrackStock() { return trackStock; }
    public void setTrackStock(boolean trackStock) { this.trackStock = trackStock; }
    public BigDecimal getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(BigDecimal stockQuantity) { this.stockQuantity = stockQuantity; }
    public BigDecimal getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
