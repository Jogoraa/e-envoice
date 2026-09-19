package et.ut.einvoice.audit.repository;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditCheckpointRepository extends JpaRepository<AuditCheckpoint, UUID> {

    @Query("SELECT c FROM AuditCheckpoint c WHERE c.tenantId = :tenantId AND c.streamId = :streamId ORDER BY c.lastEventSequence DESC LIMIT 1")
    Optional<AuditCheckpoint> findLatestCheckpoint(@Param("tenantId") UUID tenantId, @Param("streamId") String streamId);

    List<AuditCheckpoint> findByTenantIdAndStreamIdOrderByLastEventSequenceDesc(UUID tenantId, String streamId);

    List<AuditCheckpoint> findAllByTenantIdAndStreamIdOrderByLastEventSequenceAsc(UUID tenantId, String streamId);
}
