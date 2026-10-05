package et.ut.einvoice.withholding.repository;

import et.ut.einvoice.withholding.domain.WithholdingReceipt;
import et.ut.einvoice.withholding.domain.WithholdingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WithholdingReceiptRepository extends JpaRepository<WithholdingReceipt, UUID> {
    Optional<WithholdingReceipt> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<WithholdingReceipt> findByReceiptNumberAndTenantId(String receiptNumber, UUID tenantId);
    Optional<WithholdingReceipt> findByRrnAndTenantId(String rrn, UUID tenantId);
    List<WithholdingReceipt> findByTenantIdAndWithholdingTypeOrderByIssueDateDesc(UUID tenantId, WithholdingType withholdingType);
    List<WithholdingReceipt> findByTenantIdAndRelatedInvoiceIdOrderByIssueDateDesc(UUID tenantId, UUID relatedInvoiceId);
    List<WithholdingReceipt> findByTenantIdOrderByIssueDateDesc(UUID tenantId);
}
