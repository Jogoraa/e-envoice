package et.ut.einvoice.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "category_type", nullable = false, length = 32)
    private String categoryType = "PRODUCT"; // 'PRODUCT', 'SERVICE', 'ALL'

    @Column(name = "description")
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE"; // 'ACTIVE', 'INACTIVE'

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Category() {}

    public Category(UUID id, UUID tenantId, String code, String name, String categoryType, String description, UUID parentId) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code != null ? code.trim().toUpperCase() : "";
        this.name = name != null ? name.trim() : "";
        this.categoryType = categoryType != null ? categoryType.trim().toUpperCase() : "PRODUCT";
        this.description = description;
        this.parentId = parentId;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategoryType() { return categoryType; }
    public void setCategoryType(String categoryType) { this.categoryType = categoryType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
