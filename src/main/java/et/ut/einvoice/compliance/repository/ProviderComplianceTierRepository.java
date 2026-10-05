package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ProviderComplianceTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderComplianceTierRepository extends JpaRepository<ProviderComplianceTier, UUID> {

    Optional<ProviderComplianceTier> findByTierLevel(int tierLevel);

    List<ProviderComplianceTier> findAllByOrderByTierLevelAsc();

    @Query("SELECT t FROM ProviderComplianceTier t WHERE " +
           ":taxpayerCount >= t.minActiveTaxpayers AND :taxpayerCount <= t.maxActiveTaxpayers " +
           "ORDER BY t.tierLevel ASC LIMIT 1")
    Optional<ProviderComplianceTier> findMatchingByTaxpayers(@Param("taxpayerCount") int taxpayerCount);

    @Query("SELECT t FROM ProviderComplianceTier t WHERE " +
           ":salesVolume >= t.minAnnualSalesVolume AND :salesVolume <= t.maxAnnualSalesVolume " +
           "ORDER BY t.tierLevel ASC LIMIT 1")
    Optional<ProviderComplianceTier> findMatchingBySalesVolume(@Param("salesVolume") BigDecimal salesVolume);
}
