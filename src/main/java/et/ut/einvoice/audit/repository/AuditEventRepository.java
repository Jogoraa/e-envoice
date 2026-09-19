package et.ut.einvoice.audit.repository;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.platform.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId AND a.streamId = :streamId ORDER BY a.sequenceNumber DESC LIMIT 1")
    Optional<AuditEvent> findLatestEventForTenantStream(@Param("tenantId") UUID tenantId, @Param("streamId") String streamId);

    @Query("SELECT a FROM AuditEvent a ORDER BY a.timestamp DESC LIMIT 1")
    Optional<AuditEvent> findLatestEvent();

    List<AuditEvent> findByTenantIdAndStreamIdOrderBySequenceNumberAsc(UUID tenantId, String streamId);

    List<AuditEvent> findByTenantIdAndStreamIdAndSequenceNumberBetweenOrderBySequenceNumberAsc(
            UUID tenantId, String streamId, Long startSequence, Long endSequence);

    Page<AuditEvent> findByTenantIdOrderBySequenceNumberDesc(UUID tenantId, Pageable pageable);

    Page<AuditEvent> findAllByTenantId(UUID tenantId, Pageable pageable);

    // Repository-level deletion protection
    @Override
    default void delete(AuditEvent entity) {
        throw new BusinessException("AUDIT_TRAIL_IMMUTABLE", "Repository delete is strictly forbidden on audit ledger.", HttpStatus.FORBIDDEN);
    }

    @Override
    default void deleteById(UUID id) {
        throw new BusinessException("AUDIT_TRAIL_IMMUTABLE", "Repository deleteById is strictly forbidden on audit ledger.", HttpStatus.FORBIDDEN);
    }

    @Override
    default void deleteAll() {
        throw new BusinessException("AUDIT_TRAIL_IMMUTABLE", "Repository deleteAll is strictly forbidden on audit ledger.", HttpStatus.FORBIDDEN);
    }

    @Override
    default void deleteAll(Iterable<? extends AuditEvent> entities) {
        throw new BusinessException("AUDIT_TRAIL_IMMUTABLE", "Repository deleteAll is strictly forbidden on audit ledger.", HttpStatus.FORBIDDEN);
    }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) {
        throw new BusinessException("AUDIT_TRAIL_IMMUTABLE", "Repository deleteAllById is strictly forbidden on audit ledger.", HttpStatus.FORBIDDEN);
    }
}
