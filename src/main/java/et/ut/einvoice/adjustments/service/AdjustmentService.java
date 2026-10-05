package et.ut.einvoice.adjustments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.adjustments.domain.AdjustmentMoRStatus;
import et.ut.einvoice.adjustments.domain.NoteType;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import et.ut.einvoice.adjustments.domain.TaxAdjustmentLine;
import et.ut.einvoice.adjustments.dto.CreateAdjustmentRequest;
import et.ut.einvoice.adjustments.repository.TaxAdjustmentLineRepository;
import et.ut.einvoice.adjustments.repository.TaxAdjustmentRepository;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(AdjustmentService.class);

    private final TaxAdjustmentRepository adjustmentRepository;
    private final TaxAdjustmentLineRepository adjustmentLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final GovernmentRegistrationProvider governmentRegistrationProvider;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AdjustmentService(TaxAdjustmentRepository adjustmentRepository,
                             TaxAdjustmentLineRepository adjustmentLineRepository,
                             InvoiceRepository invoiceRepository,
                             TaxpayerProfileRepository taxpayerProfileRepository,
                             GovernmentRegistrationProvider governmentRegistrationProvider,
                             OutboxService outboxService,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.adjustmentRepository = adjustmentRepository;
        this.adjustmentLineRepository = adjustmentLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.governmentRegistrationProvider = governmentRegistrationProvider;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Transactional
    public TaxAdjustment createAdjustment(NoteType noteType, CreateAdjustmentRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        // 1. Idempotency Check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<TaxAdjustment> existing = adjustmentRepository
                    .findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("Returning existing idempotent adjustment {} for tenant {}", existing.get().getId(), tenantId);
                return existing.get();
            }
        }

        // 2. Strict Tenant-Isolated Original Invoice Resolution (No Cross-Tenant Leaks)
        Invoice original = invoiceRepository.findByIrnAndTenantId(request.originalIrn().trim(), tenantId)
                .orElseThrow(() -> new BusinessException(
                        "INVOICE_NOT_FOUND",
                        "Original invoice with IRN " + request.originalIrn() + " not found for this tenant.",
                        "የመጀመሪያው ደረሰኝ በስርዓቱ ውስጥ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        // 3. Validation: Registered Original Required
        if (original.getStatus() != InvoiceStatus.REGISTERED) {
            throw new BusinessException(
                    "INVOICE_NOT_ELIGIBLE_FOR_ADJUSTMENT",
                    "Only registered active invoices can receive adjustments. Status is " + original.getStatus(),
                    "ማስተካከያ ማድረግ የሚቻለው በተመዘገበ ህጋዊ ደረሰኝ ላይ ብቻ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 4. Compute Tax and Total
        BigDecimal adjustedTax = request.adjustedTax() != null
                ? request.adjustedTax()
                : request.adjustedPreTax().multiply(new BigDecimal("0.1500")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjustedTotal = request.adjustedPreTax().add(adjustedTax).setScale(2, RoundingMode.HALF_UP);

        // 5. Over-Adjustment Prevention (Single & Cumulative)
        if (noteType == NoteType.CREDIT_NOTE) {
            if (adjustedTotal.compareTo(original.getGrandTotal()) > 0) {
                throw new BusinessException(
                        "CREDIT_AMOUNT_EXCEEDS_INVOICE_VALUE",
                        String.format("Credit note amount (%,.2f ETB) exceeds the original registered invoice total (%,.2f ETB).",
                                adjustedTotal, original.getGrandTotal()),
                        "የክሬዲት ኖት መጠኑ ከመጀመሪያው ደረሰኝ ጠቅላላ ዋጋ መብለጥ አይችልም።",
                        HttpStatus.BAD_REQUEST
                );
            }

            // Check cumulative prior registered credit notes
            List<TaxAdjustment> priorAdjustments = adjustmentRepository
                    .findByTenantIdAndOriginalInvoiceId(tenantId, original.getId());
            BigDecimal priorCreditSum = priorAdjustments.stream()
                    .filter(a -> a.getNoteType() == NoteType.CREDIT_NOTE && "REGISTERED".equals(a.getStatus()))
                    .map(TaxAdjustment::getAdjustedTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (priorCreditSum.add(adjustedTotal).compareTo(original.getGrandTotal()) > 0) {
                throw new BusinessException(
                        "CUMULATIVE_CREDIT_EXCEEDS_INVOICE_VALUE",
                        String.format("Cumulative credit notes (%,.2f + %,.2f = %,.2f ETB) exceed original invoice total (%,.2f ETB).",
                                priorCreditSum, adjustedTotal, priorCreditSum.add(adjustedTotal), original.getGrandTotal()),
                        "ድምር የክሬዲት ኖቶች ከመጀመሪያው ደረሰኝ ጠቅላላ ዋጋ ሊበልጡ አይችሉም።",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        BigDecimal originalGrandTotal = original.getGrandTotal();
        BigDecimal newGrandTotal = (noteType == NoteType.CREDIT_NOTE)
                ? originalGrandTotal.subtract(adjustedTotal).setScale(2, RoundingMode.HALF_UP)
                : originalGrandTotal.add(adjustedTotal).setScale(2, RoundingMode.HALF_UP);

        // 6. Persist PENDING Adjustment
        TaxAdjustment adjustment = new TaxAdjustment(
                UUID.randomUUID(),
                tenantId,
                noteType,
                original.getId(),
                original.getIrn(),
                request.reason().trim(),
                request.adjustedPreTax(),
                adjustedTax,
                adjustedTotal,
                originalGrandTotal,
                newGrandTotal,
                request.idempotencyKey()
        );

        if (request.lines() != null && !request.lines().isEmpty()) {
            for (CreateAdjustmentRequest.AdjustmentLineDto lineDto : request.lines()) {
                TaxAdjustmentLine line = new TaxAdjustmentLine(
                        UUID.randomUUID(),
                        tenantId,
                        adjustment,
                        lineDto.lineNumber(),
                        lineDto.itemCode(),
                        lineDto.productDescription(),
                        lineDto.adjustedQuantity(),
                        lineDto.unitPrice(),
                        lineDto.preTaxAdjustment(),
                        lineDto.taxAdjustment(),
                        lineDto.totalAdjustment()
                );
                adjustment.addLine(line);
            }
        }

        TaxAdjustment saved = adjustmentRepository.save(adjustment);

        // 7. Append Audit
        auditService.recordEvent(
                tenantId,
                "ADJUSTMENT",
                "USER",
                noteType.name(),
                "TAX_ADJUSTMENT",
                saved.getId().toString(),
                "ORIGINAL_IRN=" + original.getIrn() + ", ADJUSTED_TOTAL=" + adjustedTotal,
                "127.0.0.1"
        );

        // 8. Enqueue Outbox Event
        String payloadJson = serializePayload(saved);
        outboxService.enqueueEvent(
                tenantId,
                "ADJUSTMENT",
                saved.getId().toString(),
                "ADJUSTMENT_REGISTRATION",
                payloadJson
        );

        // 9. Synchronous Government Registration Boundary (External Call)
        TaxpayerProfile seller = taxpayerProfileRepository.findByTenantId(tenantId).orElse(null);
        try {
            saved.markSubmitted();
            GovernmentRegistrationProvider.GovernmentRegistrationResult result =
                    governmentRegistrationProvider.registerAdjustment(saved, seller, "SYSTEM_TOKEN");

            if (result != null && result.success()) {
                saved.markRegistered(
                        result.irn(),
                        result.rrn(),
                        result.ackDate(),
                        result.signedQr(),
                        result.signedInvoice()
                );
            } else if (result != null) {
                saved.markFailed(result.errorMessage() != null ? result.errorMessage() : "MoR rejection");
            } else {
                saved.markUnknown("No response received from MoR EIRS");
            }
        } catch (Exception ex) {
            log.warn("EIRS registration encounter network/timeout for adjustment {}: {}", saved.getId(), ex.getMessage());
            saved.markUnknown(ex.getMessage());
        }

        return adjustmentRepository.save(saved);
    }

    @Transactional
    public TaxAdjustment retryAdjustment(UUID adjustmentId) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        TaxAdjustment adjustment = adjustmentRepository.findByIdAndTenantId(adjustmentId, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "ADJUSTMENT_NOT_FOUND",
                        "Tax adjustment " + adjustmentId + " not found",
                        "የታክስ ማስተካከያ ሰነድ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        if (adjustment.getMorStatus() == AdjustmentMoRStatus.REGISTERED) {
            return adjustment; // Idempotent: already registered
        }

        TaxpayerProfile seller = taxpayerProfileRepository.findByTenantId(tenantId).orElse(null);
        try {
            adjustment.markSubmitted();
            GovernmentRegistrationProvider.GovernmentRegistrationResult result =
                    governmentRegistrationProvider.registerAdjustment(adjustment, seller, "SYSTEM_TOKEN");

            if (result != null && result.success()) {
                adjustment.markRegistered(
                        result.irn(),
                        result.rrn(),
                        result.ackDate(),
                        result.signedQr(),
                        result.signedInvoice()
                );
            } else if (result != null) {
                adjustment.markFailed(result.errorMessage());
            } else {
                adjustment.markUnknown("No response from MoR");
            }
        } catch (Exception ex) {
            adjustment.markUnknown(ex.getMessage());
        }

        return adjustmentRepository.save(adjustment);
    }

    @Transactional(readOnly = true)
    public TaxAdjustment getAdjustment(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return adjustmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "ADJUSTMENT_NOT_FOUND",
                        "Tax adjustment " + id + " not found",
                        "የታክስ ማስተካከያ ሰነድ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));
    }

    private String serializePayload(TaxAdjustment adj) {
        try {
            return objectMapper.writeValueAsString(adj);
        } catch (Exception ex) {
            return "{\"adjustmentId\":\"" + adj.getId() + "\"}";
        }
    }
}
