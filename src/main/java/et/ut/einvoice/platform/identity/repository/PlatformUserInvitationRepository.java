package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.PlatformUserInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformUserInvitationRepository extends JpaRepository<PlatformUserInvitation, UUID> {

    Optional<PlatformUserInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformUserInvitation> findWithLockByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformUserInvitation> findWithLockById(UUID id);

    List<PlatformUserInvitation> findByEmail(String email);

    List<PlatformUserInvitation> findByEmailAndStatus(String email, String status);

    List<PlatformUserInvitation> findByStatus(String status);
}
