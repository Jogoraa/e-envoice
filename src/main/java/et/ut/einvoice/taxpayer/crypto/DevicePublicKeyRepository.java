package et.ut.einvoice.taxpayer.crypto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DevicePublicKeyRepository extends JpaRepository<DevicePublicKey, UUID> {
    Optional<DevicePublicKey> findByTenantIdAndDeviceIdAndKeyVersion(UUID tenantId, UUID deviceId, int keyVersion);
    List<DevicePublicKey> findAllByTenantIdAndDeviceId(UUID tenantId, UUID deviceId);
    Optional<DevicePublicKey> findFirstByTenantIdAndDeviceIdAndStatusOrderByKeyVersionDesc(UUID tenantId, UUID deviceId, DeviceKeyStatus status);
}
