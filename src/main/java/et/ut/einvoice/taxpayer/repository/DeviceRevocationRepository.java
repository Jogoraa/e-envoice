package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.DeviceRevocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRevocationRepository extends JpaRepository<DeviceRevocation, UUID> {
    Optional<DeviceRevocation> findByTenantIdAndDeviceId(UUID tenantId, UUID deviceId);
    boolean existsByTenantIdAndDeviceId(UUID tenantId, UUID deviceId);
}
