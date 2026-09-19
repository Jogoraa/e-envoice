package et.ut.einvoice.receipts.service;

import et.ut.einvoice.documents.service.QrCodeService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.receipts.domain.Receipt;
import et.ut.einvoice.receipts.domain.ReceiptType;
import et.ut.einvoice.receipts.dto.CreateReceiptRequest;
import et.ut.einvoice.receipts.repository.ReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final QrCodeService qrCodeService;
    private final et.ut.einvoice.audit.service.AuditService auditService;

    public ReceiptService(ReceiptRepository receiptRepository, InvoiceRepository invoiceRepository, QrCodeService qrCodeService, et.ut.einvoice.audit.service.AuditService auditService) {
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.qrCodeService = qrCodeService;
        this.auditService = auditService;
    }

    @Transactional
    public Receipt createReceipt(ReceiptType type, CreateReceiptRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        // 1. Negative Test IRC-N09: Check if invoice exists
        Invoice invoice = invoiceRepository.findByIrnAndTenantId(request.invoiceIrn(), tenantId)
                .orElseThrow(() -> new BusinessException(
                        "INVOICE_NOT_FOUND",
                        "Invoice with IRN " + request.invoiceIrn() + " does not exist. Cannot issue receipt.",
                        "ደረሰኙ በስርዓቱ ውስጥ አልተገኘም፤ ለሌለ ደረሰኝ የክፍያ ማረጋገጫ መስጠት አይቻልም።",
                        HttpStatus.NOT_FOUND
                ));

        // 2. Negative Test IRC-N09: Check if invoice is cancelled
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException(
                    "CANNOT_ISSUE_RECEIPT_FOR_CANCELLED_INVOICE",
                    "Invoice with IRN " + request.invoiceIrn() + " has been CANCELLED. Generating receipts for cancelled invoices is strictly blocked.",
                    "የተሰረዘ ደረሰኝ ስለሆነ የክፍያ ማረጋገጫ መስጠት አይቻልም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (invoice.getStatus() != InvoiceStatus.REGISTERED) {
            throw new BusinessException(
                    "INVOICE_NOT_REGISTERED",
                    "Invoice must be in REGISTERED state before receipts can be generated.",
                    "ደረሰኙ በቅድሚያ በገቢዎች ሚኒስቴር መመዝገብ አለበት።",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 3. Generate RRN and Receipt QR Code (IRC-P03, IRC-P04)
        String rrn = "RRN-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        String receiptNumber = "REC-" + System.currentTimeMillis();
        String qrPayload = String.format("IRN:%s|RRN:%s|AMT:%.2f|TYPE:%s",
                invoice.getIrn(), rrn, request.amount(), type.name());
        String qrBase64 = qrCodeService.generateQrCodeBase64(qrPayload, 180, 180);

        Receipt receipt = new Receipt(
                UUID.randomUUID(),
                tenantId,
                type,
                invoice.getId(),
                invoice.getIrn(),
                receiptNumber,
                request.amount(),
                request.withholdingAmount() != null ? request.withholdingAmount() : BigDecimal.ZERO,
                rrn,
                qrBase64
        );

        Receipt saved = receiptRepository.save(receipt);
        auditService.recordEvent(
                tenantId,
                "RECEIPT",
                "USER",
                "CREATE_RECEIPT",
                "RECEIPT",
                saved.getId().toString(),
                "IRN=" + invoice.getIrn() + ", RRN=" + rrn + ", AMT=" + request.amount(),
                "127.0.0.1"
        );
        return saved;
    }
}
