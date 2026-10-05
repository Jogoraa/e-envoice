package et.ut.einvoice.compliance.repository;

import et.ut.einvoice.compliance.domain.ExemptSectorAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExemptSectorAuthorizationRepository extends JpaRepository<ExemptSectorAuthorization, UUID> {
    Optional<ExemptSectorAuthorization> findByTenantId(UUID tenantId);
    Optional<ExemptSectorAuthorization> findByTenantIdAndStatus(UUID tenantId, String status);
}
