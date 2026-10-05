package et.ut.einvoice.creditsales.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.cashreceipt.domain.CashReceipt;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPaymentMethod;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import et.ut.einvoice.cashreceipt.dto.CreateCashReceiptRequest;
import et.ut.einvoice.cashreceipt.service.CashReceiptService;
import et.ut.einvoice.creditsales.domain.CreditSettlement;
import et.ut.einvoice.creditsales.domain.CreditStatus;
import et.ut.einvoice.creditsales.dto.CreditAccountSummaryResponse;
import et.ut.einvoice.creditsales.dto.CreditSettlementResponse;
import et.ut.einvoice.creditsales.dto.SettleCreditRequest;
import et.ut.einvoice.creditsales.repository.CreditSettlementRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
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
public class CreditSettlementService {

    private final CreditSettlementRepository creditSettlementRepository;
    private final InvoiceRepository invoiceRepository;
    private final CashReceiptService cashReceiptService;
    private final AuditService auditService;

    public CreditSettlementService(CreditSettlementRepository creditSettlementRepository,
                                   InvoiceRepository invoiceRepository,
                                   CashReceiptService cashReceiptService,
                                   AuditService auditService) {
        this.creditSettlementRepository = creditSettlementRepository;
        this.invoiceRepository = invoiceRepository;
        this.cashReceiptService = cashReceiptService;
        this.auditService = auditService;
    }

    @Transactional
    public CreditSettlement recordSettlement(SettleCreditRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        Invoice invoice = invoiceRepository.findByIdAndTenantId(request.invoiceId(), tenantId)
                .orElseThrow(() -> new BusinessException(
                        "INVOICE_NOT_FOUND",
                        "Invoice " + request.invoiceId() + " not found for tenant " + tenantId,
                        "ደረሰኙ በስርዓቱ ውስጥ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        if (invoice.getStatus() != InvoiceStatus.REGISTERED) {
            throw new BusinessException(
                    "INVOICE_NOT_REGISTERED",
                    "Cannot settle credit on an unregistered invoice. Current status: " + invoice.getStatus(),
                    "የተመዘገበ ደረሰኝ ስላልሆነ ክፍያ መመዝገብ አይቻልም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException(
                    "INVOICE_IS_CANCELLED",
                    "Cannot settle credit on a cancelled invoice",
                    "የተሰረዘ ደረሰኝ ስለሆነ ክፍያ መመዝገብ አይቻልም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!"CREDIT".equalsIgnoreCase(invoice.getPaymentTerm())) {
            throw new BusinessException(
                    "NOT_A_CREDIT_INVOICE",
                    "Invoice " + invoice.getDocumentNumber() + " is an IMMEDIATE payment sale, not a CREDIT sale",
                    "ይህ ደረሰኝ የብድር ሽያጭ አይደለም።",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (invoice.getCreditStatus() == CreditStatus.SETTLED) {
            throw new BusinessException(
                    "CREDIT_ALREADY_SETTLED",
                    "Credit invoice " + invoice.getDocumentNumber() + " is already fully settled",
                    "ይህ የብድር ደረሰኝ ሙሉ በሙሉ ተከፍሎ የተጠናቀቀ ነው።",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal balanceBefore = invoice.getOutstandingBalance();

        // Apply settlement logic on invoice
        invoice.applySettlement(request.amount());
        BigDecimal balanceAfter = invoice.getOutstandingBalance();
        invoiceRepository.save(invoice);

        // Generate corresponding Cash Receipt
        CashReceiptPaymentMethod method = CashReceiptPaymentMethod.CASH;
        if (request.paymentMethod() != null) {
            try {
                method = CashReceiptPaymentMethod.valueOf(request.paymentMethod().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String payer = (invoice.getBuyerLegalName() != null && !invoice.getBuyerLegalName().isBlank())
                ? invoice.getBuyerLegalName()
                : "Credit Customer";

        CashReceipt cashReceipt = cashReceiptService.issueCashReceipt(new CreateCashReceiptRequest(
                payer,
                invoice.getBuyerTin(),
                request.amount(),
                invoice.getCurrency(),
                CashReceiptPurpose.CREDIT_SALE_SETTLEMENT,
                "Settlement for credit invoice " + invoice.getDocumentNumber() + " (IRN: " + invoice.getIrn() + ")",
                invoice.getId(),
                invoice.getDocumentNumber(),
                method,
                request.paymentReference(),
                request.paymentDate() != null ? request.paymentDate() : Instant.now()
        ));

        String settlementNumber = "STL-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        CreditSettlement settlement = new CreditSettlement(
                UUID.randomUUID(),
                tenantId,
                invoice.getId(),
                cashReceipt.getId(),
                settlementNumber,
                request.amount(),
                balanceBefore,
                balanceAfter,
                method.name(),
                request.paymentReference(),
                request.paymentDate() != null ? request.paymentDate() : Instant.now()
        );

        CreditSettlement saved = creditSettlementRepository.save(settlement);

        auditService.recordEvent(
                tenantId,
                "USER",
                "SETTLE_CREDIT_SALE",
                "CREDIT_SETTLEMENT",
                saved.getId().toString(),
                "INV=" + invoice.getDocumentNumber() + ", AMT=" + request.amount() + ", BAL_AFTER=" + balanceAfter
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public CreditAccountSummaryResponse getCreditAccountSummary(UUID invoiceId) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();

        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "INVOICE_NOT_FOUND",
                        "Invoice " + invoiceId + " not found",
                        "ደረሰኙ በስርዓቱ ውስጥ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        List<CreditSettlementResponse> settlements = creditSettlementRepository
                .findByTenantIdAndInvoiceIdOrderBySettledAtAsc(tenantId, invoiceId)
                .stream()
                .map(CreditSettlementResponse::fromEntity)
                .toList();

        BigDecimal totalSettled = settlements.stream()
                .map(CreditSettlementResponse::settlementAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CreditAccountSummaryResponse(
                invoice.getId(),
                invoice.getDocumentNumber(),
                invoice.getIrn(),
                invoice.getBuyerLegalName(),
                invoice.getBuyerTin(),
                invoice.getGrandTotal(),
                totalSettled,
                invoice.getOutstandingBalance(),
                invoice.getCreditDueDate(),
                invoice.getCreditTermsDescription(),
                invoice.getCreditStatus(),
                settlements
        );
    }
}
