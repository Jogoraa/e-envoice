package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.SystemPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemPermissionRepository extends JpaRepository<SystemPermission, UUID> {

    Optional<SystemPermission> findByCode(String code);

    List<SystemPermission> findByCategory(String category);

    boolean existsByCode(String code);
}
