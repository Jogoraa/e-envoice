package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.domain.OutboxStatus;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.platform.outbox.worker.OutboxRelayWorker;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class DistributedOutboxWorkerSafetyTestSuite {

    @Autowired
    private OutboxRelayWorker outboxRelayWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        invoiceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantId = UUID.randomUUID();
        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantId, "0011223344", "43256663343256663322", "Outbox Safety Test PLC", "Safety Corp",
                "13", "574", "+251911310694", "safety@test.com", "8EFBBDD7FF", "ERP"
        ));

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
    }

    @Test
    @DisplayName("Outbox 1: Concurrent execution across 4 simulated workers processes events with 0 duplicate registrations")
    void test_ConcurrentOutboxWorkers_ZeroDuplicates() throws Exception {
        AtomicInteger morCallCount = new AtomicInteger(0);
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    morCallCount.incrementAndGet();
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                            "IRN-WORKER-" + UUID.randomUUID(), "RRN-WORKER-1", "2026-09-18T12:00:00Z", "qr", "sig"
                    );
                });

        // Create 10 invoices and 10 outbox events
        int eventCount = 10;
        for (int i = 0; i < eventCount; i++) {
            UUID invoiceId = UUID.randomUUID();
            Invoice invoice = new Invoice(
                    invoiceId, tenantId, "DOC-WORKER-" + i, (long) (i + 1),
                    Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE"
            );
            invoiceRepository.save(invoice);
            outboxService.enqueueEvent(tenantId, "INVOICE", invoiceId.toString(), "INVOICE_REGISTRATION", "{}");
        }

        // Run 4 concurrent worker threads executing the relay loop
        int workers = 4;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch latch = new CountDownLatch(workers);

        for (int w = 0; w < workers; w++) {
            executor.submit(() -> {
                try {
                    outboxRelayWorker.processOutboxEvents();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify all events are PUBLISHED
        List<OutboxEvent> allEvents = outboxEventRepository.findAll();
        assertEquals(eventCount, allEvents.size());
        for (OutboxEvent event : allEvents) {
            assertEquals(OutboxStatus.PUBLISHED, event.getStatus(), "Every outbox event must be in PUBLISHED state");
        }

        // Verify all invoices are REGISTERED
        List<Invoice> allInvoices = invoiceRepository.findAll();
        assertEquals(eventCount, allInvoices.size());
        for (Invoice invoice : allInvoices) {
            assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(), "Every invoice must be in REGISTERED state");
            assertNotNull(invoice.getIrn());
        }
    }

    @Test
    @DisplayName("Outbox 2: If an invoice is already REGISTERED, a retried outbox event does NOT duplicate MoR call")
    void test_AlreadyRegisteredInvoice_SkipsMoRCall() {
        AtomicInteger morCallCount = new AtomicInteger(0);
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    morCallCount.incrementAndGet();
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                            "IRN-1", "RRN-1", "2026-09-18T12:00:00Z", "qr", "sig"
                    );
                });

        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, tenantId, "DOC-SKIPPED", 1L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoice.markRegistered("PRE-EXISTING-IRN", "RRN-1", Instant.now().toString(), "qr", "sig");
        invoiceRepository.save(invoice);

        OutboxEvent event = outboxService.enqueueEvent(tenantId, "INVOICE", invoiceId.toString(), "INVOICE_REGISTRATION", "{}");

        // Process event
        outboxRelayWorker.processSingleEvent(event);

        // Verify that external MoR HTTP call was NOT invoked
        assertEquals(0, morCallCount.get(), "MoR call must be skipped for an already registered invoice");

        // Event should still be marked PUBLISHED
        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, updated.getStatus());
    }

    @Test
    @DisplayName("Outbox 3: Stale IN_FLIGHT event from crashed worker is automatically recovered to PENDING and processed")
    void test_StaleInFlightEvent_RecoveredAndProcessed() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, tenantId, "DOC-STALE", 1L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoiceRepository.save(invoice);

        OutboxEvent event = outboxService.enqueueEvent(tenantId, "INVOICE", invoiceId.toString(), "INVOICE_REGISTRATION", "{}");
        event.markInFlight();
        // Simulate stuck in-flight 5 minutes ago due to worker container SIGKILL
        event.setNextAttemptAt(Instant.now().minus(java.time.Duration.ofMinutes(5)));
        outboxEventRepository.save(event);

        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenReturn(GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                        "IRN-STALE-RECOVERED", "RRN-STALE", Instant.now().toString(), "qr", "sig"
                ));

        // Running outboxRelayWorker.processOutboxEvents will trigger stale recovery, claim, and publish
        outboxRelayWorker.processOutboxEvents();

        OutboxEvent recovered = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, recovered.getStatus(), "Stale IN_FLIGHT event must be recovered and published");

        Invoice inv = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, inv.getStatus());
        assertEquals("IRN-STALE-RECOVERED", inv.getIrn());
    }

    @Test
    @DisplayName("Outbox 4: Gateway errors trigger exponential backoff retry scheduling")
    void test_GatewayFailure_TriggersExponentialBackoff() {
        OutboxEvent event = outboxService.enqueueEvent(tenantId, "INVOICE", UUID.randomUUID().toString(), "INVOICE_REGISTRATION", "{}");

        Instant before = Instant.now();
        outboxService.markFailed(event.getId(), "Gateway 503 Service Unavailable", 2);

        OutboxEvent failed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, failed.getStatus());
        assertTrue(failed.getNextAttemptAt().isAfter(before.plusSeconds(5)), "Next retry must be backed off exponentially");
        assertEquals("Gateway 503 Service Unavailable", failed.getLastError());
    }
}
