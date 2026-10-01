package et.ut.einvoice.government.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.compliance.crypto.DigitalSignatureProvider;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider.GovernmentRegistrationResult;
import et.ut.einvoice.government.domain.GovernmentSubmission;
import et.ut.einvoice.government.domain.GovernmentSubmissionStatus;
import et.ut.einvoice.government.repository.GovernmentSubmissionRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.events.DomainEventPublisher;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.platform.outbox.worker.OutboxRelayWorker.InvoiceRegisteredEvent;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Single Authoritative Government Submission Pipeline mandated by Phase 1C.
 * Eliminates competing submission routes and guarantees:
 * - One logical invoice -> One logical government submission attempt
 * - No external MoR HTTP calls inside database transactions
 * - State machine: QUEUED -> IN_FLIGHT -> ACCEPTED / REJECTED / UNKNOWN
 * - On network timeout or unhandled disconnect, records durable UNKNOWN outcome without reckless retry
 */
@Service
public class AuthoritativeGovernmentSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeGovernmentSubmissionService.class);

    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final GovernmentSubmissionRepository submissionRepository;
    private final GovernmentRegistrationProvider governmentProvider;
    private final OutboxService outboxService;
    private final DomainEventPublisher eventPublisher;
    private final DigitalSignatureProvider signatureProvider;
    private final AuditService auditService;

    public AuthoritativeGovernmentSubmissionService(
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            GovernmentSubmissionRepository submissionRepository,
            GovernmentRegistrationProvider governmentProvider,
            OutboxService outboxService,
            DomainEventPublisher eventPublisher,
            DigitalSignatureProvider signatureProvider,
            AuditService auditService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.submissionRepository = submissionRepository;
        this.governmentProvider = governmentProvider;
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
        this.signatureProvider = signatureProvider;
        this.auditService = auditService;
    }

    public record SubmissionOutcome(
            boolean successful,
            GovernmentSubmissionStatus status,
            String irn,
            String errorCode,
            String errorMessage
    ) {}

    /**
     * Executes authoritative government submission.
     * Guaranteed to execute the external HTTP call outside any open database transaction.
     */
    public SubmissionOutcome executeAuthoritativeSubmission(UUID invoiceId, UUID outboxEventId, UUID tenantId) {
        TenantContext previousContext = TenantContextHolder.getContext();
        if (previousContext == null) {
            TenantContextHolder.setContext(TenantContext.create(tenantId, "authoritative-submission-worker", Set.of("ROLE_TENANT_ADMIN")));
        }

        try {
            // 2. Fetch invoice and validate existence
            Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
            if (invoice == null) {
                log.error("[SUBMISSION] Invoice {} not found for tenant {}", invoiceId, tenantId);
                if (outboxEventId != null) {
                    outboxService.markFailed(outboxEventId, "Invoice not found: " + invoiceId, 1);
                }
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.REJECTED, null, "INVOICE_NOT_FOUND", "Invoice not found: " + invoiceId);
            }

            // 3. Taxpayer Profile check: Fail closed if missing or invalid
            TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId).orElse(null);
            if (seller == null || seller.getTin() == null || !seller.getTin().matches("^\\d{10}$")) {
                String err = "Taxpayer profile missing or invalid for tenant " + tenantId;
                log.error("[SUBMISSION] Fail-closed: {}", err);
                if (outboxEventId != null) {
                    outboxService.markFailed(outboxEventId, err, 1);
                }
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.REJECTED, null, "TAXPAYER_PROFILE_REQUIRED", err);
            }

            // 4. Idempotency check: verify submission record state
            GovernmentSubmission submission = findOrCreateSubmission(invoice, tenantId);
            if (submission.getStatus() == GovernmentSubmissionStatus.ACCEPTED || invoice.getStatus() == InvoiceStatus.REGISTERED) {
                log.info("[SUBMISSION] Invoice {} is already registered (IRN: {}). Skipping duplicate submission.", invoiceId, invoice.getIrn());
                if (outboxEventId != null) {
                    outboxService.markPublished(outboxEventId);
                }
                return new SubmissionOutcome(true, GovernmentSubmissionStatus.ACCEPTED, invoice.getIrn(), null, null);
            }

            if (submission.getStatus() == GovernmentSubmissionStatus.IN_FLIGHT) {
                log.warn("[SUBMISSION] Invoice {} submission is currently IN_FLIGHT. Concurrency guard active.", invoiceId);
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.IN_FLIGHT, null, "CONCURRENT_SUBMISSION", "Submission in flight");
            }

            // 5. Mark IN_FLIGHT durably before network boundary
            markInFlight(submission);

            // 6. External MoR HTTP Call (OUTSIDE DB TRANSACTION)
            GovernmentRegistrationResult regResult;
            try {
                regResult = governmentProvider.registerInvoice(invoice, seller, "bearer-token");
            } catch (Exception ex) {
                log.error("[SUBMISSION] Network/Timeout error communicating with MoR for invoice {}: {}", invoiceId, ex.getMessage(), ex);
                markUnknown(submission, "NETWORK_TIMEOUT", "Remote outcome unknown: " + ex.getMessage());
                invoice.markOfflineBuffered();
                invoiceRepository.save(invoice);
                if (outboxEventId != null) {
                    outboxService.markFailed(outboxEventId, "UNKNOWN_OUTCOME: " + ex.getMessage(), submission.getAttemptCount());
                }
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.UNKNOWN, null, "NETWORK_TIMEOUT", ex.getMessage());
            }

            // 7. Process Submission Result
            if (regResult == null) {
                log.warn("[SUBMISSION] GovernmentProvider returned null registration result for invoice {}", invoiceId);
                markUnknown(submission, "PROVIDER_NULL", "Government provider returned null result");
                invoice.markOfflineBuffered();
                invoiceRepository.save(invoice);
                if (outboxEventId != null) {
                    outboxService.markFailed(outboxEventId, "PROVIDER_NULL", submission.getAttemptCount());
                }
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.UNKNOWN, null, "PROVIDER_NULL", "Provider returned null");
            }

            if (regResult.success()) {
                applyAcceptedResult(invoice, submission, regResult, outboxEventId, tenantId);
                return new SubmissionOutcome(true, GovernmentSubmissionStatus.ACCEPTED, regResult.irn(), null, null);
            } else if ("TIMEOUT".equalsIgnoreCase(regResult.errorCode()) || "UNKNOWN".equalsIgnoreCase(regResult.errorCode())) {
                markUnknown(submission, regResult.errorCode(), regResult.errorMessage());
                invoice.markOfflineBuffered();
                invoiceRepository.save(invoice);
                if (outboxEventId != null) {
                    outboxService.markFailed(outboxEventId, regResult.errorMessage(), submission.getAttemptCount());
                }
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.UNKNOWN, null, regResult.errorCode(), regResult.errorMessage());
            } else {
                applyRejectedResult(invoice, submission, regResult, outboxEventId, tenantId);
                return new SubmissionOutcome(false, GovernmentSubmissionStatus.REJECTED, null, regResult.errorCode(), regResult.errorMessage());
            }

        } finally {
            if (previousContext == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setContext(previousContext);
            }
        }
    }

    @Transactional
    public GovernmentSubmission findOrCreateSubmission(Invoice invoice, UUID tenantId) {
        return submissionRepository.findByTenantIdAndInvoiceId(tenantId, invoice.getId())
                .stream().findFirst()
                .orElseGet(() -> {
                    String subId = "SUB-" + invoice.getDocumentNumber() + "-" + System.currentTimeMillis();
                    GovernmentSubmission sub = new GovernmentSubmission(
                            UUID.randomUUID(), tenantId, invoice.getId(),
                            "MOR_EIRS", "1.0", subId, invoice.getDocumentNumber() != null ? invoice.getDocumentNumber() : invoice.getId().toString()
                    );
                    return submissionRepository.save(sub);
                });
    }

    @Transactional
    public void markInFlight(GovernmentSubmission submission) {
        submission.markInFlight();
        submissionRepository.save(submission);
    }

    @Transactional
    public void markUnknown(GovernmentSubmission submission, String errorCode, String errorMessage) {
        submission.markUnknown(errorCode, errorMessage);
        submissionRepository.save(submission);
    }

    @Transactional
    public void applyAcceptedResult(Invoice invoice, GovernmentSubmission submission, GovernmentRegistrationResult regResult, UUID outboxEventId, UUID tenantId) {
        invoice.markRegistered(
                regResult.irn(),
                regResult.rrn(),
                regResult.ackDate(),
                regResult.signedQr(),
                regResult.signedInvoice()
        );
        invoiceRepository.save(invoice);

        submission.markAccepted(regResult.irn());
        submissionRepository.save(submission);

        if (outboxEventId != null) {
            outboxService.markPublished(outboxEventId);
        }

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "AUTHORITATIVE_SUBMISSION",
                "EIRS_INVOICE_REGISTERED",
                "INVOICE",
                invoice.getId().toString(),
                "IRN=" + regResult.irn(),
                "127.0.0.1"
        );

        eventPublisher.publish(new InvoiceRegisteredEvent(
                invoice.getId(),
                tenantId,
                invoice.getIrn(),
                invoice.getBuyerEmail(),
                invoice.getBuyerPhone()
        ));
    }

    @Transactional
    public void applyRejectedResult(Invoice invoice, GovernmentSubmission submission, GovernmentRegistrationResult regResult, UUID outboxEventId, UUID tenantId) {
        submission.markRejected(regResult.errorCode(), regResult.errorMessage());
        submissionRepository.save(submission);

        invoice.markOfflineBuffered();
        invoiceRepository.save(invoice);

        if (outboxEventId != null) {
            outboxService.markFailed(outboxEventId, regResult.errorMessage(), submission.getAttemptCount());
        }

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "AUTHORITATIVE_SUBMISSION",
                "EIRS_INVOICE_REJECTED",
                "INVOICE",
                invoice.getId().toString(),
                "CODE=" + regResult.errorCode() + "|ERR=" + regResult.errorMessage(),
                "127.0.0.1"
        );
    }
}
