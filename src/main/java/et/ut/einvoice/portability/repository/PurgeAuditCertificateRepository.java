package et.ut.einvoice.portability.repository;

import et.ut.einvoice.portability.domain.PurgeAuditCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurgeAuditCertificateRepository extends JpaRepository<PurgeAuditCertificate, UUID> {
    Optional<PurgeAuditCertificate> findByCertificateNumber(String certificateNumber);
    Optional<PurgeAuditCertificate> findByTenantId(UUID tenantId);
}
