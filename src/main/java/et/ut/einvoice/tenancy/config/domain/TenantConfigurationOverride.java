package et.ut.einvoice.tenancy.config.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_configuration_overrides", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_config_key", columnNames = {"tenant_id", "config_key"})
})
public class TenantConfigurationOverride {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_classification", nullable = false, length = 32)
    private ConfigSafetyClassification safetyClassification = ConfigSafetyClassification.TENANT_OVERRIDABLE;

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

    public TenantConfigurationOverride() {}

    public TenantConfigurationOverride(UUID id, UUID tenantId, String configKey, String configValue,
                                       ConfigSafetyClassification safetyClassification, String actor) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.configKey = configKey;
        this.configValue = configValue;
        this.safetyClassification = safetyClassification != null ? safetyClassification : ConfigSafetyClassification.TENANT_OVERRIDABLE;
        this.createdBy = actor != null ? actor : "SYSTEM";
        this.updatedBy = actor != null ? actor : "SYSTEM";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public void updateValue(String newValue, String actor) {
        this.configValue = newValue;
        this.updatedBy = actor != null ? actor : "SYSTEM";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public ConfigSafetyClassification getSafetyClassification() { return safetyClassification; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
}
