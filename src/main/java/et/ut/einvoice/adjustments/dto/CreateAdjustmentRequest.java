package et.ut.einvoice.adjustments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAdjustmentRequest(
        @NotBlank(message = "Original Invoice IRN is required.")
        @Size(max = 64)
        String originalIrn,
        @NotBlank(message = "Adjustment reason is required.")
        @Size(max = 255)
        String reason,
        @NotNull(message = "Adjusted pre-tax amount is mandatory.")
        @Positive(message = "Adjusted pre-tax amount must be positive.")
        BigDecimal adjustedPreTax,
        @PositiveOrZero(message = "Adjusted tax amount must be positive or zero.")
        BigDecimal adjustedTax
) {}
