package et.ut.einvoice.catalog.repository;

import et.ut.einvoice.catalog.domain.Category;
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
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Category> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<Category> findByTenantIdAndStatusOrderByCodeAsc(UUID tenantId, String status);

    List<Category> findByTenantIdOrderByCodeAsc(UUID tenantId);

    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND " +
           "(:query IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.code) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:categoryType IS NULL OR c.categoryType = :categoryType OR c.categoryType = 'ALL') AND " +
           "(:status IS NULL OR c.status = :status)")
    Page<Category> searchCategories(
            @Param("tenantId") UUID tenantId,
            @Param("query") String query,
            @Param("categoryType") String categoryType,
            @Param("status") String status,
            Pageable pageable
    );
}
