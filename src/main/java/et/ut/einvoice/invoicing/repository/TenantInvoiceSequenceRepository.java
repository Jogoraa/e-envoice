package et.ut.einvoice.invoicing.repository;

import et.ut.einvoice.invoicing.domain.TenantInvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantInvoiceSequenceRepository extends JpaRepository<TenantInvoiceSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TenantInvoiceSequence s WHERE s.tenantId = :tenantId")
    Optional<TenantInvoiceSequence> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
