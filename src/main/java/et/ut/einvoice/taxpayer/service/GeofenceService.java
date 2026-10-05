package et.ut.einvoice.taxpayer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.taxpayer.domain.*;
import et.ut.einvoice.taxpayer.repository.DeviceRepository;
import et.ut.einvoice.taxpayer.repository.DeviceRevocationRepository;
import et.ut.einvoice.taxpayer.repository.DeviceTelemetryLogRepository;
import et.ut.einvoice.taxpayer.repository.GeofenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * mPOS Geolocation Compliance, Dynamic Geofence Polygon Engine & Telemetry Service
 * Implements FDRE MoR Directive No. 1142/2026 Art. 4(5).
 */
@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);

    public static final double MAX_PERMITTED_ACCURACY_METERS = 100.0;
    public static final Duration MAX_GPS_AGE = Duration.ofHours(24);

    private final DeviceRepository deviceRepository;
    private final GeofenceRepository geofenceRepository;
    private final DeviceTelemetryLogRepository telemetryLogRepository;
    private final DeviceRevocationRepository revocationRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public GeofenceService(
            DeviceRepository deviceRepository,
            GeofenceRepository geofenceRepository,
            DeviceTelemetryLogRepository telemetryLogRepository,
            DeviceRevocationRepository revocationRepository,
            OutboxService outboxService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.deviceRepository = deviceRepository;
        this.geofenceRepository = geofenceRepository;
        this.telemetryLogRepository = telemetryLogRepository;
        this.revocationRepository = revocationRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compatible overload for legacy calls.
     */
    public void validateDeviceLocation(UUID tenantId, UUID deviceId, Double latitude, Double longitude) {
        validateMposTransaction(tenantId, deviceId, latitude, longitude, 10.0, Instant.now());
    }

    /**
     * Authoritative transaction-time GPS validation pursuant to Directive No. 1142/2026 Art. 4(5).
     * Gated by device type: Desktop fixed terminals do NOT demand GPS; regulated mPOS MUST provide verified GPS.
     */
    @Transactional
    public void validateMposTransaction(
            UUID tenantId,
            UUID deviceId,
            Double latitude,
            Double longitude,
            Double accuracy,
            Instant capturedAt
    ) {
        if (deviceId == null) {
            // Direct ERP / Desktop server transaction without registered device ID
            return;
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(
                        "DEVICE_NOT_FOUND",
                        "POS Device not registered in system: " + deviceId,
                        "የሽያጭ መመዝገቢያ መሳሪያው አልተመዘገበም።",
                        HttpStatus.BAD_REQUEST
                ));

        // 1. Strict Tenant Isolation
        if (!device.getTenantId().equals(tenantId)) {
            log.error("DEVICE SPOOFING: Device {} belongs to tenant {}, transaction requested by tenant {}",
                    deviceId, device.getTenantId(), tenantId);
            throw new BusinessException(
                    "DEVICE_TENANT_MISMATCH",
                    "Device does not belong to the authenticated taxpayer organization.",
                    "መሳሪያው የዚህ ድርጅት አይደለም።",
                    HttpStatus.FORBIDDEN
            );
        }

        // 2. Device Trust & Revocation / Suspension Verification
        if (revocationRepository.existsByTenantIdAndDeviceId(tenantId, deviceId) ||
                device.getRegistrationStatus() == DeviceRegistrationStatus.REVOKED) {
            throw new BusinessException(
                    "DEVICE_REVOKED",
                    "This device has been revoked for security or compliance violations.",
                    "መሳሪያው በህግ ወይም በደህንነት ምክንያት ታግዷል።",
                    HttpStatus.FORBIDDEN
            );
        }

        if (device.getRegistrationStatus() == DeviceRegistrationStatus.SUSPENDED || !device.isActive()) {
            throw new BusinessException(
                    "DEVICE_SUSPENDED",
                    "This device is currently suspended from issuing fiscal invoices.",
                    "መሳሪያው ለጊዜው ታግዷል።",
                    HttpStatus.FORBIDDEN
            );
        }

        // 3. Gate by Device Type: Desktop vs Regulated mPOS
        if (!device.isMpos()) {
            log.debug("Device {} is {} (fixed/desktop) - mandatory GPS waived under Art. 4(5)", deviceId, device.getDeviceType());
            return;
        }

        // 4. Regulated mPOS: GPS coordinates are strictly mandatory
        if (latitude == null || longitude == null) {
            throw new BusinessException(
                    "MANDATORY_MPOS_GPS_REQUIRED",
                    "Mobile POS transactions must provide verified GPS coordinates pursuant to Directive No. 1142/2026 Art. 4(5).",
                    "በተንቀሳቃሽ መመዝገቢያ (mPOS) የሚደረግ ሽያጭ የቦታ መገኛ (GPS) ማካተት ግዴታ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 5. GPS Validity: Range check
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw new BusinessException(
                    "INVALID_GPS_COORDINATES",
                    String.format("Invalid GPS coordinates: lat=%.6f, lon=%.6f", latitude, longitude),
                    "የቀረበው የቦታ መገኛ ትክክል አይደለም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 6. Accuracy tolerance check
        if (accuracy != null && accuracy > MAX_PERMITTED_ACCURACY_METERS) {
            throw new BusinessException(
                    "INACCURATE_GPS_COORDINATES",
                    String.format("GPS accuracy of %.1f meters exceeds the maximum authorized regulatory limit of %.1f meters.",
                            accuracy, MAX_PERMITTED_ACCURACY_METERS),
                    "የቀረበው የጂፒኤስ ትክክለኛነት መጠን ከተፈቀደው ገደብ በላይ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 7. Dynamic Geofence Polygon Containment Check
        boolean inside = evaluateGeofenceContainment(device, latitude, longitude);
        if (!inside) {
            log.error("GEOFENCE VIOLATION: Device {} at (lat={}, lon={}) is outside authorized operating zone",
                    deviceId, latitude, longitude);
            throw new BusinessException(
                    "OUT_OF_GEOFENCE_VIOLATION",
                    String.format("Device coordinates (%.6f, %.6f) are outside the authorized geofenced work area pursuant to Directive No. 1142/2026 Art. 4(5).",
                            latitude, longitude),
                    "መሳሪያው ከተፈቀደለት የስራ ክልል ውጪ ስለሆነ ሽያጩ ታግዷል።",
                    HttpStatus.FORBIDDEN
            );
        }

        // 8. Update Device state and record transaction telemetry
        device.setLastLatitude(latitude);
        device.setLastLongitude(longitude);
        device.setLastAccuracy(accuracy != null ? accuracy : 10.0);
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);

        DeviceTelemetryLog telemetry = new DeviceTelemetryLog(
                UUID.randomUUID(),
                tenantId,
                deviceId,
                latitude,
                longitude,
                accuracy != null ? accuracy : 10.0,
                capturedAt != null ? capturedAt : Instant.now(),
                null,
                true,
                "TRANSACTION"
        );
        telemetryLogRepository.save(telemetry);
    }

    /**
     * Authenticated background telemetry heartbeat from mPOS device.
     */
    @Transactional
    public DeviceTelemetryLog recordTelemetryHeartbeat(
            UUID tenantId,
            UUID deviceId,
            Double latitude,
            Double longitude,
            Double accuracy,
            Instant capturedAt,
            Integer batteryLevel
    ) {
        Device device = deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new BusinessException("DEVICE_NOT_FOUND", "Device not found for tenant"));

        boolean inside = evaluateGeofenceContainment(device, latitude, longitude);

        device.setLastLatitude(latitude);
        device.setLastLongitude(longitude);
        device.setLastAccuracy(accuracy);
        device.setLastHeartbeat(Instant.now());
        device.setLastSeenAt(Instant.now());
        device.setLastTelemetryStatus(inside ? "OK" : "GEOFENCE_WARNING");
        deviceRepository.save(device);

        DeviceTelemetryLog logEntry = new DeviceTelemetryLog(
                UUID.randomUUID(),
                tenantId,
                deviceId,
                latitude,
                longitude,
                accuracy,
                capturedAt != null ? capturedAt : Instant.now(),
                batteryLevel,
                inside,
                "HEARTBEAT"
        );
        DeviceTelemetryLog saved = telemetryLogRepository.save(logEntry);

        // Enqueue durable MoR telemetry outbox event
        String payload = String.format(
                "{\"deviceId\":\"%s\",\"serial\":\"%s\",\"lat\":%.6f,\"lon\":%.6f,\"accuracy\":%.2f,\"inside\":%b,\"capturedAt\":\"%s\"}",
                deviceId, device.getDeviceSerial(), latitude, longitude, accuracy, inside, logEntry.getCapturedAt()
        );
        outboxService.enqueueEvent(tenantId, "DEVICE", deviceId.toString(), "DEVICE_TELEMETRY", payload);

        auditService.recordEvent(
                tenantId, "DEVICE", "SYSTEM", "RECORD_TELEMETRY", "DEVICE", deviceId.toString(),
                "LAT=" + latitude + ";LON=" + longitude + ";INSIDE=" + inside, "127.0.0.1"
        );

        return saved;
    }

    /**
     * Evaluates dynamic polygon containment using Ray-Casting algorithm.
     */
    public boolean evaluateGeofenceContainment(Device device, double latitude, double longitude) {
        // 1. Check device-specific authorized geofence
        if (device.getAuthorizedGeofenceId() != null) {
            Optional<Geofence> gfOpt = geofenceRepository.findById(device.getAuthorizedGeofenceId());
            if (gfOpt.isPresent() && gfOpt.get().isActive()) {
                return isPointInsideGeojsonPolygon(latitude, longitude, gfOpt.get().getPolygonGeojson());
            }
        }

        // 2. Check tenant active geofences
        List<Geofence> tenantFences = geofenceRepository.findAllByTenantIdAndIsActiveTrue(device.getTenantId());
        if (tenantFences.isEmpty()) {
            // If no geofence polygon is configured for this tenant, device is permitted across Ethiopia
            return true;
        }

        // Device is inside if contained in at least one authorized active tenant geofence polygon
        for (Geofence gf : tenantFences) {
            if (isPointInsideGeojsonPolygon(latitude, longitude, gf.getPolygonGeojson())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Deterministic Ray-Casting Point-in-Polygon (PIP) evaluation over GeoJSON coordinates.
     * Supports GeoJSON Polygon coordinates: [[[lon, lat], [lon, lat], ...]]
     */
    public boolean isPointInsideGeojsonPolygon(double latitude, double longitude, String geojson) {
        if (geojson == null || geojson.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(geojson);
            JsonNode coordsNode = root;
            if (root.has("coordinates")) {
                coordsNode = root.get("coordinates");
            } else if (root.has("geometry") && root.get("geometry").has("coordinates")) {
                coordsNode = root.get("geometry").get("coordinates");
            }

            // In GeoJSON, Polygon coordinates is an array of LinearRings: [[[lon, lat], ...]]
            if (coordsNode.isArray() && coordsNode.size() > 0) {
                JsonNode ring = coordsNode.get(0);
                if (ring.isArray() && ring.size() > 0 && ring.get(0).isArray()) {
                    List<double[]> polygonPoints = new ArrayList<>();
                    for (JsonNode pt : ring) {
                        double lon = pt.get(0).asDouble();
                        double lat = pt.get(1).asDouble();
                        polygonPoints.add(new double[]{lat, lon});
                    }
                    return isPointInPolygon(latitude, longitude, polygonPoints);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse polygon GeoJSON, falling back to false: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Pure Ray-Casting algorithm for point in 2D polygon with edge boundary inclusion.
     */
    public static boolean isPointInPolygon(double lat, double lon, List<double[]> points) {
        if (points == null || points.size() < 3) return false;

        boolean inside = false;
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = points.get(i)[0]; // lat
            double xi = points.get(i)[1]; // lon
            double yj = points.get(j)[0];
            double xj = points.get(j)[1];

            // Point on vertex
            if (Math.abs(yi - lat) < 1e-9 && Math.abs(xi - lon) < 1e-9) {
                return true;
            }

            // Point on horizontal segment
            if (Math.abs(yi - yj) < 1e-9 && Math.abs(yi - lat) < 1e-9 &&
                    lon >= Math.min(xi, xj) - 1e-9 && lon <= Math.max(xi, xj) + 1e-9) {
                return true;
            }

            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon <= (xj - xi) * (lat - yi) / (yj - yi) + xi + 1e-9);

            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
