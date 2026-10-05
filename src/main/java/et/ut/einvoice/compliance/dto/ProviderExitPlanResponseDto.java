package et.ut.einvoice.compliance.dto;

import et.ut.einvoice.compliance.domain.ProviderExitPlan;
import et.ut.einvoice.compliance.domain.ProviderExitStatus;
import java.time.Instant;
import java.util.UUID;

public class ProviderExitPlanResponseDto {
    private UUID id;
    private String exitReason;
    private ProviderExitStatus status;
    private int noticePeriodMonths;
    private Instant announcementDate;
    private Instant effectiveExitDate;
    private Instant dataRetrievalDeadline;
    private String strategyDocumentReference;
    private Instant strategySubmittedAt;
    private String authorityApprovalReference;
    private Instant authorityApprovedAt;
    private String authorityApprovedBy;
    private Instant taxpayersNotifiedAt;
    private int totalActiveTenants;
    private int migratedTenantsCount;
    private String surrenderedCertificateReference;
    private String cessationConfirmationReference;
    private Instant cessationConfirmedAt;
    private double migrationProgressPercent;

    public static ProviderExitPlanResponseDto fromEntity(ProviderExitPlan plan) {
        ProviderExitPlanResponseDto dto = new ProviderExitPlanResponseDto();
        dto.id = plan.getId();
        dto.exitReason = plan.getExitReason();
        dto.status = plan.getStatus();
        dto.noticePeriodMonths = plan.getNoticePeriodMonths();
        dto.announcementDate = plan.getAnnouncementDate();
        dto.effectiveExitDate = plan.getEffectiveExitDate();
        dto.dataRetrievalDeadline = plan.getDataRetrievalDeadline();
        dto.strategyDocumentReference = plan.getStrategyDocumentReference();
        dto.strategySubmittedAt = plan.getStrategySubmittedAt();
        dto.authorityApprovalReference = plan.getAuthorityApprovalReference();
        dto.authorityApprovedAt = plan.getAuthorityApprovedAt();
        dto.authorityApprovedBy = plan.getAuthorityApprovedBy();
        dto.taxpayersNotifiedAt = plan.getTaxpayersNotifiedAt();
        dto.totalActiveTenants = plan.getTotalActiveTenants();
        dto.migratedTenantsCount = plan.getMigratedTenantsCount();
        dto.surrenderedCertificateReference = plan.getSurrenderedCertificateReference();
        dto.cessationConfirmationReference = plan.getCessationConfirmationReference();
        dto.cessationConfirmedAt = plan.getCessationConfirmedAt();
        dto.migrationProgressPercent = plan.getTotalActiveTenants() > 0
                ? ((double) plan.getMigratedTenantsCount() / plan.getTotalActiveTenants()) * 100.0
                : 0.0;
        return dto;
    }

    public UUID getId() { return id; }
    public String getExitReason() { return exitReason; }
    public ProviderExitStatus getStatus() { return status; }
    public int getNoticePeriodMonths() { return noticePeriodMonths; }
    public Instant getAnnouncementDate() { return announcementDate; }
    public Instant getEffectiveExitDate() { return effectiveExitDate; }
    public Instant getDataRetrievalDeadline() { return dataRetrievalDeadline; }
    public String getStrategyDocumentReference() { return strategyDocumentReference; }
    public Instant getStrategySubmittedAt() { return strategySubmittedAt; }
    public String getAuthorityApprovalReference() { return authorityApprovalReference; }
    public Instant getAuthorityApprovedAt() { return authorityApprovedAt; }
    public String getAuthorityApprovedBy() { return authorityApprovedBy; }
    public Instant getTaxpayersNotifiedAt() { return taxpayersNotifiedAt; }
    public int getTotalActiveTenants() { return totalActiveTenants; }
    public int getMigratedTenantsCount() { return migratedTenantsCount; }
    public String getSurrenderedCertificateReference() { return surrenderedCertificateReference; }
    public String getCessationConfirmationReference() { return cessationConfirmationReference; }
    public Instant getCessationConfirmedAt() { return cessationConfirmedAt; }
    public double getMigrationProgressPercent() { return migrationProgressPercent; }
}
