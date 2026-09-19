package et.ut.einvoice.audit.ledger;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port abstraction for immutable fiscal audit ledger persistence and verification.
 * Decouples the domain and application services from underlying immutable storage backends
 * (e.g. PostgreSQL append-only tables in development, or external immudb or another
 * currently supported immutable ledger explicitly evaluated during deployment).
 */
public interface ImmutableAuditLedger {

    /**
     * Appends a single audit event to the ledger with strict monotonic ordering and hash-chaining.
     */
    AuditEvent append(AuditEvent event);

    /**
     * Appends a batch of audit events atomically to the ledger.
     */
    List<AuditEvent> appendBatch(List<AuditEvent> events);

    /**
     * Verifies the cryptographic integrity of a specific event within its stream.
     */
    boolean verify(AuditEvent event);

    /**
     * Verifies the cryptographic integrity, sequence continuity, and hash chain of an entire stream.
     */
    boolean verifyStream(UUID tenantId, String streamId);

    /**
     * Retrieves the latest anchor checkpoint for a tenant stream.
     */
    Optional<AuditCheckpoint> getCheckpoint(UUID tenantId, String streamId);

    /**
     * Verifies an anchor checkpoint against the underlying hash chain records.
     */
    boolean verifyCheckpoint(UUID tenantId, String streamId, UUID checkpointId);
}
