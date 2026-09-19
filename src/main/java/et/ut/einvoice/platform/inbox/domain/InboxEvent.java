package et.ut.einvoice.platform.inbox.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_source_event", columnNames = {"source", "event_id"})
})
public class InboxEvent {

    @Id
    private UUID id;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "RECEIVED";

    public InboxEvent() {}

    public InboxEvent(UUID id, String source, String eventId, String eventType, String payloadHash) {
        this.id = id;
        this.source = source;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payloadHash = payloadHash;
        this.receivedAt = Instant.now();
        this.status = "RECEIVED";
    }

    public UUID getId() { return id; }
    public String getSource() { return source; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayloadHash() { return payloadHash; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getStatus() { return status; }

    public void markProcessed() {
        this.status = "PROCESSED";
        this.processedAt = Instant.now();
    }
}
