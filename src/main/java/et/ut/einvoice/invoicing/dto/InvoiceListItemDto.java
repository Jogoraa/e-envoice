package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceListItemDto(
        UUID id,
        String documentNumber,
        Instant invoiceDate,
        TransactionType transactionType,
        String paymentMode,
        InvoiceStatus status,
        BigDecimal preTaxTotal,
        BigDecimal taxTotal,
        BigDecimal exciseTotal,
        BigDecimal grandTotal,
        String currency,
        String irn,
        String rrn,
        int reprintCount,
        BuyerSummary buyer
) {
    public record BuyerSummary(String legalName, String tin) {}

    public static InvoiceListItemDto fromInvoiceResponse(InvoiceResponseDto invoice) {
        BuyerSummary buyerSummary = invoice.buyer() == null
                ? null
                : new BuyerSummary(invoice.buyer().legalName(), invoice.buyer().tin());

        return new InvoiceListItemDto(
                invoice.id(),
                invoice.documentNumber(),
                invoice.invoiceDate(),
                invoice.transactionType(),
                invoice.paymentMode(),
                invoice.status(),
                invoice.preTaxTotal(),
                invoice.taxTotal(),
                invoice.exciseTotal(),
                invoice.grandTotal(),
                invoice.currency(),
                invoice.irn(),
                invoice.rrn(),
                invoice.reprintCount(),
                buyerSummary
        );
    }
}
