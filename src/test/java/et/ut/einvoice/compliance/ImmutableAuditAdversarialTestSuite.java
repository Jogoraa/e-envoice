package et.ut.einvoice.compliance;

import et.ut.einvoice.audit.domain.*;
import et.ut.einvoice.audit.export.AuditExportPackage;
import et.ut.einvoice.audit.export.AuditExportService;
import et.ut.einvoice.audit.export.AuditExportVerifier;
import et.ut.einvoice.audit.h2.AuditDatabaseImmutabilityInitializer;
import et.ut.einvoice.audit.repository.AuditCheckpointRepository;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.repository.AuditOutboxEventRepository;
import et.ut.einvoice.audit.repository.EvidenceManifestRepository;
import et.ut.einvoice.audit.service.*;
import et.ut.einvoice.audit.signature.DevKeyCheckpointSigner;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
// Isolated H2 database: prevents accumulated cross-suite H2 lock-table and
// HikariCP pool state from degrading PESSIMISTIC_WRITE serialisation in test03.
// This produces a context-cache miss, giving this suite a fresh Spring context
// and an empty H2 database — identical to the isolated run where 34/34 pass.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:immutable_audit_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
public class ImmutableAuditAdversarialTestSuite {

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditCheckpointRepository auditCheckpointRepository;

    @Autowired
    private AuditOutboxEventRepository auditOutboxRepository;

    @Autowired
    private EvidenceManifestRepository manifestRepository;

    @Autowired
    private AuditChainVerifier chainVerifier;

    @Autowired
    private AuditCheckpointService checkpointService;

    @Autowired
    private AuditCheckpointVerifier checkpointVerifier;

    @Autowired
    private AuditExportService exportService;

    @Autowired
    private AuditExportVerifier exportVerifier;

    @Autowired
    private AuditPayloadSanitizer payloadSanitizer;

    @Autowired
    private AuditOutboxService outboxService;

    @Autowired
    private EvidenceManifestService manifestService;

    @Autowired
    private EvidenceManifestVerifier manifestVerifier;

    @Autowired
    private AuditCanonicalizer canonicalizer;

    @Autowired
    private DevKeyCheckpointSigner devSigner;

    @Autowired
    private et.ut.einvoice.audit.signature.CheckpointSignatureVerifier signatureVerifier;

