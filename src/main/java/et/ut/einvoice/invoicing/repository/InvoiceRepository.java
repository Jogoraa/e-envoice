package et.ut.einvoice.invoicing.repository;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Invoice> findByIrn(String irn);

    Optional<Invoice> findByIrnAndTenantId(String irn, UUID tenantId);

    Optional<Invoice> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Page<Invoice> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Invoice> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Invoice> findAllByTenantIdAndStatus(UUID tenantId, InvoiceStatus status, Pageable pageable);

    @Query("SELECT MAX(i.invoiceCounter) FROM Invoice i WHERE i.tenantId = :tenantId")
    Long findMaxInvoiceCounter(@Param("tenantId") UUID tenantId);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId ORDER BY i.invoiceCounter DESC LIMIT 1")
    Optional<Invoice> findLatestInvoice(@Param("tenantId") UUID tenantId);

    Page<Invoice> findAllByInvoiceDateBetween(Instant fromDate, Instant toDate, Pageable pageable);
}
