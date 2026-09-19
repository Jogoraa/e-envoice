package et.ut.einvoice.platform.outbox.service;

import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository repository;
    private final InsaDigitalSignatureService signatureService;

    public OutboxService(OutboxEventRepository repository, InsaDigitalSignatureService signatureService) {
        this.repository = repository;
        this.signatureService = signatureService;
    }

    /**
     * Enqueues an outbox event within the current active database transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent enqueueEvent(UUID tenantId, String aggregateType, String aggregateId, String eventType, String payloadJson) {
        String payloadHash = signatureService.computeSha256Hash(payloadJson != null ? payloadJson : "");
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                tenantId,
                aggregateType,
                aggregateId,
                eventType,
                payloadJson,
                payloadHash
        );
        OutboxEvent saved = repository.save(event);
        log.info("Enqueued Outbox Event {} [Type: {}, Aggregate: {}] for Tenant {}",
                saved.getId(), eventType, aggregateId, tenantId);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        repository.findById(eventId).ifPresent(e -> {
            e.markPublished();
            repository.save(e);
            log.info("Outbox event {} published successfully", eventId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String errorMessage, int currentAttempt) {
        repository.findById(eventId).ifPresent(e -> {
            long delaySeconds = (long) Math.min(300, Math.pow(2, currentAttempt) * 2);
            Instant nextRetry = Instant.now().plus(Duration.ofSeconds(delaySeconds));
            e.markFailed(errorMessage, nextRetry);
            repository.save(e);
            log.warn("Outbox event {} failed (attempt {}), next retry at {}: {}", eventId, currentAttempt, nextRetry, errorMessage);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public java.util.List<OutboxEvent> claimPendingEvents(int limit) {
        java.util.List<OutboxEvent> events = repository.findPendingEventsLocked(Instant.now(), org.springframework.data.domain.PageRequest.of(0, limit));
        for (OutboxEvent event : events) {
            event.markInFlight();
        }
        return repository.saveAll(events);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleInFlight(Duration staleDuration) {
        Instant staleThreshold = Instant.now().minus(staleDuration);
        int recovered = repository.recoverStaleInFlightEvents(staleThreshold, Instant.now());
        if (recovered > 0) {
            log.warn("Stale worker recovery: reset {} stuck IN_FLIGHT events back to PENDING", recovered);
        }
        return recovered;
    }
}
