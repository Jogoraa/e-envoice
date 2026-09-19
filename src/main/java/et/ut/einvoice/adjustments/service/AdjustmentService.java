package et.ut.einvoice.adjustments.service;

import et.ut.einvoice.adjustments.domain.NoteType;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import et.ut.einvoice.adjustments.dto.CreateAdjustmentRequest;
import et.ut.einvoice.adjustments.repository.TaxAdjustmentRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdjustmentService {

    private final TaxAdjustmentRepository adjustmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final et.ut.einvoice.audit.service.AuditService auditService;

    public AdjustmentService(TaxAdjustmentRepository adjustmentRepository, InvoiceRepository invoiceRepository, et.ut.einvoice.audit.service.AuditService auditService) {
        this.adjustmentRepository = adjustmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxAdjustment createAdjustment(NoteType noteType, CreateAdjustmentRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        Invoice original = invoiceRepository.findByIrnAndTenantId(request.originalIrn(), tenantId)
                .or(() -> invoiceRepository.findByIrn(request.originalIrn()))
                .orElseThrow(() -> new BusinessException(
                        "INVOICE_NOT_FOUND",
                        "Original invoice with IRN " + request.originalIrn() + " not found.",
                        "የመጀመሪያው ደረሰኝ በስርዓቱ ውስጥ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        if (original.getStatus() != InvoiceStatus.REGISTERED) {
            throw new BusinessException(
                    "INVOICE_NOT_ELIGIBLE_FOR_ADJUSTMENT",
                    "Only registered active invoices can receive adjustments. Status is " + original.getStatus(),
                    "ማስተካከያ ማድረግ የሚቻለው በተመዘገበ ህጋዊ ደረሰኝ ላይ ብቻ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal adjustedTax = request.adjustedTax() != null
                ? request.adjustedTax()
                : request.adjustedPreTax().multiply(new BigDecimal("0.1500")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjustedTotal = request.adjustedPreTax().add(adjustedTax).setScale(2, RoundingMode.HALF_UP);

        // Directive Art. 25 & Master Compliance IRC-P06: Credit Note cannot exceed original invoice total
        if (noteType == NoteType.CREDIT_NOTE && adjustedTotal.compareTo(original.getGrandTotal()) > 0) {
            throw new BusinessException(
                    "CREDIT_AMOUNT_EXCEEDS_INVOICE_VALUE",
                    String.format("Credit note amount (%,.2f ETB) exceeds the original registered invoice total (%,.2f ETB).",
                            adjustedTotal, original.getGrandTotal()),
                    "የክሬዲት ኖት መጠኑ ከመጀመሪያው ደረሰኝ ጠቅላላ ዋጋ መብለጥ አይችልም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        TaxAdjustment adjustment = new TaxAdjustment(
                UUID.randomUUID(),
                tenantId,
                noteType,
                original.getId(),
                original.getIrn(),
                request.reason(),
                request.adjustedPreTax(),
                adjustedTax,
                adjustedTotal
        );

        adjustment.setIrn((noteType == NoteType.CREDIT_NOTE ? "CN-" : "DN-") + UUID.randomUUID());
        adjustment.setAckDate(Instant.now().toString());

        TaxAdjustment saved = adjustmentRepository.save(adjustment);
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
        return saved;
    }
}
