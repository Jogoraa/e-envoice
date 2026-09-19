package et.ut.einvoice.metering.repository;

import et.ut.einvoice.metering.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    List<UsageRecord> findByTenantId(UUID tenantId);
}
