package et.ut.einvoice.catalog.repository;

import et.ut.einvoice.catalog.domain.ServiceItem;
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
public interface ServiceItemRepository extends JpaRepository<ServiceItem, UUID> {

    Optional<ServiceItem> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ServiceItem> findByTenantIdAndServiceCode(UUID tenantId, String serviceCode);

    boolean existsByTenantIdAndServiceCode(UUID tenantId, String serviceCode);

    List<ServiceItem> findByTenantIdOrderByServiceCodeAsc(UUID tenantId);

    @Query("SELECT s FROM ServiceItem s WHERE s.tenantId = :tenantId AND " +
           "(:branchId IS NULL OR s.branchId = :branchId OR s.branchId IS NULL) AND " +
           "(:query IS NULL OR LOWER(s.serviceCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(s.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:categoryCode IS NULL OR s.categoryCode = :categoryCode) AND " +
           "(:isActive IS NULL OR s.isActive = :isActive)")
    Page<ServiceItem> searchServices(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("query") String query,
            @Param("categoryCode") String categoryCode,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
