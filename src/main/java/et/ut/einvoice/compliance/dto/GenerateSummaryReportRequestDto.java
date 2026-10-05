package et.ut.einvoice.compliance.dto;

import java.time.Instant;
import java.util.UUID;

public class GenerateSummaryReportRequestDto {
    private UUID tenantId;
    private String reportPeriodLabel;
    private Instant periodFrom;
    private Instant periodTo;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getReportPeriodLabel() { return reportPeriodLabel; }
    public void setReportPeriodLabel(String reportPeriodLabel) { this.reportPeriodLabel = reportPeriodLabel; }

    public Instant getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(Instant periodFrom) { this.periodFrom = periodFrom; }

    public Instant getPeriodTo() { return periodTo; }
    public void setPeriodTo(Instant periodTo) { this.periodTo = periodTo; }
}
