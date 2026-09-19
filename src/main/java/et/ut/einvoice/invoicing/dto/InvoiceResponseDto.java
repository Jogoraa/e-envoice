package et.ut.einvoice.invoicing.dto;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record InvoiceResponseDto(
        UUID id,
        UUID tenantId,
        String documentNumber,
        Long invoiceCounter,
        Instant invoiceDate,
        TransactionType transactionType,
        String paymentMode,
        String paymentTerm,
        InvoiceStatus status,
        BigDecimal preTaxTotal,
        BigDecimal taxTotal,
        BigDecimal exciseTotal,
        BigDecimal grandTotal,
        String currency,
        String irn,
        String rrn,
        String ackDate,
        String signedQr,
        int reprintCount,
        BuyerResponse buyer,
        List<LineItemResponse> lines
) {
    public record BuyerResponse(
            String legalName,
            String tin,
            String phone,
            String email,
            String region,
            String woreda
    ) {}

    public record LineItemResponse(
            Integer lineNumber,
            String itemCode,
            String productDescription,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal preTaxValue,
            String taxCode,
            BigDecimal taxAmount,
            BigDecimal totalLineAmount
    ) {}

    public static InvoiceResponseDto fromEntity(Invoice inv) {
        BuyerResponse buyerResp = new BuyerResponse(
                inv.getBuyerLegalName(),
                inv.getBuyerTin(),
                inv.getBuyerPhone(),
                inv.getBuyerEmail(),
                inv.getBuyerRegion(),
                inv.getBuyerWoreda()
        );

        List<LineItemResponse> linesResp = inv.getLines().stream().map(l -> new LineItemResponse(
                l.getLineNumber(),
                l.getItemCode(),
                l.getProductDescription(),
                l.getQuantity(),
                l.getUnit(),
                l.getUnitPrice(),
                l.getDiscount(),
                l.getPreTaxValue(),
                l.getTaxCode(),
                l.getTaxAmount(),
                l.getTotalLineAmount()
        )).collect(Collectors.toList());

        return new InvoiceResponseDto(
                inv.getId(),
                inv.getTenantId(),
                inv.getDocumentNumber(),
                inv.getInvoiceCounter(),
                inv.getInvoiceDate(),
                inv.getTransactionType(),
                inv.getPaymentMode(),
                inv.getPaymentTerm(),
                inv.getStatus(),
                inv.getPreTaxTotal(),
                inv.getTaxTotal(),
                inv.getExciseTotal(),
                inv.getGrandTotal(),
                inv.getCurrency(),
                inv.getIrn(),
                inv.getRrn(),
                inv.getAckDate(),
                inv.getSignedQr(),
                inv.getReprintCount(),
                buyerResp,
                linesResp
        );
    }
}
