package et.ut.einvoice.cancellation.service;

import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.dto.CreateCancellationRequestDto;
import et.ut.einvoice.cancellation.repository.CancellationRequestRepository;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.events.DomainEventPublisher;
import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);

    private final CancellationRequestRepository cancellationRepository;
    private final InvoiceRepository invoiceRepository;
    private final GovernmentRegistrationProvider governmentRegistrationProvider;
    private final DomainEventPublisher eventPublisher;
    private final et.ut.einvoice.audit.service.AuditService auditService;
    private final et.ut.einvoice.notifications.service.InvoiceNotificationPolicyService notificationPolicyService;
    private final et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository notificationOutboxRepository;
    private final et.ut.einvoice.notifications.metrics.SmsMetrics smsMetrics;
    private final et.ut.einvoice.cancellation.repository.CancellationEvidenceAttachmentRepository evidenceRepository;

    public CancellationService(
            CancellationRequestRepository cancellationRepository,
            InvoiceRepository invoiceRepository,
            GovernmentRegistrationProvider governmentRegistrationProvider,
            DomainEventPublisher eventPublisher,
            et.ut.einvoice.audit.service.AuditService auditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.service.InvoiceNotificationPolicyService notificationPolicyService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository notificationOutboxRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.metrics.SmsMetrics smsMetrics,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.cancellation.repository.CancellationEvidenceAttachmentRepository evidenceRepository
    ) {
        this.cancellationRepository = cancellationRepository;
        this.invoiceRepository = invoiceRepository;
        this.governmentRegistrationProvider = governmentRegistrationProvider;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.notificationPolicyService = notificationPolicyService;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.smsMetrics = smsMetrics;
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional
    public CancellationRequest requestCancellation(CreateCancellationRequestDto request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        // 1. Negative Test IRC-N010: Validate invoice exists
        Invoice invoice = invoiceRepository.findByIrnAndTenantId(request.irn(), tenantId)
                .orElseThrow(() -> new BusinessException(
                        "INVOICE_NOT_FOUND",
                        "Invoice with IRN " + request.irn() + " does not exist in the system.",
                        "ደረሰኙ በስርዓቱ ውስጥ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        // 2. Negative Test IRC-N010: Check if already cancelled
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException(
                    "INVOICE_ALREADY_CANCELLED",
                    "Invoice with IRN " + request.irn() + " is already in CANCELLED status. Duplicate cancellation is blocked.",
                    "ይህ ደረሰኝ አስቀድሞ የተሰረዘ ስለሆነ እንደገና መሰረዝ አይቻልም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (invoice.getStatus() != InvoiceStatus.REGISTERED) {
            throw new BusinessException(
                    "INVOICE_NOT_ELIGIBLE_FOR_CANCELLATION",
                    "Only registered invoices can be cancelled. Current status is " + invoice.getStatus(),
                    "መሰረዝ የሚቻለው በተመዘገቡ ደረሰኞች ላይ ብቻ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 3. Create Cancellation Entity
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                invoice.getId(),
                invoice.getIrn(),
                request.reasonCategory(),
                request.detailedReason()
        );

        // 4. Submit to MoR Gateway
        var cancelResult = governmentRegistrationProvider.cancelInvoice(
                invoice.getIrn(),
                request.reasonCategory() + ": " + request.detailedReason(),
                "mock-token"
        );

        if (cancelResult.success()) {
            cancellation.approve(cancelResult.cancellationRef());
            invoice.markCancelled();
            invoiceRepository.save(invoice);
            log.info("Invoice {} (IRN: {}) cancelled successfully with ref {}", invoice.getId(), invoice.getIrn(), cancelResult.cancellationRef());

            auditService.recordEvent(
                    tenantId,
                    "CANCELLATION",
                    "USER",
                    "CANCEL_INVOICE",
                    "INVOICE",
                    invoice.getId().toString(),
                    "IRN=" + invoice.getIrn() + ", REF=" + cancelResult.cancellationRef() + ", REASON=" + request.reasonCategory(),
                    "127.0.0.1"
            );

            // 5. Same-transaction outbox enqueueing for cancellation notification
            if (notificationPolicyService != null && notificationOutboxRepository != null) {
                var decision = notificationPolicyService.evaluateCancellationNotification(
                        invoice,
                        cancelResult.cancellationRef(),
                        UUID.randomUUID().toString()
                );
                if (decision.shouldNotify() && decision.outboxRecord() != null) {
                    notificationOutboxRepository.save(decision.outboxRecord());
                    if (smsMetrics != null) {
                        smsMetrics.recordCreated(decision.outboxRecord().getNotificationType());
                    }
                    auditService.recordEvent(
                            tenantId,
                            "SMS_NOTIFICATION",
                            "SYSTEM",
                            et.ut.einvoice.audit.domain.AuditAction.SMS_NOTIFICATION_CREATED.name(),
                            "INVOICE",
                            invoice.getId().toString(),
                            "outbox_id=" + decision.outboxRecord().getId() + ";party_id=" + decision.outboxRecord().getRecipientPartyId(),
                            "127.0.0.1"
                    );
                }
            }

            // 6. Publish Event to notify buyer per Directive Art. 26(5)
            eventPublisher.publish(new InvoiceCancelledEvent(
                    invoice.getId(),
                    tenantId,
                    invoice.getIrn(),
                    invoice.getBuyerEmail(),
                    invoice.getBuyerPhone(),
                    cancelResult.cancellationRef()
            ));
        } else {
            cancellation.reject(cancelResult.message());
            log.warn("MoR Gateway rejected cancellation for IRN {}: {}", invoice.getIrn(), cancelResult.message());
        }

        return cancellationRepository.save(cancellation);
    }

    @Transactional
    public CancellationRequest demandAuthorityEvidence(UUID tenantId, UUID cancellationId, java.time.Duration deadlineDuration) {
        CancellationRequest cancellation = cancellationRepository.findById(cancellationId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("CANCELLATION_NOT_FOUND", "Cancellation request not found.", HttpStatus.NOT_FOUND));

        java.time.Instant deadline = java.time.Instant.now().plus(deadlineDuration != null ? deadlineDuration : java.time.Duration.ofHours(48));
        cancellation.requestEvidence(deadline);
        CancellationRequest saved = cancellationRepository.save(cancellation);

        auditService.recordEvent(
                tenantId,
                "AUTHORITY",
                "CANCELLATION_EVIDENCE_REQUESTED",
                "CANCELLATION",
                saved.getId().toString(),
                "IRN=" + saved.getIrn() + ",DEADLINE=" + deadline
        );

        log.info("Authority demanded cancellation evidence for IRN {} [Deadline: {}]", saved.getIrn(), deadline);
        return saved;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CancellationRequest submitCancellationEvidence(UUID tenantId, et.ut.einvoice.cancellation.dto.SubmitEvidenceDto dto) {
        CancellationRequest cancellation = cancellationRepository.findById(dto.cancellationRequestId())
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("CANCELLATION_NOT_FOUND", "Cancellation request not found.", HttpStatus.NOT_FOUND));

        // Check if 48-hour deadline has expired
        if (cancellation.getEvidenceDeadline() != null && java.time.Instant.now().isAfter(cancellation.getEvidenceDeadline())) {
            cancellation.expire();
            cancellationRepository.saveAndFlush(cancellation);
            throw new BusinessException("EVIDENCE_DEADLINE_EXPIRED",
                    "Statutory 48-hour evidence window under Directive Art. 26(3) has expired.",
                    HttpStatus.BAD_REQUEST);
        }

        // File type validation (malware / executable prevention)
        String mime = dto.contentType() != null ? dto.contentType().trim().toLowerCase() : "";
        if (!mime.equals("application/pdf") && !mime.equals("image/jpeg") && !mime.equals("image/png")) {
            throw new BusinessException("UNSUPPORTED_FILE_TYPE",
                    "Evidence attachment must be PDF, JPEG, or PNG. Prohibited content type: " + dto.contentType(),
                    HttpStatus.BAD_REQUEST);
        }

        byte[] fileBytes;
        try {
            fileBytes = java.util.Base64.getDecoder().decode(dto.fileBase64());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_BASE64", "Invalid base64 encoding for evidence attachment.", HttpStatus.BAD_REQUEST);
        }

        if (fileBytes.length == 0 || fileBytes.length > 20 * 1024 * 1024) {
            throw new BusinessException("INVALID_FILE_SIZE", "File size must be between 1 byte and 20MB.", HttpStatus.BAD_REQUEST);
        }

        String sha256;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            sha256 = java.util.HexFormat.of().formatHex(md.digest(fileBytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }

        if (evidenceRepository != null) {
            et.ut.einvoice.cancellation.domain.CancellationEvidenceAttachment attachment =
                    new et.ut.einvoice.cancellation.domain.CancellationEvidenceAttachment(
                            UUID.randomUUID(),
                            tenantId,
                            cancellation.getId(),
                            dto.fileName(),
                            dto.contentType(),
                            fileBytes.length,
                            sha256,
                            "evidence/" + tenantId + "/" + cancellation.getId() + "/" + dto.fileName(),
                            dto.description(),
                            "TENANT_OPERATOR"
                    );
            evidenceRepository.save(attachment);
        }

        cancellation.submitEvidence("Attachment: " + dto.fileName() + " [SHA256: " + sha256 + "]");
        CancellationRequest saved = cancellationRepository.save(cancellation);

        auditService.recordEvent(
                tenantId,
                "USER",
                "CANCELLATION_EVIDENCE_SUBMITTED",
                "CANCELLATION",
                saved.getId().toString(),
                "IRN=" + saved.getIrn() + ",FILE=" + dto.fileName() + ",SHA256=" + sha256
        );

        return saved;
    }

    @Transactional
    public CancellationRequest finalizeAuthorityApproval(UUID tenantId, UUID cancellationId, String cancellationRef) {
        CancellationRequest cancellation = cancellationRepository.findById(cancellationId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("CANCELLATION_NOT_FOUND", "Cancellation request not found.", HttpStatus.NOT_FOUND));

        Invoice invoice = invoiceRepository.findById(cancellation.getInvoiceId())
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found.", HttpStatus.NOT_FOUND));

        cancellation.approve(cancellationRef);
        invoice.markCancelled();
        invoiceRepository.save(invoice);

        auditService.recordEvent(
                tenantId,
                "AUTHORITY",
                "CANCEL_INVOICE_APPROVED",
                "INVOICE",
                invoice.getId().toString(),
                "IRN=" + invoice.getIrn() + ",REF=" + cancellationRef
        );

        eventPublisher.publish(new InvoiceCancelledEvent(
                invoice.getId(),
                tenantId,
                invoice.getIrn(),
                invoice.getBuyerEmail(),
                invoice.getBuyerPhone(),
                cancellationRef
        ));

        return cancellationRepository.save(cancellation);
    }

    @Transactional
    public CancellationRequest finalizeAuthorityRejection(UUID tenantId, UUID cancellationId, String reason) {
        CancellationRequest cancellation = cancellationRepository.findById(cancellationId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("CANCELLATION_NOT_FOUND", "Cancellation request not found.", HttpStatus.NOT_FOUND));

        cancellation.reject(reason);

        auditService.recordEvent(
                tenantId,
                "AUTHORITY",
                "CANCEL_INVOICE_REJECTED",
                "CANCELLATION",
                cancellation.getId().toString(),
                "IRN=" + cancellation.getIrn() + ",REASON=" + reason
        );

        return cancellationRepository.save(cancellation);
    }

    public record InvoiceCancelledEvent(
            UUID invoiceId,
            UUID tenantId,
            String irn,
            String buyerEmail,
            String buyerPhone,
            String cancellationRef
    ) {}
}
