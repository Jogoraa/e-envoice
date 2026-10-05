package et.ut.einvoice.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marketplace_merchants")
public class MarketplaceMerchant {

    @Id
    private UUID id;

    @Column(name = "marketplace_tenant_id", nullable = false)
    private UUID marketplaceTenantId;

    @Column(name = "merchant_tin", nullable = false, length = 16)
    private String merchantTin;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "email", nullable = false, length = 128)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_status", nullable = false, length = 32)
    private MerchantStatus merchantStatus = MerchantStatus.ACTIVE;

    @Column(name = "authority_notification_state", nullable = false, length = 32)
    private String authorityNotificationState = "ACKNOWLEDGED";

    @Column(name = "authority_suspension_reason", columnDefinition = "TEXT")
    private String authoritySuspensionReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "reinstated_at")
    private Instant reinstatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MarketplaceMerchant() {}

    public MarketplaceMerchant(UUID id, UUID marketplaceTenantId, String merchantTin, String legalName, String tradeName, String address, String phone, String email) {
        this.id = id != null ? id : UUID.randomUUID();
        this.marketplaceTenantId = marketplaceTenantId;
        this.merchantTin = merchantTin;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.merchantStatus = MerchantStatus.ACTIVE;
        this.authorityNotificationState = "ACKNOWLEDGED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMarketplaceTenantId() { return marketplaceTenantId; }
    public String getMerchantTin() { return merchantTin; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public MerchantStatus getMerchantStatus() { return merchantStatus; }
    public String getAuthorityNotificationState() { return authorityNotificationState; }
    public String getAuthoritySuspensionReason() { return authoritySuspensionReason; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public Instant getReinstatedAt() { return reinstatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void suspendByAuthority(String reason) {
        this.merchantStatus = MerchantStatus.SUSPENDED_BY_AUTHORITY;
        this.authoritySuspensionReason = reason;
        this.suspendedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reinstateByAuthority() {
        this.merchantStatus = MerchantStatus.REINSTATED_BY_AUTHORITY;
        this.authoritySuspensionReason = null;
        this.reinstatedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
