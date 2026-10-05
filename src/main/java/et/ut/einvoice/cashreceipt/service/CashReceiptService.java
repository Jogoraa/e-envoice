package et.ut.einvoice.cashreceipt.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.cashreceipt.domain.CashReceipt;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPaymentMethod;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import et.ut.einvoice.cashreceipt.dto.CreateCashReceiptRequest;
import et.ut.einvoice.cashreceipt.repository.CashReceiptRepository;
import et.ut.einvoice.documents.service.QrCodeService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CashReceiptService {

    private final CashReceiptRepository cashReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final QrCodeService qrCodeService;
    private final AuditService auditService;

    public CashReceiptService(CashReceiptRepository cashReceiptRepository,
                              InvoiceRepository invoiceRepository,
                              QrCodeService qrCodeService,
                              AuditService auditService) {
        this.cashReceiptRepository = cashReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.qrCodeService = qrCodeService;
        this.auditService = auditService;
    }

    @Transactional
    public CashReceipt issueCashReceipt(CreateCashReceiptRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        // If related to an invoice, validate that the invoice exists and belongs to the same tenant
        String relatedIrn = null;
        if (request.relatedInvoiceId() != null) {
            Invoice invoice = invoiceRepository.findByIdAndTenantId(request.relatedInvoiceId(), tenantId)
                    .orElseThrow(() -> new BusinessException(
                            "RELATED_INVOICE_NOT_FOUND",
                            "Related invoice ID " + request.relatedInvoiceId() + " not found for this tenant",
                            "ተዛማጅ ደረሰኝ በስርዓቱ ውስጥ አልተገኘም።",
                            HttpStatus.NOT_FOUND
                    ));
            relatedIrn = invoice.getIrn();
        }

        String receiptNumber = "CR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String rrn = "RRN-CR-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();

        String qrPayload = String.format("TYPE:CASH_RECEIPT|TENANT:%s|REC:%s|RRN:%s|AMT:%.2f|PURPOSE:%s|REF:%s",
                tenantId, receiptNumber, rrn, request.amount(), request.purpose().name(),
                request.referenceNumber() != null ? request.referenceNumber() : "N/A");
        String qrBase64 = qrCodeService.generateQrCodeBase64(qrPayload, 180, 180);

        CashReceipt receipt = new CashReceipt(
                UUID.randomUUID(),
                tenantId,
                receiptNumber,
                request.payerName().trim(),
                request.payerTin() != null ? request.payerTin().trim() : null,
                request.amount(),
                request.currency() != null ? request.currency().trim() : "ETB",
                request.purpose(),
                request.purposeDescription(),
                request.relatedInvoiceId(),
                request.relatedCreditAccountId(),
                request.paymentMethod() != null ? request.paymentMethod() : CashReceiptPaymentMethod.CASH,
                request.referenceNumber(),
                request.receivedAt() != null ? request.receivedAt() : Instant.now(),
                rrn,
                relatedIrn,
                qrBase64
        );

        CashReceipt saved = cashReceiptRepository.save(receipt);

        auditService.recordEvent(
                tenantId,
                "USER",
                "CREATE_CASH_RECEIPT",
                "CASH_RECEIPT",
                saved.getId().toString(),
                "REC_NO=" + receiptNumber + ", PURPOSE=" + request.purpose() + ", AMT=" + request.amount()
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public CashReceipt getCashReceipt(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return cashReceiptRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "CASH_RECEIPT_NOT_FOUND",
                        "Cash receipt " + id + " not found",
                        "የጥሬ ገንዘብ ደረሰኝ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));
    }

    @Transactional(readOnly = true)
    public List<CashReceipt> listCashReceipts(CashReceiptPurpose purpose) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        if (purpose != null) {
            return cashReceiptRepository.findByTenantIdAndPurposeOrderByReceivedAtDesc(tenantId, purpose);
        }
        return cashReceiptRepository.findByTenantIdOrderByReceivedAtDesc(tenantId);
    }
}
