package et.ut.einvoice.platform.outbox.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.events.DomainEventPublisher;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxEventRepository outboxRepository;
    private final OutboxService outboxService;
    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final GovernmentRegistrationProvider governmentProvider;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final et.ut.einvoice.government.service.AuthoritativeGovernmentSubmissionService authoritativeGovernmentSubmissionService;

    public OutboxRelayWorker(
            OutboxEventRepository outboxRepository,
            OutboxService outboxService,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            GovernmentRegistrationProvider governmentProvider,
            DomainEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            et.ut.einvoice.government.service.AuthoritativeGovernmentSubmissionService authoritativeGovernmentSubmissionService
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxService = outboxService;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.governmentProvider = governmentProvider;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.authoritativeGovernmentSubmissionService = authoritativeGovernmentSubmissionService;
    }

    public OutboxEventRepository getOutboxRepository() {
        return outboxRepository;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:2000}")
    public void processOutboxEvents() {
        // Recover stuck IN_FLIGHT events from dead or crashed worker instances (>60s stale)
        outboxService.recoverStaleInFlight(java.time.Duration.ofSeconds(60));

        List<OutboxEvent> pending = outboxService.claimPendingEvents(50);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events...", pending.size());
        for (OutboxEvent event : pending) {
            try {
                processSingleEvent(event);
            } catch (Exception ex) {
                log.error("Unhandled error processing outbox event {}: {}", event.getId(), ex.getMessage(), ex);
                outboxService.markFailed(event.getId(), ex.getMessage(), event.getAttemptCount() + 1);
            }
        }
    }

    public void processSingleEvent(OutboxEvent event) {
        if ("INVOICE_REGISTRATION".equals(event.getEventType())) {
            UUID invoiceId = UUID.fromString(event.getAggregateId());
            authoritativeGovernmentSubmissionService.executeAuthoritativeSubmission(invoiceId, event.getId(), event.getTenantId());
        } else {
            outboxService.markPublished(event.getId());
        }
    }

    public record InvoiceRegisteredEvent(
            UUID invoiceId,
            UUID tenantId,
            String irn,
            String buyerEmail,
            String buyerPhone
    ) {}
}
