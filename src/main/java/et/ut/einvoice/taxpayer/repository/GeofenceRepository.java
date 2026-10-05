package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {
    Optional<Geofence> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Geofence> findAllByTenantIdAndIsActiveTrue(UUID tenantId);
}
