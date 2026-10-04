package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Metadata for a receipt lookup. Full buyer and seller particulars are available
 * only in the printable document/PDF endpoints, never in a generic JSON response.
 */
public record InvoiceReceiptSummaryDto(
        String documentNumber,
        String irn,
        InvoiceStatus status,
        Instant invoiceDate,
        BigDecimal grandTotal,
        String currency,
        boolean officialDocumentAvailable
) {
    public static InvoiceReceiptSummaryDto fromEntity(Invoice invoice) {
        return new InvoiceReceiptSummaryDto(
                invoice.getDocumentNumber(), invoice.getIrn(), invoice.getStatus(), invoice.getInvoiceDate(),
                invoice.getGrandTotal(), invoice.getCurrency(), invoice.getStatus() != InvoiceStatus.OFFLINE_BUFFERED
        );
    }
}
