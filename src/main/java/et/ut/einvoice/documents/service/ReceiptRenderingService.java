package et.ut.einvoice.documents.service;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptRenderingService {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    public String renderHtmlTaxInvoice(Invoice invoice, TaxpayerProfile seller) {
        String formattedDate = invoice.getInvoiceDate().atZone(ZoneId.of("Africa/Addis_Ababa")).format(DISPLAY_FORMATTER);
        String qrImageSrc = invoice.getSignedQr() != null ? "data:image/png;base64," + invoice.getSignedQr() : "";
        boolean isDuplicate = invoice.getReprintCount() > 0;

        StringBuilder linesHtml = new StringBuilder();
        for (InvoiceLine l : invoice.getLines()) {
            linesHtml.append(String.format("""
                <tr>
                    <td>%d</td>
                    <td><strong>%s</strong><br><small style="color:#64748b;">Code: %s</small></td>
                    <td class="text-right">%.2f %s</td>
                    <td class="text-right">%,.2f ETB</td>
                    <td class="text-right">%,.2f ETB</td>
                    <td class="text-right"><strong>%,.2f ETB</strong></td>
                </tr>
            """, l.getLineNumber(), escape(l.getProductDescription()), escape(l.getItemCode()),
                    l.getQuantity(), escape(l.getUnit()), l.getUnitPrice(), l.getTaxAmount(), l.getTotalLineAmount()));
        }

        String duplicateBanner = isDuplicate ?
                "<div style='background:#fef2f2; color:#b91c1c; padding:10px; text-align:center; font-weight:bold; border-radius:6px; margin-bottom:20px; font-size:16px;'>*** DUPLICATE - REPRINT OF REGISTERED TAX INVOICE ***</div>" : "";

        return String.format("""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Tax Invoice - %s</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f8fafc; color: #0f172a; padding: 40px 20px; display: flex; justify-content: center; }
                .invoice-card { background: white; width: 100%%; max-width: 820px; padding: 44px; border-radius: 14px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); }
                .header { display: flex; justify-content: space-between; border-bottom: 2px solid #e2e8f0; padding-bottom: 20px; margin-bottom: 24px; }
                .company-info h1 { font-size: 24px; color: #1e3a8a; margin-bottom: 4px; }
                .company-info p { font-size: 13px; color: #64748b; line-height: 1.4; }
                .tax-badge { display: inline-block; background: #eff6ff; color: #1e3a8a; padding: 4px 8px; border-radius: 4px; font-weight: 600; font-size: 12px; margin-top: 6px; }
                .grid-info { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 28px; }
                .info-box { background: #f8fafc; border: 1px solid #e2e8f0; padding: 16px; border-radius: 8px; font-size: 13.5px; }
                table { width: 100%%; border-collapse: collapse; margin-bottom: 24px; }
                th, td { padding: 12px; border-bottom: 1px solid #e2e8f0; font-size: 13.5px; text-align: left; }
                th { background: #f8fafc; color: #475569; font-size: 12px; text-transform: uppercase; }
                .text-right { text-align: right; }
                .totals-area { display: flex; justify-content: space-between; margin-bottom: 28px; }
                .qr-box { text-align: center; border: 1px dashed #cbd5e1; padding: 12px; border-radius: 8px; width: 180px; }
                .qr-box img { width: 150px; height: 150px; }
                .summary-table { width: 320px; font-size: 14px; }
                .summary-row { display: flex; justify-content: space-between; padding: 6px 0; color: #475569; }
                .grand-total { border-top: 2px solid #0f172a; margin-top: 8px; padding-top: 8px; font-size: 18px; font-weight: bold; color: #0f172a; }
                .irn-box { background: #f8fafc; border: 1px solid #e2e8f0; padding: 12px; border-radius: 6px; font-size: 11.5px; color: #475569; }
            </style>
        </head>
        <body>
            <div class="invoice-card">
                %s
                <div class="header">
                    <div class="company-info">
                        <h1>%s</h1>
                        <p>Region: %s, Woreda: %s, Addis Ababa, Ethiopia</p>
                        <p>Phone: %s | Email: %s</p>
                        <div class="tax-badge">TIN: %s | VAT: %s</div>
                    </div>
                    <div style="text-align: right;">
                        <h2 style="font-size: 26px; color: #0f172a;">TAX INVOICE</h2>
                        <p>Invoice #: <strong>%s</strong></p>
                        <p>Date: <strong>%s</strong></p>
                        <p>Status: <strong style="color: #16a34a;">%s</strong></p>
                    </div>
                </div>

                <div class="grid-info">
                    <div class="info-box">
                        <strong>Billed To (Buyer):</strong><br>
                        %s<br>
                        Phone: %s | Email: %s<br>
                        TIN: %s
                    </div>
                    <div class="info-box">
                        <strong>Transaction Details:</strong><br>
                        Type: %s<br>
                        Payment Mode: %s<br>
                        System: %s (%s)
                    </div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Description</th>
                            <th class="text-right">Qty</th>
                            <th class="text-right">Unit Price</th>
                            <th class="text-right">VAT (15%%)</th>
                            <th class="text-right">Total</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>

                <div class="totals-area">
                    <div class="qr-box">
                        <img src="%s" alt="EIMS QR Code">
                        <div style="font-size: 10px; color: #64748b; margin-top: 4px;">Official MoR EIMS QR</div>
                    </div>
                    <div class="summary-table">
                        <div class="summary-row"><span>Pre-Tax Total:</span><span>%,.2f ETB</span></div>
                        <div class="summary-row"><span>VAT Total:</span><span>%,.2f ETB</span></div>
                        <div class="summary-row grand-total"><span>Grand Total:</span><span>%,.2f ETB</span></div>
                    </div>
                </div>

                <div class="irn-box">
                    <div><strong>IRN:</strong> %s</div>
                    <div><strong>MoR Acknowledged At:</strong> %s</div>
                    <div><strong>RRN:</strong> %s</div>
                </div>
            </div>
        </body>
        </html>
        """,
                escape(invoice.getDocumentNumber()),
                duplicateBanner,
                escape(seller.getLegalName()),
                escape(seller.getRegion()),
                escape(seller.getWoreda()),
                escape(seller.getPhone()),
                escape(seller.getEmail()),
                escape(seller.getTin()),
                seller.getVatNumber() != null ? escape(seller.getVatNumber()) : "N/A",
                escape(invoice.getDocumentNumber()),
                formattedDate,
                invoice.getStatus().name(),
                invoice.getBuyerLegalName() != null ? escape(invoice.getBuyerLegalName()) : "Walk-in Customer",
                invoice.getBuyerPhone() != null ? escape(invoice.getBuyerPhone()) : "N/A",
                invoice.getBuyerEmail() != null ? escape(invoice.getBuyerEmail()) : "N/A",
                invoice.getBuyerTin() != null ? escape(invoice.getBuyerTin()) : "N/A",
                invoice.getTransactionType().name(),
                escape(invoice.getPaymentMode()),
                escape(seller.getSystemType()),
                escape(seller.getSystemNumber()),
                linesHtml.toString(),
                qrImageSrc,
                invoice.getPreTaxTotal(),
                invoice.getTaxTotal(),
                invoice.getGrandTotal(),
                invoice.getIrn() != null ? escape(invoice.getIrn()) : "N/A",
                invoice.getAckDate() != null ? escape(invoice.getAckDate()) : "N/A",
                invoice.getRrn() != null ? escape(invoice.getRrn()) : "N/A"
        );
    }

    private String escape(String input) {
        return input == null ? "" : HtmlUtils.htmlEscape(input);
    }
}
