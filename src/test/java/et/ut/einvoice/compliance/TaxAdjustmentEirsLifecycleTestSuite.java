package et.ut.einvoice.compliance;

import et.ut.einvoice.adjustments.domain.AdjustmentMoRStatus;
import et.ut.einvoice.adjustments.domain.NoteType;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import et.ut.einvoice.adjustments.dto.CreateAdjustmentRequest;
import et.ut.einvoice.adjustments.service.AdjustmentService;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class TaxAdjustmentEirsLifecycleTestSuite {

    @Autowired
    private AdjustmentService adjustmentService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantA;
    private UUID tenantB;
    private InvoiceResponseDto testInvoice;

    @BeforeEach
    void setUp() {
        taxpayerProfileRepository.deleteAll();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        // Seed taxpayer profile for tenantA
        TaxpayerProfile profileA = new TaxpayerProfile(
                tenantA,
                "0011223344",
                "VAT-11223",
                "Acme Distribution PLC",
                "Acme Goods",
                "Addis Ababa",
                "Bole",
                "0911002233",
                "acme@ut.et",
                "SYS-001",
                "POS"
        );
        taxpayerProfileRepository.save(profileA);

        // Seed taxpayer profile for tenantB
        TaxpayerProfile profileB = new TaxpayerProfile(
                tenantB,
                "0099887766",
                "VAT-99887",
                "Zenith Logistics PLC",
                "Zenith Cargo",
                "Dire Dawa",
                "Sabian",
                "0922003344",
                "zenith@ut.et",
                "SYS-002",
                "POS"
        );
        taxpayerProfileRepository.save(profileB);

        // Default mock for invoice registration
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "INV-MOR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
                    String rrn = "RRN-INV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice-mock");
                });

        // Default mock for adjustment registration
        Mockito.when(governmentRegistrationProvider.registerAdjustment(any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    TaxAdjustment adj = invocation.getArgument(0);
                    if (adj == null) {
                        return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                                "CN-EIRS-DEFAULT", "RRN-DEFAULT", Instant.now().toString(), "QR-DEFAULT", "signed"
                        );
                    }
                    String prefix = adj.getNoteType() == NoteType.CREDIT_NOTE ? "CN-EIRS-" : "DN-EIRS-";
                    String irn = prefix + UUID.randomUUID().toString().substring(0, 14).toUpperCase();
                    String rrn = "RRN-" + prefix + UUID.randomUUID().toString().substring(0, 14).toUpperCase();
                    String qr = "QR-DATA-ADJUSTMENT-" + irn;
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                            irn, rrn, Instant.now().toString(), qr, "signed-adjustment-xml"
                    );
                });

        setTenantContext(tenantA, "CLIENT_A");

        // Create an original registered invoice of 11,500 ETB (10,000 + 1,500 VAT)
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "ITM-01", "Textile Fabrics", "goods", "MTR",
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, "VAT15", null
        ));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Retail Store", null, null, null, null, null, null, null, null, null);
        testInvoice = invoiceService.createAndRegisterInvoice(
                new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null),
                "IDEM-INV-" + UUID.randomUUID()
        );
    }

    private void setTenantContext(UUID tenantId, String clientId) {
        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId,
                clientId,
                Set.of("ROLE_TENANT_ADMIN", "ROLE_ACCOUNTANT", "ROLE_CASHIER"),
                Set.of("adjustment.submit", "invoice:create"),
                UUID.randomUUID().toString()
        ));
    }

    @Test
    @DisplayName("Stage 2: Successful Credit Note EIRS Registration with Authoritative IRN and RRN")
    void test_CreditNote_SuccessfulRegistration() {
        CreateAdjustmentRequest req = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "Damaged goods return",
                new BigDecimal("2000.00"),
                new BigDecimal("300.00"),
                "IDEM-CN-" + UUID.randomUUID(),
                List.of(new CreateAdjustmentRequest.AdjustmentLineDto(
                        1, "ITM-01", "Damaged Fabric", new BigDecimal("20.00"),
                        new BigDecimal("100.00"), new BigDecimal("2000.00"),
                        new BigDecimal("300.00"), new BigDecimal("2300.00")
                ))
        );

        TaxAdjustment adjustment = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req);

        assertNotNull(adjustment);
        assertEquals(tenantA, adjustment.getTenantId());
        assertEquals(NoteType.CREDIT_NOTE, adjustment.getNoteType());
        assertEquals(testInvoice.irn(), adjustment.getOriginalIrn());
        assertEquals(new BigDecimal("2300.00"), adjustment.getAdjustedTotal());
        assertEquals(new BigDecimal("11500.00"), adjustment.getOriginalGrandTotal());
        assertEquals(new BigDecimal("9200.00"), adjustment.getNewGrandTotal());
        assertEquals("REGISTERED", adjustment.getStatus());
        assertEquals(AdjustmentMoRStatus.REGISTERED, adjustment.getMorStatus());

        // Prove NO fake local CN-UUID is generated: authoritative EIRS IRN and RRN must be present
        assertNotNull(adjustment.getIrn());
        assertTrue(adjustment.getIrn().startsWith("CN-EIRS-"), "Must receive genuine authoritative EIRS IRN, not local CN-UUID");
        assertNotNull(adjustment.getRrn());
        assertTrue(adjustment.getRrn().startsWith("RRN-CN-EIRS-"));
        assertNotNull(adjustment.getQrCode());
        assertEquals(1, adjustment.getLines().size());
    }

    @Test
    @DisplayName("Stage 2: Idempotent Submission Returns Existing Registered Adjustment")
    void test_DuplicateSubmission_ReturnsIdempotentResult() {
        String idempotencyKey = "IDEM-DUPLICATE-CHECK-" + UUID.randomUUID();
        CreateAdjustmentRequest req = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "Quantity rebate",
                new BigDecimal("1000.00"),
                new BigDecimal("150.00"),
                idempotencyKey,
                null
        );

        TaxAdjustment first = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req);
        TaxAdjustment second = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getId(), second.getId(), "Second submission with same idempotency key must return the original adjustment");
        assertEquals(first.getIrn(), second.getIrn());
    }

    @Test
    @DisplayName("Stage 2: Single Over-Adjustment Exceeding Original Total is Blocked")
    void test_SingleOverAdjustment_Blocked() {
        // Original invoice grand total is 11,500.00 ETB
        CreateAdjustmentRequest overReq = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "Excessive credit note",
                new BigDecimal("12000.00"),
                new BigDecimal("1800.00"), // Total = 13,800 > 11,500
                null,
                null
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, overReq)
        );
        assertEquals("CREDIT_AMOUNT_EXCEEDS_INVOICE_VALUE", ex.getCode());
    }

    @Test
    @DisplayName("Stage 2: Cumulative Over-Adjustment Across Multiple Credit Notes is Blocked")
    void test_CumulativeOverAdjustment_Blocked() {
        // First valid credit note: 8,000 ETB + 1,200 VAT = 9,200 ETB
        CreateAdjustmentRequest req1 = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "First return",
                new BigDecimal("8000.00"),
                new BigDecimal("1200.00"),
                "IDEM-CUM-1",
                null
        );
        TaxAdjustment adj1 = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req1);
        assertEquals(AdjustmentMoRStatus.REGISTERED, adj1.getMorStatus());

        // Second credit note: 3,000 ETB + 450 VAT = 3,450 ETB. Cumulative: 9,200 + 3,450 = 12,650 > 11,500
        CreateAdjustmentRequest req2 = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "Second return exceeding balance",
                new BigDecimal("3000.00"),
                new BigDecimal("450.00"),
                "IDEM-CUM-2",
                null
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req2)
        );
        assertEquals("CUMULATIVE_CREDIT_EXCEEDS_INVOICE_VALUE", ex.getCode());
    }

    @Test
    @DisplayName("Stage 2: Cross-Tenant Invoice Adjustment is Strictly Prohibited")
    void test_CrossTenantAdjustment_Prevented() {
        // Switch context to Tenant B
        setTenantContext(tenantB, "CLIENT_B");

        // Tenant B attempts to issue a credit note against Tenant A's invoice
        CreateAdjustmentRequest req = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "Fraudulent cross-tenant adjustment",
                new BigDecimal("1000.00"),
                new BigDecimal("150.00")
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req)
        );
        assertEquals("INVOICE_NOT_FOUND", ex.getCode(), "Cross-tenant invoice must never be found");
    }

    @Test
    @DisplayName("Stage 2: MoR EIRS Timeout Results in UNKNOWN State with Safe Retry")
    void test_MoRTimeout_TransitionsToUnknown_AndRetries() {
        // Configure mock to simulate timeout / connection failure on initial registration
        Mockito.doThrow(new RuntimeException("ConnectTimeoutException: Connection to core.mor.gov.et timed out"))
                .when(governmentRegistrationProvider).registerAdjustment(any(), any(), anyString());

        CreateAdjustmentRequest req = new CreateAdjustmentRequest(
                testInvoice.irn(),
                "Dispute resolution",
                new BigDecimal("1000.00"),
                new BigDecimal("150.00"),
                "IDEM-TIMEOUT-" + UUID.randomUUID(),
                null
        );

        TaxAdjustment adjustment = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, req);

        assertNotNull(adjustment);
        assertEquals(AdjustmentMoRStatus.UNKNOWN, adjustment.getMorStatus());
        assertNull(adjustment.getIrn(), "Failed/unknown adjustment must not have a finalized government IRN");
        assertEquals("PENDING", adjustment.getStatus());

        // Now restore MoR connectivity using doReturn
        Mockito.doReturn(GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                        "CN-EIRS-RETRY-SUCCESS", "RRN-RETRY-01", Instant.now().toString(), "QR-RETRY", "signed-payload"
                )).when(governmentRegistrationProvider).registerAdjustment(any(), any(), anyString());

        // Retry the unknown adjustment
        TaxAdjustment retried = adjustmentService.retryAdjustment(adjustment.getId());
        assertEquals(AdjustmentMoRStatus.REGISTERED, retried.getMorStatus());
        assertEquals("REGISTERED", retried.getStatus());
        assertEquals("CN-EIRS-RETRY-SUCCESS", retried.getIrn());
        assertEquals("RRN-RETRY-01", retried.getRrn());
    }
}
