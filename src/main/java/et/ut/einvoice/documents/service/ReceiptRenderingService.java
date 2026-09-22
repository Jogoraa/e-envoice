package et.ut.einvoice.documents.service;

import et.ut.einvoice.government.domain.MorReceiptViewModel;
import et.ut.einvoice.government.service.MorInvoiceCanonicalizationService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Service responsible for rendering the official, bilingual (Amharic & English)
 * Ministry of Revenues (MoR) Electronic Invoicing System (EIMS) Tax Invoice receipt.
 * Matches legacy-prototype/mor_receipt.html and MOR_Portal_Tax_Invoice.pdf with exact fidelity.
 */
@Service
public class ReceiptRenderingService {

    private final MorInvoiceCanonicalizationService canonicalizationService;
    private final QrCodeService qrCodeService;
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    public ReceiptRenderingService(
            MorInvoiceCanonicalizationService canonicalizationService,
            QrCodeService qrCodeService
    ) {
        this.canonicalizationService = canonicalizationService;
        this.qrCodeService = qrCodeService;
    }

    /**
     * Renders the complete, official MoR Tax Invoice HTML document.
     */
    public String renderHtmlTaxInvoice(Invoice invoice, TaxpayerProfile seller) {
        // 1. Ensure authoritative QR image is generated from canonical payload
        String rawQr = invoice.getSignedQr();
        String qrImageBase64 = null;
        String qrJsonStr = null;

        if (rawQr != null && !rawQr.isBlank()) {
            if (rawQr.startsWith("data:image/") || rawQr.startsWith("iVBORw0KGgo")) {
                qrImageBase64 = rawQr;
            } else {
                // rawQr is a URL or JSON string or data payload; generate high-res QR code PNG
                qrImageBase64 = qrCodeService.generateQrCodeBase64(rawQr, 600, 600);
                qrJsonStr = rawQr;
            }
        }

        if (qrImageBase64 == null || qrImageBase64.isBlank()) {
            if (invoice.getIrn() != null && !invoice.getIrn().isBlank()) {
                String portalUrl = "https://portal.mor.gov.et/public/invoice?irn=" + invoice.getIrn();
                qrImageBase64 = qrCodeService.generateQrCodeBase64(portalUrl, 600, 600);
                qrJsonStr = portalUrl;
            } else {
                qrJsonStr = canonicalizationService.buildCanonicalQrData(
                        invoice, seller, invoice.getSignedInvoice(), invoice.getAckDate()
                );
                qrImageBase64 = qrCodeService.generateQrCodeBase64(qrJsonStr, 600, 600);
            }
        }

        // 2. Build canonical view model
        MorReceiptViewModel vm = canonicalizationService.buildReceiptViewModel(
                invoice, seller, qrImageBase64, qrJsonStr
        );

        return renderFromViewModel(vm);
    }

