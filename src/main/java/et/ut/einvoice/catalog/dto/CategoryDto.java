package et.ut.einvoice.catalog.dto;

import et.ut.einvoice.catalog.domain.Category;
import java.time.Instant;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        String code,
        String name,
        String categoryType,
        String description,
        String status,
        Instant createdAt
) {
    public static CategoryDto fromEntity(Category c) {
        return new CategoryDto(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getCategoryType(),
                c.getDescription(),
                c.getStatus(),
                c.getCreatedAt()
        );
    }
}
