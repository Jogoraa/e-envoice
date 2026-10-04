package et.ut.einvoice.cancellation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCancellationRequestDto(
        @NotBlank(message = "Invoice IRN is required.")
        @Size(max = 64)
        String irn,
        @NotBlank(message = "Reason category is required.")
        @Size(max = 64)
        String reasonCategory,
        @NotBlank(message = "Detailed justification is required.")
        @Size(max = 2000)
        String detailedReason
) {}
