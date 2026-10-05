package et.ut.einvoice.adjustments.repository;

import et.ut.einvoice.adjustments.domain.TaxAdjustmentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxAdjustmentLineRepository extends JpaRepository<TaxAdjustmentLine, UUID> {
    List<TaxAdjustmentLine> findByTenantIdAndAdjustmentIdOrderByLineNumberAsc(UUID tenantId, UUID adjustmentId);
}
