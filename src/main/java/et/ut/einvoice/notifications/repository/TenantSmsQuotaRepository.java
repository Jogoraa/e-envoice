package et.ut.einvoice.notifications.repository;

import et.ut.einvoice.notifications.domain.TenantSmsQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TenantSmsQuotaRepository extends JpaRepository<TenantSmsQuota, UUID> {

    /**
     * Atomically consumes units if and only if usage + requestedUnits <= daily_limit.
     * Returns 1 if consumed successfully, 0 if quota exceeded or row not reset today.
     */
    @Modifying
    @Query("""
        UPDATE TenantSmsQuota q
        SET q.dailyUnitsUsed = q.dailyUnitsUsed + :requestedUnits,
            q.dailyMessagesUsed = q.dailyMessagesUsed + 1,
            q.updatedAt = :now
        WHERE q.tenantId = :tenantId
          AND q.resetDate = :today
          AND (q.dailyUnitsUsed + :requestedUnits) <= q.dailyLimit
    """)
    int tryConsumeQuotaAtomic(
            @Param("tenantId") UUID tenantId,
            @Param("requestedUnits") int requestedUnits,
            @Param("today") LocalDate today,
            @Param("now") Instant now
    );

    /**
     * Resets quota for a new day if resetDate < today.
     */
    @Modifying
    @Query("""
        UPDATE TenantSmsQuota q
        SET q.dailyUnitsUsed = 0,
            q.dailyMessagesUsed = 0,
            q.resetDate = :today,
            q.updatedAt = :now
        WHERE q.tenantId = :tenantId
          AND q.resetDate < :today
    """)
    int resetQuotaIfNewDay(
            @Param("tenantId") UUID tenantId,
            @Param("today") LocalDate today,
            @Param("now") Instant now
    );
}
