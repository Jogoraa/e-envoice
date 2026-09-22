package et.ut.einvoice.notifications.repository;

import et.ut.einvoice.notifications.domain.NotificationOptOut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationOptOutRepository extends JpaRepository<NotificationOptOut, UUID> {

    Optional<NotificationOptOut> findByTenantIdAndPhoneAndOptOutType(UUID tenantId, String phone, String optOutType);

    boolean existsByTenantIdAndPhoneAndOptOutType(UUID tenantId, String phone, String optOutType);
}
