package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.AuthorityInvestigationExport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuthorityInvestigationExportRepository extends JpaRepository<AuthorityInvestigationExport, UUID> {

    List<AuthorityInvestigationExport> findByCaseReference(String caseReference);

    Page<AuthorityInvestigationExport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
