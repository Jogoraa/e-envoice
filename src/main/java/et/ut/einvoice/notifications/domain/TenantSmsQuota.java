package et.ut.einvoice.notifications.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks atomic daily SMS usage quotas per tenant to protect platform balances.
 */
@Entity
@Table(name = "tenant_sms_quotas")
public class TenantSmsQuota {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit = 1000;

    @Column(name = "daily_units_used", nullable = false)
    private int dailyUnitsUsed = 0;

    @Column(name = "daily_messages_used", nullable = false)
    private int dailyMessagesUsed = 0;

    @Column(name = "reset_date", nullable = false)
    private LocalDate resetDate = LocalDate.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantSmsQuota() {}

    public TenantSmsQuota(UUID tenantId, int dailyLimit) {
        this.tenantId = tenantId;
        this.dailyLimit = dailyLimit;
        this.dailyUnitsUsed = 0;
        this.dailyMessagesUsed = 0;
        this.resetDate = LocalDate.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getTenantId() { return tenantId; }
    public int getDailyLimit() { return dailyLimit; }
    public int getDailyUnitsUsed() { return dailyUnitsUsed; }
    public int getDailyMessagesUsed() { return dailyMessagesUsed; }
    public LocalDate getResetDate() { return resetDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
        this.updatedAt = Instant.now();
    }
}
