package et.ut.einvoice.taxpayer.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.*;
import et.ut.einvoice.taxpayer.repository.DeviceRepository;
import et.ut.einvoice.taxpayer.repository.GeofenceRepository;
import et.ut.einvoice.taxpayer.service.GeofenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * mPOS Device Management & Telemetry Controller (Directive No. 1142/2026 Art. 4(5)).
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceManagementController {

    private final DeviceRepository deviceRepository;
    private final GeofenceRepository geofenceRepository;
    private final GeofenceService geofenceService;

    public DeviceManagementController(
            DeviceRepository deviceRepository,
            GeofenceRepository geofenceRepository,
            GeofenceService geofenceService
    ) {
        this.deviceRepository = deviceRepository;
        this.geofenceRepository = geofenceRepository;
        this.geofenceService = geofenceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_TENANT_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<Device> registerDevice(@RequestBody @Valid RegisterDeviceRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Device device = new Device(
                UUID.randomUUID(),
                tenantId,
                request.deviceSerial(),
                request.deviceType() != null ? request.deviceType() : "MPOS",
                request.systemNumber()
        );
        device.setPublicKey(request.publicKey());
        device.setAuthorizedGeofenceId(request.authorizedGeofenceId());
        return ResponseEntity.ok(deviceRepository.save(device));
    }

    @GetMapping
    public ResponseEntity<List<Device>> listDevices() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(deviceRepository.findAllByTenantId(tenantId));
    }

    @PostMapping("/{deviceId}/telemetry")
    public ResponseEntity<DeviceTelemetryLog> submitTelemetry(
            @PathVariable UUID deviceId,
            @RequestBody @Valid DeviceTelemetryRequest request
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        DeviceTelemetryLog log = geofenceService.recordTelemetryHeartbeat(
                tenantId,
                deviceId,
                request.latitude(),
                request.longitude(),
                request.accuracy(),
                request.capturedAt() != null ? request.capturedAt() : Instant.now(),
                request.batteryLevel()
        );
        return ResponseEntity.ok(log);
    }

    @PostMapping("/geofences")
    @PreAuthorize("hasAnyAuthority('ROLE_TENANT_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<Geofence> createGeofence(@RequestBody @Valid CreateGeofenceRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Geofence geofence = new Geofence(
                UUID.randomUUID(),
                tenantId,
                request.fenceName(),
                request.polygonGeojson()
        );
        return ResponseEntity.ok(geofenceRepository.save(geofence));
    }

    @GetMapping("/geofences")
    public ResponseEntity<List<Geofence>> listGeofences() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(geofenceRepository.findAllByTenantIdAndIsActiveTrue(tenantId));
    }

    public record RegisterDeviceRequest(
            @NotNull String deviceSerial,
            String deviceType,
            @NotNull String systemNumber,
            String publicKey,
            UUID authorizedGeofenceId
    ) {}

    public record DeviceTelemetryRequest(
            @NotNull Double latitude,
            @NotNull Double longitude,
            @NotNull Double accuracy,
            Instant capturedAt,
            Integer batteryLevel
    ) {}

    public record CreateGeofenceRequest(
            @NotNull String fenceName,
            @NotNull String polygonGeojson
    ) {}
}
