package et.ut.einvoice.compliance;

import et.ut.einvoice.invoicing.domain.ManualFiscalDocument;
import et.ut.einvoice.invoicing.domain.ManualFiscalState;
import et.ut.einvoice.invoicing.dto.*;
import et.ut.einvoice.invoicing.repository.ManualFiscalDocumentRepository;
import et.ut.einvoice.invoicing.service.ManualInvoiceReconciliationService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ManualInvoiceReconciliationTestSuite {

    @Autowired
    private ManualInvoiceReconciliationService manualService;

    @Autowired
    private ManualFiscalDocumentRepository manualRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private static final AtomicLong TIN_COUNTER = new AtomicLong(30000000L);

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        manualRepository.deleteAll();

        tenantId = UUID.randomUUID();
        String tin = "09" + TIN_COUNTER.incrementAndGet();
        Tenant tenant = new Tenant(tenantId, "ORG-MANUAL", "Manual Recovery Corp", "Manual Trade", tin);
        tenantRepository.save(tenant);
    }

    @Test
    @DisplayName("Stage 13: Import single manual fiscal document establishes 72-hour reconciliation deadline")
    void test_ReconcileManualBatch_SingleDocument_Succeeds() {
        Instant issueTime = Instant.now().minus(Duration.ofHours(5));
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null,
                "DOC-00101",
                "BOOK-A",
                issueTime,
                "0011223344",
                "Customer Alpha",
                new BigDecimal("1150.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("150.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Standard Goods", new BigDecimal("1"),
                        new BigDecimal("1000.00"), new BigDecimal("0.15"), new BigDecimal("150.00"), new BigDecimal("1150.00"))),
                "cashier-01",
                "OUTAGE-NET-FAIL-2026-03"
        );

        List<ManualFiscalDocumentResponseDto> result = manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc)));
        assertEquals(1, result.size());

        ManualFiscalDocumentResponseDto res = result.get(0);
        assertEquals("DOC-00101", res.manualDocumentNumber());
        assertEquals("BOOK-A", res.manualBook());
        assertEquals(ManualFiscalState.PENDING, res.eirsRegistrationState());
        assertEquals(0, res.duplicateReprintCount());
        assertNotNull(res.reconciliationDeadline());
        assertTrue(res.reconciliationDeadline().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Stage 13: Controlled batch reconciliation handles multiple documents atomically")
    void test_ReconcileManualBatch_MultipleDocuments_Succeeds() {
        Instant issueTime = Instant.now().minus(Duration.ofHours(2));
        CreateManualFiscalDocumentDto doc1 = new CreateManualFiscalDocumentDto(
                null, "DOC-00201", "BOOK-B", issueTime, null, null,
                new BigDecimal("230.00"), new BigDecimal("200.00"), new BigDecimal("30.00"),
                List.of(new ManualFiscalItemDto("ITEM-A", "Item A", BigDecimal.ONE, new BigDecimal("200.00"), new BigDecimal("0.15"), new BigDecimal("30.00"), new BigDecimal("230.00"))),
                "cashier-02", "OUTAGE-PWR-01"
        );
        CreateManualFiscalDocumentDto doc2 = new CreateManualFiscalDocumentDto(
                null, "DOC-00202", "BOOK-B", issueTime, null, null,
                new BigDecimal("460.00"), new BigDecimal("400.00"), new BigDecimal("60.00"),
                List.of(new ManualFiscalItemDto("ITEM-B", "Item B", BigDecimal.ONE, new BigDecimal("400.00"), new BigDecimal("0.15"), new BigDecimal("60.00"), new BigDecimal("460.00"))),
                "cashier-02", "OUTAGE-PWR-01"
        );

        List<ManualFiscalDocumentResponseDto> result = manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc1, doc2)));
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Stage 13: Duplicate manual invoice import is strictly rejected")
    void test_ReconcileManualBatch_DuplicateReference_Rejected() {
        Instant issueTime = Instant.now().minus(Duration.ofHours(1));
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null, "DOC-DUP-01", "BOOK-DUP", issueTime, null, null,
                new BigDecimal("115.00"), new BigDecimal("100.00"), new BigDecimal("15.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Item", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("0.15"), new BigDecimal("15.00"), new BigDecimal("115.00"))),
                "cashier-01", "OUTAGE-DUP"
        );

        manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc)));

        // Replay/duplicate import
        BusinessException ex = assertThrows(BusinessException.class, () ->
                manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc))));
        assertEquals("DUPLICATE_MANUAL_INVOICE", ex.getCode());
    }

    @Test
    @DisplayName("Stage 13: Future original issue time is rejected")
    void test_ReconcileManualBatch_FutureTime_Rejected() {
        Instant futureTime = Instant.now().plus(Duration.ofHours(2));
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null, "DOC-FUT-01", "BOOK-FUT", futureTime, null, null,
                new BigDecimal("115.00"), new BigDecimal("100.00"), new BigDecimal("15.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Item", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("0.15"), new BigDecimal("15.00"), new BigDecimal("115.00"))),
                "cashier-01", "OUTAGE-FUT"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc))));
        assertEquals("INVALID_ISSUE_TIME", ex.getCode());
    }

    @Test
    @DisplayName("Stage 13: Totals and Tax mismatch is rejected")
    void test_ReconcileManualBatch_TotalsMismatch_Rejected() {
        Instant issueTime = Instant.now().minus(Duration.ofHours(1));
        // subtotal 100 + tax 15 = 115, but total claimed is 150
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null, "DOC-ERR-01", "BOOK-ERR", issueTime, null, null,
                new BigDecimal("150.00"), new BigDecimal("100.00"), new BigDecimal("15.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Item", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("0.15"), new BigDecimal("15.00"), new BigDecimal("115.00"))),
                "cashier-01", "OUTAGE-ERR"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc))));
        assertEquals("TOTAL_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("Stage 13: Reprint increments duplicate counter and attaches DUPLICATE watermark without altering fiscal totals")
    void test_ReprintManualInvoice_MandatoryDuplicateMarking() {
        Instant issueTime = Instant.now().minus(Duration.ofHours(1));
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null, "DOC-REP-01", "BOOK-REP", issueTime, null, null,
                new BigDecimal("230.00"), new BigDecimal("200.00"), new BigDecimal("30.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Item", BigDecimal.ONE, new BigDecimal("200.00"), new BigDecimal("0.15"), new BigDecimal("30.00"), new BigDecimal("230.00"))),
                "cashier-01", "OUTAGE-REP"
        );

        List<ManualFiscalDocumentResponseDto> imported = manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc)));
        UUID docId = imported.get(0).id();

        // 1st Reprint
        ManualReprintResponseDto reprint1 = manualService.reprintManualInvoice(tenantId, docId);
        assertTrue(reprint1.isDuplicate());
        assertEquals("DUPLICATE - REPRINT OF REGISTERED TAX INVOICE", reprint1.watermark());
        assertEquals(1, reprint1.duplicateReprintCount());
        assertEquals(new BigDecimal("230.00"), reprint1.totalAmount());

        // 2nd Reprint
        ManualReprintResponseDto reprint2 = manualService.reprintManualInvoice(tenantId, docId);
        assertEquals(2, reprint2.duplicateReprintCount());

        // Verify underlying entity immutability
        ManualFiscalDocument persisted = manualRepository.findById(docId).orElseThrow();
        assertEquals(2, persisted.getDuplicateReprintCount());
        assertEquals(new BigDecimal("230.00"), persisted.getTotalAmount());
        assertEquals(new BigDecimal("200.00"), persisted.getSubtotal());
        assertEquals(new BigDecimal("30.00"), persisted.getTaxAmount());
    }

    @Test
    @DisplayName("Stage 13: Cross-tenant isolation blocks unauthorized manual document reprint")
    void test_CrossTenant_ManualReprint_Isolated() {
        UUID otherTenantId = UUID.randomUUID();
        String otherTin = "08" + TIN_COUNTER.incrementAndGet();
        Tenant otherTenant = new Tenant(otherTenantId, "ORG-OTHER", "Other Corp", "Other Trade", otherTin);
        tenantRepository.save(otherTenant);

        Instant issueTime = Instant.now().minus(Duration.ofHours(1));
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null, "DOC-ISO-01", "BOOK-ISO", issueTime, null, null,
                new BigDecimal("115.00"), new BigDecimal("100.00"), new BigDecimal("15.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Item", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("0.15"), new BigDecimal("15.00"), new BigDecimal("115.00"))),
                "cashier-01", "OUTAGE-ISO"
        );

        List<ManualFiscalDocumentResponseDto> imported = manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc)));
        UUID docId = imported.get(0).id();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                manualService.reprintManualInvoice(otherTenantId, docId));
        assertEquals("MANUAL_INVOICE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("Stage 13: Government registration records authoritative IRN and REGISTERED status")
    void test_RecordGovernmentRegistration_Succeeds() {
        Instant issueTime = Instant.now().minus(Duration.ofHours(1));
        CreateManualFiscalDocumentDto doc = new CreateManualFiscalDocumentDto(
                null, "DOC-EIRS-01", "BOOK-EIRS", issueTime, null, null,
                new BigDecimal("115.00"), new BigDecimal("100.00"), new BigDecimal("15.00"),
                List.of(new ManualFiscalItemDto("ITEM-1", "Item", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("0.15"), new BigDecimal("15.00"), new BigDecimal("115.00"))),
                "cashier-01", "OUTAGE-EIRS"
        );

        List<ManualFiscalDocumentResponseDto> imported = manualService.reconcileManualBatch(tenantId, new ManualBatchRequestDto(List.of(doc)));
        UUID docId = imported.get(0).id();

        String authoritativeIrn = "IRN-EIRS-MANUAL-99887766";
        manualService.recordGovernmentRegistration(tenantId, docId, authoritativeIrn);

        ManualFiscalDocument persisted = manualRepository.findById(docId).orElseThrow();
        assertEquals(ManualFiscalState.REGISTERED, persisted.getEirsRegistrationState());
        assertEquals(authoritativeIrn, persisted.getIrn());
    }
}
