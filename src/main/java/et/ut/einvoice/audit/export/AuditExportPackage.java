package et.ut.einvoice.audit.export;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic audit evidence export package.
 * Contains the hash-chain events, checkpoint reference, and cryptographic integrity summary.
 */
public record AuditExportPackage(
        UUID exportId,
        UUID tenantId,
        String streamId,
        long eventCount,
        Long firstSequence,
        Long lastSequence,
        String firstHash,
        String lastHash,
        AuditCheckpoint checkpoint,
        List<AuditEvent> events,
        String packageContentHash,
        int schemaVersion,
        String applicationVersion,
        Instant exportedAt
) {
}
