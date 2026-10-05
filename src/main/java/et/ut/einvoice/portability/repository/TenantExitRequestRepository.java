package et.ut.einvoice.portability.repository;

import et.ut.einvoice.portability.domain.TenantExitRequest;
import et.ut.einvoice.portability.domain.TenantExitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantExitRequestRepository extends JpaRepository<TenantExitRequest, UUID> {
    Optional<TenantExitRequest> findByTenantId(UUID tenantId);
    List<TenantExitRequest> findByExitStatus(TenantExitStatus status);
}
