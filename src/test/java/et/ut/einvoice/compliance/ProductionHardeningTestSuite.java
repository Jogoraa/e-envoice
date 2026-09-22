package et.ut.einvoice.compliance;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.domain.GovernmentSubmission;
import et.ut.einvoice.government.domain.GovernmentSubmissionStatus;
import et.ut.einvoice.government.reconciliation.GovernmentReconciliationService;
import et.ut.einvoice.government.repository.GovernmentSubmissionRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.offline.dto.SyncOfflineBatchRequest;
import et.ut.einvoice.offline.service.OfflineSyncService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import et.ut.einvoice.receipts.dto.CreateReceiptRequest;
import et.ut.einvoice.receipts.service.ReceiptService;
import et.ut.einvoice.taxation.domain.TaxCode;
import et.ut.einvoice.taxation.domain.TaxRule;
import et.ut.einvoice.taxation.repository.TaxRuleRepository;
import et.ut.einvoice.taxation.service.TaxEngine;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.taxpayer.service.DeviceTrustService;
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
public class ProductionHardeningTestSuite {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private GovernmentSubmissionRepository submissionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private GovernmentReconciliationService reconciliationService;

    @Autowired
    private DeviceTrustService deviceTrustService;

    @Autowired
    private OfflineSyncService offlineSyncService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private TaxEngine taxEngine;

