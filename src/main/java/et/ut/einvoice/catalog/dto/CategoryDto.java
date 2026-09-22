package et.ut.einvoice.catalog.dto;

import et.ut.einvoice.catalog.domain.Category;
import java.time.Instant;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String categoryType,
        String description,
        UUID parentId,
        String status,
        Instant createdAt
) {
    public static CategoryDto fromEntity(Category c) {
        return new CategoryDto(
                c.getId(),
                c.getTenantId(),
                c.getCode(),
                c.getName(),
                c.getCategoryType(),
                c.getDescription(),
                c.getParentId(),
                c.getStatus(),
                c.getCreatedAt()
        );
    }
}
