package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ProviderTenantTransition;
import et.ut.einvoice.compliance.domain.TenantTransitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderTenantTransitionRepository extends JpaRepository<ProviderTenantTransition, UUID> {
    List<ProviderTenantTransition> findByExitPlanId(UUID exitPlanId);
    Optional<ProviderTenantTransition> findByExitPlanIdAndTenantId(UUID exitPlanId, UUID tenantId);
    long countByExitPlanIdAndStatus(UUID exitPlanId, TenantTransitionStatus status);
    long countByExitPlanId(UUID exitPlanId);
}
