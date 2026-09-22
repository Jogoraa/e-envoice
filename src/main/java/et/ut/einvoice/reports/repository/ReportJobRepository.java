package et.ut.einvoice.reports.repository;

import et.ut.einvoice.reports.domain.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    List<ReportJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
