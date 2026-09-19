package et.ut.einvoice.government.reconciliation;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.domain.GovernmentSubmission;
import et.ut.einvoice.government.domain.GovernmentSubmissionStatus;
import et.ut.einvoice.government.repository.GovernmentSubmissionRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.events.DomainEventPublisher;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.platform.outbox.worker.OutboxRelayWorker;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GovernmentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(GovernmentReconciliationService.class);

    private final GovernmentSubmissionRepository submissionRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final GovernmentRegistrationProvider governmentProvider;
    private final OutboxService outboxService;
    private final OutboxEventRepository outboxEventRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditService auditService;

    public GovernmentReconciliationService(
            GovernmentSubmissionRepository submissionRepository,
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            GovernmentRegistrationProvider governmentProvider,
            OutboxService outboxService,
            OutboxEventRepository outboxEventRepository,
            DomainEventPublisher eventPublisher,
            AuditService auditService
    ) {
        this.submissionRepository = submissionRepository;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.governmentProvider = governmentProvider;
        this.outboxService = outboxService;
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${government.reconciliation.interval-ms:15000}")
    @Transactional
    public void reconcileUnknownSubmissions() {
        List<GovernmentSubmission> unknownList = submissionRepository.findByStatus(GovernmentSubmissionStatus.UNKNOWN);
        if (unknownList.isEmpty()) {
            return;
        }

        log.info("Running reconciliation on {} UNKNOWN submissions...", unknownList.size());
        for (GovernmentSubmission sub : unknownList) {
            reconcileSingleSubmission(sub);
        }
    }

    @Transactional
    public void reconcileSingleSubmission(GovernmentSubmission sub) {
        Invoice invoice = invoiceRepository.findById(sub.getInvoiceId()).orElse(null);
        if (invoice == null) {
            log.error("Reconciliation error: Invoice {} not found for submission {}", sub.getInvoiceId(), sub.getSubmissionId());
            return;
        }

        TaxpayerProfile seller = taxpayerProfileRepository.findById(sub.getTenantId()).orElse(null);
        if (seller == null) {
            log.error("Reconciliation error: Seller profile not found for tenant {}", sub.getTenantId());
            return;
        }

        // Query government verification endpoint
        var verifyResult = governmentProvider.verifySubmission(sub.getSubmissionId(), invoice.getDocumentNumber(), seller, "bearer-token");

        if (verifyResult.verified()) {
            log.info("Reconciliation SUCCESS: Submission {} was registered in MoR with IRN {}", sub.getSubmissionId(), verifyResult.irn());
            sub.markAccepted(verifyResult.irn());
            submissionRepository.save(sub);

            invoice.markRegistered(verifyResult.irn(), verifyResult.rrn(), verifyResult.ackDate(), "", "");
            invoiceRepository.save(invoice);

            // Clean up corresponding outbox event
            List<OutboxEvent> outboxEvents = outboxEventRepository.findByTenantIdAndAggregateId(sub.getTenantId(), invoice.getId().toString());
            for (OutboxEvent evt : outboxEvents) {
                outboxService.markPublished(evt.getId());
            }

            auditService.recordEvent(
                    sub.getTenantId(),
                    "RECONCILIATION",
                    "SYSTEM",
                    "RECONCILE_ACCEPTED",
                    "GOVERNMENT_SUBMISSION",
                    sub.getSubmissionId(),
                    "IRN=" + verifyResult.irn(),
                    "127.0.0.1"
            );

            eventPublisher.publish(new OutboxRelayWorker.InvoiceRegisteredEvent(
                    invoice.getId(),
                    invoice.getTenantId(),
                    invoice.getIrn(),
                    invoice.getBuyerEmail(),
                    invoice.getBuyerPhone()
            ));
        } else {
            log.warn("Reconciliation query: Submission {} not found on MoR. Resetting for controlled retry.", sub.getSubmissionId());
            sub.markNeedsReconciliation("Submission not found on MoR verification endpoint; scheduled for safe retry");
            submissionRepository.save(sub);

            auditService.recordEvent(
                    sub.getTenantId(),
                    "RECONCILIATION",
                    "SYSTEM",
                    "RECONCILE_NOT_FOUND",
                    "GOVERNMENT_SUBMISSION",
                    sub.getSubmissionId(),
                    "RESET_FOR_RETRY",
                    "127.0.0.1"
            );
        }
    }
}
