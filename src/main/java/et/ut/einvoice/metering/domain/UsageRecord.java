package et.ut.einvoice.metering.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
public class UsageRecord {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    public UsageRecord() {}

    public UsageRecord(UUID id, UUID tenantId, String metricType, BigDecimal quantity, String resourceId) {
        this.id = id;
        this.tenantId = tenantId;
        this.metricType = metricType;
        this.quantity = quantity != null ? quantity : BigDecimal.ONE;
        this.resourceId = resourceId;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getMetricType() { return metricType; }
    public BigDecimal getQuantity() { return quantity; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getResourceId() { return resourceId; }
}
