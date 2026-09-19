package et.ut.einvoice.portability.repository;

import et.ut.einvoice.portability.domain.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
    List<ExportJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
