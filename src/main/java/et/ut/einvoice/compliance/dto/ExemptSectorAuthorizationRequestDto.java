package et.ut.einvoice.compliance.dto;

import java.time.Instant;
import java.util.UUID;

public class ExemptSectorAuthorizationRequestDto {
    private UUID tenantId;
    private String sectorCode;
    private String authorizationReference;
    private String reportingFrequency;
    private Instant effectiveFrom;
    private Instant effectiveTo;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSectorCode() { return sectorCode; }
    public void setSectorCode(String sectorCode) { this.sectorCode = sectorCode; }

    public String getAuthorizationReference() { return authorizationReference; }
    public void setAuthorizationReference(String authorizationReference) { this.authorizationReference = authorizationReference; }

    public String getReportingFrequency() { return reportingFrequency; }
    public void setReportingFrequency(String reportingFrequency) { this.reportingFrequency = reportingFrequency; }

    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public Instant getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }
}
