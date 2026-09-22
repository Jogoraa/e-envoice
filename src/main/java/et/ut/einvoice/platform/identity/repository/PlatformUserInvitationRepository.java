package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.PlatformUserInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformUserInvitationRepository extends JpaRepository<PlatformUserInvitation, UUID> {

    Optional<PlatformUserInvitation> findByTokenHash(String tokenHash);

    List<PlatformUserInvitation> findByEmail(String email);

    List<PlatformUserInvitation> findByStatus(String status);
}
