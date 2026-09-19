package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public Verification DTO for Electronic Invoices providing the verification data payload
 * referenced in Directive No. 1142/2018 EC (2026 GC) Art. 20(3)(g) and Art. 4(2)(c).
 * Exposes strictly public verification metadata for QR code scanners, consumers, and auditors.
 * Excludes internal identifiers, client secrets, and sensitive buyer PII.
 */
public record PublicInvoiceVerificationDto(
        String irn,
        String documentNumber,
        Instant invoiceDate,
        String sellerTin,
        String sellerLegalName,
        TransactionType transactionType,
        InvoiceStatus status,
        BigDecimal preTaxTotal,
        BigDecimal taxTotal,
        BigDecimal exciseTotal,
        BigDecimal grandTotal,
        String currency,
        String verificationStatus,
        String ackDate
) {
    public static PublicInvoiceVerificationDto fromEntity(Invoice invoice, TaxpayerProfile seller) {
        String sellerLegalName = seller != null ? seller.getLegalName() : "REGISTERED_TAXPAYER";
        String sellerTin = seller != null ? seller.getTin() : "";
        String vStatus = switch (invoice.getStatus()) {
            case REGISTERED -> "VALID_REGISTERED";
            case CANCELLED -> "CANCELLED";
            case OFFLINE_BUFFERED -> "OFFLINE_BUFFERED";
            default -> invoice.getStatus().name();
        };

        return new PublicInvoiceVerificationDto(
                invoice.getIrn(),
                invoice.getDocumentNumber(),
                invoice.getInvoiceDate(),
                sellerTin,
                sellerLegalName,
                invoice.getTransactionType(),
                invoice.getStatus(),
                invoice.getPreTaxTotal(),
                invoice.getTaxTotal(),
                invoice.getExciseTotal(),
                invoice.getGrandTotal(),
                invoice.getCurrency(),
                vStatus,
                invoice.getAckDate()
        );
    }
}
