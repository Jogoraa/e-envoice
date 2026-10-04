package et.ut.einvoice.audit.dto;

import et.ut.einvoice.audit.domain.AuditCheckpoint;

import java.time.Instant;
import java.util.UUID;

public record AuditCheckpointResponseDto(
        UUID checkpointId,
        String streamId,
        Long firstEventSequence,
        Long lastEventSequence,
        long eventCount,
        String firstEventHash,
        String lastEventHash,
        String chainStateHash,
        String previousCheckpointHash,
        int schemaVersion,
        Instant createdAt
) {
    public static AuditCheckpointResponseDto fromEntity(AuditCheckpoint checkpoint) {
        if (checkpoint == null) {
            return null;
        }
        return new AuditCheckpointResponseDto(
                checkpoint.getCheckpointId(),
                checkpoint.getStreamId(),
                checkpoint.getFirstEventSequence(),
                checkpoint.getLastEventSequence(),
                checkpoint.getEventCount(),
                checkpoint.getFirstEventHash(),
                checkpoint.getLastEventHash(),
                checkpoint.getChainStateHash(),
                checkpoint.getPreviousCheckpointHash(),
                checkpoint.getSchemaVersion(),
                checkpoint.getCreatedAt()
        );
    }
}
