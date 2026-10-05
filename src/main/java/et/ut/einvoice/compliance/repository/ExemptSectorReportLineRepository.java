package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ExemptSectorReportLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExemptSectorReportLineRepository extends JpaRepository<ExemptSectorReportLine, UUID> {
    List<ExemptSectorReportLine> findByReportIdOrderByLineNumberAsc(UUID reportId);
}
