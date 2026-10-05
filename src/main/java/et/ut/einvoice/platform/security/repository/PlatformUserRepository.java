package et.ut.einvoice.platform.security.repository;

import et.ut.einvoice.platform.security.domain.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT u FROM PlatformUser u WHERE LOWER(u.username) = LOWER(:identifier) OR LOWER(u.email) = LOWER(:identifier)")
    Optional<PlatformUser> findByIdentifier(@org.springframework.data.repository.query.Param("identifier") String identifier);

    Optional<PlatformUser> findByUsername(String username);

    Optional<PlatformUser> findByEmail(String email);

    Optional<PlatformUser> findByUsernameIgnoreCase(String username);

    Optional<PlatformUser> findByEmailIgnoreCase(String email);

    Optional<PlatformUser> findByUsernameOrEmail(String username, String email);

    Optional<PlatformUser> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByRole(String role);
}

