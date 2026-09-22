package et.ut.einvoice.government.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Canonical view model containing all information required by MoR official receipt rendering
 * across HTML, PDF, and frontend presentation pursuant to Directive No. 1142/2026.
 */
public record MorReceiptViewModel(
        // Seller / Trader Particulars
        String sellerLegalName,
        String sellerTradeName,
        String sellerAmharicSubtitle,
        String sellerEnglishSubtitle,
        String sellerPhone,
        String sellerEmail,
        String sellerCity,
        String sellerZone,
        String sellerWoreda,
        String sellerKebele,
        String sellerHouseNumber,
        String sellerVatNumber,
        String sellerTin,
        String sellerSubTin,
        String sellerVatRegDate,

        // Buyer Particulars
        String buyerLegalName,
        String buyerPhone,
        String buyerEmail,
        String buyerCity,
        String buyerZone,
        String buyerWoreda,
        String buyerKebele,
        String buyerHouseNumber,
        String buyerVatNumber,
        String buyerTin,
        String buyerSubTin,
        String buyerVatRegDate,

        // Fiscal & Document Metadata
        String invoiceNumber,
        String formattedDate,
        String irn,
        String mrc,
        String transactionTypeBadgeAmharic,
        String transactionTypeBadgeEnglish,
        String salesTypeAmharic,
        String salesTypeEnglish,

        // Line Items
        List<ReceiptLineItem> lines,

        // Totals & Financials
        BigDecimal preTaxTotal,
        BigDecimal taxableTotal,
        BigDecimal vatTotal,
        BigDecimal grandTotal,
        BigDecimal withheldAmount,
        BigDecimal collectedAmount,
        String exchangeRate,
        String inWordsBirr,

        // Payment & Operational Details
        String paymentMode,
        String paymentTypeMethod,
        String reference,
        String voucherNo,
        String serviceProvider,
        String ticket,
        String transportFrom,
        String transportTo,
        String receiverName,

        // Legal & Technical Fiscal Artifacts
        String legalFooter,
        String qrDataJson,
        String qrCodeBase64,
        int reprintCount,
        boolean isDuplicate
) {
    public record ReceiptLineItem(
            int lineNumber,
            String itemCode,
            String productDescription,
            String natureOfSupplies,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String taxCode,
            BigDecimal taxAmount,
            BigDecimal totalLineAmount
    ) {}
}
