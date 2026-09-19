package et.ut.einvoice.tenancy.config.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_feature_flags", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_feature_key", columnNames = {"tenant_id", "feature_key"})
})
public class TenantFeatureFlag {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "feature_key", nullable = false, length = 128)
    private String featureKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private FeatureFlagCategory category = FeatureFlagCategory.OPTIONAL_PRODUCT_FEATURE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy = "SYSTEM";

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "SYSTEM";

    public TenantFeatureFlag() {}

    public TenantFeatureFlag(UUID id, UUID tenantId, String featureKey, boolean enabled,
                             FeatureFlagCategory category, String actor) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.featureKey = featureKey;
        this.enabled = enabled;
        this.category = category != null ? category : FeatureFlagCategory.OPTIONAL_PRODUCT_FEATURE;
        this.createdBy = actor != null ? actor : "SYSTEM";
        this.updatedBy = actor != null ? actor : "SYSTEM";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public void setEnabled(boolean enabled, String actor) {
        this.enabled = enabled;
        this.updatedBy = actor != null ? actor : "SYSTEM";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getFeatureKey() { return featureKey; }
    public boolean isEnabled() { return enabled; }
    public FeatureFlagCategory getCategory() { return category; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
}
