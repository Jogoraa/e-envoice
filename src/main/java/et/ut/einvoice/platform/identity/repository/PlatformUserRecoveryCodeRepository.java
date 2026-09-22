package et.ut.einvoice.platform.identity.repository;

import et.ut.einvoice.platform.identity.domain.PlatformUserRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlatformUserRecoveryCodeRepository extends JpaRepository<PlatformUserRecoveryCode, UUID> {

    List<PlatformUserRecoveryCode> findByUserId(UUID userId);

    List<PlatformUserRecoveryCode> findByUserIdAndUsedFalse(UUID userId);

    void deleteByUserId(UUID userId);
}
