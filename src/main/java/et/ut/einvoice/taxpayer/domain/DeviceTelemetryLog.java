package et.ut.einvoice.taxpayer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit log of mPOS device telemetry heartbeats & transaction geolocation (Directive No. 1142/2026 Art. 4(5)).
 */
@Entity
@Table(name = "device_telemetry_logs")
public class DeviceTelemetryLog {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "accuracy", nullable = false)
    private Double accuracy;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "is_inside_geofence", nullable = false)
    private boolean isInsideGeofence;

    @Column(name = "telemetry_source", nullable = false, length = 32)
    private String telemetrySource = "HEARTBEAT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DeviceTelemetryLog() {}

    public DeviceTelemetryLog(
            UUID id,
            UUID tenantId,
            UUID deviceId,
            Double latitude,
            Double longitude,
            Double accuracy,
            Instant capturedAt,
            Integer batteryLevel,
            boolean isInsideGeofence,
            String telemetrySource
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.capturedAt = capturedAt;
        this.batteryLevel = batteryLevel;
        this.isInsideGeofence = isInsideGeofence;
        this.telemetrySource = telemetrySource != null ? telemetrySource : "HEARTBEAT";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDeviceId() { return deviceId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getAccuracy() { return accuracy; }
    public Instant getCapturedAt() { return capturedAt; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public boolean isInsideGeofence() { return isInsideGeofence; }
    public String getTelemetrySource() { return telemetrySource; }
    public Instant getCreatedAt() { return createdAt; }
}
