package et.ut.einvoice.offline.dto;

import et.ut.einvoice.offline.domain.OfflineTransactionBuffer;

import java.time.Instant;

/**
 * Synchronization acknowledgement. It intentionally omits raw invoice payloads,
 * device identifiers, signatures, error internals, and tenant identifiers.
 */
public record OfflineSyncStatusDto(
        String clientTransactionId,
        Long offlineSeqNo,
        String lifecycleState,
        String syncStatus,
        String irn,
        Instant bufferedAt,
        Instant syncedAt
) {
    public static OfflineSyncStatusDto fromEntity(OfflineTransactionBuffer buffer) {
        return new OfflineSyncStatusDto(
                buffer.getClientTransactionId(),
                buffer.getOfflineSeqNo(),
                buffer.getLifecycleState(),
                buffer.getSyncStatus(),
                buffer.getIrn(),
                buffer.getBufferedAt(),
                buffer.getSyncedAt()
        );
    }
}