    /**
     * Renders official HTML from a canonical MorReceiptViewModel.
     */
    public String renderFromViewModel(MorReceiptViewModel vm) {
        StringBuilder itemsRows = new StringBuilder();
        for (MorReceiptViewModel.ReceiptLineItem it : vm.lines()) {
            itemsRows.append(String.format("""
                <tr>
                    <td class="text-center">%d</td>
                    <td class="text-left"><strong>%s</strong></td>
                    <td class="text-center">%s</td>
                    <td class="text-center">%s</td>
                    <td class="text-center">%s</td>
                    <td class="text-right">%s</td>
                    <td class="text-center">%s</td>
                    <td class="text-right"><strong>%s</strong></td>
                </tr>
            """,
                    it.lineNumber(),
                    escape(it.productDescription()),
                    escape(it.natureOfSupplies()),
                    escape(it.unit()),
                    formatNumber(it.quantity(), 0),
                    formatMoney(it.unitPrice()),
                    escape(it.taxCode()),
                    formatMoney(it.totalLineAmount())
            ));
        }

        String duplicateBanner = vm.isDuplicate() ? String.format(
                "<div class=\"duplicate-watermark\">*** DUPLICATE - REPRINT OF REGISTERED TAX INVOICE *** (COUNT: %d)</div>",
                vm.reprintCount()
        ) : "";

        String qrSrc = "";
        if (vm.qrCodeBase64() != null && !vm.qrCodeBase64().isBlank()) {
            if (vm.qrCodeBase64().startsWith("data:image")) {
                qrSrc = vm.qrCodeBase64();
            } else if (vm.qrCodeBase64().startsWith("iVBORw0KGgo")) {
                qrSrc = "data:image/png;base64," + vm.qrCodeBase64();
            } else {
                // In case a raw URL/string was passed in viewModel
                String generatedBase64 = qrCodeService.generateQrCodeBase64(vm.qrCodeBase64(), 600, 600);
                qrSrc = "data:image/png;base64," + generatedBase64;
            }
        }

        return String.format("""
<!DOCTYPE html>
<html lang="am">
<head>
  <meta charset="UTF-8">
  <title>TAX INVOICE - %s</title>
  <style>
    @page {
      size: A4 portrait;
      margin: 10mm 12mm 10mm 12mm;
    }
    @import url('https://fonts.googleapis.com/css2?family=Noto+Sans+Ethiopic:wght@400;500;600;700&display=swap');
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      font-family: inherit;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    body {
      font-family: 'Noto Sans Ethiopic', 'Noto Serif Ethiopic', 'Abyssinica SIL', 'Nyala', 'Segoe UI', Arial, sans-serif;
      font-size: 10px;
      color: #222;
      background: #fff;
      line-height: 1.25;
    }
    .receipt-container {
      width: 100%%;
      max-width: 780px;
      margin: 0 auto;
      padding: 6px;
    }
    .duplicate-watermark {
      background: #fef2f2;
      border: 2px dashed #dc2626;
      color: #b91c1c;
      padding: 8px 12px;
      text-align: center;
      font-weight: bold;
      font-size: 13px;
      border-radius: 6px;
      margin-bottom: 10px;
      letter-spacing: 0.5px;
    }
    .header-table {
      width: 100%%;
      border-collapse: collapse;
      margin-bottom: 8px;
    }
    .header-left {
      width: 60%%;
      vertical-align: top;
      padding-right: 12px;
    }
    .header-right {
      width: 40%%;
      vertical-align: top;
      text-align: right;
    }
    .logo-title-box {
      margin-bottom: 6px;
    }
    .trader-name {
      font-size: 15px;
      font-weight: bold;
      color: #111;
      margin-bottom: 2px;
    }
    .trader-sub {
      font-size: 10px;
      color: #444;
      line-height: 1.35;
    }
    .invoice-title-badge {
      margin-top: 5px;
      font-weight: bold;
      font-size: 11px;
      color: #000;
    }
    .irn-mrc-block {
      margin-top: 6px;
      font-size: 9.5px;
      word-break: break-all;
      color: #222;
    }
    .irn-mrc-block strong {
      color: #000;
    }
    .qr-container {
      display: inline-block;
      text-align: center;
      margin-top: 4px;
    }
    .qr-container img {
      width: 165px;
      height: 165px;
      display: block;
      border: none;
      padding: 0;
      background: #fff;
      image-rendering: -webkit-optimize-contrast;
      image-rendering: pixelated;
    }
    .meta-box {
      margin-bottom: 4px;
      font-size: 10.5px;
    }
    .meta-box table {
      margin-left: auto;
      border-collapse: collapse;
    }
    .meta-box td {
      padding: 1px 4px;
    }
    .divider {
      height: 1px;
      background-color: #777;
      margin: 6px 0 8px 0;
    }
    .address-table {
      width: 100%%;
      border-collapse: collapse;
      margin-bottom: 10px;
      font-size: 9.5px;
    }
    .address-col {
      width: 50%%;
      vertical-align: top;
      padding-right: 15px;
    }
    .party-title {
      font-size: 11px;
      font-weight: bold;
      margin-bottom: 4px;
      border-bottom: 1px solid #ddd;
      padding-bottom: 2px;
    }
    .field-grid {
      display: table;
      width: 100%%;
      margin-top: 2px;
    }
    .field-row {
      display: table-row;
    }
    .field-label {
      display: table-cell;
      width: 42%%;
      padding: 2px 0;
      color: #444;
    }
    .field-value {
      display: table-cell;
      width: 58%%;
      padding: 2px 0;
      font-weight: 600;
      color: #111;
    }
    .items-table {
      width: 100%%;
      border-collapse: collapse;
      margin-bottom: 8px;
      font-size: 9.5px;
    }
    .items-table th,
    .items-table td {
      border: 1px solid #bbb;
      padding: 5px 6px;
      vertical-align: middle;
    }
    .items-table th {
      background-color: #f3f3f3;
      font-weight: 600;
      text-align: center;
      line-height: 1.25;
    }
    .text-left { text-align: left; }
    .text-center { text-align: center; }
    .text-right { text-align: right; }
    .totals-wrapper {
      width: 100%%;
      margin-bottom: 8px;
    }
    .totals-table {
      margin-left: auto;
      width: 48%%;
      border-collapse: collapse;
      font-size: 9.5px;
    }
    .totals-table td {
      border: 1px solid #bbb;
      padding: 4px 6px;
    }
    .totals-label {
      text-align: right;
      width: 65%%;
      color: #333;
    }
    .totals-val {
      text-align: right;
      font-weight: bold;
      width: 35%%;
      color: #000;
    }
    .in-words-row {
      margin: 6px 0 8px 0;
      padding: 5px 6px;
      background: #fafafa;
      border: 1px solid #e0e0e0;
      font-size: 9.5px;
    }
    .bottom-meta-table {
      width: 100%%;
      border-collapse: collapse;
      margin-top: 4px;
      margin-bottom: 8px;
      font-size: 9px;
    }
    .bottom-meta-table td {
      padding: 3px 4px;
      vertical-align: top;
      border-bottom: 1px dotted #ccc;
    }
    .bottom-label { color: #555; }
    .bottom-value { font-weight: 600; color: #111; }
    .legal-footer {
      border-top: 1px solid #888;
      padding-top: 5px;
      text-align: center;
      font-size: 8.5px;
      color: #444;
      margin-top: 10px;
    }
  </style>
</head>
<body>
  <div class="receipt-container">
    %s
    <!-- Header -->
    <table class="header-table">
      <tr>
        <td class="header-left">
          <div class="logo-title-box">
            <div class="trader-name">%s</div>
            <div class="trader-sub">%s (%s)</div>
            <div class="trader-sub">Addis Ababa, Ethiopia | Tel: %s</div>
          </div>
          <div class="invoice-title-badge">
            %s<br>
            የሽያጭ አይነት: %s (%s)
          </div>
          <div class="irn-mrc-block">
            <strong>IRN:</strong> %s<br>
            <strong>MRC:</strong> %s
          </div>
        </td>
        <td class="header-right">
          <div class="meta-box">
            <table>
              <tr>
                <td style="text-align: right;">የደረሰኝ ቁጥር<br>Invoice No</td>
                <td style="font-size: 15px; font-weight: bold; text-align: right; padding-left: 8px;">%s</td>
              </tr>
              <tr>
                <td style="text-align: right;">ቀን<br>Date</td>
                <td style="font-weight: 600; text-align: right; padding-left: 8px;">%s</td>
              </tr>
            </table>
          </div>
          <div class="qr-container">
            <img src="%s" alt="Official EIMS QR Code">
          </div>
        </td>
      </tr>
    </table>

    <div class="divider"></div>

    <!-- 2 Column Addresses -->
    <table class="address-table">
      <tr>
        <td class="address-col">
          <div class="party-title">ከ / From: %s</div>
          <div class="field-grid">
            <div class="field-row">
              <div class="field-label">አድራሻ / Address:</div>
              <div class="field-value">ከተማ/City: %s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ዞን / Zone/Sub city:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ወረዳ / Woreda:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ቀበሌ / Kebele:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">የቤ/ቁ / H/No:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">የሻጭ ተ.እ.ታ ቁጥር / Seller VAT Reg:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">የሻጭ ግብር ከፋይ መለያ / Seller TIN:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ንዑስ/ቁ / Sub-TIN:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ለተ.እ.ታ የተመዘገበበት ቀን / Reg Date:</div>
              <div class="field-value">%s</div>
            </div>
          </div>
        </td>

        <td class="address-col">
          <div class="party-title">ለ / To: %s</div>
          <div class="field-grid">
            <div class="field-row">
              <div class="field-label">አድራሻ / Address:</div>
              <div class="field-value">ከተማ/City: %s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ዞን / Zone/Sub city:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ወረዳ / Woreda:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ቀበሌ / Kebele:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">የቤ/ቁ / H/No:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">የገዥ ተ.እ.ታ ቁጥር / Customer VAT:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">የገዥ ግብር ከፋይ መለያ / Customer TIN:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ንዑስ/ቁ / Sub-TIN:</div>
              <div class="field-value">%s</div>
            </div>
            <div class="field-row">
              <div class="field-label">ለተ.እ.ታ የተመዘገበበት ቀን / Reg Date:</div>
              <div class="field-value">%s</div>
            </div>
          </div>
        </td>
      </tr>
    </table>

    <!-- Line Items Table -->
    <table class="items-table">
      <thead>
        <tr>
          <th style="width: 5%%;">ተ/ቁ<br>No.</th>
          <th style="width: 33%%;">የዕቃው አይነት<br>Description</th>
          <th style="width: 14%%;">ምድብ<br>Nature of Supply</th>
          <th style="width: 8%%;">መለኪያ<br>UoM</th>
          <th style="width: 7%%;">ብዛት<br>Qty</th>
          <th style="width: 12%%;">የአንዱ ዋጋ<br>Unit Price</th>
          <th style="width: 9%%;">ታክስ ኮድ<br>Tax Code</th>
          <th style="width: 12%%;">ጠቅላላ ዋጋ<br>Total Amount</th>
        </tr>
      </thead>
      <tbody>
        %s
      </tbody>
    </table>

    <!-- Totals Table -->
    <div class="totals-wrapper">
      <table class="totals-table">
        <tr>
          <td class="totals-label">ድምር (የገንዘብ አይነት) / Total ETB</td>
          <td class="totals-val">%s</td>
        </tr>
        <tr>
          <td class="totals-label">ታክስ የሚከፈልበት ድምር / Taxable Total</td>
          <td class="totals-val">%s</td>
        </tr>
        <tr>
          <td class="totals-label">የተ.እ.ታ ታክስ / VAT Tax 15%%</td>
          <td class="totals-val">%s</td>
        </tr>
        <tr style="background-color: #f8f8f8;">
          <td class="totals-label"><strong>ጠቅላላ ዋጋ ከታክስ ጋር / Total including Tax</strong></td>
          <td class="totals-val" style="font-size: 11px;">%s</td>
        </tr>
        <tr>
          <td class="totals-label">በገዥ ተይዞ የቀረ መጠን / Withheld Amount</td>
          <td class="totals-val">%s</td>
        </tr>
        <tr>
          <td class="totals-label">የተከፈለ መጠን / Collected Amount</td>
          <td class="totals-val">%s</td>
        </tr>
        <tr>
          <td class="totals-label">Exchange Rate to Birr</td>
          <td class="totals-val">%s</td>
        </tr>
      </table>
    </div>

    <!-- In Words -->
    <div class="in-words-row">
      <strong>ጠቅላላ ዋጋ በፊደል ብር / In Words Birr:</strong> %s
    </div>

    <!-- Payment & Operational Metadata -->
    <table class="bottom-meta-table">
      <tr>
        <td style="width: 25%%;">
          <span class="bottom-label">የክፍያ ሁኔታ / Mode of Payment:</span><br>
          <span class="bottom-value">%s</span>
        </td>
        <td style="width: 25%%;">
          <span class="bottom-label">አይነት / Type/Method:</span><br>
          <span class="bottom-value">%s</span>
        </td>
        <td style="width: 25%%;">
          <span class="bottom-label">ማጣቀሻ / Reference:</span><br>
          <span class="bottom-value">%s</span>
        </td>
        <td style="width: 25%%;">
          <span class="bottom-label">ቫውቸር ቁጥር / Voucher No:</span><br>
          <span class="bottom-value">%s</span>
        </td>
      </tr>
      <tr>
        <td>
          <span class="bottom-label">አገልግሎት ሰጭ / Service Provider:</span><br>
          <span class="bottom-value">%s</span>
        </td>
        <td>
          <span class="bottom-label">ቲኬት / Ticket:</span><br>
          <span class="bottom-value">%s</span>
        </td>
        <td>
          <span class="bottom-label">ከ / From:</span> %s &nbsp; <span class="bottom-label">እስከ / To:</span> %s
        </td>
        <td>
          <span class="bottom-label">የተቀባይ ስምና ፊርማ / Receiver:</span><br>
          <span class="bottom-value">%s</span>
        </td>
      </tr>
    </table>

    <!-- Legal Compliance Note -->
    <div class="legal-footer">
      %s
    </div>
  </div>
</body>
</html>
""",
                escape(vm.invoiceNumber()),
                duplicateBanner,
                escape(vm.sellerLegalName()),
                escape(vm.sellerAmharicSubtitle()),
                escape(vm.sellerEnglishSubtitle()),
                escape(vm.sellerPhone()),
                escape(vm.transactionTypeBadgeAmharic()),
                escape(vm.salesTypeAmharic()),
                escape(vm.salesTypeEnglish()),
                escape(vm.irn()),
                escape(vm.mrc()),
                escape(vm.invoiceNumber()),
                escape(vm.formattedDate()),
                qrSrc,
                escape(vm.sellerLegalName()),
                escape(vm.sellerCity()),
                escape(vm.sellerZone()),
                escape(vm.sellerWoreda()),
                escape(vm.sellerKebele()),
                escape(vm.sellerHouseNumber()),
                escape(vm.sellerVatNumber()),
                escape(vm.sellerTin()),
                escape(vm.sellerSubTin()),
                escape(vm.sellerVatRegDate()),
                escape(vm.buyerLegalName()),
                escape(vm.buyerCity()),
                escape(vm.buyerZone()),
                escape(vm.buyerWoreda()),
                escape(vm.buyerKebele()),
                escape(vm.buyerHouseNumber()),
                escape(vm.buyerVatNumber()),
                escape(vm.buyerTin()),
                escape(vm.buyerSubTin()),
                escape(vm.buyerVatRegDate()),
                itemsRows.toString(),
                formatMoney(vm.preTaxTotal()),
                formatMoney(vm.taxableTotal()),
                formatMoney(vm.vatTotal()),
                formatMoney(vm.grandTotal()),
                formatMoney(vm.withheldAmount()),
                formatMoney(vm.collectedAmount()),
                escape(vm.exchangeRate()),
                escape(vm.inWordsBirr()),
                escape(vm.paymentMode()),
                escape(vm.paymentTypeMethod()),
                escape(vm.reference()),
                escape(vm.voucherNo()),
                escape(vm.serviceProvider()),
                escape(vm.ticket()),
                escape(vm.transportFrom()),
                escape(vm.transportTo()),
                escape(vm.receiverName()),
                escape(vm.legalFooter())
        );
    }

    private String escape(String input) {
        return input == null ? "" : HtmlUtils.htmlEscape(input);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0.00";
        return CURRENCY_FORMAT.format(amount);
    }

    private String formatNumber(BigDecimal qty, int decimals) {
        if (qty == null) return "0";
        if (decimals == 0) {
            return String.valueOf(qty.intValue());
        }
        return qty.setScale(decimals, java.math.RoundingMode.HALF_UP).toString();
    }
}
