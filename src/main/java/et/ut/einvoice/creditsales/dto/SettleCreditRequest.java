package et.ut.einvoice.creditsales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettleCreditRequest(
        @NotNull(message = "Invoice ID is mandatory")
        UUID invoiceId,

        @NotNull(message = "Settlement amount is mandatory")
        @DecimalMin(value = "0.01", message = "Settlement amount must be positive")
        BigDecimal amount,

        String paymentMethod,
        String paymentReference,
        Instant paymentDate
) {}
