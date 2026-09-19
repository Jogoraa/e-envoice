package et.ut.einvoice.webhooks.repository;

import et.ut.einvoice.webhooks.domain.OutboundWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboundWebhookDeliveryRepository extends JpaRepository<OutboundWebhookDelivery, UUID> {
    List<OutboundWebhookDelivery> findByTenantId(UUID tenantId);
    List<OutboundWebhookDelivery> findByStatus(String status);
}
