package et.ut.einvoice.compliance.dto;

public class ReviewSummaryReportRequestDto {
    private boolean accept;
    private String reason;

    public boolean isAccept() { return accept; }
    public void setAccept(boolean accept) { this.accept = accept; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
