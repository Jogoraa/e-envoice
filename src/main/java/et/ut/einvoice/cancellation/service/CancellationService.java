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

    public CancellationService(
            CancellationRequestRepository cancellationRepository,
            InvoiceRepository invoiceRepository,
            GovernmentRegistrationProvider governmentRegistrationProvider,
            DomainEventPublisher eventPublisher,
            et.ut.einvoice.audit.service.AuditService auditService
    ) {
        this.cancellationRepository = cancellationRepository;
        this.invoiceRepository = invoiceRepository;
        this.governmentRegistrationProvider = governmentRegistrationProvider;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
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

            // 5. Publish Event to notify buyer per Directive Art. 26(5)
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

    public record InvoiceCancelledEvent(
            UUID invoiceId,
            UUID tenantId,
            String irn,
            String buyerEmail,
            String buyerPhone,
            String cancellationRef
    ) {}
}
