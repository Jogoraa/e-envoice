package et.ut.einvoice.compliance;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.customer.domain.Customer;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.invoicing.domain.*;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest.BuyerRequest;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest.LineItemRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.invoicing.service.TenantSequenceService;
import et.ut.einvoice.offline.batch.OfflineReconciliationJob;
import et.ut.einvoice.offline.domain.OfflineTransactionBuffer;
import et.ut.einvoice.offline.dto.SyncOfflineBatchRequest;
import et.ut.einvoice.offline.repository.OfflineTransactionBufferRepository;
import et.ut.einvoice.offline.service.OfflineSyncService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.invoicing.domain.TenantInvoiceSequence;
import et.ut.einvoice.invoicing.repository.TenantInvoiceSequenceRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL Integration Test certifying:
 * 1. REAL Flyway migrations executed (V1 through V7)
 * 2. Strict PostgreSQL Row Level Security (RLS) enforcement
 * 3. Tenant isolation across SELECT, INSERT, UPDATE, DELETE
 * 4. Concurrency-safe atomic fiscal sequence generation without race conditions
 * 5. Offline reconciliation without synthetic IRNs and 72-hour window enforcement
 * 6. Immutability triggers and tamper-evident audit hash chain
 */
@SpringBootTest
@ActiveProfiles("postgres-test")
public class PostgreSqlFiscalIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantInvoiceSequenceRepository sequenceRepository;

    @Autowired
    private TenantSequenceService sequenceService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private OfflineSyncService offlineSyncService;

    @Autowired
    private OfflineReconciliationJob offlineReconciliationJob;

    @Autowired
    private OfflineTransactionBufferRepository bufferRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private et.ut.einvoice.audit.service.AuditChainVerifier chainVerifier;

    @Autowired
    private et.ut.einvoice.audit.service.AuditHashService hashService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID platformAdminId;
    private UUID deviceIdA;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        platformAdminId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // Seed initial platform admin context for setup
        TenantContextHolder.setContext(new TenantContext(
                platformAdminId, "PLATFORM", null, "operator", "SAAS_ADMIN",
                Set.of("ROLE_PLATFORM_ADMIN"), Set.of("*"), null, "setup-corr"
        ));

        // Seed Tenant A with unique 10-digit TIN
        String tinA = String.format("1%09d", Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000)));
        Tenant tA = new Tenant(tenantA, "ORG-A-" + tenantA.toString().substring(0, 6), "Tenant Alpha PLC", "Alpha Retail", tinA);
        tA.activate();
        tenantRepository.save(tA);

        TaxpayerProfile pA = new TaxpayerProfile(tenantA, tinA, "VAT-" + tinA, "Tenant Alpha PLC", "Alpha Retail", "AA", "01", "+251911111111", "alpha@ut.et", "SYS-ALPHA", "POS");
        taxpayerProfileRepository.save(pA);

        deviceIdA = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO devices (id, tenant_id, device_serial, system_number) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                deviceIdA, tenantA, "DEV-POS-" + deviceIdA.toString().substring(0, 6), "SYS-ALPHA"
        );

        // Seed Tenant B with unique 10-digit TIN
        String tinB = String.format("2%09d", Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000)));
        Tenant tB = new Tenant(tenantB, "ORG-B-" + tenantB.toString().substring(0, 6), "Tenant Beta PLC", "Beta Retail", tinB);
        tB.activate();
        tenantRepository.save(tB);

        TaxpayerProfile pB = new TaxpayerProfile(tenantB, tinB, "VAT-" + tinB, "Tenant Beta PLC", "Beta Retail", "AA", "02", "+251922222222", "beta@ut.et", "SYS-BETA", "POS");
        taxpayerProfileRepository.save(pB);

        TenantContextHolder.clear();
    }

    // =========================================================================
    // 1. TENANT ISOLATION TESTS (PostgreSQL RLS)
    // =========================================================================

    @Test
    @DisplayName("RLS 1: Tenant A cannot read Tenant B's invoices")
    void test_TenantA_CannotRead_TenantB_Invoice() {
        // Create invoice for Tenant B
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantB, "CLIENT-B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), "corr-b"));
        InvoiceResponseDto invB = invoiceService.createAndRegisterInvoice(createSimpleRequest(), UUID.randomUUID().toString());
        TenantContextHolder.clear();

        assertNotNull(invB.id());

        // Switch execution context to Tenant A
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:read"), "corr-a"));
        Optional<Invoice> readAttempt = invoiceRepository.findById(invB.id());
        assertTrue(readAttempt.isEmpty(), "PostgreSQL RLS must conceal Tenant B's invoice from Tenant A");
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("RLS 2: Tenant A cannot query Tenant B's customers (0 rows returned)")
    void test_TenantA_CannotQuery_TenantB_Customers() {
        // Seed customer in Tenant B
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantB, "CLIENT-B", Set.of("ROLE_TENANT_ADMIN"), Set.of("customer:create"), "corr-b"));
        Customer custB = new Customer(UUID.randomUUID(), tenantB, "Beta Buyer");
        custB.setTin("1000000003");
        custB.setPhone("+251933333333");
        customerRepository.save(custB);
        TenantContextHolder.clear();

        // Query customers as Tenant A
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("customer:read"), "corr-a"));
        List<Customer> listForA = customerRepository.findAll();
        boolean containsB = listForA.stream().anyMatch(c -> c.getTenantId().equals(tenantB));
        assertFalse(containsB, "Tenant A must receive 0 rows for Tenant B's customers");
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("RLS 3: Missing tenant context fails closed (0 rows returned)")
    void test_MissingTenantContext_FailsClosed() {
        // Seed customer in Tenant A
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("customer:create"), "corr-a"));
        Customer custA = new Customer(UUID.randomUUID(), tenantA, "Alpha Customer");
        custA.setTin("1000000004");
        custA.setPhone("+251944444444");
        customerRepository.save(custA);
        TenantContextHolder.clear();

        // Attempt read without tenant context
        TenantContextHolder.clear();
        List<Customer> unauthenticatedList = customerRepository.findAll();
        assertTrue(unauthenticatedList.isEmpty(), "Missing tenant context MUST fail closed and return zero rows");
    }

    @Test
    @DisplayName("RLS 4: Privileged platform administration path can view cross-tenant records")
    void test_PlatformAdminPath_CanAccessCrossTenant() {
        // Seed data in Tenant A
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("customer:create"), "corr-a"));
        Customer cA = new Customer(UUID.randomUUID(), tenantA, "Alpha Customer 2");
        cA.setTin("1000000005");
        cA.setPhone("+251955555555");
        customerRepository.save(cA);
        TenantContextHolder.clear();

        // Access via platform operator context
        TenantContextHolder.setContext(new TenantContext(platformAdminId, "PLATFORM", null, "operator", "SAAS_ADMIN", Set.of("ROLE_PLATFORM_ADMIN"), Set.of("*"), null, "admin-corr"));
        Optional<Customer> adminView = customerRepository.findById(cA.getId());
        assertTrue(adminView.isPresent(), "Authorized platform admin path must have auditable cross-tenant visibility");
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("RLS 5: Pooled connection reuse does not leak tenant identity")
    void test_PooledConnectionReuse_NoTenantLeakage() throws SQLException {
        // Borrow connection under Tenant A
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_USER"), Set.of(), "corr-1"));
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT current_setting('app.current_tenant_id', true)")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(tenantA.toString(), rs.getString(1));
                }
            }
        }
        TenantContextHolder.clear();

        // Borrow connection without context — must be scrubbed upon checkout
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT current_setting('app.current_tenant_id', true)")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    String val = rs.getString(1);
                    assertTrue(val == null || val.isBlank(), "Scrubbed pooled connection must not leak previous tenant");
                }
            }
        }
    }

    // =========================================================================
    // 2. FISCAL SEQUENCE CONCURRENCY (PostgreSQL Atomic ON CONFLICT)
    // =========================================================================

    @Test
    @DisplayName("Sequence 1: 50 concurrent sequence requests yield strictly monotonic sequence numbers without duplicates")
    void test_Concurrent50SequenceAllocations_ZeroDuplicates() throws InterruptedException, ExecutionException {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), "seq-" + Thread.currentThread().getName()));
                try {
                    return sequenceService.allocateNextCounter(tenantA);
                } finally {
                    TenantContextHolder.clear();
                }
            }));
        }

        latch.countDown();
        Set<Long> allocated = ConcurrentHashMap.newKeySet();
        for (Future<Long> f : futures) {
            allocated.add(f.get());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(threads, allocated.size(), "All 50 sequence numbers must be unique (zero duplicates)");
        for (long i = 1; i <= threads; i++) {
            assertTrue(allocated.contains(i), "Sequence allocation must be contiguous without gaps: missing " + i);
        }
    }

    @Test
    @DisplayName("Sequence 2: Simultaneous allocations across Tenant A and Tenant B remain completely isolated")
    void test_MultiTenantSequenceConcurrency() throws InterruptedException, ExecutionException {
        int threadsPerTenant = 25;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<Long>> futuresA = new ArrayList<>();
        List<Future<Long>> futuresB = new ArrayList<>();

        for (int i = 0; i < threadsPerTenant; i++) {
            futuresA.add(executor.submit(() -> {
                latch.await();
                TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), "seq-a"));
                try {
                    return sequenceService.allocateNextCounter(tenantA);
                } finally {
                    TenantContextHolder.clear();
                }
            }));
            futuresB.add(executor.submit(() -> {
                latch.await();
                TenantContextHolder.setContext(TenantContext.createWithClient(tenantB, "CLIENT-B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), "seq-b"));
                try {
                    return sequenceService.allocateNextCounter(tenantB);
                } finally {
                    TenantContextHolder.clear();
                }
            }));
        }

        latch.countDown();

        Set<Long> setA = ConcurrentHashMap.newKeySet();
        Set<Long> setB = ConcurrentHashMap.newKeySet();

        for (Future<Long> f : futuresA) setA.add(f.get());
        for (Future<Long> f : futuresB) setB.add(f.get());

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(threadsPerTenant, setA.size());
        assertEquals(threadsPerTenant, setB.size());
        assertEquals(Long.valueOf(threadsPerTenant), setA.stream().max(Long::compare).orElse(0L));
        assertEquals(Long.valueOf(threadsPerTenant), setB.stream().max(Long::compare).orElse(0L));
    }

    // =========================================================================
    // 3. OFFLINE RECONCILIATION & 72-HOUR WINDOW
    // =========================================================================

    @Test
    @DisplayName("Offline 1: Replayed offline transaction flows through authoritative issuance with real fiscal outcome")
    void test_OfflineReconciliation_ProducesAuthoritativeInvoice() {
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("offline:sync"), "corr"));

        String payload = "{\"amount\": 1200.00}";
        var item = new SyncOfflineBatchRequest.OfflineInvoiceItemDto(101L, Instant.now().minus(2, ChronoUnit.HOURS), payload, "SIG-VALID-TEST");
        var batch = new SyncOfflineBatchRequest(deviceIdA, List.of(item));

        List<OfflineTransactionBuffer> buffers = offlineSyncService.bufferOfflineTransactions(batch);
        assertEquals(1, buffers.size());
        OfflineTransactionBuffer buf = buffers.get(0);
        assertEquals("QUEUED", buf.getSyncStatus());

        // Execute authoritative reconciliation
        offlineReconciliationJob.executeReconciliation();

        // Verify buffer state under tenant context
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("offline:sync"), "corr"));
        OfflineTransactionBuffer updated = bufferRepository.findById(buf.getId()).orElseThrow();
        assertEquals("SYNCED", updated.getSyncStatus());
        assertNotNull(updated.getIrn());
        assertFalse(updated.getIrn().startsWith("OFFLINE-SYNC-"), "Must NEVER fabricate OFFLINE-SYNC-* IRN");

        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Offline 2: Transaction older than 72 hours is rejected server-side (EXPIRED_72H)")
    void test_OfflineReconciliation_OlderThan72Hours_Rejected() {
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("offline:sync"), "corr"));

        // Direct database insertion of an expired buffer (>72 hours old) to simulate delayed network transmission
        OfflineTransactionBuffer expired = new OfflineTransactionBuffer(
                UUID.randomUUID(), tenantA, deviceIdA, 999L, "{\"amount\": 500}", "SIG-VALID",
                Instant.now().minus(75, ChronoUnit.HOURS)
        );
        bufferRepository.save(expired);

        offlineReconciliationJob.executeReconciliation();

        // Verify expired buffer under tenant context
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("offline:sync"), "corr"));
        OfflineTransactionBuffer afterJob = bufferRepository.findById(expired.getId()).orElseThrow();
        assertEquals("FAILED", afterJob.getSyncStatus());
        assertEquals("EXPIRED_72H", afterJob.getLifecycleState());
        assertNull(afterJob.getIrn(), "Expired transactions must never receive an IRN");

        TenantContextHolder.clear();
    }

    // =========================================================================
    // 4. DATABASE IMMUTABILITY TRIGGERS & AUDIT HASH CHAIN
    // =========================================================================

    @Test
    @DisplayName("Sequence Concurrency: 1, 2, 10, 50, 100 concurrent issuers yield zero duplicate sequences")
    void test_FiscalSequenceStress_MultipleTiers_1_2_10_50_100() throws Exception {
        int[] tiers = {1, 2, 10, 50, 100};
        UUID stressTenant = UUID.randomUUID();
        String uniqueTin = "9" + String.format("%09d", Math.abs(stressTenant.hashCode()));
        
        // Seed tenant
        Tenant t = new Tenant(stressTenant, "ORG-STRESS-" + stressTenant.toString().substring(0, 6), "Stress Test Corp", "Stress", uniqueTin);
        t.activate();
        tenantRepository.save(t);

        long expectedCounter = 1L;
        for (int tier : tiers) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(tier, 25));
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<Long>> futures = new ArrayList<>();

            for (int i = 0; i < tier; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    TenantContextHolder.setContext(TenantContext.createWithClient(stressTenant, "CLIENT-STRESS", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), "stress"));
                    try {
                        return sequenceService.allocateNextCounter(stressTenant);
                    } finally {
                        TenantContextHolder.clear();
                    }
                }));
            }

            latch.countDown();
            Set<Long> tierAllocated = ConcurrentHashMap.newKeySet();
            List<Long> tierList = new ArrayList<>();
            for (Future<Long> f : futures) {
                Long val = f.get();
                tierAllocated.add(val);
                tierList.add(val);
            }

            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);

            int duplicates = tierList.size() - tierAllocated.size();
            long minVal = tierAllocated.stream().min(Long::compare).orElse(0L);
            long maxVal = tierAllocated.stream().max(Long::compare).orElse(0L);

            System.out.printf("SEQUENCE STRESS TIER %d: Generated range [%d - %d], count=%d, duplicates=%d%n",
                    tier, minVal, maxVal, tierAllocated.size(), duplicates);

            assertEquals(0, duplicates, "Tier " + tier + " must have exactly ZERO duplicates");
            assertEquals(tier, tierAllocated.size(), "Tier " + tier + " must allocate exactly " + tier + " unique counters");
            assertEquals(expectedCounter, minVal, "Tier " + tier + " minimum counter mismatch");
            assertEquals(expectedCounter + tier - 1, maxVal, "Tier " + tier + " maximum counter mismatch");

            expectedCounter += tier;
        }
    }

    // =========================================================================
    // 4. DATABASE IMMUTABILITY TRIGGERS & AUDIT HASH CHAIN
    // =========================================================================

    @Test
    @DisplayName("Immutability: Registered invoice rejects mutation of EVERY fiscal field independently (JPA & SQL)")
    void test_RegisteredInvoice_Immutability_EveryFiscalFieldProhibited() {
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), "corr"));

        Invoice invoice = new Invoice(UUID.randomUUID(), tenantA, "DOC-IMMUT-" + System.currentTimeMillis(), 500L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoice.setBuyerLegalName("Original Buyer");
        invoice.setBuyerTin("1234567890");

        InvoiceLine line = new InvoiceLine(UUID.randomUUID(), tenantA, 1, "ITEM-01", "Product 1", "GOODS", "PCS",
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), "VAT15",
                new BigDecimal("0.1500"), new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("115.00"));
        invoice.addLine(line);
        invoice.recalculateTotals();
        invoice = invoiceRepository.save(invoice);

        // Mark invoice REGISTERED with authoritative government identity
        String uniqueIrn = "IRN-IMMUT-" + UUID.randomUUID();
        String uniqueRrn = "RRN-IMMUT-" + UUID.randomUUID();
        invoice.markRegistered(uniqueIrn, uniqueRrn, "2026-10-01T12:00:00Z", "SIGNED-QR-DATA", "SIGNED-INVOICE-XML");
        final Invoice registered = invoiceRepository.save(invoice);
        UUID invoiceId = registered.getId();
        UUID lineId = line.getId();

        // 1. Verify JPA-level setter immutability
        assertThrows(BusinessException.class, () -> registered.setGrandTotal(new BigDecimal("999.00")), "JPA: mutating grandTotal must fail");
        assertThrows(BusinessException.class, () -> registered.setDocumentNumber("FORGED-DOC"), "JPA: mutating documentNumber must fail");
        assertThrows(BusinessException.class, () -> registered.setInvoiceCounter(9999L), "JPA: mutating invoiceCounter must fail");
        assertThrows(BusinessException.class, () -> registered.setBuyerLegalName("Hacked Buyer"), "JPA: mutating buyerLegalName must fail");
        assertThrows(BusinessException.class, () -> registered.setBuyerTin("0000000000"), "JPA: mutating buyerTin must fail");
        assertThrows(BusinessException.class, () -> registered.addLine(new InvoiceLine()), "JPA: adding line to registered invoice must fail");

        // 2. Verify Database Trigger / SQL Level Immutability under application role
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET grand_total = 999.00 WHERE id = ?", invoiceId), "SQL: mutating grand_total must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET document_number = 'FORGED-DOC' WHERE id = ?", invoiceId), "SQL: mutating document_number must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET invoice_counter = 9999 WHERE id = ?", invoiceId), "SQL: mutating invoice_counter must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET invoice_date = NOW() - interval '30 days' WHERE id = ?", invoiceId), "SQL: mutating invoice_date must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET currency = 'USD' WHERE id = ?", invoiceId), "SQL: mutating currency must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET pre_tax_total = 500.00 WHERE id = ?", invoiceId), "SQL: mutating pre_tax_total must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET tax_total = 50.00 WHERE id = ?", invoiceId), "SQL: mutating tax_total must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET excise_total = 25.00 WHERE id = ?", invoiceId), "SQL: mutating excise_total must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET buyer_legal_name = 'Forged Name' WHERE id = ?", invoiceId), "SQL: mutating buyer_legal_name must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET buyer_tin = '9999999999' WHERE id = ?", invoiceId), "SQL: mutating buyer_tin must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET signed_qr = 'FORGED-QR' WHERE id = ?", invoiceId), "SQL: mutating signed_qr must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET irn = 'FORGED-IRN' WHERE id = ?", invoiceId), "SQL: mutating irn must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET signed_invoice = 'FORGED-SIG' WHERE id = ?", invoiceId), "SQL: mutating signed_invoice must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoices SET ack_date = '2099-01-01' WHERE id = ?", invoiceId), "SQL: mutating ack_date must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId), "SQL: deleting registered invoice must fail");

        // 3. Verify invoice_lines table trigger immutability
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoice_lines SET unit_price = 999.00 WHERE id = ?", lineId), "SQL: mutating invoice_lines unit_price must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoice_lines SET quantity = 50.00 WHERE id = ?", lineId), "SQL: mutating invoice_lines quantity must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE invoice_lines SET product_description = 'Forged Item' WHERE id = ?", lineId), "SQL: mutating invoice_lines description must fail");
        assertThrows(Exception.class, () -> jdbcTemplate.update("DELETE FROM invoice_lines WHERE id = ?", lineId), "SQL: deleting registered invoice_line must fail");

        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Audit Hash Chain: Verifies cryptographic chaining, detects historical payload and sequence tampering")
    void test_AuditHashChain_TamperingDetection() {
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT-A", Set.of("ROLE_TENANT_ADMIN"), Set.of("audit:record"), "corr"));

        String streamId = "INVOICE";
        // 1. Generate chain of 5 valid events
        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantA, streamId, "OPERATOR-1", "ACTION-" + i, "DOC", "ID-" + i, "PAYLOAD-" + i, "127.0.0.1");
        }

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantA, streamId);
        assertEquals(5, events.size());

        // 2. Verify initial chain is valid
        var initialResult = chainVerifier.verifyStreamChain(tenantA, streamId, events);
        assertTrue(initialResult.isValid(), "Initial audit chain must be valid");
        assertEquals(5, initialResult.verifiedEventCount());

        // 3. Verify single event cryptographic consistency
        for (AuditEvent evt : events) {
            assertTrue(chainVerifier.verifySingleEvent(evt), "Event hash must match calculated hash");
        }

        // 4. Verify detection of payload tampering
        AuditEvent evt2 = events.get(2);
        AuditEvent tamperedPayloadEvent = new AuditEvent(
                evt2.getId(),
                evt2.getTenantId(),
                evt2.getStreamId(),
                evt2.getSequenceNumber(),
                evt2.getActorId(),
                evt2.getActorType(),
                evt2.getClientId(),
                evt2.getDeviceId(),
                evt2.getAction(),
                evt2.getResourceType(),
                evt2.getResourceId(),
                evt2.getClientIp(),
                evt2.getUserAgent(),
                "FORGED_PAYLOAD_HASH", // Corrupted payload hash
                evt2.getPreviousEventHash(),
                evt2.getEventHash(),
                evt2.getCorrelationId(),
                evt2.getTraceId(),
                evt2.getApplicationVersion(),
                evt2.getSchemaVersion(),
                evt2.getTimestamp()
        );
        assertFalse(chainVerifier.verifySingleEvent(tamperedPayloadEvent), "Tampered event payload must fail hash verification");

        List<AuditEvent> tamperedList = new ArrayList<>(events);
        tamperedList.set(2, tamperedPayloadEvent);
        var tamperedChainResult = chainVerifier.verifyStreamChain(tenantA, streamId, tamperedList);
        assertFalse(tamperedChainResult.isValid(), "Chain verifier must reject tampered event payload");
        assertEquals(et.ut.einvoice.audit.service.AuditChainVerifier.VerificationStatus.TAMPERED_EVENT_HASH, tamperedChainResult.status());

        // 5. Verify detection of sequence gap / deletion
        List<AuditEvent> deletedEventList = new ArrayList<>(events);
        deletedEventList.remove(2); // Remove sequence 3
        var gapResult = chainVerifier.verifyStreamChain(tenantA, streamId, deletedEventList);
        assertFalse(gapResult.isValid(), "Chain verifier must reject missing/deleted event");
        assertEquals(et.ut.einvoice.audit.service.AuditChainVerifier.VerificationStatus.SEQUENCE_GAP, gapResult.status());

        // 6. Ordinary application role immutability: UPDATE and DELETE blocked by database trigger
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("UPDATE audit_events SET action = 'FORGED' WHERE id = ?", events.get(0).getId());
        }, "Database trigger trg_audit_events_immutability must reject UPDATE statements");

        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("DELETE FROM audit_events WHERE id = ?", events.get(0).getId());
        }, "Database trigger trg_audit_events_immutability must reject DELETE statements");

        TenantContextHolder.clear();
    }

    private CreateInvoiceRequest createSimpleRequest() {
        LineItemRequest item = new LineItemRequest(
                "ITEM-01",
                "Product Alpha",
                "GOODS",
                "PCS",
                BigDecimal.ONE,
                BigDecimal.valueOf(100.00),
                BigDecimal.ZERO,
                "TOT-A",
                BigDecimal.ZERO
        );
        return new CreateInvoiceRequest(
                TransactionType.B2C,
                "CASH",
                "IMMEDIATE",
                null,
                List.of(item),
                null,
                null,
                null,
                false
        );
    }
}
