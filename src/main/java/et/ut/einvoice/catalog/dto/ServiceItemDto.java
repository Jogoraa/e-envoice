package et.ut.einvoice.catalog.dto;

import et.ut.einvoice.catalog.domain.ServiceItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceItemDto(
        UUID id,
        String serviceCode,
        String name,
        String description,
        String categoryCode,
        String unit,
        BigDecimal unitPrice,
        String taxClassification,
        boolean isActive,
        Instant createdAt
) {
    public static ServiceItemDto fromEntity(ServiceItem s) {
        return new ServiceItemDto(
                s.getId(),
                s.getServiceCode(),
                s.getName(),
                s.getDescription(),
                s.getCategoryCode(),
                s.getUnit(),
                s.getUnitPrice(),
                s.getTaxClassification(),
                s.isActive(),
                s.getCreatedAt()
        );
    }
}
