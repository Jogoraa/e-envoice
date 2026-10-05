package et.ut.einvoice.adjustments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

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
        BigDecimal adjustedTax,

        String idempotencyKey,

        List<AdjustmentLineDto> lines
) {
    public CreateAdjustmentRequest(String originalIrn, String reason, BigDecimal adjustedPreTax, BigDecimal adjustedTax) {
        this(originalIrn, reason, adjustedPreTax, adjustedTax, null, null);
    }

    public record AdjustmentLineDto(
            int lineNumber,
            String itemCode,
            String productDescription,
            BigDecimal adjustedQuantity,
            BigDecimal unitPrice,
            BigDecimal preTaxAdjustment,
            BigDecimal taxAdjustment,
            BigDecimal totalAdjustment
    ) {}
}
