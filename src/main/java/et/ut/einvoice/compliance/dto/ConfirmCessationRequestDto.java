package et.ut.einvoice.compliance.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmCessationRequestDto {

    @NotBlank(message = "Surrendered certificate reference is required")
    private String surrenderedCertificateReference;

    @NotBlank(message = "Cessation confirmation reference is required")
    private String cessationConfirmationReference;

    public String getSurrenderedCertificateReference() { return surrenderedCertificateReference; }
    public void setSurrenderedCertificateReference(String surrenderedCertificateReference) { this.surrenderedCertificateReference = surrenderedCertificateReference; }
    public String getCessationConfirmationReference() { return cessationConfirmationReference; }
    public void setCessationConfirmationReference(String cessationConfirmationReference) { this.cessationConfirmationReference = cessationConfirmationReference; }
}
