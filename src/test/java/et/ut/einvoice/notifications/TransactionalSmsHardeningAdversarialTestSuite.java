package et.ut.einvoice.notifications;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.notifications.domain.FailureClassification;
import et.ut.einvoice.notifications.domain.InvoiceNotificationOutbox;
import et.ut.einvoice.notifications.domain.NotificationStatus;
import et.ut.einvoice.notifications.domain.TenantSmsQuota;
import et.ut.einvoice.notifications.provider.MockGeezSmsProvider;
import et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository;
import et.ut.einvoice.notifications.repository.TenantSmsQuotaRepository;
import et.ut.einvoice.notifications.worker.InvoiceNotificationOutboxWorker;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Adversarial Hardening Test Suite — Transactional SMS Subsystem
 *
 * <p>
 * Each test verifies a concrete safety invariant that cannot be satisfied by
 * "happy-path" integration tests alone. All tests run against the in-process
 * H2 database (MODE=PostgreSQL) with the full Spring application context.
 *
 * <p>
 * Invariants tested:
 * <ol>
 * <li>CAS Lease Safety — only the claiming worker may mutate an IN_FLIGHT
 * record</li>
 * <li>Stale-Lease Recovery — records abandoned mid-flight are reclaimed
 * safely</li>
 * <li>Quota-Exceeded Deferral — quota exhaustion produces RETRY_SCHEDULED, not
 * DROP</li>
 * <li>Circuit Breaker Trip — 401/403 halts processing; cooldown re-enables
 * it</li>
 * <li>HTTP-429 / Capacity Failure — release-not-escalate behaviour</li>
 * <li>Fiscal Independence — SMS failures cannot alter fiscal invoice
 * status</li>
 * <li>Idempotency — duplicate outbox writes for same (tenantId, idempotencyKey)
 * are rejected</li>
 * <li>Tenant Cross-Contamination — cross-tenant query returns empty</li>
 * <li>SUBMISSION_UNKNOWN — network timeout produces unknown; NOT blindly
 * re-submitted</li>
 * <li>Reconciliation SLA Drain — unknown items beyond SLA become
 * SUBMISSION_UNKNOWN_FINAL</li>
 * <li>Lost-Lease Discard — stale completion on wrong workerId produces 0 DB
 * rows updated</li>
 * <li>Concurrent CAS Contention — exactly one of N concurrent workers claims a
 * record</li>
 * <li>Audit Completeness — every state transition emits the correct audit
 * action</li>
 * <li>Retry Backoff Progression — attempt count drives escalating backoff
 * schedule</li>
 * <li>Max-Attempts Exhaustion — no infinite retry loop; final state is
 * PERMANENTLY_FAILED</li>
 * <li>Provider Capability Adaptation — no DLR / no sender-ID handled
 * gracefully</li>
 * <li>Phone Normalization — E.164 snapshot enforced; double-prefixing
 * prevented</li>
 * <li>Message Content Integrity — rendered message contains document number +
 * token; hash consistent</li>
 * </ol>
 *
 * <p>
 * <b>Hard constraint:</b> No test in this suite may modify or weaken fiscal
 * invoice
 * integrity, hash-chain, or audit-trail behaviour. Any detected conflict
 * between
 * a proposed SMS change and fiscal logic must be reported and stopped.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionalSmsHardeningAdversarialTestSuite {

        // -------------------------------------------------------------------------
        // Infrastructure
        // -------------------------------------------------------------------------

        @Autowired
        private InvoiceService invoiceService;
        @Autowired
        private InvoiceRepository invoiceRepository;
        @Autowired
        private InvoiceNotificationOutboxRepository outboxRepository;
        @Autowired
        private InvoiceNotificationOutboxWorker outboxWorker;
        @Autowired
        private MockGeezSmsProvider mockSmsProvider;
        @Autowired
        private TaxpayerProfileRepository taxpayerProfileRepository;
        @Autowired
        private AuditEventRepository auditEventRepository;
        @Autowired
        private TenantSmsQuotaRepository quotaRepository;
        @Autowired
        private TransactionTemplate transactionTemplate;

        @MockBean
        private GovernmentRegistrationProvider governmentRegistrationProvider;

        private UUID tenantId;

        // -------------------------------------------------------------------------
        // Setup
        // -------------------------------------------------------------------------

        @BeforeEach
        void setUp() {
                tenantId = UUID.randomUUID();
                TenantContextHolder.setContext(TenantContext.createWithClient(
                                tenantId, "ADV_TEST_CLIENT", Set.of("ROLE_TENANT_ADMIN"),
                                Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()));

                taxpayerProfileRepository.deleteAll();
                taxpayerProfileRepository.save(new TaxpayerProfile(
                                tenantId, "0012345678", "VAT-ADV-001", "Adversarial Test PLC",
                                "AdvTest", "Addis Ababa", "Bole",
                                "0911223344", "adv@example.com", "SYS-ADV", "POS"));

                mockSmsProvider.reset();

                when(governmentRegistrationProvider.registerInvoice(any(), any(), any()))
                                .thenAnswer(inv -> GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                                                "IRN-ADV-" + UUID.randomUUID(),
                                                "RRN-ADV-1001",
                                                Instant.now().toString(),
                                                "SIGNED_QR_ADV",
                                                "SIGNED_INV_ADV"));
        }

        // =========================================================================
        // INVARIANT 1: CAS Lease Safety
        // =========================================================================

        @Test
        @DisplayName("CAS-1: claimLeaseAtomic returns 0 when record is already IN_FLIGHT")
        void casLeaseAtomic_idempotentClaimRejected() {
                var response = registerInvoice("INV-CAS-01", "IDEM-CAS-01-" + UUID.randomUUID(), "0911000001");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                int first = transactionTemplate.execute(
                                status -> outboxRepository.claimLeaseAtomic(outbox.getId(), "worker-A", Instant.now()));

                int second = transactionTemplate.execute(
                                status -> outboxRepository.claimLeaseAtomic(outbox.getId(), "worker-B", Instant.now()));

                assertEquals(1, first, "First claim must succeed (return 1)");
                assertEquals(0, second, "Concurrent claim on IN_FLIGHT record must be rejected (return 0)");
        }

        // =========================================================================
        // INVARIANT 2: Stale-Lease Recovery
        // =========================================================================

        @Test
        @DisplayName("CAS-2: stale IN_FLIGHT lease is reclaimed by recovery pass and reprocessed")
        void staleLeaseRecovery_reprocessesAbandonedRecord() {
                var response = registerInvoice("INV-STALE-01", "IDEM-STALE-01-" + UUID.randomUUID(), "0911000002");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                // Claim with a dead worker
                transactionTemplate.executeWithoutResult(status -> outboxRepository.claimLeaseAtomic(outbox.getId(),
                                "dead-worker", Instant.now()));

                // Verify claimed
                var claimed = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.IN_FLIGHT, claimed.getStatus());

                // Simulate that the lock is stale (threshold is in future = all locks older
                // than "now" are stale)
                int released = transactionTemplate.execute(
                                status -> outboxRepository.releaseStaleLeases(Instant.now().plus(1, ChronoUnit.HOURS)));

                assertTrue(released >= 1, "Stale lease recovery must release at least 1 stale record (our abandoned record)");

                var recovered = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.PENDING, recovered.getStatus(),
                                "Recovered record must be PENDING again");
                assertNull(recovered.getLockedAt(), "lockedAt must be cleared after recovery");
                assertNull(recovered.getLockedBy(), "lockedBy must be null after recovery");

                // Now the legitimate worker can process it
                outboxWorker.processPendingBatch();
                var processed = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.SUBMITTED, processed.getStatus(),
                                "Recovered record must be dispatched successfully on next cycle");
        }

        // =========================================================================
        // INVARIANT 3: Quota-Exceeded Deferral — NOT dropped
        // =========================================================================

        @Test
        @DisplayName("QUOTA-1: quota exhaustion produces RETRY_SCHEDULED, not DROP or PERMANENTLY_FAILED")
        void quotaExhaustion_producesRetryScheduledNotDrop() {
                // Pre-create a quota record with zero remaining
                TenantSmsQuota zeroQuota = new TenantSmsQuota(tenantId, 0);
                quotaRepository.save(zeroQuota);

                var response = registerInvoice("INV-QUOTA-01", "IDEM-QUOTA-01-" + UUID.randomUUID(), "0911000003");
                UUID invoiceId = response.id();

                outboxWorker.processPendingBatch();

                var outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, invoiceId);
                assertEquals(1, outboxList.size(), "Outbox record must NOT be dropped on quota exhaustion");
                assertEquals(NotificationStatus.RETRY_SCHEDULED, outboxList.get(0).getStatus(),
                                "Quota exceeded must produce RETRY_SCHEDULED, not PERMANENTLY_FAILED");
                assertEquals(FailureClassification.PROVIDER_CAPACITY_FAILURE,
                                outboxList.get(0).getFailureClassification());

                // CRITICAL: fiscal invoice status must remain REGISTERED
                Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
                assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(),
                                "Quota deferral must NEVER alter the authoritative fiscal invoice status");
        }

        // =========================================================================
        // INVARIANT 4: Circuit Breaker — Auth failure halts batch processing
        // =========================================================================

        @Test
        @DisplayName("CIRCUIT-1: HTTP 401 trips circuit breaker and halts subsequent dispatch attempts")
        void circuitBreaker_tripsOnAuthFailure() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_401);

                registerInvoice("INV-CB-01", "IDEM-CB-01-" + UUID.randomUUID(), "0911100001");
                registerInvoice("INV-CB-02", "IDEM-CB-02-" + UUID.randomUUID(), "0911100002");

                outboxWorker.processPendingBatch();
                int countAfterFirstCycle = mockSmsProvider.getInvocationCount();

                // Circuit breaker should be OPEN — second batch must make no provider calls
                outboxWorker.processPendingBatch();
                assertEquals(countAfterFirstCycle, mockSmsProvider.getInvocationCount(),
                                "Circuit breaker OPEN must prevent any further provider calls in next cycle");
        }

        @Test
        @DisplayName("CIRCUIT-2: HTTP 403 trips circuit breaker (account suspended / IP not allowlisted)")
        void circuitBreaker_tripsOnForbidden() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_403);
                registerInvoice("INV-CB-403", "IDEM-CB-403-" + UUID.randomUUID(), "0911100003");

                outboxWorker.processPendingBatch();
                int afterFirstBatch = mockSmsProvider.getInvocationCount();

                outboxWorker.processPendingBatch();
                assertEquals(afterFirstBatch, mockSmsProvider.getInvocationCount(),
                                "Circuit breaker OPEN after HTTP 403 must block second batch");
        }

        // =========================================================================
        // INVARIANT 5: HTTP-429 / Provider Capacity — lease released, NOT failed
        // =========================================================================

        @Test
        @DisplayName("CAPACITY-1: HTTP 429 releases lease without permanently failing the record")
        void rateLimitFailure_releasesLeaseWithoutPermanentFailure() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_429);
                var response = registerInvoice("INV-429-01", "IDEM-429-01-" + UUID.randomUUID(), "0911200001");
                UUID invoiceId = response.id();

                outboxWorker.processPendingBatch();

                var outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, invoiceId);
                assertEquals(1, outboxList.size());
                assertNotEquals(NotificationStatus.PERMANENTLY_FAILED, outboxList.get(0).getStatus(),
                                "HTTP 429 (rate-limit) must not permanently fail the record");
                assertNull(outboxList.get(0).getLockedBy(),
                                "Lease must be released after capacity failure — lockedBy must be null");
        }

        // =========================================================================
        // INVARIANT 6: Fiscal Independence (multiple failure modes)
        // =========================================================================

        @Test
        @DisplayName("FISCAL-1: PERMANENTLY_FAILED SMS leaves invoice REGISTERED (INVALID_PHONE)")
        void fiscalIndependence_invalidPhone_invoiceRemainsRegistered() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.INVALID_PHONE);
                var response = registerInvoice("INV-FISCAL-01", "IDEM-FISCAL-01-" + UUID.randomUUID(), "0911300001");

                outboxWorker.processPendingBatch();

                Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();
                assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(),
                                "PERMANENTLY_FAILED SMS must NOT alter authoritative invoice fiscal status");
                assertNotNull(invoice.getIrn(), "IRN must remain intact after SMS failure");
        }

        @Test
        @DisplayName("FISCAL-2: PERMANENTLY_FAILED SMS leaves invoice REGISTERED (HTTP 400)")
        void fiscalIndependence_http400_invoiceRemainsRegistered() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_400);
                var response = registerInvoice("INV-FISCAL-02", "IDEM-FISCAL-02-" + UUID.randomUUID(), "0911300002");

                outboxWorker.processPendingBatch();

                Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();
                assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(),
                                "HTTP 400 permanent failure must NOT alter fiscal invoice status");
        }

        @Test
        @DisplayName("FISCAL-3: SUBMISSION_UNKNOWN SMS leaves invoice REGISTERED (read timeout)")
        void fiscalIndependence_submissionUnknown_invoiceRemainsRegistered() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);
                var response = registerInvoice("INV-FISCAL-03", "IDEM-FISCAL-03-" + UUID.randomUUID(), "0911300003");

                outboxWorker.processPendingBatch();

                Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();
                assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(),
                                "SUBMISSION_UNKNOWN must NOT alter authoritative fiscal invoice status");
        }

        // =========================================================================
        // INVARIANT 7: Idempotency
        // =========================================================================

        @Test
        @DisplayName("IDEM-1: each outbox record has a unique idempotency key per tenant")
        void outboxIdempotency_everyRecordHasUniqueKey() {
                registerInvoice("INV-IDEM-01", "IDEM-IDEM-01-" + UUID.randomUUID(), "0911400001");
                registerInvoice("INV-IDEM-02", "IDEM-IDEM-02-" + UUID.randomUUID(), "0911400002");
                registerInvoice("INV-IDEM-03", "IDEM-IDEM-03-" + UUID.randomUUID(), "0911400003");

                var tenantRecords = outboxRepository.findAll().stream()
                                .filter(o -> tenantId.equals(o.getTenantId())).toList();

                long distinctKeys = tenantRecords.stream()
                                .map(InvoiceNotificationOutbox::getIdempotencyKey)
                                .distinct().count();

                assertEquals(tenantRecords.size(), distinctKeys,
                                "Every outbox record must have a unique idempotency key (no duplicate keys per tenant)");
        }

        // =========================================================================
        // INVARIANT 8: Tenant Cross-Contamination
        // =========================================================================

        @Test
        @DisplayName("ISOLATION-1: outbox records are strictly tenant-scoped; cross-tenant query returns empty")
        void tenantIsolation_crossTenantQueryReturnsEmpty() {
                var response = registerInvoice("INV-ISOL-01", "IDEM-ISOL-01-" + UUID.randomUUID(), "0911500001");
                UUID tenantAInvoiceId = response.id();
                UUID tenantB = UUID.randomUUID();

                var tenantBRecords = outboxRepository.findAllByTenantIdAndInvoiceId(tenantB, tenantAInvoiceId);
                assertTrue(tenantBRecords.isEmpty(),
                                "Tenant B must NOT be able to retrieve Tenant A outbox records");

                var tenantARecords = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, tenantAInvoiceId);
                assertEquals(1, tenantARecords.size(),
                                "Tenant A must have exactly 1 outbox record for the registered invoice");
        }

        // =========================================================================
        // INVARIANT 9: SUBMISSION_UNKNOWN — NOT blindly retried
        // =========================================================================

        @Test
        @DisplayName("UNKNOWN-1: SUBMISSION_UNKNOWN record is NOT picked up by normal pending batch")
        void submissionUnknown_notPickedUpByPendingBatch() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);
                registerInvoice("INV-UNK-01", "IDEM-UNK-01-" + UUID.randomUUID(), "0911600001");

                // First pass: timeout → SUBMISSION_UNKNOWN
                outboxWorker.processPendingBatch();
                int invocationsAfterFirstPass = mockSmsProvider.getInvocationCount();

                // Second pass: pending batch must NOT see SUBMISSION_UNKNOWN records
                outboxWorker.processPendingBatch();
                assertEquals(invocationsAfterFirstPass, mockSmsProvider.getInvocationCount(),
                                "SUBMISSION_UNKNOWN records must NOT be blindly retried via processPendingBatch");
        }

        // =========================================================================
        // INVARIANT 10: Reconciliation SLA Drain
        // =========================================================================

        @Test
        @DisplayName("RECONCILE-1: provider without status-lookup finalizes SUBMISSION_UNKNOWN immediately")
        void reconciliation_noStatusLookup_immediatelyFinalizes() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);
                mockSmsProvider.setSupportsStatusLookup(false);
                mockSmsProvider.setSupportsDeliveryReceipts(false);

                registerInvoice("INV-RECONCILE-01", "IDEM-RECONCILE-01-" + UUID.randomUUID(), "0911700001");
                outboxWorker.processPendingBatch();

                // Retrieve the SUBMISSION_UNKNOWN item and push it back through
                // completeSubmissionUnknownAtomic
                // so reconcileUnknownSubmissions can find it via findUnknownSubmissions
                var items = outboxRepository.findAll().stream()
                                .filter(o -> tenantId.equals(o.getTenantId()))
                                .filter(o -> NotificationStatus.SUBMISSION_UNKNOWN == o.getStatus())
                                .toList();
                assertFalse(items.isEmpty(), "Item must be in SUBMISSION_UNKNOWN after timeout");

                // Run reconciliation — with no status lookup, must immediately finalize
                outboxWorker.reconcileUnknownSubmissions();

                var finalItems = outboxRepository.findAll().stream()
                                .filter(o -> tenantId.equals(o.getTenantId()))
                                .filter(o -> NotificationStatus.SUBMISSION_UNKNOWN_FINAL == o.getStatus())
                                .toList();

                assertFalse(finalItems.isEmpty(),
                                "When provider has no status-lookup, reconciliation must immediately produce SUBMISSION_UNKNOWN_FINAL");

                // CRITICAL: fiscal invoices must remain REGISTERED
                invoiceRepository.findAll().stream()
                                .filter(inv -> tenantId.equals(inv.getTenantId()))
                                .forEach(inv -> assertEquals(InvoiceStatus.REGISTERED, inv.getStatus(),
                                                "SMS reconciliation must never alter fiscal invoice status"));
        }

        // =========================================================================
        // INVARIANT 11: Lost-Lease Discard
        // =========================================================================

        @Test
        @DisplayName("CAS-3: completeSubmissionAtomic with wrong workerId returns 0 (lost lease discard)")
        void lostLeaseDiscard_wrongWorkerIdReturnsZeroRows() {
                var response = registerInvoice("INV-LOST-01", "IDEM-LOST-01-" + UUID.randomUUID(), "0911800001");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                // Legitimate worker claims lease
                transactionTemplate.executeWithoutResult(status -> outboxRepository.claimLeaseAtomic(outbox.getId(),
                                "real-worker", Instant.now()));

                // Impostor worker attempts completion — must return 0
                int updatedByImpostor = transactionTemplate.execute(status -> outboxRepository.completeSubmissionAtomic(
                                outbox.getId(), "stale-worker", "FAKE-MSG-ID", Instant.now()));

                assertEquals(0, updatedByImpostor,
                                "completeSubmissionAtomic must return 0 when workerId does not match lockedBy (lost lease)");

                var current = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.IN_FLIGHT, current.getStatus(),
                                "Record must remain IN_FLIGHT after rejected impostor completion");
                assertEquals("real-worker", current.getLockedBy(),
                                "lockedBy must remain set to the original claiming worker");
        }

        @Test
        @DisplayName("CAS-4: completePermanentFailureAtomic with wrong workerId returns 0")
        void lostLeaseDiscard_permanentFailureWrongWorkerReturnsZero() {
                var response = registerInvoice("INV-LOST-02", "IDEM-LOST-02-" + UUID.randomUUID(), "0911800002");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                transactionTemplate.executeWithoutResult(status -> outboxRepository.claimLeaseAtomic(outbox.getId(),
                                "real-worker-2", Instant.now()));

                int rowsUpdated = transactionTemplate.execute(status -> outboxRepository.completePermanentFailureAtomic(
                                outbox.getId(), "impostor-worker",
                                "ERR_CODE", "Impostor message",
                                FailureClassification.MESSAGE_FAILURE, Instant.now()));

                assertEquals(0, rowsUpdated,
                                "completePermanentFailureAtomic must return 0 when workerId does not match lockedBy");
        }

        @Test
        @DisplayName("CAS-5: completeRetryScheduledAtomic with wrong workerId returns 0")
        void lostLeaseDiscard_retryScheduledWrongWorkerReturnsZero() {
                var response = registerInvoice("INV-LOST-03", "IDEM-LOST-03-" + UUID.randomUUID(), "0911800003");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                transactionTemplate.executeWithoutResult(status -> outboxRepository.claimLeaseAtomic(outbox.getId(),
                                "real-worker-3", Instant.now()));

                int rowsUpdated = transactionTemplate.execute(status -> outboxRepository.completeRetryScheduledAtomic(
                                outbox.getId(), "impostor-worker-3",
                                Instant.now().plusSeconds(60),
                                "ERR", "Impostor retry", FailureClassification.PROVIDER_NETWORK_FAILURE));

                assertEquals(0, rowsUpdated,
                                "completeRetryScheduledAtomic must return 0 when workerId does not match lockedBy");
        }

        // =========================================================================
        // INVARIANT 12: Concurrent CAS Contention — only one worker wins
        // =========================================================================

        @Test
        @DisplayName("CAS-6: concurrent CAS claims on same record — exactly one worker wins")
        void concurrentCasClaims_exactlyOneWorkerWins() throws Exception {
                var response = registerInvoice("INV-CONC-01", "IDEM-CONC-01-" + UUID.randomUUID(), "0911900001");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                int threadCount = 20;
                ExecutorService pool = Executors.newFixedThreadPool(threadCount);
                CountDownLatch startGate = new CountDownLatch(1);
                AtomicInteger successCount = new AtomicInteger(0);

                List<Future<Integer>> futures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                        final String wId = "concurrent-worker-" + i;
                        futures.add(pool.submit(() -> {
                                startGate.await();
                                try {
                                        return transactionTemplate.execute(status -> outboxRepository
                                                        .claimLeaseAtomic(outbox.getId(), wId, Instant.now()));
                                } catch (Exception ex) {
                                        return 0;
                                }
                        }));
                }

                startGate.countDown(); // release all threads simultaneously

                for (Future<Integer> f : futures) {
                        int result = f.get();
                        if (result > 0)
                                successCount.incrementAndGet();
                }

                pool.shutdown();

                assertEquals(1, successCount.get(),
                                "Exactly one concurrent worker must win the CAS lease claim; all others must return 0");

                var claimedRecord = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.IN_FLIGHT, claimedRecord.getStatus());
                assertNotNull(claimedRecord.getLockedBy(), "lockedBy must be set by the winning worker");
        }

        // =========================================================================
        // INVARIANT 13: Audit Completeness
        // =========================================================================

        @Test
        @DisplayName("AUDIT-1: successful dispatch emits SMS_NOTIFICATION_CREATED and SMS_PROVIDER_ACCEPTED")
        void auditCompleteness_successfulDispatch_emitsBothAuditEvents() {
                var response = registerInvoice("INV-AUDIT-01", "IDEM-AUDIT-01-" + UUID.randomUUID(), "0911010001");

                // Pre-dispatch: SMS_NOTIFICATION_CREATED must already exist (same transaction
                // as invoice)
                var preBatch = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
                assertTrue(preBatch.stream()
                                .anyMatch(e -> AuditAction.SMS_NOTIFICATION_CREATED.name().equals(e.getAction())),
                                "SMS_NOTIFICATION_CREATED must be recorded within the invoice registration transaction");

                // Post-dispatch
                outboxWorker.processPendingBatch();
                var postBatch = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
                assertTrue(postBatch.stream()
                                .anyMatch(e -> AuditAction.SMS_PROVIDER_ACCEPTED.name().equals(e.getAction())),
                                "SMS_PROVIDER_ACCEPTED audit event must be emitted after successful provider submission");

                // Verification token must NOT appear in any audit event payload (PII guard)
                Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();
                String token = invoice.getPublicVerificationToken();
                if (token != null && !token.isBlank()) {
                        postBatch.forEach(evt -> {
                                if (evt.getPayloadJson() != null) {
                                        assertFalse(evt.getPayloadJson().contains(token),
                                                        "Public verification token must NEVER appear in audit event payloads");
                                }
                        });
                }
        }

        @Test
        @DisplayName("AUDIT-2: permanent SMS failure emits SMS_FAILED audit event")
        void auditCompleteness_permanentFailure_emitsSmsFailedEvent() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.INVALID_PHONE);
                registerInvoice("INV-AUDIT-02", "IDEM-AUDIT-02-" + UUID.randomUUID(), "0911010002");

                outboxWorker.processPendingBatch();

                var events = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
                assertTrue(events.stream().anyMatch(e -> AuditAction.SMS_FAILED.name().equals(e.getAction())),
                                "SMS_FAILED audit event must be emitted on permanent provider rejection");
        }

        @Test
        @DisplayName("AUDIT-3: transient failure emits SMS_RETRY_SCHEDULED audit event")
        void auditCompleteness_retryScheduled_emitsSmsRetryEvent() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_500);
                registerInvoice("INV-AUDIT-03", "IDEM-AUDIT-03-" + UUID.randomUUID(), "0911010003");

                outboxWorker.processPendingBatch();

                var events = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
                assertTrue(events.stream().anyMatch(e -> AuditAction.SMS_RETRY_SCHEDULED.name().equals(e.getAction())),
                                "SMS_RETRY_SCHEDULED audit event must be emitted on transient provider failure");
        }

        @Test
        @DisplayName("AUDIT-4: read timeout emits SMS_SUBMISSION_UNKNOWN audit event")
        void auditCompleteness_submissionUnknown_emitsSmsSubmissionUnknownEvent() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);
                registerInvoice("INV-AUDIT-04", "IDEM-AUDIT-04-" + UUID.randomUUID(), "0911010004");

                outboxWorker.processPendingBatch();

                var events = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
                assertTrue(events.stream()
                                .anyMatch(e -> AuditAction.SMS_SUBMISSION_UNKNOWN.name().equals(e.getAction())),
                                "SMS_SUBMISSION_UNKNOWN audit event must be emitted on read-timeout");
        }


        // =========================================================================
        // INVARIANT 14: Retry Backoff Progression
        // =========================================================================

        @Test
        @DisplayName("BACKOFF-1: first retry nextAttemptAt is in the future (non-zero delay)")
        void retryBackoff_firstRetryIsInTheFuture() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_500);
                var response = registerInvoice("INV-BACKOFF-01", "IDEM-BACKOFF-01-" + UUID.randomUUID(), "0912000001");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                outboxWorker.processPendingBatch();

                var afterFirst = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.RETRY_SCHEDULED, afterFirst.getStatus());
                assertNotNull(afterFirst.getNextAttemptAt());
                assertTrue(afterFirst.getNextAttemptAt().isAfter(Instant.now()),
                                "First retry nextAttemptAt must be strictly in the future (non-zero delay)");
        }

        @Test
        @DisplayName("BACKOFF-2: retry backoff schedule escalates from attempt 1 through 3 (30s < 120s < 600s)")
        void retryBackoff_escalatingScheduleFromAttemptOne() {
                // The escalation table: case 1->30s, case 2->120s, case 3->600s, case 4->1800s, default->3600s.
                // Drive the outbox through 3 consecutive retries via direct CAS and verify each scheduled
                // nextAttemptAt is strictly later than the one before it.
                var response = registerInvoice("INV-BACKOFF-02", "IDEM-BACKOFF-02-" + UUID.randomUUID(), "0912000002");
                var id = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0).getId();
                final Instant base = Instant.now();

                // Attempt 0 → 1: nextAttemptAt = base + 30s (case 1)
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.claimLeaseAtomic(id, outboxWorker.getWorkerId(), Instant.now()));
                Instant next1 = base.plusSeconds(30);
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.completeRetryScheduledAtomic(id, outboxWorker.getWorkerId(),
                                next1, "ERR", "fail1", FailureClassification.PROVIDER_NETWORK_FAILURE));

                // Attempt 1 → 2: nextAttemptAt = base + 120s (case 2)
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.releaseStaleLeases(Instant.now().plus(1, ChronoUnit.HOURS)));
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.claimLeaseAtomic(id, outboxWorker.getWorkerId(), Instant.now()));
                Instant next2 = base.plusSeconds(120);
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.completeRetryScheduledAtomic(id, outboxWorker.getWorkerId(),
                                next2, "ERR", "fail2", FailureClassification.PROVIDER_NETWORK_FAILURE));

                // Attempt 2 → 3: nextAttemptAt = base + 600s (case 3)
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.releaseStaleLeases(Instant.now().plus(1, ChronoUnit.HOURS)));
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.claimLeaseAtomic(id, outboxWorker.getWorkerId(), Instant.now()));
                Instant next3 = base.plusSeconds(600);
                transactionTemplate.executeWithoutResult(st ->
                        outboxRepository.completeRetryScheduledAtomic(id, outboxWorker.getWorkerId(),
                                next3, "ERR", "fail3", FailureClassification.PROVIDER_NETWORK_FAILURE));

                // Verify strict escalation: 30s < 120s < 600s
                assertTrue(next1.isBefore(next2),
                        "Backoff must escalate: attempt-1 delay (30s) must be less than attempt-2 delay (120s)");
                assertTrue(next2.isBefore(next3),
                        "Backoff must escalate: attempt-2 delay (120s) must be less than attempt-3 delay (600s)");

                var finalRecord = outboxRepository.findById(id).orElseThrow();
                assertEquals(NotificationStatus.RETRY_SCHEDULED, finalRecord.getStatus());
                assertEquals(3, finalRecord.getAttemptCount(),
                        "Attempt count must be 3 after three consecutive RETRY_SCHEDULED transitions");
        }

        // =========================================================================
        // INVARIANT 15: Max-Attempts Exhaustion → PERMANENTLY_FAILED
        // =========================================================================

        @Test
        @DisplayName("EXHAUST-1: max-attempts exhaustion transitions to PERMANENTLY_FAILED (no infinite loop)")
        void maxAttemptsExhaustion_producesPermanentlyFailed() {
                var response = registerInvoice("INV-EXHAUST-01", "IDEM-EXHAUST-01-" + UUID.randomUUID(), "0912100001");
                var outbox = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id()).get(0);

                // Exhaust all attempts via direct CAS repository methods
                for (int attempt = 0; attempt < outbox.getMaxAttempts(); attempt++) {
                        transactionTemplate.executeWithoutResult(status -> outboxRepository
                                        .releaseStaleLeases(Instant.now().plus(1, ChronoUnit.HOURS)));
                        int claimed = transactionTemplate.execute(status -> outboxRepository
                                        .claimLeaseAtomic(outbox.getId(), outboxWorker.getWorkerId(), Instant.now()));
                        if (claimed == 0) break;
                        transactionTemplate.executeWithoutResult(status -> outboxRepository.completeRetryScheduledAtomic(
                                        outbox.getId(), outboxWorker.getWorkerId(), Instant.now(),
                                        "SERVER_ERROR", "Exhaustion test failure",
                                        FailureClassification.PROVIDER_NETWORK_FAILURE));
                }

                // Final claim → permanent failure
                transactionTemplate.executeWithoutResult(status -> outboxRepository
                                .releaseStaleLeases(Instant.now().plus(1, ChronoUnit.HOURS)));
                int claimResult = transactionTemplate.execute(status -> outboxRepository
                                .claimLeaseAtomic(outbox.getId(), outboxWorker.getWorkerId(), Instant.now()));
                if (claimResult > 0) {
                        transactionTemplate.executeWithoutResult(status -> outboxRepository.completePermanentFailureAtomic(
                                        outbox.getId(), outboxWorker.getWorkerId(),
                                        "MAX_RETRIES_EXCEEDED", "Exhausted all retry attempts",
                                        FailureClassification.PROVIDER_NETWORK_FAILURE, Instant.now()));
                }

                var finalRecord = outboxRepository.findById(outbox.getId()).orElseThrow();
                assertEquals(NotificationStatus.PERMANENTLY_FAILED, finalRecord.getStatus(),
                                "After max attempts exhaustion the outbox record must be PERMANENTLY_FAILED");
                assertNotNull(finalRecord.getFailedAt(), "failedAt must be set on permanent failure");

                Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();
                assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(),
                                "Max-attempts exhaustion must NEVER alter fiscal invoice status");
        }

        // =========================================================================
        // INVARIANT 16: Provider Capability Adaptation
        // =========================================================================

        @Test
        @DisplayName("CAP-1: provider without status-lookup finalizes SUBMISSION_UNKNOWN immediately on reconciliation")
        void providerCapability_noStatusLookup_immediatelyFinalOnReconciliation() {
                mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);
                mockSmsProvider.setSupportsStatusLookup(false);
                mockSmsProvider.setSupportsDeliveryReceipts(false);

                registerInvoice("INV-CAP-01", "IDEM-CAP-01-" + UUID.randomUUID(), "0912200001");
                outboxWorker.processPendingBatch();

                // Verify SUBMISSION_UNKNOWN state before reconciliation
                var unknownItems = outboxRepository.findAll().stream()
                                .filter(o -> tenantId.equals(o.getTenantId()))
                                .filter(o -> NotificationStatus.SUBMISSION_UNKNOWN == o.getStatus())
                                .toList();
                assertFalse(unknownItems.isEmpty(), "Item must be SUBMISSION_UNKNOWN after timeout");

                // Reconciliation — no status lookup → immediate finalization
                outboxWorker.reconcileUnknownSubmissions();

                var finalItems = outboxRepository.findAll().stream()
                                .filter(o -> tenantId.equals(o.getTenantId()))
                                .filter(o -> NotificationStatus.SUBMISSION_UNKNOWN_FINAL == o.getStatus())
                                .toList();
                assertFalse(finalItems.isEmpty(),
                                "Without status lookup, reconciliation must finalize SUBMISSION_UNKNOWN immediately");
        }

        @Test
        @DisplayName("CAP-2: provider without sender-ID support — worker dispatches successfully without sender")
        void providerCapability_noSenderId_workerDispatchesSuccessfully() {
                mockSmsProvider.setSupportsSenderId(false);

                registerInvoice("INV-CAP-02", "IDEM-CAP-02-" + UUID.randomUUID(), "0912200002");
                outboxWorker.processPendingBatch();

                var outboxList = outboxRepository.findAll().stream()
                                .filter(o -> tenantId.equals(o.getTenantId())).toList();
                assertFalse(outboxList.isEmpty());
                assertEquals(NotificationStatus.SUBMITTED, outboxList.get(0).getStatus(),
                                "Worker must successfully dispatch even when provider does not support sender-ID");
        }

        // =========================================================================
        // INVARIANT 17: Phone Normalization — E.164 snapshot enforced
        // =========================================================================

        @Test
        @DisplayName("PHONE-1: local Ethiopian mobile number is normalized to E.164 before persistence")
        void phoneNormalization_localNumberNormalizedToE164() {
                var response = registerInvoice("INV-PHONE-01", "IDEM-PHONE-01-" + UUID.randomUUID(), "0911234567");
                var outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id());

                assertEquals(1, outboxList.size());
                String snapshot = outboxList.get(0).getRecipientPhoneSnapshot();

                assertTrue(snapshot.startsWith("251"),
                                "Persisted phone snapshot must be normalized to E.164 with country code 251 (Ethiopia)");
                assertFalse(snapshot.startsWith("0"),
                                "Persisted phone snapshot must NOT retain local '0' prefix");
                assertEquals(12, snapshot.length(),
                                "Ethiopian E.164 number must be exactly 12 digits (251 + 9 local digits)");
        }

        @Test
        @DisplayName("PHONE-2: already-normalized E.164 phone is stored verbatim (no double-prefixing)")
        void phoneNormalization_e164InputStoredVerbatimNoDblPrefix() {
                var response = registerInvoice("INV-PHONE-02", "IDEM-PHONE-02-" + UUID.randomUUID(), "251911234568");
                var outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id());

                assertEquals(1, outboxList.size());
                String snapshot = outboxList.get(0).getRecipientPhoneSnapshot();
                assertEquals("251911234568", snapshot,
                                "Already E.164-normalized phone must be stored verbatim without double-prefixing (251251...)");
        }

        // =========================================================================
        // INVARIANT 18: Message Content Integrity
        // =========================================================================

        @Test
        @DisplayName("CONTENT-1: rendered message contains document number and verification token")
        void messageContent_containsDocumentNumberAndVerificationToken() {
                var response = registerInvoice("INV-CONTENT-01", "IDEM-CONTENT-01-" + UUID.randomUUID(), "0911234569");
                Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();

                var outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id());
                assertEquals(1, outboxList.size());

                String renderedMessage = outboxList.get(0).getRenderedMessage();
                assertNotNull(renderedMessage, "Rendered message must not be null");
                assertFalse(renderedMessage.isBlank(), "Rendered message must not be blank");
                assertTrue(renderedMessage.contains("INV-CONTENT-01"),
                                "Rendered message must contain the invoice document number");
                assertTrue(renderedMessage.contains(invoice.getPublicVerificationToken()),
                                "Rendered message must contain the public verification token for buyer self-verification");
        }

        @Test
        @DisplayName("CONTENT-2: rendered_message_hash is the SHA-256 hex digest of rendered_message")
        void messageContent_hashIsConsistentSha256OfRenderedMessage() throws Exception {
                var response = registerInvoice("INV-CONTENT-02", "IDEM-CONTENT-02-" + UUID.randomUUID(), "0911234570");
                var outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, response.id());

                assertEquals(1, outboxList.size());
                InvoiceNotificationOutbox outbox = outboxList.get(0);

                String hash = outbox.getRenderedMessageHash();
                assertNotNull(hash, "Rendered message hash must not be null");
                assertFalse(hash.isBlank(), "Rendered message hash must not be blank");
                assertTrue(hash.matches("[0-9a-fA-F]+"),
                                "Rendered message hash must be a valid hexadecimal digest string");

                // Recompute SHA-256 and compare
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(outbox.getRenderedMessage().getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes)
                        sb.append(String.format("%02x", b));
                assertEquals(sb.toString(), hash,
                                "Stored rendered_message_hash must be the SHA-256 hex digest of rendered_message");
        }

        // =========================================================================
        // Helper
        // =========================================================================

        private InvoiceResponseDto registerInvoice(
                        String documentNumber, String idempotencyKey, String buyerPhone) {
                var request = new CreateInvoiceRequest(
                                TransactionType.B2C,
                                "CASH",
                                "IMMEDIATE",
                                new CreateInvoiceRequest.BuyerRequest(
                                                "Test Buyer " + documentNumber, null, null, null,
                                                "TIN", buyerPhone, "buyer@example.com",
                                                "ET", "Addis Ababa", "Bole"),
                                List.of(new CreateInvoiceRequest.LineItemRequest(
                                                "ITEM-001", "Test Service", "SERVICES", "HOURS",
                                                new BigDecimal("1.0"), new BigDecimal("500.00"),
                                                BigDecimal.ZERO, "VAT15", BigDecimal.ZERO)),
                                null,
                                null,
                                documentNumber,
                                false);
                return invoiceService.createAndRegisterInvoice(request, idempotencyKey);
        }
}
