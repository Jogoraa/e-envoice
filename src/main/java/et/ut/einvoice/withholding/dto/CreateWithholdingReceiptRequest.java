package et.ut.einvoice.withholding.dto;

import et.ut.einvoice.withholding.domain.WithholdingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateWithholdingReceiptRequest(
        @NotNull(message = "Withholding type is mandatory")
        WithholdingType withholdingType,

        UUID relatedInvoiceId,
        String relatedInvoiceIrn,

        @NotBlank(message = "Withholding agent TIN is mandatory")
        String withholdingAgentTin,

        @NotBlank(message = "Withholding agent name is mandatory")
        String withholdingAgentName,

        @NotBlank(message = "Taxpayer TIN is mandatory")
        String taxpayerTin,

        @NotBlank(message = "Taxpayer name is mandatory")
        String taxpayerName,

        @NotNull(message = "Tax base amount is mandatory")
        @DecimalMin(value = "0.01", message = "Tax base amount must be positive")
        BigDecimal taxBaseAmount,

        BigDecimal withheldTaxRate, // Optional: if null, auto-calculated based on type (2% for Income, 50% or 100% of VAT for VAT)

        String paymentReference,
        Instant issueDate
) {}