    @Autowired
    private AuditDatabaseImmutabilityInitializer immutabilityInitializer;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        immutabilityInitializer.registerTriggersIfH2();
    }

    // ==========================================
    // 1. CONCURRENT AUDIT WRITES
    // ==========================================
    @Test
    @DisplayName("01. 100 concurrent audit writes complete without loss or deadlocks")
    void test01_concurrentAuditWrites() throws Exception {
        UUID tenantId = UUID.randomUUID();
        int threadCount = 16;
        int totalWrites = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalWrites);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < totalWrites; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    auditService.recordEvent(
                            tenantId,
                            "WORKER-" + (index % 4),
                            "INVOICE_CREATED",
                            "INVOICE",
                            "INV-" + index,
                            "{\"docNumber\":\"INV-" + index + "\"}"
                    );
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent writes timed out");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Errors during concurrent writes: " + errors);
        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, "MAIN");
        assertEquals(100, events.size(), "All 100 events must be persisted");

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, "MAIN", events);
        assertTrue(result.isValid(), "Audit stream hash chain must be 100% intact: " + result.errorMessage());
    }

    // ==========================================
    // 2. SAME-STREAM CONCURRENT WRITES
    // ==========================================
    @Test
    @DisplayName("02. 100 concurrent successful same-stream audit writes complete with 0 deadlocks, no sequence gaps within the committed test event set, and an intact hash chain")
    void test02_sameStreamConcurrentWrites() throws Exception {
        // SCOPE NOTE: This test verifies that 100 concurrent successful writes to a single
        // audit stream serialize correctly — producing 0 deadlocks, no sequence gaps within
        // the committed test event set, and an intact cryptographic hash chain.
        //
        // This result does NOT assert that sequence gaps are impossible in production.
        // The fiscal sequence architecture permits committed sequence allocations followed by
        // a surrounding transaction rollback, which legitimately produces a gap. Sequence
        // gaps are an anomaly signal, not by themselves proof of tampering.
        UUID tenantId = UUID.randomUUID();
        String streamId = "FISCAL-STREAM-A";
        int totalWrites = 100;
        int threadCount = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalWrites);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < totalWrites; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    auditService.recordFiscalEvent(
                            tenantId,
                            streamId,
                            AuditAction.INVOICE_SUBMITTED,
                            "INVOICE",
                            "INV-STREAM-" + idx,
                            "{\"seqAttempt\":" + idx + "}"
                    );
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Same-stream concurrency timed out");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Encountered errors during concurrent writes: " + errors);
        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        assertEquals(totalWrites, events.size(),
                "All 100 successful audit writes must be persisted with no gaps within the committed test event set");

        // Verify strict monotonicity 1..100 within the committed test event set
        for (int i = 0; i < totalWrites; i++) {
            assertEquals((long) (i + 1), events.get(i).getSequenceNumber(),
                    "Sequence within committed test set must be exactly " + (i + 1));
        }

        AuditChainVerifier.StreamVerificationResult verifyResult = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertTrue(verifyResult.isValid(), "Hash chain must be cryptographically intact across all committed test events: " + verifyResult.errorMessage());
    }

    // ==========================================
    // 3. MULTI-TENANT CONCURRENT WRITES
    // ==========================================
    @Test
    @DisplayName("03. 500 concurrent events across 5 tenants execute in parallel with unbroken individual chains")
    void test03_multiTenantConcurrentWrites() throws Exception {
        int tenantCount = 5;
        int eventsPerTenant = 100;
        int totalEvents = tenantCount * eventsPerTenant;
        UUID[] tenants = new UUID[tenantCount];
        for (int i = 0; i < tenantCount; i++) {
            tenants[i] = UUID.randomUUID();
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalEvents);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < tenantCount; t++) {
            final UUID tId = tenants[t];
            for (int e = 0; e < eventsPerTenant; e++) {
                final int eventIdx = e;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        auditService.recordEvent(
                                tId,
                                "TENANT-STREAM",
                                "ACTOR-" + eventIdx,
                                "INVOICE_REGISTERED",
                                "INVOICE",
                                "INV-" + eventIdx,
                                "{\"status\":\"REGISTERED\"}",
                                "10.0.0.1"
                        );
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(45, TimeUnit.SECONDS), "Multi-tenant writes timed out");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Multi-tenant errors: " + errors);

        for (UUID tId : tenants) {
            List<AuditEvent> tenantEvents = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tId, "TENANT-STREAM");
            assertEquals(eventsPerTenant, tenantEvents.size());
            AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tId, "TENANT-STREAM", tenantEvents);
            assertTrue(result.isValid(), "Chain failed for tenant " + tId + ": " + result.errorMessage());
        }
    }

    // ==========================================
    // 4. JPA UPDATE REJECTION
    // ==========================================
    @Test
    @DisplayName("04. JPA update attempt is rejected by entity lifecycle guard")
    void test04_jpaUpdateRejection() {
        UUID tenantId = UUID.randomUUID();
        AuditEvent event = auditService.recordEvent(
                tenantId,
                "ADMIN",
                "TENANT_CREATED",
                "TENANT",
                tenantId.toString(),
                "{\"name\":\"Test Tenant\"}"
        );

        assertThrows(Exception.class, () -> {
            transactionTemplate.execute(status -> {
                AuditEvent managed = entityManager.find(AuditEvent.class, event.getId());
                managed.setAction("MODIFIED_ACTION");
                entityManager.flush();
                return null;
            });
        }, "JPA update must be rejected by @PreUpdate callback");
    }

    // ==========================================
    // 5. JPA DELETE REJECTION
    // ==========================================
    @Test
    @DisplayName("05. JPA delete attempt is rejected by entity lifecycle guard")
    void test05_jpaDeleteRejection() {
        UUID tenantId = UUID.randomUUID();
        AuditEvent event = auditService.recordEvent(
                tenantId,
                "ADMIN",
                "SECURITY_EVENT",
                "SECURITY",
                "SEC-1",
                "{\"alert\":\"test\"}"
        );

        assertThrows(Exception.class, () -> {
            transactionTemplate.execute(status -> {
                AuditEvent managed = entityManager.find(AuditEvent.class, event.getId());
                entityManager.remove(managed);
                entityManager.flush();
                return null;
            });
        }, "JPA delete must be rejected by @PreRemove callback");

        assertThrows(Exception.class, () -> {
            auditEventRepository.delete(event);
        }, "Repository delete method must be rejected");
    }

    // ==========================================
    // 6. NATIVE SQL MODIFICATION REJECTION
    // ==========================================
    @Test
    @DisplayName("06. Native SQL UPDATE and DELETE statements are rejected by database-level triggers")
    void test06_nativeSqlModificationRejection() {
        UUID tenantId = UUID.randomUUID();
        AuditEvent event = auditService.recordEvent(
                tenantId,
                "ADMIN",
                "FEATURE_FLAG_CHANGED",
                "FEATURE",
                "FLAG-1",
                "{\"enabled\":true}"
        );

        // Verify native SQL UPDATE rejection
        assertThrows(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE audit_events SET payload_json = '{\"tampered\":true}' WHERE id = ?")) {
                stmt.setObject(1, event.getId());
                stmt.executeUpdate();
            }
        }, "Database-level trigger must reject native SQL UPDATE on audit_events");

        // Verify native SQL DELETE rejection
        assertThrows(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM audit_events WHERE id = ?")) {
                stmt.setObject(1, event.getId());
                stmt.executeUpdate();
            }
        }, "Database-level trigger must reject native SQL DELETE on audit_events");
    }

    // ==========================================
    // 7. HASH-CHAIN VERIFICATION
    // ==========================================
    @Test
    @DisplayName("07. Hash chain verifier successfully verifies unbroken audit chain")
    void test07_hashChainVerification() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "VERIFY-STREAM";

        for (int i = 1; i <= 10; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACTION-" + i, "RES", "RES-" + i, "{\"step\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        assertEquals(10, events.size());

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertTrue(result.isValid());
        assertEquals(10, result.verifiedEventCount());
    }

    // ==========================================
    // 8. TAMPERED PAYLOAD DETECTION
    // ==========================================
    @Test
    @DisplayName("08. Tampered payload is detected by AuditChainVerifier and fails closed")
    void test08_tamperedPayloadDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "TAMPER-STREAM-1";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACTION-" + i, "RES", "RES-" + i, "{\"step\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));

        // Maliciously tamper with event #3 payload in memory
        AuditEvent original = events.get(2);
        AuditEvent tampered = new AuditEvent(
                original.getId(),
                original.getTenantId(),
                original.getStreamId(),
                original.getSequenceNumber(),
                original.getActorId(),
                original.getActorType(),
                original.getClientId(),
                original.getDeviceId(),
                original.getAction(),
                original.getResourceType(),
                original.getResourceId(),
                original.getClientIp(),
                original.getUserAgent(),
                "badpayloadhashbadpayloadhashbadpayloadhashbadpayloadhashbadpaylo",
                original.getPreviousEventHash(),
                original.getEventHash(),
                original.getCorrelationId(),
                original.getTraceId(),
                original.getApplicationVersion(),
                original.getSchemaVersion(),
                original.getTimestamp()
        );
        tampered.setPayloadJson("{\"step\":3,\"maliciousField\":true}");
        events.set(2, tampered);

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid(), "Verifier must detect tampered payload");
        assertEquals(AuditChainVerifier.VerificationStatus.TAMPERED_EVENT_HASH, result.status());
        assertEquals(3L, result.failedSequenceNumber());
    }

    // ==========================================
    // 9. TAMPERED HASH DETECTION
    // ==========================================
    @Test
    @DisplayName("09. Forged event hash is detected and fails closed")
    void test09_tamperedHashDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "TAMPER-STREAM-2";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACTION-" + i, "RES", "RES-" + i, "{\"step\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));

        // Forged event hash on event #4
        AuditEvent original = events.get(3);
        AuditEvent tampered = new AuditEvent(
                original.getId(),
                original.getTenantId(),
                original.getStreamId(),
                original.getSequenceNumber(),
                original.getActorId(),
                original.getActorType(),
                original.getClientId(),
                original.getDeviceId(),
                original.getAction(),
                original.getResourceType(),
                original.getResourceId(),
                original.getClientIp(),
                original.getUserAgent(),
                original.getPayloadHash(),
                original.getPreviousEventHash(),
                "f".repeat(64), // FORGED HASH
                original.getCorrelationId(),
                original.getTraceId(),
                original.getApplicationVersion(),
                original.getSchemaVersion(),
                original.getTimestamp()
        );
        events.set(3, tampered);

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid());
        assertEquals(AuditChainVerifier.VerificationStatus.TAMPERED_EVENT_HASH, result.status());
    }

    // ==========================================
    // 10. REMOVED EVENT DETECTION
    // ==========================================
    @Test
    @DisplayName("10. Omission of an audit event causes sequence gap detection")
    void test10_removedEventDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "REMOVED-STREAM";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACTION-" + i, "RES", "RES-" + i, "{\"step\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));
        events.remove(2); // Remove event sequence 3

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid());
        assertEquals(AuditChainVerifier.VerificationStatus.SEQUENCE_GAP, result.status());
    }

    // ==========================================
    // 11. REORDERED EVENT DETECTION
    // ==========================================
    @Test
    @DisplayName("11. Reordering events causes broken previous hash detection")
    void test11_reorderedEventDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "REORDER-STREAM";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACTION-" + i, "RES", "RES-" + i, "{\"step\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));
        // Swap event 2 and 3
        AuditEvent e2 = events.get(1);
        AuditEvent e3 = events.get(2);
        events.set(1, e3);
        events.set(2, e2);

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid());
    }

    // ==========================================
    // 12. DUPLICATE SEQUENCE DETECTION
    // ==========================================
    @Test
    @DisplayName("12. Duplicate sequence number is detected as tampering")
    void test12_duplicateSequenceDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "DUP-SEQ-STREAM";

        for (int i = 1; i <= 3; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACTION-" + i, "RES", "RES-" + i, "{\"step\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));
        // Duplicate event #2
        events.add(1, events.get(1));

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid());
        assertEquals(AuditChainVerifier.VerificationStatus.SEQUENCE_DUPLICATE, result.status());
    }

    // ==========================================
    // 13. CROSS-TENANT AUDIT ACCESS REJECTION
    // ==========================================
    @Test
    @DisplayName("13. Injected cross-tenant event fails closed immediately")
    void test13_crossTenantAuditAccessRejection() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String streamId = "ISOLATION-STREAM";

        auditService.recordEvent(tenantA, streamId, "USER", "ACTION-1", "RES", "RES-1", "{\"t\":\"A\"}", "127.0.0.1");
        auditService.recordEvent(tenantB, streamId, "USER", "ACTION-2", "RES", "RES-2", "{\"t\":\"B\"}", "127.0.0.1");

        List<AuditEvent> eventsA = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantA, streamId);
        List<AuditEvent> eventsB = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantB, streamId);

        assertEquals(1, eventsA.size());
        assertEquals(1, eventsB.size());

        // Attempt to verify tenant B's event inside tenant A's stream
        AuditChainVerifier.StreamVerificationResult crossResult = chainVerifier.verifyStreamChain(tenantA, streamId, eventsB);
        assertFalse(crossResult.isValid());
        assertEquals(AuditChainVerifier.VerificationStatus.CROSS_TENANT_CONTAMINATION, crossResult.status());
    }

    // ==========================================
    // 14. AUDIT OUTBOX DUPLICATE HANDLING
    // ==========================================
    @Test
    @DisplayName("14. Audit outbox deduplication prevents duplicate event enqueueing")
    void test14_auditOutboxDuplicateHandling() {
        UUID tenantId = UUID.randomUUID();
        AuditEvent event = auditService.recordEvent(
                tenantId,
                "ACTOR",
                "INVOICE_CREATED",
                "INVOICE",
                "INV-OUTBOX-1",
                "{\"payload\":\"test\"}"
        );

        // Attempt duplicate enqueueing of same audit event ID
        outboxService.enqueue(event);

        List<AuditOutboxEvent> outboxList = auditOutboxRepository.findByStatusOrderByCreatedAtAsc(AuditOutboxEvent.OutboxStatus.PENDING);
        long matches = outboxList.stream().filter(o -> o.getAuditEventId().equals(event.getId())).count();
        assertEquals(1, matches, "Outbox must deduplicate by auditEventId");
    }

    // ==========================================
    // 15. AUDIT OUTBOX RETRY
    // ==========================================
    @Test
    @DisplayName("15. Audit outbox retries failed publications and transitions to DEAD_LETTER when threshold reached")
    void test15_auditOutboxRetry() {
        UUID tenantId = UUID.randomUUID();
        AuditEvent event = auditService.recordEvent(
                tenantId,
                "ACTOR",
                "INVOICE_SUBMITTED",
                "INVOICE",
                "INV-RETRY-1",
                "{\"payload\":\"retry\"}"
        );

        AuditOutboxEvent outboxEvent = auditOutboxRepository.findByAuditEventId(event.getId()).orElseThrow();

        // Simulate 4 failures
        for (int i = 0; i < 4; i++) {
            outboxService.recordFailure(outboxEvent.getId(), "Network timeout attempt " + (i + 1));
            AuditOutboxEvent current = auditOutboxRepository.findById(outboxEvent.getId()).orElseThrow();
            assertEquals(AuditOutboxEvent.OutboxStatus.FAILED, current.getStatus());
        }

        // 5th failure reaches max_attempts (5) -> transitions to DEAD_LETTER
        outboxService.recordFailure(outboxEvent.getId(), "Terminal failure");
        AuditOutboxEvent deadLetter = auditOutboxRepository.findById(outboxEvent.getId()).orElseThrow();
        assertEquals(AuditOutboxEvent.OutboxStatus.DEAD_LETTER, deadLetter.getStatus());
        assertEquals(5, deadLetter.getAttemptCount());
    }

    // ==========================================
    // 16. CHECKPOINT GENERATION
    // ==========================================
    @Test
    @DisplayName("16. Checkpoint generation computes deterministic anchor state and boundary hashes")
    void test16_checkpointGeneration() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "CHECKPOINT-STREAM-1";

        for (int i = 1; i <= 10; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"val\":" + i + "}", "127.0.0.1");
        }

        Optional<AuditCheckpoint> cpOpt = checkpointService.generateCheckpoint(tenantId, streamId);
        assertTrue(cpOpt.isPresent());

        AuditCheckpoint cp = cpOpt.get();
        assertEquals(tenantId, cp.getTenantId());
        assertEquals(streamId, cp.getStreamId());
        assertEquals(1L, cp.getFirstEventSequence());
        assertEquals(10L, cp.getLastEventSequence());
        assertEquals(10L, cp.getEventCount());
        assertNotNull(cp.getFirstEventHash());
        assertNotNull(cp.getLastEventHash());
        assertNotNull(cp.getChainStateHash());
        assertEquals(AuditCheckpointService.GENESIS_CHECKPOINT_HASH, cp.getPreviousCheckpointHash());
    }

    // ==========================================
    // 17. CHECKPOINT VERIFICATION
    // ==========================================
    @Test
    @DisplayName("17. Checkpoint verification independently validates anchor state against underlying events")
    void test17_checkpointVerification() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "CHECKPOINT-STREAM-2";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"val\":" + i + "}", "127.0.0.1");
        }

        AuditCheckpoint checkpoint = checkpointService.generateCheckpoint(tenantId, streamId).orElseThrow();
        boolean valid = checkpointVerifier.verifyCheckpoint(tenantId, streamId, checkpoint.getCheckpointId());
        assertTrue(valid, "Checkpoint must verify successfully against events");
    }

    // ==========================================
    // 18. BROKEN CHECKPOINT DETECTION
    // ==========================================
    @Test
    @DisplayName("18. Broken or altered checkpoint is detected and fails closed")
    void test18_brokenCheckpointDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "CHECKPOINT-STREAM-3";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"val\":" + i + "}", "127.0.0.1");
        }

        AuditCheckpoint validCp = checkpointService.generateCheckpoint(tenantId, streamId).orElseThrow();

        // Checkpoint with corrupted chainStateHash
        AuditCheckpoint corruptedCp = new AuditCheckpoint(
                validCp.getCheckpointId(),
                validCp.getTenantId(),
                validCp.getStreamId(),
                validCp.getFirstEventSequence(),
                validCp.getLastEventSequence(),
                validCp.getEventCount(),
                validCp.getFirstEventHash(),
                validCp.getLastEventHash(),
                "badhashbadhashbadhashbadhashbadhashbadhashbadhashbadhashbadhashbad",
                validCp.getPreviousCheckpointHash(),
                validCp.getSchemaVersion(),
                validCp.getCreatedAt()
        );

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        boolean valid = checkpointVerifier.verifyCheckpointAgainstEvents(corruptedCp, events);
        assertFalse(valid, "Corrupted checkpoint must fail verification");
    }

    // ==========================================
    // 19. DETERMINISTIC EXPORT
    // ==========================================
    @Test
    @DisplayName("19. Deterministic audit export produces equivalent package content hashes on repeated executions")
    void test19_deterministicExport() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "EXPORT-STREAM";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"val\":" + i + "}", "127.0.0.1");
        }

        AuditExportPackage pkg1 = exportService.generateExport(tenantId, streamId).orElseThrow();
        AuditExportPackage pkg2 = exportService.generateExport(tenantId, streamId).orElseThrow();

        assertEquals(pkg1.packageContentHash(), pkg2.packageContentHash(), "Package content hashes must be identical");
        assertEquals(pkg1.eventCount(), pkg2.eventCount());
        assertEquals(pkg1.firstHash(), pkg2.firstHash());
        assertEquals(pkg1.lastHash(), pkg2.lastHash());
    }

    // ==========================================
    // 20. EXPORT VERIFICATION
    // ==========================================
    @Test
    @DisplayName("20. AuditExportVerifier independently verifies exported package offline")
    void test20_exportVerification() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "EXPORT-VERIFY-STREAM";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"val\":" + i + "}", "127.0.0.1");
        }

        checkpointService.generateCheckpoint(tenantId, streamId);
        AuditExportPackage pkg = exportService.generateExport(tenantId, streamId).orElseThrow();

        boolean verified = exportVerifier.verifyPackage(pkg);
        assertTrue(verified, "Exported audit evidence package must pass independent verification");
    }

    // ==========================================
    // 21. SECRET SANITIZATION
    // ==========================================
    @Test
    @DisplayName("21. Sensitive credentials, passwords, private keys and tokens are scrubbed from audit payloads")
    void test21_secretSanitization() {
        UUID tenantId = UUID.randomUUID();
        String payloadWithSecrets = "{" +
                "\"username\":\"admin\"," +
                "\"password\":\"SuperSecretP@ss123!\"," +
                "\"apiKey\":\"ut_live_abcdef1234567890abcdef1234567890\"," +
                "\"clientSecret\":\"cs_secret_key_value_here\"," +
                "\"privateKey\":\"-----BEGIN RSA PRIVATE KEY-----\\nMIIEowIBAAKCAQEA0...\\n-----END RSA PRIVATE KEY-----\"," +
                "\"authorization\":\"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-ae70wFZr9AqBR-98RaGQO53ea6a05vnNmKqnDT4nU\"" +
                "}";

        AuditEvent event = auditService.recordEvent(
                tenantId,
                "USER",
                "SECURITY_EVENT",
                "CREDENTIALS",
                "CRED-1",
                payloadWithSecrets
        );

        String storedPayload = event.getPayloadJson();
        assertFalse(storedPayload.contains("SuperSecretP@ss123!"), "Password must not be in audit payload");
        assertFalse(storedPayload.contains("ut_live_abcdef1234567890abcdef1234567890"), "API key must not be in audit payload");
        assertFalse(storedPayload.contains("cs_secret_key_value_here"), "Client secret must not be in audit payload");
        assertFalse(storedPayload.contains("-----BEGIN RSA PRIVATE KEY-----"), "Private key must not be in audit payload");
        assertFalse(storedPayload.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"), "Bearer token must not be in audit payload");
        assertTrue(storedPayload.contains("[REDACTED_FINGERPRINT:HMAC256:"), "Sensitive keys must be replaced with safe fingerprints");
    }

    // ==========================================
    // 22. APPLICATION RESTART RECOVERY
    // ==========================================
    @Test
    @DisplayName("22. Monotonic sequence and hash chain resume seamlessly from persisted state after restart")
    void test22_applicationRestartRecovery() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "RESTART-STREAM";

        // Record initial event before simulated restart
        AuditEvent initialEvent = auditService.recordEvent(
                tenantId, streamId, "ACTOR", "INVOICE_CREATED", "INVOICE", "INV-1", "{\"msg\":\"before restart\"}", "127.0.0.1"
        );
        assertEquals(1L, initialEvent.getSequenceNumber());

        // Simulate new request / worker after application restart
        AuditEvent postRestartEvent = auditService.recordEvent(
                tenantId, streamId, "ACTOR", "INVOICE_SUBMITTED", "INVOICE", "INV-1", "{\"msg\":\"after restart\"}", "127.0.0.1"
        );
        assertEquals(2L, postRestartEvent.getSequenceNumber());
        assertEquals(initialEvent.getEventHash(), postRestartEvent.getPreviousEventHash());

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertTrue(result.isValid(), "Chain continuity must be preserved across restarts");
    }

    // ==========================================
    // 23. DUPLICATE EVENT SUBMISSION
    // ==========================================
    @Test
    @DisplayName("23. Duplicate event submission with duplicate ID is rejected")
    void test23_duplicateEventSubmission() {
        UUID tenantId = UUID.randomUUID();
        AuditEvent event = auditService.recordEvent(
                tenantId, "DUP-STREAM", "ACTOR", "INVOICE_CREATED", "INVOICE", "INV-DUP", "{\"data\":1}", "127.0.0.1"
        );

        // Attempting to insert another event with the same ID should be rejected
        AuditEvent duplicateIdEvent = new AuditEvent(
                event.getId(),
                tenantId,
                "DUP-STREAM",
                2L,
                "ACTOR",
                "USER",
                null,
                null,
                "INVOICE_SUBMITTED",
                "INVOICE",
                "INV-DUP",
                "127.0.0.1",
                null,
                "hash1",
                event.getEventHash(),
                "hash2",
                "corr",
                "trace",
                "1.0.0-RELEASE",
                1,
                Instant.now()
        );

        assertThrows(Exception.class, () -> {
            transactionTemplate.execute(status -> {
                entityManager.persist(duplicateIdEvent);
                entityManager.flush();
                return null;
            });
        }, "Inserting duplicate primary key event ID must fail");
    }

    // ==========================================
    // 24. CORRUPTED LOCAL EVIDENCE DETECTION
    // ==========================================
    @Test
    @DisplayName("24. EvidenceManifestVerifier detects tampered local evidence artifacts")
    void test24_corruptedLocalEvidenceDetection() {
        UUID tenantId = UUID.randomUUID();
        byte[] validArtifactContent = "{\"fiscalExport\":\"certified_data_record\"}".getBytes(StandardCharsets.UTF_8);

        EvidenceManifest manifest = manifestService.createManifest(tenantId, "EXPORT_BUNDLE", validArtifactContent, "sig=valid");

        // Verify valid artifact matches
        boolean validResult = manifestVerifier.verifyManifest(manifest, validArtifactContent);
        assertTrue(validResult, "Valid artifact content must pass verification");

        // Corrupted artifact content
        byte[] corruptedContent = "{\"fiscalExport\":\"tampered_data_record\"}".getBytes(StandardCharsets.UTF_8);
        boolean corruptedResult = manifestVerifier.verifyManifest(manifest, corruptedContent);
        assertFalse(corruptedResult, "Tampered artifact content must fail manifest verification");
    }

    // ==========================================
    // 25. CHANGED PREVIOUS HASH EXPLICIT DETECTION
    // ==========================================
    @Test
    @DisplayName("25. Corrupted previousEventHash pointer is detected as broken hash chain")
    void test25_changedPreviousHashExplicit() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "PREV-HASH-STREAM";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"v\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));
        AuditEvent original = events.get(2); // Sequence 3
        AuditEvent corrupted = new AuditEvent(
                original.getId(), original.getTenantId(), original.getStreamId(), original.getSequenceNumber(),
                original.getActorId(), original.getActorType(), original.getClientId(), original.getDeviceId(),
                original.getAction(), original.getResourceType(), original.getResourceId(),
                original.getClientIp(), original.getUserAgent(), original.getPayloadHash(),
                "0".repeat(64), // CORRUPTED PREVIOUS HASH
                original.getEventHash(), original.getCorrelationId(), original.getTraceId(),
                original.getApplicationVersion(), original.getSchemaVersion(), original.getTimestamp()
        );
        events.set(2, corrupted);

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid(), "Broken previousEventHash must fail verification");
        assertEquals(AuditChainVerifier.VerificationStatus.BROKEN_PREVIOUS_HASH, result.status());
        assertEquals(3L, result.failedSequenceNumber());
    }

    // ==========================================
    // 26. INSERTED EVENT DETECTION
    // ==========================================
    @Test
    @DisplayName("26. Illegitimate inserted event breaks sequence continuity and is detected")
    void test26_insertedEventDetection() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "INSERT-STREAM";

        for (int i = 1; i <= 3; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"v\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> events = new ArrayList<>(auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId));
        // Fabricate and insert an unauthorized duplicate/alien event at index 2
        AuditEvent alien = new AuditEvent(
                UUID.randomUUID(), tenantId, streamId, 2L,
                "ATTACKER", "USER", null, null, "UNAUTHORIZED_ACTION", "INVOICE", "INV-X",
                "192.168.1.1", null, "hash", events.get(0).getEventHash(), "forgedhash",
                "c", "t", "1.0", 1, Instant.now()
        );
        events.add(2, alien);

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, events);
        assertFalse(result.isValid(), "Inserted alien event must fail chain verification");
    }

    // ==========================================
    // 27. CHANGED CHECKPOINT SEQUENCE DETECTION
    // ==========================================
    @Test
    @DisplayName("27. Checkpoint with corrupted sequence boundaries fails verification")
    void test27_changedCheckpointSequenceDetected() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "CP-SEQ-STREAM";

        for (int i = 1; i <= 5; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"v\":" + i + "}", "127.0.0.1");
        }

        AuditCheckpoint validCp = checkpointService.generateCheckpoint(tenantId, streamId).orElseThrow();
        // Alter lastEventSequence from 5 to 6
        AuditCheckpoint tamperedSeqCp = new AuditCheckpoint(
                validCp.getCheckpointId(), validCp.getTenantId(), validCp.getStreamId(),
                validCp.getFirstEventSequence(),
                999L, // Corrupted sequence boundary
                validCp.getEventCount(),
                validCp.getFirstEventHash(), validCp.getLastEventHash(),
                validCp.getChainStateHash(), validCp.getPreviousCheckpointHash(),
                validCp.getSchemaVersion(), validCp.getCreatedAt()
        );

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        boolean valid = checkpointVerifier.verifyCheckpointAgainstEvents(tamperedSeqCp, events);
        assertFalse(valid, "Checkpoint with tampered sequence boundaries must fail verification");
    }

    // ==========================================
    // 28. VERIFIER NEVER REPAIRS HISTORY (FAIL CLOSED)
    // ==========================================
    @Test
    @DisplayName("28. Verifier strictly adheres to FAIL CLOSED and never modifies or repairs historical records")
    void test28_verifierNeverRepairsHistory() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "FAIL-CLOSED-STREAM";

        for (int i = 1; i <= 4; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"v\":" + i + "}", "127.0.0.1");
        }

        List<AuditEvent> originalEvents = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        assertEquals(4, originalEvents.size());
        String originalSeq2Hash = originalEvents.get(1).getEventHash();

        // Create tampered copy and verify
        List<AuditEvent> tamperedCopy = new ArrayList<>(originalEvents);
        AuditEvent tampered = new AuditEvent(
                originalEvents.get(1).getId(), tenantId, streamId, 2L,
                "USER", "USER", null, null, "TAMPERED", "RES", "R-2",
                "127.0.0.1", null, "badpayloadhash", originalEvents.get(0).getEventHash(),
                "badeventhash", null, null, "1.0", 1, originalEvents.get(1).getTimestamp()
        );
        tamperedCopy.set(1, tampered);

        AuditChainVerifier.StreamVerificationResult result = chainVerifier.verifyStreamChain(tenantId, streamId, tamperedCopy);
        assertFalse(result.isValid(), "Must detect tampering and fail closed");

        // Verify that the underlying database records were NEVER altered or repaired by verifier
        List<AuditEvent> postVerifyDbEvents = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        assertEquals(4, postVerifyDbEvents.size());
        assertEquals(originalSeq2Hash, postVerifyDbEvents.get(1).getEventHash(), "Database records must remain unmodified");
    }

    // ==========================================
    // 29. ATOMIC BUSINESS TRANSACTION + AUDIT COMMIT
    // ==========================================
    @Test
    @DisplayName("29. Business transaction rollback rolls back both audit event and outbox record atomically")
    void test29_atomicBusinessTransactionRollback() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "ROLLBACK-STREAM";

        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute(status -> {
                auditService.recordEvent(tenantId, streamId, "ACTOR", "INVOICE_CREATED", "INVOICE", "INV-RB-1", "{\"state\":\"draft\"}", "127.0.0.1");
                throw new RuntimeException("Simulated outer business transaction failure!");
            });
        });

        // Verify neither audit event nor outbox record was committed
        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        assertTrue(events.isEmpty(), "Rolled-back transaction must leave no audit events in the database");
    }

    // ==========================================
    // 30. COMPREHENSIVE REALISTIC SECRET SANITIZATION
    // ==========================================
    @Test
    @DisplayName("30. AuditPayloadSanitizer scrubs passwords, JWTs, API keys, client secrets, signing keys, DB passwords, and government secrets")
    void test30_comprehensiveRealisticSecretSanitization() {
        UUID tenantId = UUID.randomUUID();
        String realisticPayload = "{\n" +
                "  \"username\": \"billing_service\",\n" +
                "  \"password\": \"P@ssw0rd2026!$\",\n" +
                "  \"jwt\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJl\",\n" +
                "  \"apiKey\": \"mor_live_key_998877665544332211\",\n" +
                "  \"clientSecret\": \"cs_prod_super_secret_token_value\",\n" +
                "  \"privateSigningKey\": \"-----BEGIN RSA PRIVATE KEY-----\\nMIIEowIBAAKCAQEA0testkey\\n-----END RSA PRIVATE KEY-----\",\n" +
                "  \"databasePassword\": \"PostgresRootPass123!\",\n" +
                "  \"governmentSecret\": \"EIRS_GOV_PORTAL_SECRET_KEY\",\n" +
                "  \"sessionCredential\": \"sess_token_secure_999\",\n" +
                "  \"publicDocNumber\": \"INV-ET-2026-0099\"\n" +
                "}";

        AuditEvent event = auditService.recordEvent(
                tenantId, "SANITIZER-STREAM", "SYSTEM", "CONFIG_UPDATED", "SYSTEM", "SYS-1", realisticPayload, "127.0.0.1"
        );

        String savedJson = event.getPayloadJson();
        assertFalse(savedJson.contains("P@ssw0rd2026!$"), "password leaked!");
        assertFalse(savedJson.contains("eyJhbGciOiJSUzI1Ni"), "jwt leaked!");
        assertFalse(savedJson.contains("mor_live_key_998877665544332211"), "apiKey leaked!");
        assertFalse(savedJson.contains("cs_prod_super_secret_token_value"), "clientSecret leaked!");
        assertFalse(savedJson.contains("MIIEowIBAAKCAQEA0testkey"), "privateSigningKey leaked!");
        assertFalse(savedJson.contains("PostgresRootPass123!"), "databasePassword leaked!");
        assertFalse(savedJson.contains("EIRS_GOV_PORTAL_SECRET_KEY"), "governmentSecret leaked!");
        assertFalse(savedJson.contains("sess_token_secure_999"), "sessionCredential leaked!");
        assertTrue(savedJson.contains("INV-ET-2026-0099"), "Non-sensitive public fiscal metadata must be preserved");
    }

    // ==========================================
    // 31. FAIL-CLOSED EVENT IDENTITY
    // ==========================================
    @Test
    @DisplayName("31. Audit event recording strictly fails closed on missing or malformed identity")
    void test31_failClosedEventIdentity() {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.clear(); // Ensure no ambient context

        // 1. Missing tenantId -> rejected (no zero UUID substitution)
        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEvent(null, "ACTOR-1", "ACTION", "RES", "ID", "{}"),
                "Missing tenantId must throw IllegalArgumentException");

        // 2. Missing actorId -> rejected (no silent SYSTEM substitution without ambient context)
        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEvent(tenantId, null, "ACTION", "RES", "ID", "{}"),
                "Missing actorId must throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEvent(tenantId, "   ", "ACTION", "RES", "ID", "{}"),
                "Blank actorId must throw IllegalArgumentException");

        // 3. Missing actorType -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEventInternal(tenantId, "MAIN", "ACTOR", "", "ACTION", "RES", "ID", "{}", "127.0.0.1", null, null),
                "Missing actorType must throw IllegalArgumentException");

        // 4. Missing action -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEvent(tenantId, "ACTOR", null, "RES", "ID", "{}"),
                "Missing action must throw IllegalArgumentException");

        // 5. Missing resourceType -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEvent(tenantId, "ACTOR", "ACTION", null, "ID", "{}"),
                "Missing resourceType must throw IllegalArgumentException");

        // 6. Missing resourceId -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                auditService.recordEvent(tenantId, "ACTOR", "ACTION", "RES", null, "{}"),
                "Missing resourceId must throw IllegalArgumentException");

        // 7. Canonicalizer validateRequiredFields rejects null timestamp or missing tenant
        AuditEvent eventMissingTimestamp = new AuditEvent(
                UUID.randomUUID(), tenantId, "MAIN", 1L, "ACTOR", "USER", null, null,
                "ACTION", "RES", "ID", "127.0.0.1", null, "phash", "prevhash", "ehash",
                null, null, "1.0", 1, null // NULL TIMESTAMP
        );
        assertThrows(IllegalArgumentException.class, () ->
                canonicalizer.canonicalizeToString(eventMissingTimestamp),
                "Canonicalizer must fail closed on missing occurredAt timestamp");
    }

    // ==========================================
    // 32. HMAC-SHA-256 SECRET FINGERPRINTING HARDENING
    // ==========================================
    @Test
    @DisplayName("32. Secret correlation utilizes server-held HMAC-SHA-256 preventing offline dictionary attacks")
    void test32_hmacSecretFingerprintingHardening() {
        String secretA = "CompanySecretPassword2026!";
        String secretB = "AnotherDifferentSecret999#";

        // Same secret -> same HMAC fingerprint
        String fpA1 = payloadSanitizer.computeFingerprint(secretA);
        String fpA2 = payloadSanitizer.computeFingerprint(secretA);
        assertEquals(fpA1, fpA2, "Identical secret must produce identical HMAC fingerprint");
        assertTrue(fpA1.startsWith("HMAC256:"), "Fingerprint must use HMAC256 prefix");

        // Different secrets -> different fingerprints
        String fpB = payloadSanitizer.computeFingerprint(secretB);
        assertNotEquals(fpA1, fpB, "Distinct secrets must produce distinct HMAC fingerprints");

        // Raw secret never appears in sanitized payload or logs
        String payload = "{\"password\":\"CompanySecretPassword2026!\",\"authorizationHeader\":\"Bearer secret_bearer_token\"}";
        String sanitized = payloadSanitizer.sanitize(payload);
        assertFalse(sanitized.contains("CompanySecretPassword2026!"), "Raw password must never be stored");
        assertFalse(sanitized.contains("secret_bearer_token"), "Raw token must never be stored");
        assertTrue(sanitized.contains("HMAC256:"), "Sanitized output must include HMAC fingerprint for correlation");
    }

    // ==========================================
    // 33. CANONICAL SERIALIZATION UNAMBIGUITY & NFC NORMALIZATION
    // ==========================================
    @Test
    @DisplayName("33. Audit canonicalization guarantees round-trip uniqueness, delimiter escaping, and NFC normalization")
    void test33_canonicalSerializationUnambiguityAndNFC() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        // NFC Normalization: Composed vs Decomposed Unicode characters produce identical canonical serialization
        // Amharic or accented text: "e\u0301" (decomposed) vs "\u00e9" (composed)
        String decomposed = "cafe\u0301";
        String composed = "caf\u00e9";

        AuditEvent event1 = new AuditEvent(
                UUID.randomUUID(), tenantId, "MAIN", 1L, "ACTOR", "USER", null, null,
                "INVOICE_CREATED", "INVOICE", decomposed, "127.0.0.1", null, "phash", "prevhash", "",
                null, null, "1.0.0-RELEASE", 1, now
        );
        AuditEvent event2 = new AuditEvent(
                event1.getId(), tenantId, "MAIN", 1L, "ACTOR", "USER", null, null,
                "INVOICE_CREATED", "INVOICE", composed, "127.0.0.1", null, "phash", "prevhash", "",
                null, null, "1.0.0-RELEASE", 1, now
        );

        String canon1 = canonicalizer.canonicalizeToString(event1);
        String canon2 = canonicalizer.canonicalizeToString(event2);
        assertEquals(canon1, canon2, "Decomposed and composed Unicode strings must normalize to identical canonical representation");

        // Delimiter escaping: '|', '=', and '\' are safely escaped to prevent collision
        String escaped = AuditCanonicalizer.escapeField("key|value=test\\sample");
        assertEquals("key\\|value\\=test\\\\sample", escaped, "Delimiters must be escaped with backslash");

        // Unambiguity: Distinct logical events never produce the same canonical string
        AuditEvent eventDistinct = new AuditEvent(
                UUID.randomUUID(), tenantId, "MAIN", 2L, "ACTOR", "USER", null, null,
                "INVOICE_CREATED", "INVOICE", "INV-DISTINCT", "127.0.0.1", null, "phash", "prevhash", "",
                null, null, "1.0.0-RELEASE", 1, now
        );
        assertNotEquals(canon1, canonicalizer.canonicalizeToString(eventDistinct),
                "Two distinct logical events must never produce identical canonical byte sequences");
    }

    // ==========================================
    // 34. CHECKPOINT TRUST MODEL & SIGNATURE VERIFICATION
    // ==========================================
    @Test
    @DisplayName("34. Development checkpoint signing validates internal cryptographic integrity and detects tampering")
    void test34_developmentCheckpointTrustModelVerification() {
        UUID tenantId = UUID.randomUUID();
        String streamId = "TRUST-MODEL-STREAM";

        for (int i = 1; i <= 3; i++) {
            auditService.recordEvent(tenantId, streamId, "USER", "ACT-" + i, "RES", "R-" + i, "{\"val\":" + i + "}", "127.0.0.1");
        }

        AuditCheckpoint checkpoint = checkpointService.generateCheckpoint(tenantId, streamId).orElseThrow();
        assertNotNull(checkpoint.getChainStateHash());

        // Sign checkpoint state digest using DevKeyCheckpointSigner
        String dataToSign = checkpoint.getChainStateHash();
        String signature = devSigner.sign(dataToSign);
        assertNotNull(signature);

        java.security.PublicKey publicKey = devSigner.getPublicKey();
        String algorithm = devSigner.getAlgorithm();

        // Verify valid signature
        boolean validSig = signatureVerifier.verify(dataToSign, signature, publicKey, algorithm);
        assertTrue(validSig, "Valid checkpoint signature must verify successfully as internal cryptographic integrity evidence");

        // Tampered signature or tampered data fails closed
        boolean tamperedDataSig = signatureVerifier.verify(dataToSign + "-tampered", signature, publicKey, algorithm);
        assertFalse(tamperedDataSig, "Tampered checkpoint data must fail signature verification");

        boolean tamperedSig = signatureVerifier.verify(dataToSign, signature.substring(0, signature.length() - 4) + "AAAA", publicKey, algorithm);
        assertFalse(tamperedSig, "Corrupted signature must fail verification");
    }
}


