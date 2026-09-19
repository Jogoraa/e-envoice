package et.ut.einvoice.taxpayer.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.DeviceRevocation;
import et.ut.einvoice.taxpayer.repository.DeviceRevocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeviceTrustService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTrustService.class);

    private final DeviceRevocationRepository revocationRepository;
    private final AuditService auditService;

    public DeviceTrustService(DeviceRevocationRepository revocationRepository, AuditService auditService) {
        this.revocationRepository = revocationRepository;
        this.auditService = auditService;
    }

    public void verifyDeviceTrust(UUID tenantId, UUID deviceId) {
        if (deviceId == null) {
            return;
        }

        if (revocationRepository.existsByTenantIdAndDeviceId(tenantId, deviceId)) {
            log.warn("SECURITY ALERT: Synchronization attempt from revoked device {} for tenant {}", deviceId, tenantId);
            throw new BusinessException(
                    "DEVICE_REVOKED",
                    "The device has been revoked and is barred from synchronizing transactions pursuant to Directive No. 1142/2026 Art. 16.",
                    "መሣሪያው አገልግሎት እንዳይሰጥ ስለታገደ ግብይቶችን ወደ ማዕከላዊ ስርዓቱ መላክ አይችልም።",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Transactional
    public DeviceRevocation revokeDevice(UUID tenantId, UUID deviceId, String reason, String revokedBy) {
        if (revocationRepository.existsByTenantIdAndDeviceId(tenantId, deviceId)) {
            return revocationRepository.findByTenantIdAndDeviceId(tenantId, deviceId).orElseThrow();
        }

        DeviceRevocation revocation = new DeviceRevocation(
                UUID.randomUUID(),
                tenantId,
                deviceId,
                reason,
                revokedBy != null ? revokedBy : "SYSTEM_SECURITY"
        );

        DeviceRevocation saved = revocationRepository.save(revocation);
        auditService.recordEvent(
                tenantId,
                "SECURITY",
                revokedBy != null ? revokedBy : "SYSTEM",
                "REVOKE_DEVICE",
                "DEVICE",
                deviceId.toString(),
                "REASON=" + reason,
                "127.0.0.1"
        );
        log.warn("Device {} for tenant {} was REVOKED. Reason: {}", deviceId, tenantId, reason);
        return saved;
    }
}
