package et.ut.einvoice.cashreceipt.repository;

import et.ut.einvoice.cashreceipt.domain.CashReceipt;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashReceiptRepository extends JpaRepository<CashReceipt, UUID> {
    Optional<CashReceipt> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<CashReceipt> findByReceiptNumberAndTenantId(String receiptNumber, UUID tenantId);
    Optional<CashReceipt> findByRrnAndTenantId(String rrn, UUID tenantId);
    List<CashReceipt> findByTenantIdAndPurposeOrderByReceivedAtDesc(UUID tenantId, CashReceiptPurpose purpose);
    List<CashReceipt> findByTenantIdAndRelatedInvoiceIdOrderByReceivedAtDesc(UUID tenantId, UUID relatedInvoiceId);
    List<CashReceipt> findByTenantIdOrderByReceivedAtDesc(UUID tenantId);
}
