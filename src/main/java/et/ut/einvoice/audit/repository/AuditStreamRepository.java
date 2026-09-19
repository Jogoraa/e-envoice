package et.ut.einvoice.audit.repository;

import et.ut.einvoice.audit.domain.AuditStream;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditStreamRepository extends JpaRepository<AuditStream, AuditStream.AuditStreamId> {

    Optional<AuditStream> findByTenantIdAndStreamId(UUID tenantId, String streamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuditStream s WHERE s.tenantId = :tenantId AND s.streamId = :streamId")
    Optional<AuditStream> findWithLock(@Param("tenantId") UUID tenantId, @Param("streamId") String streamId);
}
