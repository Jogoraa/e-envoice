package et.ut.einvoice.audit.repository;

import et.ut.einvoice.audit.domain.EvidenceManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenceManifestRepository extends JpaRepository<EvidenceManifest, UUID> {

    @Query("SELECT m FROM EvidenceManifest m WHERE m.tenantId = :tenantId AND m.artifactType = :artifactType ORDER BY m.createdAt DESC LIMIT 1")
    Optional<EvidenceManifest> findLatestManifest(@Param("tenantId") UUID tenantId, @Param("artifactType") String artifactType);

    List<EvidenceManifest> findByTenantIdAndArtifactTypeOrderByCreatedAtDesc(UUID tenantId, String artifactType);

    List<EvidenceManifest> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);
}
