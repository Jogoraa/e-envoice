package et.ut.einvoice.compliance.dto;

import jakarta.validation.constraints.NotBlank;

public class SubmitExitStrategyRequestDto {

    @NotBlank(message = "Strategy document reference is required")
    private String strategyDocumentReference;

    public String getStrategyDocumentReference() { return strategyDocumentReference; }
    public void setStrategyDocumentReference(String strategyDocumentReference) { this.strategyDocumentReference = strategyDocumentReference; }
}
