package et.ut.einvoice.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateServiceRequest(
        UUID branchId,

        @NotBlank(message = "Service code is mandatory")
        @Size(min = 1, max = 64)
        String serviceCode,

        @NotBlank(message = "Service name is mandatory")
        @Size(min = 1, max = 128)
        String name,

        String description,

        UUID categoryId,

        String categoryCode,

        String unit,

        @NotNull(message = "Unit price is mandatory")
        @PositiveOrZero(message = "Unit price cannot be negative")
        BigDecimal unitPrice,

        String taxClassification
) {}
