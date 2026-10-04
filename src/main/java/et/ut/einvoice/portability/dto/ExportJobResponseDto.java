package et.ut.einvoice.portability.dto;

import et.ut.einvoice.portability.domain.ExportJob;

import java.time.Instant;
import java.util.UUID;

/** Export job metadata. The job id is required solely for the scoped download URL. */
public record ExportJobResponseDto(
        UUID id,
        String status,
        Instant createdAt,
        Instant completedAt
) {
    public static ExportJobResponseDto fromEntity(ExportJob job) {
        return new ExportJobResponseDto(job.getId(), job.getStatus(), job.getCreatedAt(), job.getCompletedAt());
    }
}
