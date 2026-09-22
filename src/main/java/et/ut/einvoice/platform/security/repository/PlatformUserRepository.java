package et.ut.einvoice.platform.security.repository;

import et.ut.einvoice.platform.security.domain.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByUsername(String username);

    Optional<PlatformUser> findByEmail(String email);

    Optional<PlatformUser> findByUsernameOrEmail(String username, String email);

    boolean existsByRole(String role);
}
