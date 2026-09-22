package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.PlatformUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformUserSessionRepository extends JpaRepository<PlatformUserSession, UUID> {

    Optional<PlatformUserSession> findByTokenHash(String tokenHash);

    List<PlatformUserSession> findByUserId(UUID userId);

    List<PlatformUserSession> findByUserIdAndRevokedFalseAndExpiresAtAfter(UUID userId, Instant now);

    List<PlatformUserSession> findByRevokedFalseAndExpiresAtAfter(Instant now);
}
