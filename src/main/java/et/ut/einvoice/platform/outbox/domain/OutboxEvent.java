package et.ut.einvoice.platform.outbox.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public OutboxEvent() {}

    public OutboxEvent(UUID id, UUID tenantId, String aggregateType, String aggregateId,
                       String eventType, String payload, String payloadHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.createdAt = Instant.now();
        this.nextAttemptAt = Instant.now();
        this.status = OutboxStatus.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getPayloadHash() { return payloadHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public OutboxStatus getStatus() { return status; }
    public String getLastError() { return lastError; }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markInFlight() {
        this.status = OutboxStatus.IN_FLIGHT;
        this.attemptCount++;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error, Instant nextRetry) {
        this.lastError = error;
        if (this.attemptCount >= 5) {
            this.status = OutboxStatus.DEAD_LETTER;
        } else {
            this.status = OutboxStatus.FAILED;
            this.nextAttemptAt = nextRetry;
        }
    }
}
