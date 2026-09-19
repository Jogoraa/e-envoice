package et.ut.einvoice.government.repository;

import et.ut.einvoice.government.domain.GovernmentSubmission;
import et.ut.einvoice.government.domain.GovernmentSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GovernmentSubmissionRepository extends JpaRepository<GovernmentSubmission, UUID> {
    Optional<GovernmentSubmission> findBySubmissionId(String submissionId);
    List<GovernmentSubmission> findByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);
    List<GovernmentSubmission> findByStatus(GovernmentSubmissionStatus status);
}
