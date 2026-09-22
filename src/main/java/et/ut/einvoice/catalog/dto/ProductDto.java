package et.ut.einvoice.catalog.dto;

import et.ut.einvoice.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductDto(
        UUID id,
        UUID tenantId,
        UUID branchId,
        String itemCode,
        String sku,
        String barcode,
        String description,
        UUID categoryId,
        String categoryCode,
        String unit,
        BigDecimal unitPrice,
        String taxClassification,
        boolean isActive,
        boolean trackStock,
        BigDecimal stockQuantity,
        BigDecimal minStockLevel,
        Instant createdAt
) {
    public static ProductDto fromEntity(Product p) {
        return new ProductDto(
                p.getId(),
                p.getTenantId(),
                p.getBranchId(),
                p.getItemCode(),
                p.getSku(),
                p.getBarcode(),
                p.getDescription(),
                p.getCategoryId(),
                p.getCategoryCode(),
                p.getUnit(),
                p.getUnitPrice(),
                p.getTaxClassification(),
                p.isActive(),
                p.isTrackStock(),
                p.getStockQuantity(),
                p.getMinStockLevel(),
                p.getCreatedAt()
        );
    }
}
