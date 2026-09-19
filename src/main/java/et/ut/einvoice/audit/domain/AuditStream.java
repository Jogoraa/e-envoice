package et.ut.einvoice.audit.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit Stream tracking entity maintaining authoritative monotonic sequence numbers
 * and hash chain heads per (tenant_id, stream_id).
 * Concurrency is controlled via database-level pessimistic row locking (SELECT ... FOR UPDATE).
 */
@Entity
@Table(name = "audit_streams")
@IdClass(AuditStream.AuditStreamId.class)
public class AuditStream {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "stream_id", nullable = false, length = 64)
    private String streamId;

    @Column(name = "last_sequence_number", nullable = false)
    private Long lastSequenceNumber = 0L;

    @Column(name = "last_event_hash", nullable = false, length = 64)
    private String lastEventHash;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AuditStream() {}

    public AuditStream(UUID tenantId, String streamId, String genesisHash) {
        this.tenantId = tenantId;
        this.streamId = streamId;
        this.lastSequenceNumber = 0L;
        this.lastEventHash = genesisHash;
        this.updatedAt = Instant.now();
    }

    public void advance(Long nextSequence, String newEventHash, UUID newEventId) {
        this.lastSequenceNumber = nextSequence;
        this.lastEventHash = newEventHash;
        this.lastEventId = newEventId;
        this.updatedAt = Instant.now();
    }

    public UUID getTenantId() { return tenantId; }
    public String getStreamId() { return streamId; }
    public Long getLastSequenceNumber() { return lastSequenceNumber; }
    public String getLastEventHash() { return lastEventHash; }
    public UUID getLastEventId() { return lastEventId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class AuditStreamId implements Serializable {
        private UUID tenantId;
        private String streamId;

        public AuditStreamId() {}

        public AuditStreamId(UUID tenantId, String streamId) {
            this.tenantId = tenantId;
            this.streamId = streamId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AuditStreamId that = (AuditStreamId) o;
            return Objects.equals(tenantId, that.tenantId) && Objects.equals(streamId, that.streamId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, streamId);
        }
    }
}
