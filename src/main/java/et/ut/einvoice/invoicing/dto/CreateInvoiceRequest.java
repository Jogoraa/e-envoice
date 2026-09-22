package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        String customDocumentNumber,
        Boolean saveCustomerToMaster
) {
    public CreateInvoiceRequest(
            TransactionType transactionType,
            String paymentMode,
            String paymentTerm,
            BuyerRequest buyer,
            List<LineItemRequest> items,
            Double latitude,
            Double longitude,
            String customDocumentNumber
    ) {
        this(transactionType, paymentMode, paymentTerm, buyer, items, latitude, longitude, customDocumentNumber, false);
    }

    public record BuyerRequest(
            @Size(max = 255)
            String legalName,

            @Pattern(regexp = "^$|^\\d{10}$", message = "Buyer TIN must contain exactly 10 numeric digits if provided.")
            @Size(max = 32)
            String tin,

            @Pattern(regexp = "^$|^[0-9a-zA-Z]{6,32}$", message = "VAT number must contain 6 to 32 alphanumeric characters.")
            @Size(max = 32)
            String vatNumber,

            @Size(max = 64)
            String idNumber,

            @Size(max = 32)
            String idType,

            @Pattern(regexp = "^$|^(\\+251|0)(9|7)\\d{8}$|^(\\+251|0)[1-5]\\d{7,8}$", message = "Invalid Ethiopian phone format.")
            @Size(max = 32)
            String phone,

            @Pattern(regexp = "^$|^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format.")
            @Size(max = 128)
            String email,
            @Size(max = 8)
            String country,
            @Size(max = 64)
            String region,
            @Size(max = 64)
            String city,
            @Size(max = 64)
            String zone,
            @Size(max = 64)
            String woreda,
            @Size(max = 64)
            String kebele,
            @Size(max = 64)
            String houseNo
    ) {
        public BuyerRequest(
                String legalName,
                String tin,
                String vatNumber,
                String idNumber,
                String idType,
                String phone,
                String email,
                String country,
                String region,
                String city
        ) {
            this(legalName, tin, vatNumber, idNumber, idType, phone, email, country, region, city, null, null, null, null);
        }

        public String normalizedTin() {
            if (tin == null || tin.trim().isBlank()) return null;
            return tin.trim();
        }
    }

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
