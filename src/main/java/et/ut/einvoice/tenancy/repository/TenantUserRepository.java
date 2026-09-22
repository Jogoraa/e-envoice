package et.ut.einvoice.tenancy.repository;

import et.ut.einvoice.tenancy.domain.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<TenantUser> findByTenantIdAndEmail(UUID tenantId, String email);

    List<TenantUser> findAllByTenantId(UUID tenantId);

    List<TenantUser> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);
}
