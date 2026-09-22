package et.ut.einvoice.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank(message = "Category code is mandatory")
        @Size(min = 2, max = 64, message = "Category code must be between 2 and 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Category code must contain only alphanumeric characters, underscores, or hyphens")
        String code,

        @NotBlank(message = "Category name is mandatory")
        @Size(min = 2, max = 128, message = "Category name must be between 2 and 128 characters")
        String name,

        String categoryType, // 'PRODUCT', 'SERVICE', 'ALL'

        String description,

        UUID parentId
) {}
