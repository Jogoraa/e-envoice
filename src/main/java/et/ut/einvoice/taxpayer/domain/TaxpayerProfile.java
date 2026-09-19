package et.ut.einvoice.taxpayer.domain;

import et.ut.einvoice.platform.exception.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "taxpayer_profiles")
public class TaxpayerProfile {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "tin", nullable = false, unique = true, length = 16)
    private String tin;

    @Column(name = "vat_number", length = 32)
    private String vatNumber;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "woreda", nullable = false)
    private String woreda;

    @Column(name = "sub_city")
    private String subCity;

    @Column(name = "kebele")
    private String kebele;

    @Column(name = "house_number")
    private String houseNumber;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "system_number", nullable = false)
    private String systemNumber;

    @Column(name = "system_type", nullable = false)
    private String systemType = "POS";

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    public TaxpayerProfile() {}

    public TaxpayerProfile(UUID tenantId, String tin, String vatNumber, String legalName, String tradeName,
                           String region, String woreda, String phone, String email, String systemNumber, String systemType) {
        this.tenantId = tenantId;
        this.tin = tin;
        this.vatNumber = vatNumber;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.region = region;
        this.woreda = woreda;
        this.phone = phone;
        this.email = email;
        this.systemNumber = systemNumber;
        this.systemType = systemType;
        this.isLocked = false;
        this.createdAt = Instant.now();
    }

    public UUID getTenantId() { return tenantId; }
    public String getTin() { return tin; }
    public String getVatNumber() { return vatNumber; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getRegion() { return region; }
    public String getWoreda() { return woreda; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getSystemNumber() { return systemNumber; }
    public String getSystemType() { return systemType; }
    public boolean isLocked() { return isLocked; }

    public void lock() {
        this.isLocked = true;
        this.lockedAt = Instant.now();
    }

    public void updateContactDetails(String phone, String email) {
        // Operational contact info can be updated, but legal tax identifiers remain locked per Art. 4(3)(a)
        this.phone = phone;
        this.email = email;
    }

    public void updateLegalIdentity(String tin, String vatNumber, String legalName) {
        if (this.isLocked) {
            throw new BusinessException(
                    "TAXPAYER_IDENTITY_LOCKED",
                    "Taxpayer identity fields (TIN, VAT, Legal Name) are locked post-onboarding pursuant to Directive No. 1142/2026 Art. 4(3)(a).",
                    "የግብር ከፋይ መለያ መረጃዎች (ቲን፣ የተጨማሪ እሴት ታክስ፣ ህጋዊ ስም) ከመጀመሪያ ምዝገባ በኋላ ሊለወጡ አይችሉም።",
                    org.springframework.http.HttpStatus.FORBIDDEN
            );
        }
        this.tin = tin;
        this.vatNumber = vatNumber;
        this.legalName = legalName;
    }
}
