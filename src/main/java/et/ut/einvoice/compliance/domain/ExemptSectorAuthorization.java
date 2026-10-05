package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Records that a high-volume taxpayer has been authorized by the Authority to issue invoices
 * for B2C consumer transactions without a direct EIRS connection, submitting periodic
 * aggregate summary sales reports instead.
 *
 * Directive No. 1142/2026 Art. 20(1)-(4) — Exemptions from the Obligation to Use
 * Electronic Sales Register System.
 *
 * Eligible sectors under Art. 20(1): banking, securities markets, digital payment processing,
 * and telecommunications services. The Ministry may extend to other sectors (Art. 20(2)).
 *
 * Compliance invariants enforced here:
 *   - Only ACTIVE authorizations permit exempt invoicing (Art. 20(3))
 *   - B2B transactions are NOT covered; they still require direct EIRS (Art. 20(5))
 *   - Effective dates are enforced to prevent use of expired authorizations
 */
@Entity
@Table(name = "exempt_sector_authorizations")
public class ExemptSectorAuthorization {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sector_code", length = 32, nullable = false)
    private String sectorCode;

    @Column(name = "authorized_by", length = 128, nullable = false)
    private String authorizedBy;

    /** MoR official letter reference or internal decision number */
    @Column(name = "authorization_reference", length = 128, nullable = false)
    private String authorizationReference;

    /** Reporting cadence determined per Art. 20(3)(d): DAILY, WEEKLY, or MONTHLY */
    @Column(name = "reporting_frequency", length = 16, nullable = false)
    private String reportingFrequency;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    private String revocationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ExemptSectorAuthorization() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ExemptSectorAuthorization(UUID id, UUID tenantId, String sectorCode,
                                     String authorizedBy, String authorizationReference,
                                     String reportingFrequency) {
        this.id = id;
        this.tenantId = tenantId;
        this.sectorCode = sectorCode;
        this.authorizedBy = authorizedBy;
        this.authorizationReference = authorizationReference;
        this.reportingFrequency = reportingFrequency;
        this.status = "ACTIVE";
        this.effectiveFrom = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isCurrentlyActive() {
        Instant now = Instant.now();
        boolean afterStart = !now.isBefore(effectiveFrom);
        boolean beforeEnd = (effectiveTo == null) || now.isBefore(effectiveTo);
        return "ACTIVE".equals(status) && afterStart && beforeEnd;
    }

    public void revoke(String reason) {
        this.status = "REVOKED";
        this.effectiveTo = Instant.now();
        this.revocationReason = reason;
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        this.status = "SUSPENDED";
        this.revocationReason = reason;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSectorCode() { return sectorCode; }
    public String getAuthorizedBy() { return authorizedBy; }
    public String getAuthorizationReference() { return authorizationReference; }
    public String getReportingFrequency() { return reportingFrequency; }
    public String getStatus() { return status; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public void setId(UUID id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setSectorCode(String sectorCode) { this.sectorCode = sectorCode; }
    public void setAuthorizedBy(String authorizedBy) { this.authorizedBy = authorizedBy; }
    public void setAuthorizationReference(String authorizationReference) { this.authorizationReference = authorizationReference; }
    public void setReportingFrequency(String reportingFrequency) { this.reportingFrequency = reportingFrequency; }
    public void setStatus(String status) { this.status = status; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
