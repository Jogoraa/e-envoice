package et.ut.einvoice.compliance.dto;

import jakarta.validation.constraints.NotBlank;

public class ApproveExitStrategyRequestDto {

    @NotBlank(message = "Approval reference is required")
    private String approvalReference;

    @NotBlank(message = "Approved by is required")
    private String approvedBy;

    public String getApprovalReference() { return approvalReference; }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
}
