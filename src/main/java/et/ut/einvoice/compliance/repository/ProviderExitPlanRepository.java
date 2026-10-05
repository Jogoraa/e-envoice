package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ProviderExitPlan;
import et.ut.einvoice.compliance.domain.ProviderExitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderExitPlanRepository extends JpaRepository<ProviderExitPlan, UUID> {
    List<ProviderExitPlan> findByStatus(ProviderExitStatus status);
    Optional<ProviderExitPlan> findTopByOrderByCreatedAtDesc();
}
