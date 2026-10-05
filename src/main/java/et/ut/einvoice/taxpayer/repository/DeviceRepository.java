package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Device> findByTenantIdAndDeviceSerial(UUID tenantId, String deviceSerial);
    List<Device> findAllByTenantId(UUID tenantId);
}
