package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.DeviceTelemetryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTelemetryLogRepository extends JpaRepository<DeviceTelemetryLog, UUID> {
    List<DeviceTelemetryLog> findAllByTenantIdAndDeviceIdOrderByCapturedAtDesc(UUID tenantId, UUID deviceId);
}
