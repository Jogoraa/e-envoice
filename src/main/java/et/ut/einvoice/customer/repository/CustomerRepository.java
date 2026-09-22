package et.ut.einvoice.customer.repository;

import et.ut.einvoice.customer.domain.Customer;
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
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndTin(UUID tenantId, String tin);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenantId = :tenantId
          AND c.status = 'ACTIVE'
          AND (
              LOWER(c.legalName) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(c.tin) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%'))
              OR (c.tradeName IS NOT NULL AND LOWER(c.tradeName) LIKE LOWER(CONCAT('%', :query, '%')))
          )
        ORDER BY c.legalName ASC
    """)
    Page<Customer> searchCustomers(
            @Param("tenantId") UUID tenantId,
            @Param("query") String query,
            Pageable pageable
    );

    Page<Customer> findByTenantId(UUID tenantId, Pageable pageable);

    List<Customer> findByTenantId(UUID tenantId);

    List<Customer> findByTenantIdAndStatusOrderByLegalNameAsc(UUID tenantId, String status);
}
