package et.ut.einvoice.webhooks.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbound_webhook_deliveries")
public class OutboundWebhookDelivery {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "target_url", nullable = false, length = 512)
    private String targetUrl;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "signature", nullable = false, length = 128)
    private String signature;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING"; // 'PENDING', 'DELIVERED', 'FAILED', 'DEAD_LETTER'

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public OutboundWebhookDelivery() {}

    public OutboundWebhookDelivery(UUID id, UUID tenantId, String eventType, String targetUrl,
                                   String payloadJson, String signature) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.targetUrl = targetUrl;
        this.payloadJson = payloadJson;
        this.signature = signature;
        this.attemptCount = 0;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getTargetUrl() { return targetUrl; }
    public String getPayloadJson() { return payloadJson; }
    public String getSignature() { return signature; }
    public int getAttemptCount() { return attemptCount; }
    public String getStatus() { return status; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    public void markDelivered() {
        this.status = "DELIVERED";
        this.lastAttemptAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String error) {
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.errorMessage = error;
        if (this.attemptCount >= 5) {
            this.status = "DEAD_LETTER";
        } else {
            this.status = "FAILED";
        }
    }
}
