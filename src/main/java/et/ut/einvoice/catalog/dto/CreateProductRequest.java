package et.ut.einvoice.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        UUID branchId,

        @NotBlank(message = "Item code is mandatory")
        @Size(min = 1, max = 64)
        String itemCode,

        @NotBlank(message = "SKU is mandatory")
        @Size(min = 1, max = 64)
        String sku,

        String barcode,

        @NotBlank(message = "Product description is mandatory")
        @Size(min = 1, max = 255)
        String description,

        UUID categoryId,

        String categoryCode,

        String unit,

        @NotNull(message = "Unit price is mandatory")
        @PositiveOrZero(message = "Unit price cannot be negative")
        BigDecimal unitPrice,

        String taxClassification,

        Boolean trackStock,

        BigDecimal stockQuantity
) {}
