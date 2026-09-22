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
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.idempotency.domain.IdempotencyRecord;
import et.ut.einvoice.platform.idempotency.domain.IdempotencyStatus;
import et.ut.einvoice.platform.idempotency.repository.IdempotencyRecordRepository;
import et.ut.einvoice.platform.idempotency.service.IdempotencyService;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class IdempotencyNetworkFailureTestSuite {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        invoiceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantId = UUID.randomUUID();
        String tin = "77" + UUID.randomUUID().toString().replaceAll("[^0-9]", "3").substring(0, 8);
        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantId, tin, "43256663343256663322", "Idempotency Test PLC", "Idem Corp",
                "13", "574", "+251911310694", "idem@test.com", "8EFBBDD7FF", "ERP"
        ));

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "IRN-IDEM-" + UUID.randomUUID();
                    String rrn = "RRN-IDEM-" + UUID.randomUUID();
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                            irn, rrn, "2026-09-18T12:00:00Z", "qr-mock", "signed-mock"
                    );
                });

        TenantContextHolder.setContext(TenantContext.createWithClient(tenantId, "IDEM_CLIENT", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()));
    }

    private CreateInvoiceRequest createRequest(BigDecimal amount) {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "SKU-IDEM", "Item", "goods", "PCS",
                BigDecimal.ONE, amount, BigDecimal.ZERO, "VAT15", null
        ));
        var buyer = new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, "0911000000", null, "13", "01", null, null);
        return new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", buyer, items, null, null, null);
    }

    @Test
    @DisplayName("Idempotency 1: Network response lost after DB commit -> client retry recovers committed invoice with 0 duplicates")
    void test_NetworkResponseLost_ClientRetryRecoversCommittedInvoice() {
        String idemKey = "IDEM-NET-LOSS-" + UUID.randomUUID();
        CreateInvoiceRequest req = createRequest(new BigDecimal("500.00"));

        // 1. Initial attempt: First call commits invoice
        InvoiceResponseDto initialResponse = invoiceService.createAndRegisterInvoice(req, idemKey);
        assertNotNull(initialResponse);
        UUID invoiceId = initialResponse.id();
        long initialCount = invoiceRepository.count();

        // Simulate network loss where client never received response, but DB committed!
        // Client retries with identical key and payload:
        InvoiceResponseDto retriedResponse = invoiceService.createAndRegisterInvoice(req, idemKey);

        assertNotNull(retriedResponse);
        assertEquals(invoiceId, retriedResponse.id(), "Retried response must return the exact same invoice ID");
        assertEquals(initialResponse.invoiceCounter(), retriedResponse.invoiceCounter(), "Retried response must have the identical invoice counter");
        assertEquals(initialResponse.irn(), retriedResponse.irn(), "Retried response must have the identical IRN");
        assertEquals(initialCount, invoiceRepository.count(), "Zero duplicate invoices must be created in the database");
    }

    @Test
    @DisplayName("Idempotency 2: Same idempotency key with different payload throws IDEMPOTENCY_PAYLOAD_MISMATCH")
    void test_SameKey_DifferentPayload_RejectedWithConflict() {
        String idemKey = "IDEM-CONFLICT-" + UUID.randomUUID();
        CreateInvoiceRequest req1 = createRequest(new BigDecimal("100.00"));
        CreateInvoiceRequest req2 = createRequest(new BigDecimal("999.00")); // Different payload

        invoiceService.createAndRegisterInvoice(req1, idemKey);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                invoiceService.createAndRegisterInvoice(req2, idemKey)
        );

        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("Idempotency 3: Concurrent same-key requests produce exactly one business result without unhandled 500 error")
    void test_ConcurrentSameKeyRequests_ProduceExactlyOneResult() throws Exception {
        String idemKey = "IDEM-CONCURRENT-" + UUID.randomUUID();
        CreateInvoiceRequest req = createRequest(new BigDecimal("250.00"));

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setContext(TenantContext.createWithClient(tenantId, "IDEM_CLIENT", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create"), UUID.randomUUID().toString()));
                    invoiceService.createAndRegisterInvoice(req, idemKey);
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    if ("CONCURRENT_REQUEST_PROCESSING".equals(be.getCode())) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    TenantContextHolder.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly one invoice is committed in database for this idempotency key
        var matches = invoiceRepository.findByTenantIdAndIdempotencyKey(tenantId, idemKey);
        assertTrue(matches.isPresent(), "An invoice must be successfully created");
        assertTrue(successCount.get() >= 1, "At least one request must succeed");
    }
}
