package et.ut.einvoice.cancellation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitEvidenceDto(
        @NotNull UUID cancellationRequestId,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotBlank String fileBase64,
        String description
) {}
