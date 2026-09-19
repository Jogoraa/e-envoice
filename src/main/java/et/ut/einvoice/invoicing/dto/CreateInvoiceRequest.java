package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateInvoiceRequest(
        @NotNull(message = "Transaction type is mandatory.")
        TransactionType transactionType,
        @NotBlank(message = "Payment mode is mandatory.")
        @Size(max = 64)
        String paymentMode,
        @Size(max = 64)
        String paymentTerm,
        @Valid BuyerRequest buyer,
        @NotEmpty(message = "Invoice must contain at least one line item.")
        @Size(max = 1000, message = "Line items cannot exceed 1000 items.")
        @Valid List<LineItemRequest> items,
        Double latitude,
        Double longitude,
        @Size(max = 64)
        String customDocumentNumber
) {
    public record BuyerRequest(
            @Size(max = 255)
            String legalName,
            @Size(max = 32)
            String tin,
            @Size(max = 64)
            String idNumber,
            @Size(max = 32)
            String idType,
            @Size(max = 32)
            String phone,
            @Size(max = 128)
            String email,
            @Size(max = 64)
            String region,
            @Size(max = 64)
            String woreda,
            @Size(max = 64)
            String kebele,
            @Size(max = 64)
            String houseNo
    ) {}

    public record LineItemRequest(
            @NotBlank(message = "Item code is mandatory.")
            @Size(max = 64)
            String itemCode,
            @NotBlank(message = "Product description is mandatory.")
            @Size(max = 255)
            String productDescription,
            @Size(max = 32)
            String natureOfSupplies,
            @Size(max = 32)
            String unit,
            @NotNull(message = "Quantity is mandatory.")
            @Positive(message = "Quantity must be greater than zero.")
            BigDecimal quantity,
            @NotNull(message = "Unit price is mandatory.")
            @Positive(message = "Unit price must be greater than zero.")
            BigDecimal unitPrice,
            @PositiveOrZero(message = "Discount must be positive or zero.")
            BigDecimal discount,
            @Size(max = 32)
            String taxCode,
            @PositiveOrZero(message = "Excise rate must be positive or zero.")
            BigDecimal exciseRate
    ) {}
}
