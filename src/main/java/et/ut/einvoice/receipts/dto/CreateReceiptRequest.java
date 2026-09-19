package et.ut.einvoice.receipts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateReceiptRequest(
        @NotBlank(message = "Original Invoice IRN is required.")
        @Size(max = 64)
        String invoiceIrn,
        @NotNull(message = "Receipt amount must be positive.")
        @Positive(message = "Receipt amount must be positive.")
        BigDecimal amount,
        @PositiveOrZero(message = "Withholding amount must be positive or zero.")
        BigDecimal withholdingAmount
) {}
