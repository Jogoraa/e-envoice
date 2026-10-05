package et.ut.einvoice.taxpayer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Geofence Polygon Boundary Entity (Directive No. 1142/2026 Art. 4(5)).
 */
@Entity
@Table(name = "geofences")
public class Geofence {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "fence_name", nullable = false, length = 64)
    private String fenceName;

    @Column(name = "polygon_geojson", nullable = false, columnDefinition = "TEXT")
    private String polygonGeojson;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Geofence() {}

    public Geofence(UUID id, UUID tenantId, String fenceName, String polygonGeojson) {
        this.id = id;
        this.tenantId = tenantId;
        this.fenceName = fenceName;
        this.polygonGeojson = polygonGeojson;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getFenceName() { return fenceName; }
    public void setFenceName(String fenceName) { this.fenceName = fenceName; }
    public String getPolygonGeojson() { return polygonGeojson; }
    public void setPolygonGeojson(String polygonGeojson) { this.polygonGeojson = polygonGeojson; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public Instant getCreatedAt() { return createdAt; }
}
