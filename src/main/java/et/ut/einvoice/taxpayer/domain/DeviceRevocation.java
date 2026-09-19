package et.ut.einvoice.taxpayer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_revocations")
public class DeviceRevocation {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "revocation_reason", nullable = false)
    private String revocationReason;

    @Column(name = "revoked_by", nullable = false, length = 64)
    private String revokedBy;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt = Instant.now();

    public DeviceRevocation() {}

    public DeviceRevocation(UUID id, UUID tenantId, UUID deviceId, String revocationReason, String revokedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.revocationReason = revocationReason;
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDeviceId() { return deviceId; }
    public String getRevocationReason() { return revocationReason; }
    public String getRevokedBy() { return revokedBy; }
    public Instant getRevokedAt() { return revokedAt; }
}
