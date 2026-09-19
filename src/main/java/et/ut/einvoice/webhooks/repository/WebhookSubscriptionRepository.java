package et.ut.einvoice.webhooks.repository;

import et.ut.einvoice.webhooks.domain.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
