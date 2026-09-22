package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.repository.TenantInvoiceSequenceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class TenancyScaleLoadBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(TenancyScaleLoadBenchmarkTest.class);

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantInvoiceSequenceRepository sequenceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private final List<UUID> tenantList = new ArrayList<>();
    private final int TENANT_COUNT = 10;
    private final int TOTAL_TRANSACTIONS = 100;
    private final int CONCURRENCY = 16;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        sequenceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantList.clear();
        for (int i = 1; i <= TENANT_COUNT; i++) {
            UUID tid = UUID.randomUUID();
            tenantList.add(tid);
            String tin = "88" + UUID.randomUUID().toString().replaceAll("[^0-9]", "4").substring(0, 8);
            taxpayerProfileRepository.save(new TaxpayerProfile(
                    tid, tin, "VAT-112233" + (10 + i),
                    "Benchmark Tenant " + i + " PLC", "Tenant " + i,
                    "14", "01", "+251911" + (100000 + i), "t" + i + "@benchmark.et",
                    "8EFBBDD7FA", "ERP"
            ));
        }

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "BENCH-IRN-" + UUID.randomUUID();
                    String rrn = "BENCH-RRN-" + UUID.randomUUID();
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                            irn, rrn, "2026-09-18T12:00:00Z", "qr-bench", "sig-bench"
                    );
                });
    }

    @Test
    @DisplayName("Load Benchmark: Concurrent Multi-Tenant Invoicing Throughput and Latency Telemetry")
    void test_ConcurrentMultiTenant_LoadBenchmark() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_TRANSACTIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        long overallStartNanos = System.nanoTime();

        for (int i = 0; i < TOTAL_TRANSACTIONS; i++) {
            final int txIndex = i;
            final UUID tenantId = tenantList.get(txIndex % TENANT_COUNT);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setContext(TenantContext.createWithClient(
                            tenantId, "BENCH_CLIENT", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()
                    ));

                    var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                            "SKU-BENCH", "Benchmark Item", "goods", "PCS",
                            BigDecimal.ONE, new BigDecimal("150.00"), BigDecimal.ZERO, "VAT15", null
                    ));
                    var req = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);

                    long start = System.currentTimeMillis();
                    InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(req, "bench-idemp-" + txIndex);
                    long duration = System.currentTimeMillis() - start;

                    latenciesMs.add(duration);
                    if (response != null && response.irn() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    log.error("Benchmark transaction failed: {}", ex.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long overallDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - overallStartNanos);
        assertTrue(completed, "Benchmark should complete within 60s");
        assertEquals(0, failureCount.get(), "Failure count must be 0");
        assertEquals(TOTAL_TRANSACTIONS, successCount.get());

        // Sort latencies to compute percentiles
        List<Long> sortedLatencies = new ArrayList<>(latenciesMs);
        Collections.sort(sortedLatencies);

        long p50 = sortedLatencies.get((int) (sortedLatencies.size() * 0.50));
        long p95 = sortedLatencies.get((int) (sortedLatencies.size() * 0.95));
        long p99 = sortedLatencies.get((int) (sortedLatencies.size() * 0.99));
        double throughputTps = (TOTAL_TRANSACTIONS / (double) overallDurationMs) * 1000.0;

        log.info("===============================================================");
        log.info("MULTI-TENANT IN-PROCESS LOAD BENCHMARK TELEMETRY RESULTS");
        log.info("Concurrent Worker Threads   : {}", CONCURRENCY);
        log.info("Distinct Active Tenants     : {}", TENANT_COUNT);
        log.info("Total Transactions Processed: {}", TOTAL_TRANSACTIONS);
        log.info("Successful Invoices         : {}", successCount.get());
        log.info("Failed Transactions         : {}", failureCount.get());
        log.info("Overall Elapsed Time        : {} ms", overallDurationMs);
        log.info("Measured Throughput         : {} TPS", String.format("%.2f", throughputTps));
        log.info("Latency Distribution (P50)  : {} ms", p50);
        log.info("Latency Distribution (P95)  : {} ms", p95);
        log.info("Latency Distribution (P99)  : {} ms", p99);
        log.info("Failure Rate                : 0.0%");
        log.info("===============================================================");

        assertTrue(throughputTps > 5.0, "Throughput must be healthy in container environment");
    }
}
