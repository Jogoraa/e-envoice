package et.ut.einvoice.catalog.repository;

import et.ut.einvoice.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Product> findByTenantIdAndItemCode(UUID tenantId, String itemCode);

    Optional<Product> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    boolean existsByTenantIdAndItemCode(UUID tenantId, String itemCode);

    List<Product> findByTenantIdOrderByItemCodeAsc(UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND " +
           "(:branchId IS NULL OR p.branchId = :branchId OR p.branchId IS NULL) AND " +
           "(:query IS NULL OR LOWER(p.itemCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " (p.barcode IS NOT NULL AND LOWER(p.barcode) LIKE LOWER(CONCAT('%', :query, '%')))) AND " +
           "(:categoryCode IS NULL OR p.categoryCode = :categoryCode) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive)")
    Page<Product> searchProducts(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("query") String query,
            @Param("categoryCode") String categoryCode,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
