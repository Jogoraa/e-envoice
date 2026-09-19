package et.ut.einvoice.receipts.repository;

import et.ut.einvoice.receipts.domain.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {
    Optional<Receipt> findByRrnAndTenantId(String rrn, UUID tenantId);
    Page<Receipt> findAllByTenantId(UUID tenantId, Pageable pageable);
}
