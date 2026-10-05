package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ExemptSectorSummaryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExemptSectorSummaryReportRepository extends JpaRepository<ExemptSectorSummaryReport, UUID> {
    List<ExemptSectorSummaryReport> findByTenantId(UUID tenantId);
    Optional<ExemptSectorSummaryReport> findByTenantIdAndReportPeriodLabelAndReportingFrequency(
            UUID tenantId, String reportPeriodLabel, String reportingFrequency);
    List<ExemptSectorSummaryReport> findByStatus(String status);
}
