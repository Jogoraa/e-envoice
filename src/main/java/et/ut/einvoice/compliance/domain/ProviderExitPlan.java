package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Represents a formal Provider Exit Strategy & Cessation Governance Plan
 * pursuant to FDRE MoR Directive No. 1142/2026 Art. 17.
 */
@Entity
@Table(name = "provider_exit_plans")
public class ProviderExitPlan {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "exit_reason", length = 64, nullable = false)
    private String exitReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ProviderExitStatus status = ProviderExitStatus.PLANNED;

    @Column(name = "notice_period_months", nullable = false)
    private int noticePeriodMonths = 6;

    @Column(name = "announcement_date", nullable = false)
    private Instant announcementDate;

    @Column(name = "effective_exit_date", nullable = false)
    private Instant effectiveExitDate;

    @Column(name = "data_retrieval_deadline", nullable = false)
    private Instant dataRetrievalDeadline;

    @Column(name = "strategy_document_reference", length = 255)
    private String strategyDocumentReference;

    @Column(name = "strategy_submitted_at")
    private Instant strategySubmittedAt;

    @Column(name = "authority_approval_reference", length = 128)
    private String authorityApprovalReference;

    @Column(name = "authority_approved_at")
    private Instant authorityApprovedAt;

    @Column(name = "authority_approved_by", length = 128)
    private String authorityApprovedBy;

    @Column(name = "taxpayers_notified_at")
    private Instant taxpayersNotifiedAt;

    @Column(name = "total_active_tenants", nullable = false)
    private int totalActiveTenants = 0;

    @Column(name = "migrated_tenants_count", nullable = false)
    private int migratedTenantsCount = 0;

    @Column(name = "surrendered_certificate_reference", length = 128)
    private String surrenderedCertificateReference;

    @Column(name = "cessation_confirmation_reference", length = 128)
    private String cessationConfirmationReference;

    @Column(name = "cessation_confirmed_at")
    private Instant cessationConfirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ProviderExitPlan() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ProviderExitPlan(UUID id, String exitReason, int noticePeriodMonths,
                            Instant announcementDate, Instant effectiveExitDate) {
        this.id = id;
        this.exitReason = exitReason;
        this.noticePeriodMonths = noticePeriodMonths;
        this.announcementDate = announcementDate != null ? announcementDate : Instant.now();
        this.effectiveExitDate = effectiveExitDate;
        // Art. 17(4): 6 months data retrieval/transfer window after effective exit date
        this.dataRetrievalDeadline = effectiveExitDate.plus(180, ChronoUnit.DAYS);
        this.status = ProviderExitStatus.PLANNED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void submitStrategy(String strategyDocumentReference) {
        this.strategyDocumentReference = strategyDocumentReference;
        this.strategySubmittedAt = Instant.now();
        this.status = ProviderExitStatus.STRATEGY_SUBMITTED;
        this.updatedAt = Instant.now();
    }

    public void recordAuthorityApproval(String approvalReference, String approvedBy) {
        this.authorityApprovalReference = approvalReference;
        this.authorityApprovedBy = approvedBy;
        this.authorityApprovedAt = Instant.now();
        this.status = ProviderExitStatus.AUTHORITY_APPROVED;
        this.updatedAt = Instant.now();
    }

    public void recordTaxpayersNotified(int totalActiveTenants) {
        this.totalActiveTenants = totalActiveTenants;
        this.taxpayersNotifiedAt = Instant.now();
        this.status = ProviderExitStatus.TAXPAYERS_NOTIFIED;
        this.updatedAt = Instant.now();
    }

    public void markInTransition() {
        this.status = ProviderExitStatus.IN_TRANSITION;
        this.updatedAt = Instant.now();
    }

    public void recordTenantMigrationComplete() {
        this.migratedTenantsCount++;
        if (this.migratedTenantsCount >= this.totalActiveTenants && this.totalActiveTenants > 0) {
            this.status = ProviderExitStatus.MIGRATION_COMPLETED;
        }
        this.updatedAt = Instant.now();
    }

    public void confirmCessation(String surrenderedCertificateReference, String confirmationReference) {
        if (this.status != ProviderExitStatus.MIGRATION_COMPLETED && this.totalActiveTenants > 0) {
            throw new IllegalStateException("Cannot confirm cessation of service until 100% of taxpayers have migrated (Directive Art. 17(5))");
        }
        this.surrenderedCertificateReference = surrenderedCertificateReference;
        this.cessationConfirmationReference = confirmationReference;
        this.cessationConfirmedAt = Instant.now();
        this.status = ProviderExitStatus.CESSATION_CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public boolean isAllTenantsMigrated() {
        return this.totalActiveTenants > 0 && this.migratedTenantsCount >= this.totalActiveTenants;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public ProviderExitStatus getStatus() { return status; }
    public void setStatus(ProviderExitStatus status) { this.status = status; }
    public int getNoticePeriodMonths() { return noticePeriodMonths; }
    public void setNoticePeriodMonths(int noticePeriodMonths) { this.noticePeriodMonths = noticePeriodMonths; }
    public Instant getAnnouncementDate() { return announcementDate; }
    public void setAnnouncementDate(Instant announcementDate) { this.announcementDate = announcementDate; }
    public Instant getEffectiveExitDate() { return effectiveExitDate; }
    public void setEffectiveExitDate(Instant effectiveExitDate) { this.effectiveExitDate = effectiveExitDate; }
    public Instant getDataRetrievalDeadline() { return dataRetrievalDeadline; }
    public void setDataRetrievalDeadline(Instant dataRetrievalDeadline) { this.dataRetrievalDeadline = dataRetrievalDeadline; }
    public String getStrategyDocumentReference() { return strategyDocumentReference; }
    public void setStrategyDocumentReference(String strategyDocumentReference) { this.strategyDocumentReference = strategyDocumentReference; }
    public Instant getStrategySubmittedAt() { return strategySubmittedAt; }
    public void setStrategySubmittedAt(Instant strategySubmittedAt) { this.strategySubmittedAt = strategySubmittedAt; }
    public String getAuthorityApprovalReference() { return authorityApprovalReference; }
    public void setAuthorityApprovalReference(String authorityApprovalReference) { this.authorityApprovalReference = authorityApprovalReference; }
    public Instant getAuthorityApprovedAt() { return authorityApprovedAt; }
    public void setAuthorityApprovedAt(Instant authorityApprovedAt) { this.authorityApprovedAt = authorityApprovedAt; }
    public String getAuthorityApprovedBy() { return authorityApprovedBy; }
    public void setAuthorityApprovedBy(String authorityApprovedBy) { this.authorityApprovedBy = authorityApprovedBy; }
    public Instant getTaxpayersNotifiedAt() { return taxpayersNotifiedAt; }
    public void setTaxpayersNotifiedAt(Instant taxpayersNotifiedAt) { this.taxpayersNotifiedAt = taxpayersNotifiedAt; }
    public int getTotalActiveTenants() { return totalActiveTenants; }
    public void setTotalActiveTenants(int totalActiveTenants) { this.totalActiveTenants = totalActiveTenants; }
    public int getMigratedTenantsCount() { return migratedTenantsCount; }
    public void setMigratedTenantsCount(int migratedTenantsCount) { this.migratedTenantsCount = migratedTenantsCount; }
    public String getSurrenderedCertificateReference() { return surrenderedCertificateReference; }
    public void setSurrenderedCertificateReference(String surrenderedCertificateReference) { this.surrenderedCertificateReference = surrenderedCertificateReference; }
    public String getCessationConfirmationReference() { return cessationConfirmationReference; }
    public void setCessationConfirmationReference(String cessationConfirmationReference) { this.cessationConfirmationReference = cessationConfirmationReference; }
    public Instant getCessationConfirmedAt() { return cessationConfirmedAt; }
    public void setCessationConfirmedAt(Instant cessationConfirmedAt) { this.cessationConfirmedAt = cessationConfirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
