package et.ut.einvoice.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks explicit notification opt-outs for walk-in or unmanaged recipient phone numbers.
 */
@Entity
@Table(name = "notification_opt_outs")
public class NotificationOptOut {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "opt_out_type", nullable = false, length = 32)
    private String optOutType = "TRANSACTIONAL";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public NotificationOptOut() {}

    public NotificationOptOut(UUID id, UUID tenantId, String phone, String optOutType) {
        this.id = id;
        this.tenantId = tenantId;
        this.phone = phone;
        this.optOutType = optOutType != null ? optOutType : "TRANSACTIONAL";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPhone() { return phone; }
    public String getOptOutType() { return optOutType; }
    public Instant getCreatedAt() { return createdAt; }
}
