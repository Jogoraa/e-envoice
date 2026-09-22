package et.ut.einvoice.tenancy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Commercial subscription entity for multi-tenant SaaS plan, limits, and billing lifecycle.
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_tenant", columnList = "tenant_id", unique = true)
})
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode = "SME_STANDARD";

    @Column(name = "billing_cycle", nullable = false, length = 16)
    private String billingCycle = "MONTHLY";

    @Column(name = "max_monthly_invoices", nullable = false)
    private Integer maxMonthlyInvoices = 5000;

    @Column(name = "rate_limit_rps", nullable = false)
    private Integer rateLimitRps = 20;

    @Column(name = "offline_allowed", nullable = false)
    private Boolean offlineAllowed = false;

    @Column(name = "geofence_enforced", nullable = false)
    private Boolean geofenceEnforced = false;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Subscription() {}

    public Subscription(UUID id, UUID tenantId, String planCode, String billingCycle,
                        Integer maxMonthlyInvoices, Integer rateLimitRps, Boolean offlineAllowed,
                        Boolean geofenceEnforced, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.planCode = planCode != null ? planCode : "SME_STANDARD";
        this.billingCycle = billingCycle != null ? billingCycle : "MONTHLY";
        this.maxMonthlyInvoices = maxMonthlyInvoices != null ? maxMonthlyInvoices : 5000;
        this.rateLimitRps = rateLimitRps != null ? rateLimitRps : 20;
        this.offlineAllowed = offlineAllowed != null ? offlineAllowed : false;
        this.geofenceEnforced = geofenceEnforced != null ? geofenceEnforced : false;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPlanCode() { return planCode; }
    public String getBillingCycle() { return billingCycle; }
    public Integer getMaxMonthlyInvoices() { return maxMonthlyInvoices; }
    public Integer getRateLimitRps() { return rateLimitRps; }
    public Boolean getOfflineAllowed() { return offlineAllowed; }
    public Boolean getGeofenceEnforced() { return geofenceEnforced; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }
    public void setMaxMonthlyInvoices(Integer maxMonthlyInvoices) { this.maxMonthlyInvoices = maxMonthlyInvoices; }
    public void setRateLimitRps(Integer rateLimitRps) { this.rateLimitRps = rateLimitRps; }
    public void setOfflineAllowed(Boolean offlineAllowed) { this.offlineAllowed = offlineAllowed; }
    public void setGeofenceEnforced(Boolean geofenceEnforced) { this.geofenceEnforced = geofenceEnforced; }
    public void setStatus(String status) { this.status = status; }
}
