package et.ut.einvoice.adjustments.repository;

import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaxAdjustmentRepository extends JpaRepository<TaxAdjustment, UUID> {
    Page<TaxAdjustment> findAllByTenantId(UUID tenantId, Pageable pageable);
}
