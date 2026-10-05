package et.ut.einvoice.adjustments.repository;

import et.ut.einvoice.adjustments.domain.AdjustmentMoRStatus;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxAdjustmentRepository extends JpaRepository<TaxAdjustment, UUID> {
    Page<TaxAdjustment> findAllByTenantId(UUID tenantId, Pageable pageable);
    List<TaxAdjustment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<TaxAdjustment> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<TaxAdjustment> findByIrnAndTenantId(String irn, UUID tenantId);
    Optional<TaxAdjustment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<TaxAdjustment> findByTenantIdAndOriginalInvoiceId(UUID tenantId, UUID originalInvoiceId);
    List<TaxAdjustment> findByTenantIdAndMorStatus(UUID tenantId, AdjustmentMoRStatus morStatus);
}
