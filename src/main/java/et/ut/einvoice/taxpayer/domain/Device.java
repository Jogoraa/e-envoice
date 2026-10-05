package et.ut.einvoice.taxpayer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Registered POS Device Entity (Directive No. 1142/2026 Art. 4(5)).
 */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "device_serial", nullable = false, length = 64)
    private String deviceSerial;

    @Column(name = "device_type", nullable = false, length = 32)
    private String deviceType = "MPOS";

    @Column(name = "system_number", nullable = false, length = 32)
    private String systemNumber;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 32)
    private DeviceRegistrationStatus registrationStatus = DeviceRegistrationStatus.ACTIVE;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "authorized_geofence_id")
    private UUID authorizedGeofenceId;

    @Column(name = "last_latitude", columnDefinition = "DOUBLE PRECISION")
    private Double lastLatitude;

    @Column(name = "last_longitude", columnDefinition = "DOUBLE PRECISION")
    private Double lastLongitude;

    @Column(name = "last_accuracy", columnDefinition = "DOUBLE PRECISION")
    private Double lastAccuracy;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "last_telemetry_status", length = 32)
    private String lastTelemetryStatus = "OK";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Device() {}

    public Device(UUID id, UUID tenantId, String deviceSerial, String deviceType, String systemNumber) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceSerial = deviceSerial;
        this.deviceType = deviceType != null ? deviceType : "MPOS";
        this.systemNumber = systemNumber;
        this.isActive = true;
        this.registrationStatus = DeviceRegistrationStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getDeviceSerial() { return deviceSerial; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getSystemNumber() { return systemNumber; }
    public void setSystemNumber(String systemNumber) { this.systemNumber = systemNumber; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public DeviceRegistrationStatus getRegistrationStatus() { return registrationStatus; }
    public void setRegistrationStatus(DeviceRegistrationStatus registrationStatus) { this.registrationStatus = registrationStatus; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public UUID getAuthorizedGeofenceId() { return authorizedGeofenceId; }
    public void setAuthorizedGeofenceId(UUID authorizedGeofenceId) { this.authorizedGeofenceId = authorizedGeofenceId; }
    public Double getLastLatitude() { return lastLatitude; }
    public void setLastLatitude(Double lastLatitude) { this.lastLatitude = lastLatitude; }
    public Double getLastLongitude() { return lastLongitude; }
    public void setLastLongitude(Double lastLongitude) { this.lastLongitude = lastLongitude; }
    public Double getLastAccuracy() { return lastAccuracy; }
    public void setLastAccuracy(Double lastAccuracy) { this.lastAccuracy = lastAccuracy; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public String getLastTelemetryStatus() { return lastTelemetryStatus; }
    public void setLastTelemetryStatus(String lastTelemetryStatus) { this.lastTelemetryStatus = lastTelemetryStatus; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isMpos() {
        return "MPOS".equalsIgnoreCase(deviceType);
    }
}
