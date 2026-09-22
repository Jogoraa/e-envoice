package et.ut.einvoice.tenancy.repository;

import et.ut.einvoice.tenancy.domain.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByClientId(String clientId);
    Optional<ApiClient> findByTenantIdAndClientId(UUID tenantId, String clientId);
    List<ApiClient> findByTenantId(UUID tenantId);
}