    @Autowired
    private TaxRuleRepository taxRuleRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        tenantB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "ERP_CLIENT_A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()));

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantA, "0041746204", "43256663343256663322", "Tenant A Solutions PLC", "Tenant A",
                "13", "574", "+251911310694", "a@test.com", "8EFBBDD7FF", "ERP"
        ));

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantB, "0052857315", "54367774454367774433", "Tenant B Logistics PLC", "Tenant B",
                "14", "102", "+251922410795", "b@test.com", "8EFBBDD7FE", "ERP"
        ));

        taxRuleRepository.deleteAll();
        taxRuleRepository.save(new TaxRule(UUID.randomUUID(), "VAT15", "VAT", new BigDecimal("0.1500"), Instant.parse("2020-01-01T00:00:00Z"), null, 1));

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");

        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "TEST-IRN-" + UUID.randomUUID();
                    String rrn = "TEST-RRN-" + UUID.randomUUID();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice-mock");
                });
    }

    @Test
    @DisplayName("Hardening 1: Government Transaction Boundary & Outbox Decoupling")
    void test_GovernmentTransactionBoundary_Outbox() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Service", "services", "HRS", BigDecimal.ONE, new BigDecimal("2000.00"), BigDecimal.ZERO, "VAT15", null));
        var req = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);

        InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, "idemp-boundary-" + UUID.randomUUID());

        assertNotNull(response);
        assertEquals(InvoiceStatus.REGISTERED, response.status());

        // Verify that GovernmentSubmission record was created
        List<GovernmentSubmission> subs = submissionRepository.findByTenantIdAndInvoiceId(tenantA, response.id());
        assertFalse(subs.isEmpty());
        assertEquals(GovernmentSubmissionStatus.ACCEPTED, subs.get(0).getStatus());

        // Verify OutboxEvent was published
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByTenantIdAndAggregateId(tenantA, response.id().toString());
        assertFalse(outboxEvents.isEmpty());
    }

    @Test
    @DisplayName("Hardening 2: Idempotency with Client Isolation and Request Hash Mismatch (409 Conflict)")
    void test_Idempotency_ClientIsolation_And_Conflict() {
        String key = "idemp-test-" + UUID.randomUUID();
        var items1 = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Coffee", "goods", "KG", BigDecimal.ONE, new BigDecimal("500.00"), BigDecimal.ZERO, "VAT15", null));
        var req1 = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items1, null, null, null);

        // First submission succeeds
        InvoiceResponseDto resp1 = invoiceService.createAndRegisterInvoice(req1, key);
        assertNotNull(resp1);

        // Same key + same payload -> returns identical response
        InvoiceResponseDto respDuplicate = invoiceService.createAndRegisterInvoice(req1, key);
        assertEquals(resp1.id(), respDuplicate.id());
        assertEquals(resp1.irn(), respDuplicate.irn());

        // Same key + DIFFERENT payload -> must throw 409 Conflict
        var items2 = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-02", "Tea", "goods", "KG", new BigDecimal("5.00"), new BigDecimal("100.00"), BigDecimal.ZERO, "VAT15", null));
        var req2 = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items2, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.createAndRegisterInvoice(req2, key));
        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", ex.getCode());

        // Different client + same key -> succeeds independently without collision
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "ERP_CLIENT_B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), UUID.randomUUID().toString()));
        InvoiceResponseDto respClientB = invoiceService.createAndRegisterInvoice(req2, key);
        assertNotNull(respClientB);
        assertNotEquals(resp1.id(), respClientB.id());
    }

    @Test
    @DisplayName("Hardening 3: Financial Immutability on Registered Invoices")
    void test_FinancialImmutability_RegisteredInvoice() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null));
        var req = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);

        InvoiceResponseDto resp = invoiceService.createAndRegisterInvoice(req, null);
        Invoice invoice = invoiceRepository.findById(resp.id()).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus());

        // Direct modification attempt must throw FINANCIAL_MUTATION_FORBIDDEN
        BusinessException ex = assertThrows(BusinessException.class, invoice::recalculateTotals);
        assertEquals("FINANCIAL_MUTATION_FORBIDDEN", ex.getCode());

        InvoiceLine newLine = new InvoiceLine(UUID.randomUUID(), tenantA, 2, "NEW", "Item", "goods", "PCS", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, "VAT15", new BigDecimal("0.15"), new BigDecimal("1.50"), BigDecimal.ZERO, new BigDecimal("11.50"));
        BusinessException ex2 = assertThrows(BusinessException.class, () -> invoice.addLine(newLine));
        assertEquals("FINANCIAL_MUTATION_FORBIDDEN", ex2.getCode());
    }

    @Test
    @DisplayName("Hardening 4: Versioned Tax Rule Lookup & Historical Immutability")
    void test_TaxEngine_HistoricalRuleVersioning() {
        // Ensure standard rule is active
        TaxRule activeRule = taxRuleRepository.findActiveRule("VAT15", Instant.now()).orElse(null);
        assertNotNull(activeRule);
        assertEquals(new BigDecimal("0.1500"), activeRule.getRate());

        var calc = taxEngine.calculateLineTax(BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, TaxCode.VAT15, BigDecimal.ZERO, Instant.now());
        assertEquals(new BigDecimal("15.00"), calc.taxAmount());
        assertEquals(new BigDecimal("115.00"), calc.totalLineAmount());
        assertEquals(activeRule.getVersion(), calc.ruleVersion());
    }

    @Test
    @DisplayName("Hardening 5: Device Revocation Bars Offline Sync")
    void test_DeviceRevocation_BarredFromSync() {
        UUID deviceId = UUID.randomUUID();

        // 1. Unrevoked device can buffer transactions
        var tx = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(1L, Instant.now(), "{\"amount\":100}", "SIG-123");
        var batch = new SyncOfflineBatchRequest(deviceId, List.of(tx));
        var buffers = offlineSyncService.bufferOfflineTransactions(batch);
        assertEquals(1, buffers.size());

        // 2. Revoke device
        deviceTrustService.revokeDevice(tenantA, deviceId, "Reported stolen at branch 03", "SECURITY_ADMIN");

        // 3. Subsequent sync attempt from revoked device must be barred with 403 Forbidden
        BusinessException ex = assertThrows(BusinessException.class, () -> offlineSyncService.bufferOfflineTransactions(batch));
        assertEquals("DEVICE_REVOKED", ex.getCode());
    }

    @Test
    @DisplayName("Hardening 6: Accepted-Then-Timeout Reconciliation Recovers Registered State")
    void test_Reconciliation_AcceptedThenTimeout() {
        UUID invoiceId = UUID.randomUUID();
        String docNumber = "DOC-REC-" + System.currentTimeMillis();
        Invoice invoice = new Invoice(invoiceId, tenantA, docNumber, 999L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoice.setStatus(InvoiceStatus.SUBMISSION_PENDING);
        invoiceRepository.save(invoice);

        String subId = "SUB-TIMEOUT-" + UUID.randomUUID();
        GovernmentSubmission sub = new GovernmentSubmission(UUID.randomUUID(), tenantA, invoiceId, "MoR-EIRS", "v1.0", subId, "HASH123");
        sub.markUnknown("GATEWAY_TIMEOUT", "Connection timed out after 10s");
        submissionRepository.save(sub);

        // Mock verification response indicating MoR actually accepted the submission earlier
        String existingIrn = "RECONCILED-IRN-" + UUID.randomUUID();
        Mockito.when(governmentRegistrationProvider.verifySubmission(anyString(), anyString(), any(), anyString()))
                .thenReturn(GovernmentRegistrationProvider.GovernmentVerificationResult.found(existingIrn, "RRN-" + docNumber, "2026-09-18T14:00:00Z"));

        // Execute reconciliation
        reconciliationService.reconcileSingleSubmission(sub);

        // Verify status transitioned to ACCEPTED & REGISTERED without creating duplicate invoice
        GovernmentSubmission reconciledSub = submissionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(GovernmentSubmissionStatus.ACCEPTED, reconciledSub.getStatus());
        assertEquals(existingIrn, reconciledSub.getGovernmentReference());

        Invoice reconciledInv = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, reconciledInv.getStatus());
        assertEquals(existingIrn, reconciledInv.getIrn());
    }

    @Test
    @DisplayName("Hardening 7: Cross-Tenant Isolation Defense-in-Depth")
    void test_CrossTenant_Isolation() {
        // Tenant A creates an invoice
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT_A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()));
        var items = List.of(new CreateInvoiceRequest.LineItemRequest("ITM-01", "Confidential Goods", "goods", "PCS", BigDecimal.ONE, new BigDecimal("5000.00"), BigDecimal.ZERO, "VAT15", null));
        var req = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);
        InvoiceResponseDto invA = invoiceService.createAndRegisterInvoice(req, null);

        // Switch to Tenant B context
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantB, "CLIENT_B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:read", "receipt:create"), UUID.randomUUID().toString()));

        // Tenant B querying Tenant A's invoice returns empty
        assertTrue(invoiceRepository.findByIrnAndTenantId(invA.irn(), tenantB).isEmpty());

        // Tenant B attempting to create a receipt against Tenant A's invoice is rejected with 404
        CreateReceiptRequest receiptReq = new CreateReceiptRequest(invA.irn(), new BigDecimal("5750.00"), BigDecimal.ZERO);
        BusinessException ex = assertThrows(BusinessException.class, () -> receiptService.createReceipt(et.ut.einvoice.receipts.domain.ReceiptType.SALES_RECEIPT, receiptReq));
        assertEquals("INVOICE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("Hardening 8: Scoped Audit Chaining Prevents Global Serial Bottlenecks")
    void test_AuditStream_TenantScopedChaining() {
        AuditEvent event1 = auditService.recordEvent(tenantA, "FINANCIAL", "USER_1", "CREATE", "INVOICE", "INV-100", "{}", "127.0.0.1");
        AuditEvent event2 = auditService.recordEvent(tenantA, "FINANCIAL", "USER_1", "UPDATE", "INVOICE", "INV-100", "{}", "127.0.0.1");

        assertNotNull(event1);
        assertNotNull(event2);
        assertEquals(event1.getEventHash(), event2.getPreviousEventHash());
        assertEquals("FINANCIAL", event2.getStreamId());
        assertEquals(tenantA, event2.getTenantId());
    }
}
