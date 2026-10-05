package et.ut.einvoice.creditsales.repository;

import et.ut.einvoice.creditsales.domain.CreditSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditSettlementRepository extends JpaRepository<CreditSettlement, UUID> {
    Optional<CreditSettlement> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<CreditSettlement> findBySettlementNumberAndTenantId(String settlementNumber, UUID tenantId);
    List<CreditSettlement> findByTenantIdAndInvoiceIdOrderBySettledAtAsc(UUID tenantId, UUID invoiceId);
    List<CreditSettlement> findByTenantIdOrderBySettledAtDesc(UUID tenantId);
}
