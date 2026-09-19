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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
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

    public OutboxRelayWorker(
            OutboxEventRepository outboxRepository,
            OutboxService outboxService,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            GovernmentRegistrationProvider governmentProvider,
            DomainEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxService = outboxService;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.governmentProvider = governmentProvider;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
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
        et.ut.einvoice.platform.context.TenantContextHolder.setContext(
                et.ut.einvoice.platform.context.TenantContext.create(event.getTenantId(), "outbox-worker", java.util.Set.of("ROLE_TENANT_ADMIN"))
        );
        try {
            if ("INVOICE_REGISTRATION".equals(event.getEventType())) {
                UUID invoiceId = UUID.fromString(event.getAggregateId());
                Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
                if (invoice == null) {
                    outboxService.markFailed(event.getId(), "Invoice not found: " + invoiceId, event.getAttemptCount() + 1);
                    return;
                }

                // Idempotent recovery: do not re-submit if already registered
                if (invoice.getStatus() == et.ut.einvoice.invoicing.domain.InvoiceStatus.REGISTERED) {
                    log.info("Invoice {} is already registered with IRN {}. Marking outbox event published.", invoiceId, invoice.getIrn());
                    outboxService.markPublished(event.getId());
                    return;
                }

                TaxpayerProfile seller = taxpayerProfileRepository.findById(event.getTenantId()).orElse(null);
                if (seller == null) {
                    outboxService.markFailed(event.getId(), "Taxpayer profile not found for tenant: " + event.getTenantId(), event.getAttemptCount() + 1);
                    return;
                }

                // External HTTP call executed outside any database transaction
                var result = governmentProvider.registerInvoice(invoice, seller, "bearer-token");
                if (result.success()) {
                    invoice.markRegistered(result.irn(), result.rrn(), result.ackDate(), result.signedQr(), result.signedInvoice());
                    invoiceRepository.save(invoice);
                    outboxService.markPublished(event.getId());

                    // Publish async notification event
                    eventPublisher.publish(new InvoiceRegisteredEvent(
                            invoice.getId(),
                            invoice.getTenantId(),
                            invoice.getIrn(),
                            invoice.getBuyerEmail(),
                            invoice.getBuyerPhone()
                    ));
                } else {
                    log.warn("EIRS registration failed for invoice {}: {}", invoiceId, result.errorMessage());
                    outboxService.markFailed(event.getId(), result.errorMessage(), event.getAttemptCount() + 1);
                }
            } else {
                // Other generic integration events
                outboxService.markPublished(event.getId());
            }
        } finally {
            et.ut.einvoice.platform.context.TenantContextHolder.clear();
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
