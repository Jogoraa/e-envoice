package et.ut.einvoice.taxpayer.repository;

import et.ut.einvoice.taxpayer.domain.TenantLifecycleEvent;
import et.ut.einvoice.taxpayer.domain.TenantLifecycleEventType;
import et.ut.einvoice.taxpayer.domain.TenantNotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantLifecycleEventRepository extends JpaRepository<TenantLifecycleEvent, UUID> {
    List<TenantLifecycleEvent> findByTenantId(UUID tenantId);
    Optional<TenantLifecycleEvent> findByTenantIdAndEventType(UUID tenantId, TenantLifecycleEventType eventType);
    List<TenantLifecycleEvent> findByNotificationStatus(TenantNotificationStatus status);
}
