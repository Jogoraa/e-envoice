package et.ut.einvoice.tenancy.config.repository;

import et.ut.einvoice.tenancy.config.domain.TenantConfigurationOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantConfigurationOverrideRepository extends JpaRepository<TenantConfigurationOverride, UUID> {
    Optional<TenantConfigurationOverride> findByTenantIdAndConfigKey(UUID tenantId, String configKey);
    List<TenantConfigurationOverride> findByTenantId(UUID tenantId);
}
