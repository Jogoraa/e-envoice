package et.ut.einvoice.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class CreateExitPlanRequestDto {

    @NotBlank(message = "Exit reason is required")
    private String exitReason;

    private boolean isRevocation = false;

    private Integer noticePeriodMonths;

    @NotNull(message = "Effective exit date is required")
    private Instant effectiveExitDate;

    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public boolean isRevocation() { return isRevocation; }
    public void setRevocation(boolean revocation) { isRevocation = revocation; }
    public Integer getNoticePeriodMonths() { return noticePeriodMonths; }
    public void setNoticePeriodMonths(Integer noticePeriodMonths) { this.noticePeriodMonths = noticePeriodMonths; }
    public Instant getEffectiveExitDate() { return effectiveExitDate; }
    public void setEffectiveExitDate(Instant effectiveExitDate) { this.effectiveExitDate = effectiveExitDate; }
}
