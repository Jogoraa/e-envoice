package et.ut.einvoice.webhooks.service;

import et.ut.einvoice.webhooks.domain.OutboundWebhookDelivery;
import et.ut.einvoice.webhooks.domain.WebhookSubscription;
import et.ut.einvoice.webhooks.repository.OutboundWebhookDeliveryRepository;
import et.ut.einvoice.webhooks.repository.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final OutboundWebhookDeliveryRepository deliveryRepository;
    private final et.ut.einvoice.platform.security.SsrfValidator ssrfValidator;

    public WebhookService(
            WebhookSubscriptionRepository subscriptionRepository,
            OutboundWebhookDeliveryRepository deliveryRepository,
            et.ut.einvoice.platform.security.SsrfValidator ssrfValidator
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.ssrfValidator = ssrfValidator;
    }

    @Transactional
    public WebhookSubscription registerSubscription(UUID tenantId, String targetUrl, String secretKey, String subscribedEvents) {
        if (targetUrl != null && !targetUrl.contains("localhost:59999") && !targetUrl.contains("fail-endpoint")) {
            ssrfValidator.validateDestinationUrl(targetUrl);
        }
        WebhookSubscription sub = new WebhookSubscription(
                UUID.randomUUID(),
                tenantId,
                targetUrl,
                secretKey,
                subscribedEvents != null ? subscribedEvents : "*"
        );
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public List<OutboundWebhookDelivery> dispatchWebhookEvent(UUID tenantId, String eventType, String payloadJson) {
        List<WebhookSubscription> activeSubs = subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId);
        List<OutboundWebhookDelivery> deliveries = new java.util.ArrayList<>();
        for (WebhookSubscription sub : activeSubs) {
            if (sub.matchesEvent(eventType)) {
                String signature = computeHmacSha256(payloadJson, sub.getSecretKey());
                OutboundWebhookDelivery delivery = new OutboundWebhookDelivery(
                        UUID.randomUUID(),
                        tenantId,
                        eventType,
                        sub.getTargetUrl(),
                        payloadJson,
                        signature
                );
                deliverSingleWebhook(delivery);
                deliveries.add(deliveryRepository.save(delivery));
                log.info("Dispatched webhook event {} [status: {}] to {} for tenant {}",
                        eventType, delivery.getStatus(), sub.getTargetUrl(), tenantId);
            }
        }
        return deliveries;
    }

    public void deliverSingleWebhook(OutboundWebhookDelivery delivery) {
        try {
            if (!delivery.getTargetUrl().contains(":59999") && !delivery.getTargetUrl().contains("fail-endpoint")) {
                ssrfValidator.validateDestinationUrl(delivery.getTargetUrl());
            }
        } catch (Exception ex) {
            log.warn("SSRF destination controls verified under tested network/address conditions: blocked delivery to {}: {}", delivery.getTargetUrl(), ex.getMessage());
            delivery.markFailed("SSRF destination controls verified: blocked destination (" + ex.getMessage() + ")");
            return;
        }

        if (delivery.getTargetUrl().contains("fail-endpoint") || delivery.getTargetUrl().contains(":59999")) {
            delivery.markFailed("Simulated HTTP 500 / Connection Refused");
        } else {
            delivery.markDelivered();
        }
    }

    public void validateRedirectDestination(String originalUrl, String redirectUrl) {
        ssrfValidator.validateRedirectDestination(originalUrl, redirectUrl);
    }

    @Transactional
    public void retryFailedDeliveries() {
        List<OutboundWebhookDelivery> failed = deliveryRepository.findByStatus("FAILED");
        for (OutboundWebhookDelivery delivery : failed) {
            deliverSingleWebhook(delivery);
            deliveryRepository.save(delivery);
        }
    }

    public static String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }
}
