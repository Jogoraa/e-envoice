package et.ut.einvoice.withholding.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.documents.service.QrCodeService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.withholding.domain.WithholdingReceipt;
import et.ut.einvoice.withholding.domain.WithholdingType;
import et.ut.einvoice.withholding.dto.CreateWithholdingReceiptRequest;
import et.ut.einvoice.withholding.repository.WithholdingReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WithholdingReceiptService {

    public static final BigDecimal DEFAULT_INCOME_WITHHOLDING_RATE = new BigDecimal("0.0200"); // 2% Income Tax Withholding
    public static final BigDecimal DEFAULT_VAT_WITHHOLDING_RATE = new BigDecimal("0.5000"); // 50% VAT Withholding

    private final WithholdingReceiptRepository withholdingReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final QrCodeService qrCodeService;
    private final AuditService auditService;

    public WithholdingReceiptService(WithholdingReceiptRepository withholdingReceiptRepository,
                                     InvoiceRepository invoiceRepository,
                                     QrCodeService qrCodeService,
                                     AuditService auditService) {
        this.withholdingReceiptRepository = withholdingReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.qrCodeService = qrCodeService;
        this.auditService = auditService;
    }

    @Transactional
    public WithholdingReceipt issueWithholdingReceipt(CreateWithholdingReceiptRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        UUID resolvedInvoiceId = request.relatedInvoiceId();
        String resolvedInvoiceIrn = request.relatedInvoiceIrn();

        if (request.relatedInvoiceIrn() != null && !request.relatedInvoiceIrn().isBlank()) {
            final String targetIrn = request.relatedInvoiceIrn().trim();
            Invoice invoice = invoiceRepository.findByIrnAndTenantId(targetIrn, tenantId)
                    .orElseThrow(() -> new BusinessException(
                            "INVOICE_NOT_FOUND",
                            "Invoice with IRN " + targetIrn + " does not exist for this tenant",
                            "ደረሰኙ በስርዓቱ ውስጥ አልተገኘም።",
                            HttpStatus.NOT_FOUND
                    ));
            if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
                throw new BusinessException(
                        "CANNOT_WITHHOLD_CANCELLED_INVOICE",
                        "Cannot issue withholding receipt for cancelled invoice",
                        "የተሰረዘ ደረሰኝ ስለሆነ የታክስ ተቀናሽ ደረሰኝ መስጠት አይቻልም።",
                        HttpStatus.BAD_REQUEST
                );
            }
            resolvedInvoiceId = invoice.getId();
            resolvedInvoiceIrn = invoice.getIrn();
        } else if (request.relatedInvoiceId() != null) {
            final UUID targetId = request.relatedInvoiceId();
            Invoice invoice = invoiceRepository.findByIdAndTenantId(targetId, tenantId)
                    .orElseThrow(() -> new BusinessException(
                            "INVOICE_NOT_FOUND",
                            "Invoice with ID " + targetId + " does not exist for this tenant",
                            "ደረሰኙ በስርዓቱ ውስጥ አልተገኘም።",
                            HttpStatus.NOT_FOUND
                    ));
            resolvedInvoiceId = invoice.getId();
            resolvedInvoiceIrn = invoice.getIrn();
        }

        BigDecimal rate = request.withheldTaxRate();
        if (rate == null) {
            rate = (request.withholdingType() == WithholdingType.INCOME_TAX_WITHHOLDING)
                    ? DEFAULT_INCOME_WITHHOLDING_RATE
                    : DEFAULT_VAT_WITHHOLDING_RATE;
        }

        BigDecimal withheldAmount = request.taxBaseAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        String prefix = (request.withholdingType() == WithholdingType.INCOME_TAX_WITHHOLDING) ? "ITW-" : "VTW-";
        String receiptNumber = prefix + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String rrn = "RRN-WH-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();

        String qrPayload = String.format("TYPE:%s|AGENT:%s|TAXPAYER:%s|REC:%s|RRN:%s|BASE:%.2f|RATE:%.4f|AMT:%.2f",
                request.withholdingType().name(), request.withholdingAgentTin(), request.taxpayerTin(),
                receiptNumber, rrn, request.taxBaseAmount(), rate, withheldAmount);
        String qrBase64 = qrCodeService.generateQrCodeBase64(qrPayload, 180, 180);

        WithholdingReceipt receipt = new WithholdingReceipt(
                UUID.randomUUID(),
                tenantId,
                receiptNumber,
                request.withholdingType(),
                resolvedInvoiceId,
                resolvedInvoiceIrn,
                request.withholdingAgentTin().trim(),
                request.withholdingAgentName().trim(),
                request.taxpayerTin().trim(),
                request.taxpayerName().trim(),
                request.taxBaseAmount(),
                rate,
                withheldAmount,
                request.paymentReference(),
                request.issueDate() != null ? request.issueDate() : Instant.now(),
                rrn,
                qrBase64
        );

        WithholdingReceipt saved = withholdingReceiptRepository.save(receipt);

        auditService.recordEvent(
                tenantId,
                "USER",
                "ISSUE_WITHHOLDING_RECEIPT",
                "WITHHOLDING_RECEIPT",
                saved.getId().toString(),
                "TYPE=" + request.withholdingType() + ", REC_NO=" + receiptNumber + ", AMT=" + withheldAmount
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public WithholdingReceipt getWithholdingReceipt(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return withholdingReceiptRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "WITHHOLDING_RECEIPT_NOT_FOUND",
                        "Withholding receipt " + id + " not found",
                        "የታክስ ተቀናሽ ደረሰኝ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));
    }

    @Transactional(readOnly = true)
    public List<WithholdingReceipt> listWithholdingReceipts(WithholdingType type) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        if (type != null) {
            return withholdingReceiptRepository.findByTenantIdAndWithholdingTypeOrderByIssueDateDesc(tenantId, type);
        }
        return withholdingReceiptRepository.findByTenantIdOrderByIssueDateDesc(tenantId);
    }
}
