package et.ut.einvoice.cancellation.repository;

import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.domain.CancellationState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, UUID> {
    Optional<CancellationRequest> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<CancellationRequest> findByIrnAndTenantId(String irn, UUID tenantId);
    Page<CancellationRequest> findAllByTenantId(UUID tenantId, Pageable pageable);
    List<CancellationRequest> findAllByStateAndSlaDeadlineAtBefore(CancellationState state, Instant now);
}
