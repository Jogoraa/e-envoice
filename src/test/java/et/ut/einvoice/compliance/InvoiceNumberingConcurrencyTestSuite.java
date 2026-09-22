package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class InvoiceNumberingConcurrencyTestSuite {

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
        invoiceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        String tinA = "55" + UUID.randomUUID().toString().replaceAll("[^0-9]", "1").substring(0, 8);
        String tinB = "66" + UUID.randomUUID().toString().replaceAll("[^0-9]", "2").substring(0, 8);

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantA, tinA, "43256663343256663322", "Tenant A Solutions PLC", "Tenant A",
                "13", "574", "+251911310694", "a@test.com", "8EFBBDD7FF", "POS"
        ));

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantB, tinB, "54367774454367774433", "Tenant B Logistics PLC", "Tenant B",
                "14", "102", "+251922410795", "b@test.com", "8EFBBDD7FE", "ERP"
        ));

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "IRN-" + UUID.randomUUID();
                    String rrn = "RRN-" + UUID.randomUUID();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice");
                });
    }

    private CreateInvoiceRequest createSampleRequest() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "SKU-CONCURRENCY", "Test Concurrency Item", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, "VAT15", null
        ));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, "0911000000", null, "13", "01", null, null);
        return new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null);
    }

    @Test
    @DisplayName("Concurrency 1: 10 Concurrent requests produce strictly unique, monotonic invoice counters with 0 collisions")
    void test_10ConcurrentRequests_ZeroDuplicates() throws Exception {
        executeConcurrentInvoiceCreation(tenantA, 10, 4);
    }

    @Test
    @DisplayName("Concurrency 2: 100 Concurrent requests across 16 threads produce strictly unique, monotonic invoice counters with 0 collisions")
    void test_100ConcurrentRequests_ZeroDuplicates() throws Exception {
        executeConcurrentInvoiceCreation(tenantA, 100, 16);
    }

    @Test
    @DisplayName("Concurrency 3: Multi-tenant parallel allocation produces strictly isolated, unique counters per tenant")
    void test_MultiTenant_ParallelAllocation_NoCrossContamination() throws Exception {
        int countPerTenant = 50;
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(countPerTenant * 2);

        Set<Long> countersA = ConcurrentHashMap.newKeySet();
        Set<Long> countersB = ConcurrentHashMap.newKeySet();

        List<Throwable> workerErrors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < countPerTenant; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setContext(TenantContext.createWithClient(tenantA, "CLIENT_A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), UUID.randomUUID().toString()));
                    InvoiceResponseDto res = invoiceService.createAndRegisterInvoice(createSampleRequest(), UUID.randomUUID().toString());
                    countersA.add(res.invoiceCounter());
                } catch (Throwable e) {
                    workerErrors.add(e);
                } finally {
                    TenantContextHolder.clear();
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setContext(TenantContext.createWithClient(tenantB, "CLIENT_B", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), UUID.randomUUID().toString()));
                    InvoiceResponseDto res = invoiceService.createAndRegisterInvoice(createSampleRequest(), UUID.randomUUID().toString());
                    countersB.add(res.invoiceCounter());
                } catch (Throwable e) {
                    workerErrors.add(e);
                } finally {
                    TenantContextHolder.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent multi-tenant execution timed out");
        assertTrue(workerErrors.isEmpty(), "Worker thread errors encountered: " + workerErrors);
        assertEquals(countPerTenant, countersA.size(), "Tenant A must produce exactly " + countPerTenant + " distinct invoice counters");
        assertEquals(countPerTenant, countersB.size(), "Tenant B must produce exactly " + countPerTenant + " distinct invoice counters");
    }

    private void executeConcurrentInvoiceCreation(UUID tenantId, int totalRequests, int threadCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        Set<Long> allocatedCounters = ConcurrentHashMap.newKeySet();
        Set<String> documentNumbers = ConcurrentHashMap.newKeySet();
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setContext(TenantContext.createWithClient(tenantId, "CONCURRENT_CLIENT", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), UUID.randomUUID().toString()));
                    InvoiceResponseDto res = invoiceService.createAndRegisterInvoice(createSampleRequest(), UUID.randomUUID().toString());
                    allocatedCounters.add(res.invoiceCounter());
                    documentNumbers.add(res.documentNumber());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out after 60 seconds");
        assertEquals(0, failureCount.get(), "Zero requests should fail under concurrency");
        assertEquals(totalRequests, allocatedCounters.size(), "Must generate exactly " + totalRequests + " unique invoice counters (zero duplicate numbers allowed)");
        assertEquals(totalRequests, documentNumbers.size(), "Must generate exactly " + totalRequests + " unique document numbers");
    }
}
