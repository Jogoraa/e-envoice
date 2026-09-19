package et.ut.einvoice.tenancy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "tin", nullable = false, unique = true, length = 16)
    private String tin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.SUBSCRIPTION_ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "government_status", nullable = false)
    private GovernmentStatus governmentStatus = GovernmentStatus.GOVERNMENT_ACTIVE;

    @Column(name = "tenant_type", nullable = false)
    private String tenantType = "SME";

    @Column(name = "database_shard")
    private String databaseShard = "shared_cluster";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    public Tenant() {}

    public Tenant(UUID id, String organizationId, String legalName, String tradeName, String tin) {
        this.id = id;
        this.organizationId = organizationId;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.tin = tin;
        this.status = TenantStatus.ONBOARDING;
        this.createdAt = Instant.now();
    }

    public Tenant(UUID id, String organizationId, String legalName, String tradeName, String tin, String tenantType) {
        this(id, organizationId, legalName, tradeName, tin);
        if (tenantType != null) {
            this.tenantType = tenantType;
        }
    }

    public UUID getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getTin() { return tin; }
    public TenantStatus getStatus() { return status; }
    public String getTenantType() { return tenantType; }
    public String getDatabaseShard() { return databaseShard; }
    public Instant getCreatedAt() { return createdAt; }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
    }

    public void deactivate() {
        this.status = TenantStatus.DEACTIVATED;
    }

    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public GovernmentStatus getGovernmentStatus() { return governmentStatus; }
    public void setGovernmentStatus(GovernmentStatus governmentStatus) { this.governmentStatus = governmentStatus; }
}
