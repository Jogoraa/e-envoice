package et.ut.einvoice.webhooks.controller;

import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.webhooks.domain.OutboundWebhookDelivery;
import et.ut.einvoice.webhooks.domain.WebhookSubscription;
import et.ut.einvoice.webhooks.repository.OutboundWebhookDeliveryRepository;
import et.ut.einvoice.webhooks.repository.WebhookSubscriptionRepository;
import et.ut.einvoice.webhooks.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/master/webhooks")
@Tag(name = "Master Webhook Oversight", description = "Platform control plane for monitoring merchant webhook endpoints and delivery pipelines")
@PreAuthorize("hasAnyRole('ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_ADMIN')")
public class MasterWebhookOversightController {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final OutboundWebhookDeliveryRepository deliveryRepository;
    private final TenantRepository tenantRepository;
    private final WebhookService webhookService;

    public MasterWebhookOversightController(
            WebhookSubscriptionRepository subscriptionRepository,
            OutboundWebhookDeliveryRepository deliveryRepository,
            TenantRepository tenantRepository,
            WebhookService webhookService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.tenantRepository = tenantRepository;
        this.webhookService = webhookService;
    }

    public record WebhookSubscriptionDto(
            UUID id,
            UUID tenantId,
            String tenantName,
            String targetUrl,
            String subscribedEvents,
            boolean isActive,
            Instant createdAt
    ) {}

    public record WebhookDeliveryLogDto(
            UUID id,
            UUID tenantId,
            String tenantName,
            String eventType,
            String targetUrl,
            String status,
            int attempts,
            String lastError,
            Instant createdAt,
            Instant deliveredAt
    ) {}

    @GetMapping("/subscriptions")
    @Operation(summary = "List all active webhook subscriptions across all tenants")
    @Transactional(readOnly = true)
    public ResponseEntity<List<WebhookSubscriptionDto>> listAllSubscriptions() {
        List<WebhookSubscription> list = subscriptionRepository.findAll();
        List<Tenant> tenants = tenantRepository.findAll();
        Map<UUID, String> tenantMap = new HashMap<>();
        tenants.forEach(t -> tenantMap.put(t.getId(), t.getLegalName()));

        List<WebhookSubscriptionDto> dtos = list.stream().map(s -> new WebhookSubscriptionDto(
                s.getId(),
                s.getTenantId(),
                tenantMap.getOrDefault(s.getTenantId(), "Unknown"),
                s.getTargetUrl(),
                s.getSubscribedEvents(),
                s.isActive(),
                s.getCreatedAt()
        )).toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Get recent webhook delivery logs across all tenants with status and errors")
    @Transactional(readOnly = true)
    public ResponseEntity<List<WebhookDeliveryLogDto>> listRecentDeliveries(
            @RequestParam(name = "status", required = false) String status
    ) {
        Page<OutboundWebhookDelivery> page = deliveryRepository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Tenant> tenants = tenantRepository.findAll();
        Map<UUID, String> tenantMap = new HashMap<>();
        tenants.forEach(t -> tenantMap.put(t.getId(), t.getLegalName()));

        List<WebhookDeliveryLogDto> dtos = page.getContent().stream()
                .filter(d -> status == null || status.isBlank() || status.equalsIgnoreCase(d.getStatus()))
                .map(d -> new WebhookDeliveryLogDto(
                        d.getId(),
                        d.getTenantId(),
                        tenantMap.getOrDefault(d.getTenantId(), "Unknown"),
                        d.getEventType(),
                        d.getTargetUrl(),
                        d.getStatus(),
                        d.getAttempts(),
                        d.getLastError(),
                        d.getCreatedAt(),
                        d.getDeliveredAt()
                )).toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/deliveries/{id}/retry")
    @Operation(summary = "Retry a failed webhook delivery attempt")
    @Transactional
    public ResponseEntity<Map<String, Object>> retryDelivery(@PathVariable("id") UUID id) {
        OutboundWebhookDelivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Delivery log not found with ID: " + id));

        webhookService.deliverSingleWebhook(delivery);
        deliveryRepository.save(delivery);

        return ResponseEntity.ok(Map.of(
                "deliveryId", delivery.getId(),
                "newStatus", delivery.getStatus(),
                "attempts", delivery.getAttempts(),
                "message", "Delivery re-executed."
        ));
    }
}
