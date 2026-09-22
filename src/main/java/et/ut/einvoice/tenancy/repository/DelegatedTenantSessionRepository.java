package et.ut.einvoice.tenancy.repository;

import et.ut.einvoice.tenancy.domain.DelegatedTenantSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DelegatedTenantSessionRepository extends JpaRepository<DelegatedTenantSessionEntity, UUID> {

    Optional<DelegatedTenantSessionEntity> findBySessionId(UUID sessionId);

    List<DelegatedTenantSessionEntity> findByTargetTenantIdAndIsRevokedFalse(UUID targetTenantId);

    boolean existsBySessionIdAndIsRevokedFalseAndExpiresAtAfter(UUID sessionId, Instant now);
}
