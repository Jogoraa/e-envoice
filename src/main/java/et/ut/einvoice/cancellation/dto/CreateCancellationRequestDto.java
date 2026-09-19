package et.ut.einvoice.cancellation.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCancellationRequestDto(
        @NotBlank(message = "Invoice IRN is required.")
        String irn,
        @NotBlank(message = "Reason category is required.")
        String reasonCategory,
        @NotBlank(message = "Detailed justification is required.")
        String detailedReason
) {}
