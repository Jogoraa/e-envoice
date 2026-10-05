package et.ut.einvoice.purchasevoucher.dto;

import et.ut.einvoice.purchasevoucher.domain.UnavailableReceiptReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreatePurchaseVoucherRequest(
        @NotBlank(message = "Supplier name is mandatory")
        String supplierName,

        String supplierTin,
        String supplierIdNumber,
        String supplierIdType,
        String supplierPhone,
        String supplierAddress,

        @NotNull(message = "Unavailable receipt reason is mandatory")
        UnavailableReceiptReason unavailableReason,

        String reasonDescription,

        Instant transactionDate,

        String currency,

        String attachmentReference,

        BigDecimal withholdingAmount,

        @NotEmpty(message = "At least one item line is required")
        List<@Valid LineItemDto> items
) {
    public record LineItemDto(
            @NotBlank(message = "Item description is mandatory")
            String itemDescription,

            @NotNull(message = "Quantity is mandatory")
            @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
            BigDecimal quantity,

            @NotBlank(message = "Unit of measure is mandatory")
            String unitOfMeasure,

            @NotNull(message = "Unit price is mandatory")
            @DecimalMin(value = "0.01", message = "Unit price must be positive")
            BigDecimal unitPrice,

            BigDecimal taxRate
    ) {}
}
