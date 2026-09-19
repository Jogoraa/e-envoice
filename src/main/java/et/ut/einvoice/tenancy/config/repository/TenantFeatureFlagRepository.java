package et.ut.einvoice.tenancy.config.repository;

import et.ut.einvoice.tenancy.config.domain.TenantFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantFeatureFlagRepository extends JpaRepository<TenantFeatureFlag, UUID> {
    Optional<TenantFeatureFlag> findByTenantIdAndFeatureKey(UUID tenantId, String featureKey);
    List<TenantFeatureFlag> findByTenantId(UUID tenantId);
}
