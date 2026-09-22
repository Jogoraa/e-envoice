package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemRoleRepository extends JpaRepository<SystemRole, UUID> {

    Optional<SystemRole> findByCode(String code);

    List<SystemRole> findByScope(String scope);

    List<SystemRole> findByStatus(String status);

    boolean existsByCode(String code);
}
