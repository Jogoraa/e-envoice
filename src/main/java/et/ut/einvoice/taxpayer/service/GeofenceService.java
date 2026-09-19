package et.ut.einvoice.taxpayer.service;

import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Geofencing & GPS Validation Service (Directive No. 1142/2026 Art. 4(5)).
 */
@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);

    // Addis Ababa bounding box coordinate limits for validation
    private static final double MIN_LAT = 8.80;
    private static final double MAX_LAT = 9.15;
    private static final double MIN_LON = 38.65;
    private static final double MAX_LON = 38.95;

    public void validateDeviceLocation(UUID tenantId, UUID deviceId, Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            // Non-mobile desktop clients do not enforce GPS checks
            return;
        }

        log.info("Validating geofence for tenant {} device {}: lat={}, lon={}", tenantId, deviceId, latitude, longitude);

        // Point-in-polygon / bounding-box containment check
        boolean inside = (latitude >= MIN_LAT && latitude <= MAX_LAT) && (longitude >= MIN_LON && longitude <= MAX_LON);

        if (!inside) {
            log.error("GEOFENCE VIOLATION: Device {} at ({}, {}) is outside authorized business zone.", deviceId, latitude, longitude);
            throw new BusinessException(
                    "OUT_OF_GEOFENCE_VIOLATION",
                    String.format("Device coordinates (%.4f, %.4f) are outside the authorized geofenced work area pursuant to Directive No. 1142/2026 Art. 4(5).",
                            latitude, longitude),
                    "መሳሪያው ከተፈቀደለት የስራ ክልል ውጪ ስለሆነ ሽያጩ ታግዷል።",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
