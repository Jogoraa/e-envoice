package et.ut.einvoice.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit Checkpoint entity providing periodic cryptographic anchor states
 * over monotonic audit hash chains.
 */
@Entity
@Table(name = "audit_checkpoints", indexes = {
        @Index(name = "idx_audit_checkpoints_tenant_stream", columnList = "tenant_id, stream_id, last_event_sequence")
})
public class AuditCheckpoint {

    @Id
    @Column(name = "checkpoint_id")
    private UUID checkpointId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "stream_id", nullable = false, length = 64)
    private String streamId;

    @Column(name = "first_event_sequence", nullable = false)
    private Long firstEventSequence;

    @Column(name = "last_event_sequence", nullable = false)
    private Long lastEventSequence;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    @Column(name = "first_event_hash", nullable = false, length = 64)
    private String firstEventHash;

    @Column(name = "last_event_hash", nullable = false, length = 64)
    private String lastEventHash;

    @Column(name = "chain_state_hash", nullable = false, length = 64)
    private String chainStateHash;

    @Column(name = "previous_checkpoint_hash", nullable = false, length = 64)
    private String previousCheckpointHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    public AuditCheckpoint() {}

    public AuditCheckpoint(UUID checkpointId, UUID tenantId, String streamId, Long firstEventSequence,
                           Long lastEventSequence, long eventCount, String firstEventHash, String lastEventHash,
                           String chainStateHash, String previousCheckpointHash, int schemaVersion, Instant createdAt) {
        this.checkpointId = checkpointId != null ? checkpointId : UUID.randomUUID();
        this.tenantId = tenantId;
        this.streamId = streamId;
        this.firstEventSequence = firstEventSequence;
        this.lastEventSequence = lastEventSequence;
        this.eventCount = eventCount;
        this.firstEventHash = firstEventHash;
        this.lastEventHash = lastEventHash;
        this.chainStateHash = chainStateHash;
        this.previousCheckpointHash = previousCheckpointHash;
        this.schemaVersion = schemaVersion > 0 ? schemaVersion : 1;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getCheckpointId() { return checkpointId; }
    public UUID getTenantId() { return tenantId; }
    public String getStreamId() { return streamId; }
    public Long getFirstEventSequence() { return firstEventSequence; }
    public Long getLastEventSequence() { return lastEventSequence; }
    public long getEventCount() { return eventCount; }
    public String getFirstEventHash() { return firstEventHash; }
    public String getLastEventHash() { return lastEventHash; }
    public String getChainStateHash() { return chainStateHash; }
    public String getPreviousCheckpointHash() { return previousCheckpointHash; }
    public Instant getCreatedAt() { return createdAt; }
    public int getSchemaVersion() { return schemaVersion; }
}
