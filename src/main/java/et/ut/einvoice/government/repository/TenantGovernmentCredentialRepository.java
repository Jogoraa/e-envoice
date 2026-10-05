package et.ut.einvoice.government.repository;

import et.ut.einvoice.government.domain.TenantGovernmentCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantGovernmentCredentialRepository extends JpaRepository<TenantGovernmentCredential, UUID> {
    Optional<TenantGovernmentCredential> findByTenantId(UUID tenantId);
    Optional<TenantGovernmentCredential> findBySellerTin(String sellerTin);
}
