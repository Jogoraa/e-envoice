package et.ut.einvoice.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services")
public class ServiceItem {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "service_code", nullable = false, length = 64)
    private String serviceCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_code", length = 64)
    private String categoryCode;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit = "SERVICE";

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "tax_classification", nullable = false, length = 32)
    private String taxClassification = "VAT15";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ServiceItem() {}

    public ServiceItem(UUID id, UUID tenantId, UUID branchId, String serviceCode, String name,
                       String description, UUID categoryId, String categoryCode, String unit,
                       BigDecimal unitPrice, String taxClassification) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.serviceCode = serviceCode != null ? serviceCode.trim().toUpperCase() : "";
        this.name = name != null ? name.trim() : "";
        this.description = description;
        this.categoryId = categoryId;
        this.categoryCode = categoryCode;
        this.unit = unit != null ? unit.trim().toUpperCase() : "SERVICE";
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        this.taxClassification = taxClassification != null ? taxClassification.trim().toUpperCase() : "VAT15";
        this.isActive = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
