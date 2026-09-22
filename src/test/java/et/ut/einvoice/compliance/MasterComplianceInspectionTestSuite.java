package et.ut.einvoice.compliance;

import et.ut.einvoice.adjustments.domain.NoteType;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import et.ut.einvoice.adjustments.dto.CreateAdjustmentRequest;
import et.ut.einvoice.adjustments.service.AdjustmentService;
import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.dto.CreateCancellationRequestDto;
import et.ut.einvoice.cancellation.service.CancellationService;
import et.ut.einvoice.documents.service.ReceiptRenderingService;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.receipts.domain.Receipt;
import et.ut.einvoice.receipts.domain.ReceiptType;
import et.ut.einvoice.receipts.dto.CreateReceiptRequest;
import et.ut.einvoice.receipts.service.ReceiptService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class MasterComplianceInspectionTestSuite {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private AdjustmentService adjustmentService;

    @Autowired
    private CancellationService cancellationService;

    @Autowired
    private ReceiptRenderingService receiptRenderingService;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        TenantContextHolder.setContext(TenantContext.create(tenantId, "tester", Set.of("ROLE_TENANT_ADMIN")));

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantId,
                "0041746204",
                "43256663343256663322",
                "UT Systems PLC",
                "UT Systems",
                "13",
                "574",
                "+251911310694",
                "contact@utsolutionsplc.com",
                "8EFBBDD7FF",
                "POS"
        ));

        // Mock default successful response from MoR EIRS
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "TEST-IRN-" + UUID.randomUUID();
                    String rrn = "TEST-RRN-" + UUID.randomUUID();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice-mock");
                });

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.cancelInvoice(anyString(), anyString(), anyString()))
                .thenReturn(new GovernmentRegistrationProvider.CancellationResult(true, "CANCEL-ACK-12345", "Cancelled"));
    }

    @Test
    @DisplayName("Case ID: IRC-P01 — Register B2C Sales Invoice Without Buyer TIN and With Multi-Rate VAT")
    void test_IRC_P01_RegisterB2CSalesInvoice() {
        var items = List.of(
                new CreateInvoiceRequest.LineItemRequest("SRV-01", "Consulting", "goods", "HRS", new BigDecimal("2.0"), new BigDecimal("5000.00"), BigDecimal.ZERO, "VAT15", null),
                new CreateInvoiceRequest.LineItemRequest("SRV-02", "Training", "goods", "PCS", new BigDecimal("1.0"), new BigDecimal("2000.00"), BigDecimal.ZERO, "VAT0", null),
                new CreateInvoiceRequest.LineItemRequest("SRV-03", "Exempt Service", "goods", "PCS", new BigDecimal("1.0"), new BigDecimal("1500.00"), BigDecimal.ZERO, "VATEX", null)
        );

        var buyer = new CreateInvoiceRequest.BuyerRequest("Walk-in Retail Buyer", null, "111222333", "KID", "0911223344", "buyer@example.com", "13", "574", "01", "101");
        var req = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null);

        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, UUID.randomUUID().toString());

        assertNotNull(response);
        assertEquals(InvoiceStatus.REGISTERED, response.status());
        assertNotNull(response.irn());
        assertNotNull(response.signedQr());
        assertEquals(3, response.lines().size());

        // Pre-tax: 10000 + 2000 + 1500 = 13500.00
        assertEquals(new BigDecimal("13500.00"), response.preTaxTotal());
        // Tax: 10000 * 0.15 = 1500.00 (VAT0 and VATEX contribute 0)
        assertEquals(new BigDecimal("1500.00"), response.taxTotal());
        assertEquals(new BigDecimal("15000.00"), response.grandTotal());
    }

    @Test
    @DisplayName("Case ID: IRC-P02 — Register B2B Sales Invoice with Buyer TIN, Discounts & Excise Tax")
    void test_IRC_P02_RegisterB2BSalesInvoice() {
        var items = List.of(
                new CreateInvoiceRequest.LineItemRequest("PROD-01", "Enterprise Cloud Server", "goods", "SET", new BigDecimal("2.0"), new BigDecimal("50000.00"), new BigDecimal("5000.00"), "VAT15", new BigDecimal("0.1000"))
        );

        var buyer = new CreateInvoiceRequest.BuyerRequest("Commercial Trading Partner PLC", "0016324478", null, null, "0911445566", "partner@example.com", "13", "01", "02", "204");
        var req = new CreateInvoiceRequest(TransactionType.B2B, "BANK_TRANSFER", "CREDIT_30", buyer, items, null, null, null);

        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, UUID.randomUUID().toString());

        assertNotNull(response);
        assertEquals(InvoiceStatus.REGISTERED, response.status());
        assertEquals("0016324478", response.buyer().tin());
        // Pre-tax: (2 * 50000) - 5000 discount = 95000.00
        assertEquals(new BigDecimal("95000.00"), response.preTaxTotal());
        // Excise 10%: 9500.00
        assertEquals(new BigDecimal("9500.00"), response.exciseTotal());
        // Tax base: 95000 + 9500 = 104500.00. VAT 15%: 15675.00
        assertEquals(new BigDecimal("15675.00"), response.taxTotal());
        assertEquals(new BigDecimal("120175.00"), response.grandTotal());
    }

    @Test
    @DisplayName("Case ID: IRC-N08 — Negative Test: Register B2B Sales Invoice with Invalid Buyer TIN")
    void test_IRC_N08_Negative_InvalidBuyerTin() {
        var items = List.of(
                new CreateInvoiceRequest.LineItemRequest("SRV-01", "Software", "goods", "PCS", BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null)
        );

        // Invalid TIN 000000000
        var buyer = new CreateInvoiceRequest.BuyerRequest("Invalid Corp", "000000000", null, null, "0911000000", "inv@test.com", "13", "01", "01", "1");
        var req = new CreateInvoiceRequest(TransactionType.B2B, "CASH", "IMMEDIATE", buyer, items, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.createAndRegisterInvoice(req, UUID.randomUUID().toString()));
        assertEquals("INVALID_BUYER_TIN", ex.getCode());
    }

    @Test
    @DisplayName("Case ID: IRC-P03 — Register Sales Receipt from a Registered Invoice")
    void test_IRC_P03_RegisterSalesReceipt() {
        // First register an invoice
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("5000.00"), BigDecimal.ZERO, "VAT15", null));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer One", null, null, null, "0911000000", null, "13", "01", null, null);
        var inv = invoiceService.createAndRegisterInvoice(new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null), null);

        // Register Sales Receipt referencing invoice IRN
        CreateReceiptRequest receiptReq = new CreateReceiptRequest(inv.irn(), new BigDecimal("5750.00"), BigDecimal.ZERO);
        Receipt receipt = receiptService.createReceipt(ReceiptType.SALES_RECEIPT, receiptReq);

        assertNotNull(receipt);
        assertEquals("REGISTERED", receipt.getStatus());
        assertEquals(inv.irn(), receipt.getInvoiceIrn());
        assertNotNull(receipt.getRrn());
        assertNotNull(receipt.getQrCode());
    }

    @Test
    @DisplayName("Case ID: IRC-P04 — Register Withhold Receipt from a Registered Invoice")
    void test_IRC_P04_RegisterWithholdReceipt() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Consulting", "goods", "PCS", BigDecimal.ONE, new BigDecimal("10000.00"), BigDecimal.ZERO, "VAT15", null));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer B2B", "0016324478", null, null, "0911000000", null, "13", "01", null, null);
        var inv = invoiceService.createAndRegisterInvoice(new CreateInvoiceRequest(TransactionType.B2B, "BANK_TRANSFER", "IMMEDIATE", buyer, items, null, null, null), null);

        CreateReceiptRequest withReq = new CreateReceiptRequest(inv.irn(), new BigDecimal("11500.00"), new BigDecimal("200.00"));
        Receipt receipt = receiptService.createReceipt(ReceiptType.WITHHOLDING_RECEIPT, withReq);

        assertNotNull(receipt);
        assertEquals(ReceiptType.WITHHOLDING_RECEIPT, receipt.getReceiptType());
        assertEquals(new BigDecimal("200.00"), receipt.getWithholdingAmount());
    }

    @Test
    @DisplayName("Case ID: IRC-N09 — Negative Test: Generate Receipt from Cancelled Invoice")
    void test_IRC_N09_Negative_ReceiptForCancelledInvoice() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, null, null, null, null, null, null);
        var inv = invoiceService.createAndRegisterInvoice(new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null), null);

        // Cancel invoice
        cancellationService.requestCancellation(new CreateCancellationRequestDto(inv.irn(), "DUPLICATE", "Mistaken order"));

        // Attempt receipt generation on cancelled invoice
        CreateReceiptRequest receiptReq = new CreateReceiptRequest(inv.irn(), new BigDecimal("1150.00"), BigDecimal.ZERO);
        BusinessException ex = assertThrows(BusinessException.class, () -> receiptService.createReceipt(ReceiptType.SALES_RECEIPT, receiptReq));
        assertEquals("CANNOT_ISSUE_RECEIPT_FOR_CANCELLED_INVOICE", ex.getCode());
    }

    @Test
    @DisplayName("Case ID: IRC-P06 — Register Credit Memo & Limit Check")
    void test_IRC_P06_RegisterCreditMemo() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("10000.00"), BigDecimal.ZERO, "VAT15", null));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, null, null, null, null, null, null);
        var inv = invoiceService.createAndRegisterInvoice(new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null), null);

        // Valid Credit Note of 2000 ETB
        CreateAdjustmentRequest adjReq = new CreateAdjustmentRequest(inv.irn(), "Price Reduction", new BigDecimal("2000.00"), new BigDecimal("300.00"));
        TaxAdjustment adj = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, adjReq);

        assertNotNull(adj);
        assertEquals(NoteType.CREDIT_NOTE, adj.getNoteType());
        assertTrue(adj.getIrn().startsWith("CN-"));

        // Invalid Credit Note exceeding invoice total (11,500.00 ETB)
        CreateAdjustmentRequest excessiveReq = new CreateAdjustmentRequest(inv.irn(), "Excessive Reduction", new BigDecimal("15000.00"), new BigDecimal("2250.00"));
        BusinessException ex = assertThrows(BusinessException.class, () -> adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, excessiveReq));
        assertEquals("CREDIT_AMOUNT_EXCEEDS_INVOICE_VALUE", ex.getCode());
    }

    @Test
    @DisplayName("Case ID: IRC-P05 & IRC-N010 — Invoice Cancellation & Duplicate Rejection")
    void test_IRC_P05_And_N010_InvoiceCancellation() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, null, null, null, null, null, null);
        var inv = invoiceService.createAndRegisterInvoice(new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null), null);

        // First cancellation succeeds (IRC-P05)
        CancellationRequest canReq = cancellationService.requestCancellation(new CreateCancellationRequestDto(inv.irn(), "DATA_ENTRY_ERROR", "Wrong quantity entered"));
        assertNotNull(canReq);
        assertEquals("APPROVED", canReq.getState().name());

        // Second cancellation on already cancelled invoice fails (IRC-N010)
        BusinessException ex = assertThrows(BusinessException.class, () ->
                cancellationService.requestCancellation(new CreateCancellationRequestDto(inv.irn(), "DUPLICATE", "Try again")));
        assertEquals("INVOICE_ALREADY_CANCELLED", ex.getCode());
    }

    @Test
    @DisplayName("Case ID: ADD-P001 — Authoritative Printing Layout & Duplicate Markings")
    void test_ADD_P001_PrintingLayout() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null));
        var buyer = new CreateInvoiceRequest.BuyerRequest("B2B Corp", "0016324478", null, null, "0911000000", "test@test.com", "13", "01", null, null);
        var inv = invoiceService.createAndRegisterInvoice(new CreateInvoiceRequest(TransactionType.B2B, "CASH", "IMMEDIATE", buyer, items, null, null, null), null);

        Invoice entity = invoiceRepository.findById(inv.id()).orElseThrow();
        TaxpayerProfile profile = taxpayerProfileRepository.findById(tenantId).orElseThrow();

        // 1. First print layout
        String originalHtml = receiptRenderingService.renderHtmlTaxInvoice(entity, profile);
        assertNotNull(originalHtml);
        assertTrue(originalHtml.contains("TAX INVOICE"));
        assertTrue(originalHtml.contains(inv.irn()));
        assertFalse(originalHtml.contains("*** DUPLICATE"));

        // 2. Reprint layout per Directive Art. 22
        entity.recordReprint();
        String reprintHtml = receiptRenderingService.renderHtmlTaxInvoice(entity, profile);
        assertTrue(reprintHtml.contains("*** DUPLICATE - REPRINT OF REGISTERED TAX INVOICE ***"));
    }
}
