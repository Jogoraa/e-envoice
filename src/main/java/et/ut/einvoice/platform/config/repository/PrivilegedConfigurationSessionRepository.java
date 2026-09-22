package et.ut.einvoice.platform.config.repository;

import et.ut.einvoice.platform.config.domain.PrivilegedConfigurationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivilegedConfigurationSessionRepository extends JpaRepository<PrivilegedConfigurationSession, UUID> {

    Optional<PrivilegedConfigurationSession> findByIdAndRevokedFalseAndExpiresAtAfter(UUID id, Instant now);

    @Modifying
    @Query("UPDATE PrivilegedConfigurationSession s SET s.revoked = true, s.revocationReason = :reason WHERE s.userId = :userId AND s.revoked = false")
    int revokeAllActiveSessionsForUser(UUID userId, String reason);
}
