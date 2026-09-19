package et.ut.einvoice.platform.outbox.repository;

import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE (o.status = 'PENDING' OR o.status = 'FAILED') AND o.nextAttemptAt <= :now ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents(@Param("now") Instant now, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints({
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
    })
    @Query("SELECT o FROM OutboxEvent o WHERE (o.status = 'PENDING' OR o.status = 'FAILED') AND o.nextAttemptAt <= :now ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEventsLocked(@Param("now") Instant now, Pageable pageable);

    List<OutboxEvent> findByTenantIdAndAggregateId(UUID tenantId, String aggregateId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent o SET o.status = et.ut.einvoice.platform.outbox.domain.OutboxStatus.PENDING, o.nextAttemptAt = :now WHERE o.status = et.ut.einvoice.platform.outbox.domain.OutboxStatus.IN_FLIGHT AND o.nextAttemptAt <= :staleThreshold")
    int recoverStaleInFlightEvents(@Param("staleThreshold") Instant staleThreshold, @Param("now") Instant now);
 
    default List<OutboxEvent> findPendingEventsWithLock(int limit) {
        return findPendingEvents(Instant.now(), org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
