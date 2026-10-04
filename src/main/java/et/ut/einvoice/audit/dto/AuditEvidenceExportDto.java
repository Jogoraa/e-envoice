package et.ut.einvoice.audit.dto;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.export.AuditExportPackage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEvidenceExportDto(
        UUID exportId,
        String streamId,
        long eventCount,
        Long firstSequence,
        Long lastSequence,
        String firstHash,
        String lastHash,
        AuditCheckpointResponseDto checkpoint,
        List<AuditEvidenceEventDto> events,
        String packageContentHash,
        int schemaVersion,
        String applicationVersion,
        Instant exportedAt
) {
    public record AuditEvidenceEventDto(
            Long sequenceNumber,
            String action,
            String resourceType,
            String payloadHash,
            String previousEventHash,
            String eventHash,
            int schemaVersion,
            Instant timestamp
    ) {
        public static AuditEvidenceEventDto fromEntity(AuditEvent event) {
            return new AuditEvidenceEventDto(
                    event.getSequenceNumber(),
                    event.getAction(),
                    event.getResourceType(),
                    event.getPayloadHash(),
                    event.getPreviousEventHash(),
                    event.getEventHash(),
                    event.getSchemaVersion(),
                    event.getTimestamp()
            );
        }
    }

    public static AuditEvidenceExportDto fromPackage(AuditExportPackage pkg) {
        return new AuditEvidenceExportDto(
                pkg.exportId(),
                pkg.streamId(),
                pkg.eventCount(),
                pkg.firstSequence(),
                pkg.lastSequence(),
                pkg.firstHash(),
                pkg.lastHash(),
                AuditCheckpointResponseDto.fromEntity(pkg.checkpoint()),
                pkg.events().stream().map(AuditEvidenceEventDto::fromEntity).toList(),
                pkg.packageContentHash(),
                pkg.schemaVersion(),
                pkg.applicationVersion(),
                pkg.exportedAt()
        );
    }
}
