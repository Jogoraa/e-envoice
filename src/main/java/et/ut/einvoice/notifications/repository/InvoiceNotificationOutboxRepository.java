package et.ut.einvoice.notifications.repository;

import et.ut.einvoice.notifications.domain.FailureClassification;
import et.ut.einvoice.notifications.domain.InvoiceNotificationOutbox;
import et.ut.einvoice.notifications.domain.NotificationStatus;
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
public interface InvoiceNotificationOutboxRepository extends JpaRepository<InvoiceNotificationOutbox, UUID> {

    Optional<InvoiceNotificationOutbox> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<InvoiceNotificationOutbox> findByProviderAndProviderMessageId(String provider, String providerMessageId);

    List<InvoiceNotificationOutbox> findAllByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);

    @Query("""
        SELECT o FROM InvoiceNotificationOutbox o
        WHERE o.status IN (et.ut.einvoice.notifications.domain.NotificationStatus.PENDING, et.ut.einvoice.notifications.domain.NotificationStatus.RETRY_SCHEDULED)
          AND o.nextAttemptAt <= :now
        ORDER BY o.nextAttemptAt ASC
    """)
    List<InvoiceNotificationOutbox> findPendingBatch(@Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT o FROM InvoiceNotificationOutbox o
        WHERE o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedAt < :staleThreshold
    """)
    List<InvoiceNotificationOutbox> findStaleInFlight(@Param("staleThreshold") Instant staleThreshold);

    @Query("""
        SELECT o FROM InvoiceNotificationOutbox o
        WHERE o.status = et.ut.einvoice.notifications.domain.NotificationStatus.SUBMISSION_UNKNOWN
          AND o.nextAttemptAt <= :now
    """)
    List<InvoiceNotificationOutbox> findUnknownSubmissions(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.status = et.ut.einvoice.notifications.domain.NotificationStatus.PENDING,
            o.lockedAt = null,
            o.lockedBy = null
        WHERE o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedAt < :staleThreshold
    """)
    int releaseStaleLeases(@Param("staleThreshold") Instant staleThreshold);

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT,
            o.lockedAt = :now,
            o.lockedBy = :workerId
        WHERE o.id = :id
          AND (o.status = et.ut.einvoice.notifications.domain.NotificationStatus.PENDING 
               OR o.status = et.ut.einvoice.notifications.domain.NotificationStatus.RETRY_SCHEDULED)
    """)
    int claimLeaseAtomic(@Param("id") UUID id, @Param("workerId") String workerId, @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.status = et.ut.einvoice.notifications.domain.NotificationStatus.SUBMITTED,
            o.providerMessageId = :providerMessageId,
            o.submittedAt = :submittedAt,
            o.lockedAt = null,
            o.lockedBy = null
        WHERE o.id = :id
          AND o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedBy = :workerId
    """)
    int completeSubmissionAtomic(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("providerMessageId") String providerMessageId,
            @Param("submittedAt") Instant submittedAt
    );

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.status = et.ut.einvoice.notifications.domain.NotificationStatus.PERMANENTLY_FAILED,
            o.lastErrorCode = :errorCode,
            o.lastErrorMessage = :errorMessage,
            o.failureClassification = :classification,
            o.failedAt = :failedAt,
            o.lockedAt = null,
            o.lockedBy = null
        WHERE o.id = :id
          AND o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedBy = :workerId
    """)
    int completePermanentFailureAtomic(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("classification") FailureClassification classification,
            @Param("failedAt") Instant failedAt
    );

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.status = et.ut.einvoice.notifications.domain.NotificationStatus.RETRY_SCHEDULED,
            o.nextAttemptAt = :nextAttemptAt,
            o.lastErrorCode = :errorCode,
            o.lastErrorMessage = :errorMessage,
            o.failureClassification = :classification,
            o.attemptCount = o.attemptCount + 1,
            o.lockedAt = null,
            o.lockedBy = null
        WHERE o.id = :id
          AND o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedBy = :workerId
    """)
    int completeRetryScheduledAtomic(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("classification") FailureClassification classification
    );

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.status = et.ut.einvoice.notifications.domain.NotificationStatus.SUBMISSION_UNKNOWN,
            o.lastErrorCode = :errorCode,
            o.lastErrorMessage = :errorMessage,
            o.failureClassification = et.ut.einvoice.notifications.domain.FailureClassification.PROVIDER_UNKNOWN_OUTCOME,
            o.lockedAt = null,
            o.lockedBy = null
        WHERE o.id = :id
          AND o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedBy = :workerId
    """)
    int completeSubmissionUnknownAtomic(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    @Modifying
    @Query("""
        UPDATE InvoiceNotificationOutbox o
        SET o.lockedAt = null,
            o.lockedBy = null
        WHERE o.id = :id
          AND o.status = et.ut.einvoice.notifications.domain.NotificationStatus.IN_FLIGHT
          AND o.lockedBy = :workerId
    """)
    int releaseLeaseAtomic(@Param("id") UUID id, @Param("workerId") String workerId);

    long countByStatus(NotificationStatus status);
}
