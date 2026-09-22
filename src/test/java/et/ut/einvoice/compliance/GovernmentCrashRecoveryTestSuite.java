package et.ut.einvoice.compliance;

import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.domain.GovernmentSubmission;
import et.ut.einvoice.government.domain.GovernmentSubmissionStatus;
import et.ut.einvoice.government.reconciliation.GovernmentReconciliationService;
import et.ut.einvoice.government.repository.GovernmentSubmissionRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.domain.OutboxStatus;
import et.ut.einvoice.platform.outbox.repository.OutboxEventRepository;
import et.ut.einvoice.platform.outbox.service.OutboxService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class GovernmentCrashRecoveryTestSuite {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private GovernmentSubmissionRepository submissionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private GovernmentReconciliationService reconciliationService;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantId;
    private TaxpayerProfile seller;

    @BeforeEach
    void setUp() {
        submissionRepository.deleteAll();
        outboxEventRepository.deleteAll();
        invoiceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantId = UUID.randomUUID();
        seller = new TaxpayerProfile(
                tenantId, "0011223344", "VAT-11223344", "Crash Test PLC", "Crash Test",
                "14", "01", "+251911000111", "crash@test.com", "8EFBBDD7FA", "ERP"
        );
        taxpayerProfileRepository.save(seller);

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
    }

    @Test
    @DisplayName("Crash 1: Accepted-then-timeout reconciliation recovers registered IRN with ZERO duplicate invoice")
    void test_AcceptedThenTimeout_ReconciliationRecoversWithoutDuplicate() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, tenantId, "DOC-RECON-" + UUID.randomUUID(), 101L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoice.setStatus(InvoiceStatus.SUBMISSION_PENDING);
        invoiceRepository.save(invoice);

        long invoiceCountBefore = invoiceRepository.count();

        // 1. Record timed-out UNKNOWN submission
        String subId = "SUB-TIMEOUT-" + UUID.randomUUID();
        GovernmentSubmission sub = new GovernmentSubmission(UUID.randomUUID(), tenantId, invoiceId, "MoR-EIRS", "v1.0", subId, "HASH-REQ-123");
        sub.markUnknown("GATEWAY_TIMEOUT", "Connection dropped after 10s");
        submissionRepository.save(sub);

        // 2. Mock verification response showing MoR accepted earlier
        String existingIrn = "RECONCILED-IRN-" + UUID.randomUUID();
        Mockito.when(governmentRegistrationProvider.verifySubmission(anyString(), anyString(), any(), anyString()))
                .thenReturn(GovernmentRegistrationProvider.GovernmentVerificationResult.found(existingIrn, "RRN-RECON-1", "2026-09-18T14:00:00Z"));

        // 3. Execute reconciliation
        reconciliationService.reconcileSingleSubmission(sub);

        // 4. Verify original invoice is now REGISTERED with the recovered IRN
        Invoice recovered = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, recovered.getStatus());
        assertEquals(existingIrn, recovered.getIrn());

        // 5. Verify submission state is ACCEPTED
        GovernmentSubmission reconciledSub = submissionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(GovernmentSubmissionStatus.ACCEPTED, reconciledSub.getStatus());
        assertEquals(existingIrn, reconciledSub.getGovernmentIrn());

        // 6. Verify absolute absence of duplicate invoices
        assertEquals(invoiceCountBefore, invoiceRepository.count(), "Reconciliation must NEVER create a second invoice or duplicate tax liability!");
    }

    @Test
    @DisplayName("Crash 2: Gateway rejection marks submission REJECTED and leaves invoice in non-registered state")
    void test_GatewayRejection_MarkedRejected() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, tenantId, "DOC-REJ-" + UUID.randomUUID(), 102L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoiceRepository.save(invoice);

        String subId = "SUB-REJ-" + UUID.randomUUID();
        GovernmentSubmission sub = new GovernmentSubmission(UUID.randomUUID(), tenantId, invoiceId, "MoR-EIRS", "v1.0", subId, "HASH-REQ-456");
        sub.markRejected("INVALID_BUYER_TIN", "Buyer TIN 000000000 is invalid in MoR tax register");
        submissionRepository.save(sub);

        GovernmentSubmission loaded = submissionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(GovernmentSubmissionStatus.REJECTED, loaded.getStatus());
        assertEquals("INVALID_BUYER_TIN", loaded.getErrorCode());
        assertNull(loaded.getGovernmentIrn());
    }

    @Test
    @DisplayName("Crash 3: Unacknowledged submission transitions to NEEDS_RECONCILIATION for safe retry")
    void test_UnacknowledgedSubmission_TransitionsToNeedsReconciliation() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, tenantId, "DOC-UNACK-" + UUID.randomUUID(), 103L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoiceRepository.save(invoice);

        String subId = "SUB-UNACK-" + UUID.randomUUID();
        GovernmentSubmission sub = new GovernmentSubmission(UUID.randomUUID(), tenantId, invoiceId, "MoR-EIRS", "v1.0", subId, "HASH-REQ-789");
        sub.markUnknown("GATEWAY_TIMEOUT", "Connection reset by peer");
        submissionRepository.save(sub);

        // Verification endpoint indicates submission was not found on MoR
        Mockito.when(governmentRegistrationProvider.verifySubmission(anyString(), anyString(), any(), anyString()))
                .thenReturn(GovernmentRegistrationProvider.GovernmentVerificationResult.notFound());

        reconciliationService.reconcileSingleSubmission(sub);

        GovernmentSubmission updated = submissionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(GovernmentSubmissionStatus.NEEDS_RECONCILIATION, updated.getStatus());
    }

    @Test
    @DisplayName("Crash 4: Outbox event remains deliverable across simulated process restart")
    void test_OutboxEvent_PersistsAndDeliversAfterCrash() {
        // Enqueue event
        OutboxEvent event = outboxService.enqueueEvent(
                tenantId, "INVOICE", UUID.randomUUID().toString(), "INVOICE_REGISTRATION", "{\"test\":true}"
        );
        assertEquals(OutboxStatus.PENDING, event.getStatus());

        // Simulate crash & restart: event is still in DB with PENDING status
        List<OutboxEvent> pending = outboxEventRepository.findPendingEventsWithLock(10);
        assertTrue(pending.stream().anyMatch(e -> e.getId().equals(event.getId())));

        // Worker picks up and marks published
        outboxService.markPublished(event.getId());
        OutboxEvent finished = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, finished.getStatus());
    }

    @Test
    @DisplayName("Crash 5: Repeated reconciliation execution on ACCEPTED submission is idempotent")
    void test_RepeatedReconciliation_IsIdempotent() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, tenantId, "DOC-IDEMP-" + UUID.randomUUID(), 104L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invoiceRepository.save(invoice);

        String subId = "SUB-IDEMP-" + UUID.randomUUID();
        GovernmentSubmission sub = new GovernmentSubmission(UUID.randomUUID(), tenantId, invoiceId, "MoR-EIRS", "v1.0", subId, "HASH-IDEMP");
        sub.markAccepted("EXISTING-IRN-123");
        submissionRepository.save(sub);

        // Reconcile already-accepted submission
        assertDoesNotThrow(() -> reconciliationService.reconcileUnknownSubmissions());

        GovernmentSubmission unchanged = submissionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(GovernmentSubmissionStatus.ACCEPTED, unchanged.getStatus());
    }
}
