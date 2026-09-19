package et.ut.einvoice.platform.idempotency.repository;

import et.ut.einvoice.platform.idempotency.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByTenantIdAndClientIdAndIdempotencyKey(UUID tenantId, String clientId, String idempotencyKey);
    Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
