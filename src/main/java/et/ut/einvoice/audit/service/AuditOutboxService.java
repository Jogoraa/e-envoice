package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.domain.AuditOutboxEvent;
import et.ut.einvoice.audit.repository.AuditOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional Audit Outbox Service providing at-least-once guaranteed delivery
 * to downstream immutable ledger stores and external SIEM systems.
 */
@Service
public class AuditOutboxService {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxService.class);

    private final AuditOutboxEventRepository outboxRepository;

    public AuditOutboxService(AuditOutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Enqueues an audit outbox event atomically within the calling business transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditOutboxEvent enqueueAuditOutbox(AuditEvent event, String canonicalPayload) {
        AuditOutboxEvent outboxEvent = new AuditOutboxEvent(
                UUID.randomUUID(),
                event.getTenantId(),
                event.getId(),
                event.getStreamId(),
                event.getSequenceNumber(),
                event.getEventHash(),
                canonicalPayload
        );
        return outboxRepository.save(outboxEvent);
    }

    /**
     * Claims pending audit events in an autonomous transaction to prevent worker contention.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<AuditOutboxEvent> claimPendingEvents(int batchSize) {
        Instant now = Instant.now();
        List<AuditOutboxEvent> pending = outboxRepository.findPendingEvents(now, PageRequest.of(0, batchSize));
        for (AuditOutboxEvent event : pending) {
            event.markInFlight();
        }
        return outboxRepository.saveAll(pending);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID outboxId) {
        outboxRepository.findById(outboxId).ifPresent(event -> {
            event.markPublished();
            outboxRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID outboxId, String error) {
        outboxRepository.findById(outboxId).ifPresent(event -> {
            long backoffMillis = (long) Math.pow(2, event.getAttemptCount()) * 1000L;
            event.markFailed(error, backoffMillis);
            outboxRepository.save(event);
            log.warn("Audit outbox event {} failed (attempt {}): {}", outboxId, event.getAttemptCount(), error);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleInFlight(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        return outboxRepository.recoverStaleInFlightEvents(cutoff, Instant.now());
    }

    @Transactional
    public void recordFailure(UUID outboxId, String error) {
        markFailed(outboxId, error);
    }

    @Transactional
    public List<AuditOutboxEvent> claimPendingBatch(int batchSize) {
        return claimPendingEvents(batchSize);
    }

    @Transactional
    public AuditOutboxEvent enqueue(AuditEvent event) {
        if (outboxRepository.findByAuditEventId(event.getId()).isPresent()) {
            return outboxRepository.findByAuditEventId(event.getId()).get();
        }
        return enqueueAuditOutbox(event, event.getPayloadJson() != null ? event.getPayloadJson() : "{}");
    }
}
