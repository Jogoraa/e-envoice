package et.ut.einvoice.webhooks.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "target_url", nullable = false, length = 512)
    private String targetUrl;

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Column(name = "secret_key", nullable = false, length = 128)
    private String secretKey;

    @Column(name = "subscribed_events", nullable = false, length = 512)
    private String subscribedEvents;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public WebhookSubscription() {}

    public WebhookSubscription(UUID id, UUID tenantId, String targetUrl, String secretKey, String subscribedEvents) {
        this.id = id;
        this.tenantId = tenantId;
        this.targetUrl = targetUrl;
        this.secretKey = secretKey;
        this.subscribedEvents = subscribedEvents;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTargetUrl() { return targetUrl; }
    public String getSecretKey() { return secretKey; }
    public String getSubscribedEvents() { return subscribedEvents; }
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean matchesEvent(String eventType) {
        if ("*".equals(subscribedEvents) || subscribedEvents == null) return true;
        if (subscribedEvents.endsWith("*")) {
            String prefix = subscribedEvents.substring(0, subscribedEvents.length() - 1);
            return eventType != null && eventType.startsWith(prefix);
        }
        return subscribedEvents.contains(eventType);
    }
}
