package et.ut.einvoice.cashreceipt.dto;

import et.ut.einvoice.cashreceipt.domain.CashReceiptPaymentMethod;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateCashReceiptRequest(
        @NotBlank(message = "Payer name is mandatory")
        String payerName,

        String payerTin,

        @NotNull(message = "Amount is mandatory")
        @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
        BigDecimal amount,

        String currency,

        @NotNull(message = "Purpose is mandatory")
        CashReceiptPurpose purpose,

        String purposeDescription,

        UUID relatedInvoiceId,

        String relatedCreditAccountId,

        CashReceiptPaymentMethod paymentMethod,

        String referenceNumber,

        Instant receivedAt
) {}
