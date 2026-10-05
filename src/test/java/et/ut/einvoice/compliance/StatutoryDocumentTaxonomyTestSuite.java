package et.ut.einvoice.compliance;

import et.ut.einvoice.cashreceipt.domain.CashReceipt;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPaymentMethod;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import et.ut.einvoice.cashreceipt.dto.CreateCashReceiptRequest;
import et.ut.einvoice.cashreceipt.service.CashReceiptService;
import et.ut.einvoice.creditsales.domain.CreditSettlement;
import et.ut.einvoice.creditsales.domain.CreditStatus;
import et.ut.einvoice.creditsales.dto.CreditAccountSummaryResponse;
import et.ut.einvoice.creditsales.dto.SettleCreditRequest;
import et.ut.einvoice.creditsales.service.CreditSettlementService;
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
import et.ut.einvoice.purchasevoucher.domain.PurchaseVoucher;
import et.ut.einvoice.purchasevoucher.domain.UnavailableReceiptReason;
import et.ut.einvoice.purchasevoucher.dto.CreatePurchaseVoucherRequest;
import et.ut.einvoice.purchasevoucher.service.PurchaseVoucherService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.withholding.domain.WithholdingReceipt;
import et.ut.einvoice.withholding.domain.WithholdingType;
import et.ut.einvoice.withholding.dto.CreateWithholdingReceiptRequest;
import et.ut.einvoice.withholding.service.WithholdingReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class StatutoryDocumentTaxonomyTestSuite {

    @Autowired
    private CashReceiptService cashReceiptService;

    @Autowired
    private PurchaseVoucherService purchaseVoucherService;

    @Autowired
    private WithholdingReceiptService withholdingReceiptService;

    @Autowired
    private CreditSettlementService creditSettlementService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        taxpayerProfileRepository.deleteAll();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        // Seed taxpayer profile for tenantA
        TaxpayerProfile profileA = new TaxpayerProfile(
                tenantA,
                "0012345678",
                "VAT-12345",
                "Alpha General Trading PLC",
                "Alpha Trading",
                "Addis Ababa",
                "Bole",
                "0911223344",
                "alpha@ut.et",
                "SYS-001",
                "POS"
        );
        taxpayerProfileRepository.save(profileA);

        // Seed taxpayer profile for tenantB
        TaxpayerProfile profileB = new TaxpayerProfile(
                tenantB,
                "0098765432",
                "VAT-98765",
                "Beta Manufacturing Share Co",
                "Beta Goods",
                "Hawassa",
                "Tabor",
                "0922334455",
                "beta@ut.et",
                "SYS-002",
                "POS"
        );
        taxpayerProfileRepository.save(profileB);

        // Mock default successful response from MoR EIRS for test registration
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "TEST-IRN-" + UUID.randomUUID();
                    String rrn = "TEST-RRN-" + UUID.randomUUID();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice-mock");
                });

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");

        // Set default context to tenantA
        setTenantContext(tenantA, "CLIENT_A");
    }

    private void setTenantContext(UUID tenantId, String clientId) {
        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId,
                clientId,
                Set.of("ROLE_TENANT_ADMIN", "ROLE_ACCOUNTANT", "ROLE_CASHIER"),
                Set.of("receipt.cash.create", "receipt.voucher.create", "invoice:create", "credit:settle"),
                UUID.randomUUID().toString()
        ));
    }

    @Test
    @DisplayName("Stage 1A: Standalone Cash Receipt without Sales Invoice (Loan Repayment / Customer Deposit)")
    void test_CashReceipt_WithoutInvoice() {
        CreateCashReceiptRequest req = new CreateCashReceiptRequest(
                "Abebe Commercial Bank",
                "0000000001",
                new BigDecimal("50000.00"),
                "ETB",
                CashReceiptPurpose.LOAN_REPAYMENT,
                "Term loan monthly installment disbursement",
                null, // No invoice ID
                "LN-2026-0881",
                CashReceiptPaymentMethod.BANK_TRANSFER,
                "TX-998877",
                Instant.now()
        );

        CashReceipt receipt = cashReceiptService.issueCashReceipt(req);

        assertNotNull(receipt);
        assertEquals(tenantA, receipt.getTenantId());
        assertNotNull(receipt.getReceiptNumber());
        assertTrue(receipt.getReceiptNumber().startsWith("CR-"));
        assertEquals(new BigDecimal("50000.00"), receipt.getAmount());
        assertEquals(CashReceiptPurpose.LOAN_REPAYMENT, receipt.getPurpose());
        assertNull(receipt.getRelatedInvoiceId());
        assertEquals("LN-2026-0881", receipt.getRelatedCreditAccountId());
        assertNotNull(receipt.getRrn());
        assertTrue(receipt.getRrn().startsWith("RRN-CR-"));
        assertNotNull(receipt.getQrCode());
        assertEquals("REGISTERED", receipt.getStatus().name());
    }

    @Test
    @DisplayName("Stage 1A: Advance Payment Cash Receipt before Sales Invoice Generation")
    void test_CashReceipt_AdvancePayment() {
        CreateCashReceiptRequest req = new CreateCashReceiptRequest(
                "Ministry of Agriculture",
                "0019283746",
                new BigDecimal("250000.00"),
                "ETB",
                CashReceiptPurpose.ADVANCE_PAYMENT,
                "30% mobilization advance for solar pump supply contract",
                null,
                "AGR-SO-2026-004",
                CashReceiptPaymentMethod.BANK_TRANSFER,
                "CBE-FT-55443322",
                Instant.now()
        );

        CashReceipt receipt = cashReceiptService.issueCashReceipt(req);

        assertNotNull(receipt);
        assertEquals(CashReceiptPurpose.ADVANCE_PAYMENT, receipt.getPurpose());
        assertEquals(new BigDecimal("250000.00"), receipt.getAmount());
        assertEquals("ETB", receipt.getCurrency());
        assertNotNull(receipt.getQrCode());
    }

    @Test
    @DisplayName("Stage 1B: Purchase Voucher issued by Buyer without Seller IRN (Farmer Agricultural Produce)")
    void test_PurchaseVoucher_FarmerProduceWithoutSellerIrn() {
        var items = List.of(
                new CreatePurchaseVoucherRequest.LineItemDto("Red Teff Grade 1", new BigDecimal("50.00"), "QUINTAL", new BigDecimal("8500.00"), BigDecimal.ZERO),
                new CreatePurchaseVoucherRequest.LineItemDto("White Wheat Seed", new BigDecimal("20.00"), "QUINTAL", new BigDecimal("6000.00"), BigDecimal.ZERO)
        );

        CreatePurchaseVoucherRequest req = new CreatePurchaseVoucherRequest(
                "Farmer Kebede Belay",
                null, // Smallholder farmer has no TIN
                "ET-ID-88776655",
                "NATIONAL_ID",
                "0911001122",
                "West Gojjam, Merawi, Kebele 02",
                UnavailableReceiptReason.FARMER_AGRICULTURAL_PRODUCE,
                "Smallholder direct farm gate procurement without fiscal machine",
                Instant.now(),
                "ETB",
                "ATTACH-WEIGHBRIDGE-TICKET-4431",
                new BigDecimal("10900.00"), // 2% statutory withholding on bulk agricultural produce
                items
        );

        PurchaseVoucher voucher = purchaseVoucherService.createPurchaseVoucher(req);

        assertNotNull(voucher);
        assertEquals(tenantA, voucher.getTenantId());
        assertEquals("0012345678", voucher.getBuyerTin());
        assertEquals("Farmer Kebede Belay", voucher.getSupplierName());
        assertNull(voucher.getSupplierTin());
        assertEquals(UnavailableReceiptReason.FARMER_AGRICULTURAL_PRODUCE, voucher.getUnavailableReason());
        // Line 1: 50 * 8500 = 425,000; Line 2: 20 * 6000 = 120,000; Total = 545,000
        assertEquals(new BigDecimal("545000.00"), voucher.getTotalAmount());
        assertEquals(new BigDecimal("10900.00"), voucher.getWithholdingAmount());
        assertEquals(new BigDecimal("534100.00"), voucher.getNetPayableAmount());
        assertNotNull(voucher.getRrn());
        assertTrue(voucher.getRrn().startsWith("RRN-PV-"));
        assertNotNull(voucher.getQrCode());
        assertEquals(2, voucher.getLines().size());
        assertEquals("REGISTERED", voucher.getStatus());
    }

    @Test
    @DisplayName("Stage 1C: Distinct VAT Withholding Receipt with 50% Statutory Withholding Rate")
    void test_WithholdingReceipt_VatWithholding() {
        CreateWithholdingReceiptRequest req = new CreateWithholdingReceiptRequest(
                WithholdingType.VAT_WITHHOLDING,
                null,
                null,
                "0000000022", // Public procurement authority withholding agent TIN
                "Federal Public Procurement Agency",
                "0012345678", // Taxpayer TIN
                "Alpha General Trading PLC",
                new BigDecimal("100000.00"), // Tax base
                new BigDecimal("0.5000"), // 50% VAT withholding rate
                "WH-PAY-REF-8877",
                Instant.now()
        );

        WithholdingReceipt receipt = withholdingReceiptService.issueWithholdingReceipt(req);

        assertNotNull(receipt);
        assertEquals(WithholdingType.VAT_WITHHOLDING, receipt.getWithholdingType());
        assertEquals(new BigDecimal("100000.00"), receipt.getTaxBaseAmount());
        assertEquals(new BigDecimal("0.5000"), receipt.getWithheldTaxRate());
        assertEquals(new BigDecimal("50000.00"), receipt.getWithheldTaxAmount());
        assertTrue(receipt.getReceiptNumber().startsWith("VTW-"));
        assertNotNull(receipt.getQrCode());
    }

    @Test
    @DisplayName("Stage 1C: Distinct Income Tax Withholding Receipt with 2% Statutory Rate")
    void test_WithholdingReceipt_IncomeTaxWithholding() {
        CreateWithholdingReceiptRequest req = new CreateWithholdingReceiptRequest(
                WithholdingType.INCOME_TAX_WITHHOLDING,
                null,
                null,
                "0033445566",
                "Commercial Bank of Ethiopia",
                "0012345678",
                "Alpha General Trading PLC",
                new BigDecimal("200000.00"),
                null, // Should default to 2% (0.0200)
                "CBE-WH-0099",
                Instant.now()
        );

        WithholdingReceipt receipt = withholdingReceiptService.issueWithholdingReceipt(req);

        assertNotNull(receipt);
        assertEquals(WithholdingType.INCOME_TAX_WITHHOLDING, receipt.getWithholdingType());
        assertEquals(new BigDecimal("0.0200"), receipt.getWithheldTaxRate());
        assertEquals(new BigDecimal("4000.00"), receipt.getWithheldTaxAmount());
        assertTrue(receipt.getReceiptNumber().startsWith("ITW-"));
    }

    @Test
    @DisplayName("Stage 1D: Credit Invoice Creation, Partial Settlement, and Full Settlement Lifecycle")
    void test_CreditInvoice_And_SettlementLifecycle() {
        // 1. Create a credit invoice of 11,500 ETB (10,000 + 15% VAT)
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "ITM-CR-01", "Industrial Machinery Spares", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("10000.00"), BigDecimal.ZERO, "VAT15", null
        ));
        var buyer = new CreateInvoiceRequest.BuyerRequest(
                "Apex Factory PLC", "0055443322", null, null, "0911556677",
                "apex@factory.et", "14", "05", null, null
        );

        CreateInvoiceRequest invReq = new CreateInvoiceRequest(
                TransactionType.B2B,
                "BANK_TRANSFER",
                "CREDIT", // Credit Sale
                buyer,
                items,
                null, null, null
        );

        InvoiceResponseDto createdInv = invoiceService.createAndRegisterInvoice(invReq, "IDEM-CR-" + UUID.randomUUID());
        assertNotNull(createdInv);
        assertEquals("CREDIT", createdInv.paymentTerm());
        assertEquals(new BigDecimal("11500.00"), createdInv.grandTotal());
        assertEquals(new BigDecimal("11500.00"), createdInv.outstandingBalance());
        assertEquals(CreditStatus.UNPAID, createdInv.creditStatus());

        // Verify registered invoice in DB
        Invoice invoice = invoiceRepository.findById(createdInv.id()).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus());
        assertEquals(new BigDecimal("11500.00"), invoice.getGrandTotal());

        // 2. First Partial Settlement of 5,000 ETB
        CreditSettlement stl1 = creditSettlementService.recordSettlement(new SettleCreditRequest(
                invoice.getId(),
                new BigDecimal("5000.00"),
                "BANK_TRANSFER",
                "TXN-PARTIAL-1",
                Instant.now()
        ));

        assertNotNull(stl1);
        assertEquals(new BigDecimal("5000.00"), stl1.getSettlementAmount());
        assertEquals(new BigDecimal("11500.00"), stl1.getBalanceBefore());
        assertEquals(new BigDecimal("6500.00"), stl1.getBalanceAfter());

        // Verify invoice after first settlement: grand_total is strictly UNTOUCHED, outstanding_balance is 6,500
        Invoice afterStl1 = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("11500.00"), afterStl1.getGrandTotal(), "Grand total must NEVER mutate upon settlement");
        assertEquals(new BigDecimal("6500.00"), afterStl1.getOutstandingBalance());
        assertEquals(CreditStatus.PARTIALLY_PAID, afterStl1.getCreditStatus());

        // 3. Second Final Settlement of remaining 6,500 ETB
        CreditSettlement stl2 = creditSettlementService.recordSettlement(new SettleCreditRequest(
                invoice.getId(),
                new BigDecimal("6500.00"),
                "CASH",
                "TXN-FINAL-2",
                Instant.now()
        ));

        assertEquals(new BigDecimal("6500.00"), stl2.getSettlementAmount());
        assertEquals(new BigDecimal("6500.00"), stl2.getBalanceBefore());
        assertEquals(new BigDecimal("0.00"), stl2.getBalanceAfter());

        // Verify invoice after final settlement: grand_total remains 11,500, status SETTLED
        Invoice afterFinal = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("11500.00"), afterFinal.getGrandTotal());
        assertEquals(new BigDecimal("0.00"), afterFinal.getOutstandingBalance());
        assertEquals(CreditStatus.SETTLED, afterFinal.getCreditStatus());

        // 4. Overpayment Rejection: Attempt third settlement when already fully settled
        BusinessException exOver = assertThrows(BusinessException.class, () ->
                creditSettlementService.recordSettlement(new SettleCreditRequest(
                        invoice.getId(),
                        new BigDecimal("100.00"),
                        "CASH",
                        "TXN-OVER",
                        Instant.now()
                ))
        );
        assertEquals("CREDIT_ALREADY_SETTLED", exOver.getCode());

        // 5. Verify Credit Account Summary
        CreditAccountSummaryResponse summary = creditSettlementService.getCreditAccountSummary(invoice.getId());
        assertNotNull(summary);
        assertEquals(new BigDecimal("11500.00"), summary.originalAmount());
        assertEquals(new BigDecimal("11500.00"), summary.totalSettled());
        assertEquals(new BigDecimal("0.00"), summary.outstandingBalance());
        assertEquals(CreditStatus.SETTLED, summary.creditStatus());
        assertEquals(2, summary.settlements().size());
    }

    @Test
    @DisplayName("Stage 1 Negative Test: Cross-Tenant Access Prohibited (Tenant B cannot settle Tenant A invoice)")
    void test_CrossTenant_DocumentAccess_Prevented() {
        // Create an invoice under Tenant A
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "ITM-01", "Merchandise", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("2000.00"), BigDecimal.ZERO, "VAT15", null
        ));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, null, null, null, null, null, null);
        InvoiceResponseDto invA = invoiceService.createAndRegisterInvoice(
                new CreateInvoiceRequest(TransactionType.B2C, "CASH", "CREDIT", buyer, items, null, null, null),
                null
        );

        // Switch security context to Tenant B
        setTenantContext(tenantB, "CLIENT_B");

        // Tenant B attempts to settle Tenant A's credit invoice
        BusinessException ex = assertThrows(BusinessException.class, () ->
                creditSettlementService.recordSettlement(new SettleCreditRequest(
                        invA.id(),
                        new BigDecimal("500.00"),
                        "CASH",
                        "ROGUE-SETTLE",
                        Instant.now()
                ))
        );
        assertEquals("INVOICE_NOT_FOUND", ex.getCode(), "Cross-tenant document must not be found or settled");
    }

    @Test
    @DisplayName("Stage 1 Negative Test: Settle Non-Credit Invoice Prohibited")
    void test_SettleNonCreditInvoice_Prevented() {
        // Immediate invoice
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "ITM-01", "Counter Sale", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null
        ));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Retail Customer", null, null, null, null, null, null, null, null, null);
        InvoiceResponseDto immediateInv = invoiceService.createAndRegisterInvoice(
                new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null),
                null
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                creditSettlementService.recordSettlement(new SettleCreditRequest(
                        immediateInv.id(),
                        new BigDecimal("500.00"),
                        "CASH",
                        "IMMEDIATE-SETTLE",
                        Instant.now()
                ))
        );
        assertEquals("NOT_A_CREDIT_INVOICE", ex.getCode());
    }
}
