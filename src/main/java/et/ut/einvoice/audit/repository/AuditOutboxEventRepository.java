package et.ut.einvoice.audit.repository;

import et.ut.einvoice.audit.domain.AuditOutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditOutboxEventRepository extends JpaRepository<AuditOutboxEvent, UUID> {

    @Query("SELECT e FROM AuditOutboxEvent e WHERE e.status = 'PENDING' AND e.nextAttemptAt <= :now ORDER BY e.createdAt ASC")
    List<AuditOutboxEvent> findPendingEvents(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE AuditOutboxEvent e SET e.status = 'PENDING', e.nextAttemptAt = :now WHERE e.status = 'IN_FLIGHT' AND e.nextAttemptAt <= :cutoff")
    int recoverStaleInFlightEvents(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    Optional<AuditOutboxEvent> findByAuditEventId(UUID auditEventId);

    List<AuditOutboxEvent> findByStatusOrderByCreatedAtAsc(AuditOutboxEvent.OutboxStatus status);

    List<AuditOutboxEvent> findAllByTenantIdAndStreamIdOrderBySequenceNumberAsc(UUID tenantId, String streamId);

    long countByStatus(AuditOutboxEvent.OutboxStatus status);
}
