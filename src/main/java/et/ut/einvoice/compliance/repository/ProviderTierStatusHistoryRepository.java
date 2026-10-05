package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ProviderTierStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderTierStatusHistoryRepository extends JpaRepository<ProviderTierStatusHistory, UUID> {

    @Query("SELECT h FROM ProviderTierStatusHistory h ORDER BY h.assessmentTime DESC LIMIT 1")
    Optional<ProviderTierStatusHistory> findLatest();

    List<ProviderTierStatusHistory> findAllByOrderByAssessmentTimeDesc();
}
