package et.ut.einvoice.reports.dto;

import et.ut.einvoice.reports.domain.ReportJob;

import java.time.Instant;
import java.util.UUID;

/** Report job state without tenant identifiers, storage URLs, or report content. */
public record ReportJobResponseDto(
        UUID id,
        String reportDefinitionId,
        String reportTitle,
        String dateRange,
        String format,
        String status,
        int recordCount,
        Instant createdAt,
        Instant completedAt
) {
    public static ReportJobResponseDto fromEntity(ReportJob job) {
        return new ReportJobResponseDto(
                job.getId(), job.getReportDefinitionId(), job.getReportTitle(), job.getDateRange(),
                job.getFormat(), job.getStatus(), job.getRecordCount(), job.getCreatedAt(), job.getCompletedAt()
        );
    }
}
