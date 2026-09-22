package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.PlatformAccountVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformAccountVerificationRepository extends JpaRepository<PlatformAccountVerification, UUID> {

    Optional<PlatformAccountVerification> findByTokenHash(String tokenHash);

    List<PlatformAccountVerification> findByUserIdAndVerificationTypeAndUsedFalseAndExpiresAtAfter(
            UUID userId, String verificationType, Instant now);

    void deleteByUserIdAndVerificationType(UUID userId, String verificationType);
}
