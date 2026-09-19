package et.ut.einvoice.taxation.repository;

import et.ut.einvoice.taxation.domain.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID> {

    @Query("""
        SELECT r FROM TaxRule r
        WHERE r.taxCode = :taxCode
          AND r.isActive = true
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY r.version DESC
    """)
    List<TaxRule> findActiveRules(@Param("taxCode") String taxCode, @Param("date") Instant date);

    default Optional<TaxRule> findActiveRule(String taxCode, Instant date) {
        List<TaxRule> rules = findActiveRules(taxCode, date);
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0));
    }
}
