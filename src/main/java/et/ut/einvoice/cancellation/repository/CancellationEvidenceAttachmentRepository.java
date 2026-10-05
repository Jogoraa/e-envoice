package et.ut.einvoice.cancellation.repository;

import et.ut.einvoice.cancellation.domain.CancellationEvidenceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CancellationEvidenceAttachmentRepository extends JpaRepository<CancellationEvidenceAttachment, UUID> {
    List<CancellationEvidenceAttachment> findAllByTenantIdAndCancellationRequestId(UUID tenantId, UUID cancellationRequestId);
}
