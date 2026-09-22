package et.ut.einvoice.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Audit Outbox Event for reliable at-least-once downstream
 * publication to external immutable ledgers, archival storage, and security SIEM.
 */
@Entity
@Table(name = "audit_outbox_events", indexes = {
        @Index(name = "idx_audit_outbox_status_next", columnList = "status, next_attempt_at"),
        @Index(name = "idx_audit_outbox_tenant_stream", columnList = "tenant_id, stream_id")
})
public class AuditOutboxEvent {

    public enum OutboxStatus {
        PENDING,
        IN_FLIGHT,
        PUBLISHED,
        FAILED,
        DEAD_LETTER
    }

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "audit_event_id", nullable = false)
    private UUID auditEventId;

    @Column(name = "stream_id", nullable = false, length = 64)
    private String streamId;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    @Column(name = "canonical_payload", columnDefinition = "TEXT", nullable = false)
    private String canonicalPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    public AuditOutboxEvent() {}

    public AuditOutboxEvent(UUID id, UUID tenantId, UUID auditEventId, String streamId, Long sequenceNumber,
                            String eventHash, String canonicalPayload) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.auditEventId = auditEventId;
        this.streamId = streamId;
        this.sequenceNumber = sequenceNumber;
        this.eventHash = eventHash;
        this.canonicalPayload = canonicalPayload;
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = 5;
        this.createdAt = Instant.now();
        this.nextAttemptAt = Instant.now();
    }

    public void markInFlight() {
        this.status = OutboxStatus.IN_FLIGHT;
        this.attemptCount++;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed(String error, long backoffMillis) {
        this.attemptCount++;
        this.lastError = error;
        if (this.attemptCount >= this.maxAttempts) {
            this.status = OutboxStatus.DEAD_LETTER;
        } else {
            this.status = OutboxStatus.FAILED;
            this.nextAttemptAt = Instant.now().plusMillis(backoffMillis);
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAuditEventId() { return auditEventId; }
    public String getStreamId() { return streamId; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public String getEventHash() { return eventHash; }
    public String getCanonicalPayload() { return canonicalPayload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }

    public void setStatus(OutboxStatus status) { this.status = status; }
}
