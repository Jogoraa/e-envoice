package et.ut.einvoice.notifications;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.controller.PublicInvoiceVerificationController;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.PublicInvoiceVerificationDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.notifications.domain.FailureClassification;
import et.ut.einvoice.notifications.domain.InvoiceNotificationOutbox;
import et.ut.einvoice.notifications.domain.NotificationStatus;
import et.ut.einvoice.notifications.provider.MockGeezSmsProvider;
import et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceNotificationIntegrationTestSuite {

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
    private PublicInvoiceVerificationController publicVerificationController;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.createWithClient(tenantId, "CLIENT_A", Set.of("ROLE_TENANT_ADMIN"), Set.of("invoice:create", "invoice:read"), UUID.randomUUID().toString()));

        taxpayerProfileRepository.deleteAll();
        TaxpayerProfile seller = new TaxpayerProfile(
                tenantId,
                "0012345678",
                "VAT-1234",
                "UT Solutions PLC",
                "UT Solutions",
                "Addis Ababa",
                "Bole",
                "0911223344",
                "seller@example.com",
                "SYS-001",
                "POS"
        );
        taxpayerProfileRepository.save(seller);

        mockSmsProvider.reset();

        when(governmentRegistrationProvider.registerInvoice(any(), any(), any()))
                .thenAnswer(inv -> GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                        "IRN-" + UUID.randomUUID(),
                        "RRN-1001",
                        Instant.now().toString(),
                        "SIGNED_QR_DATA",
                        "SIGNED_INVOICE_DATA"
                ));
    }

    @Test
    @DisplayName("Guarantees same-transaction outbox enqueueing upon invoice registration")
    void testSameTransactionOutboxEnqueueing() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                TransactionType.B2C,
                "CASH",
                "IMMEDIATE",
                new CreateInvoiceRequest.BuyerRequest(
                        "Abebe Bikila",
                        null,
                        null,
                        null,
                        "TIN",
                        "0911234567",
                        "abebe@example.com",
                        "ET", "Addis Ababa", "Bole"
                ),
                List.of(
                        new CreateInvoiceRequest.LineItemRequest(
                                "ITEM-01",
                                "Professional Consulting Services",
                                "SERVICES",
                                "HOURS",
                                new BigDecimal("2.0"),
                                new BigDecimal("1000.00"),
                                BigDecimal.ZERO,
                                "VAT15",
                                BigDecimal.ZERO
                        )
                ),
                null,
                null,
                "INV-SMS-001",
                false
        );

        var response = invoiceService.createAndRegisterInvoice(request, "IDEM-SMS-1");
        assertNotNull(response);
        assertEquals(InvoiceStatus.REGISTERED, response.status());

        Invoice persisted = invoiceRepository.findById(response.id()).orElseThrow();
        assertNotNull(persisted.getPublicVerificationToken());
        assertFalse(persisted.getPublicVerificationToken().isBlank());

        List<InvoiceNotificationOutbox> outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, persisted.getId());
        assertEquals(1, outboxList.size(), "Durable outbox record must exist in same transaction");

        InvoiceNotificationOutbox outbox = outboxList.get(0);
        assertEquals(NotificationStatus.PENDING, outbox.getStatus());
        assertEquals("251911234567", outbox.getRecipientPhoneSnapshot());
        assertTrue(outbox.getRenderedMessage().contains("INV-SMS-001"));
        assertTrue(outbox.getRenderedMessage().contains(persisted.getPublicVerificationToken()));

        // Audit check
        var auditEvents = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
        boolean auditRecorded = auditEvents.stream().anyMatch(e -> AuditAction.SMS_NOTIFICATION_CREATED.name().equals(e.getAction()));
        assertTrue(auditRecorded, "SMS_NOTIFICATION_CREATED audit event must be persisted in transaction");
    }

    @Test
    @DisplayName("Worker claims lease and successfully submits notification to provider")
    void testWorkerDispatchesNotification() {
        testSameTransactionOutboxEnqueueing();

        outboxWorker.processPendingBatch();

        List<InvoiceNotificationOutbox> outboxList = outboxRepository.findAll();
        assertFalse(outboxList.isEmpty());
        InvoiceNotificationOutbox outbox = outboxList.get(outboxList.size() - 1);

        assertEquals(NotificationStatus.SUBMITTED, outbox.getStatus());
        assertNotNull(outbox.getProviderMessageId());
        assertNotNull(outbox.getSubmittedAt());
        assertNull(outbox.getLockedAt());

        // Audit check
        var auditEvents = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantId);
        boolean acceptedRecorded = auditEvents.stream().anyMatch(e -> AuditAction.SMS_PROVIDER_ACCEPTED.name().equals(e.getAction()));
        assertTrue(acceptedRecorded, "SMS_PROVIDER_ACCEPTED audit event must be recorded");
    }

    @Test
    @DisplayName("Socket read timeout transitions to SUBMISSION_UNKNOWN and is NOT blindly retried")
    void testTimeoutTransitionsToSubmissionUnknown() {
        mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);

        CreateInvoiceRequest request = new CreateInvoiceRequest(
                TransactionType.B2C,
                "CASH",
                "IMMEDIATE",
                new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, "TIN", "0911999888", null, "ET", null, null),
                List.of(new CreateInvoiceRequest.LineItemRequest("ITEM", "Desc", "GOODS", "PCS", BigDecimal.ONE, new BigDecimal("500.00"), BigDecimal.ZERO, "VAT15", BigDecimal.ZERO)),
                null, null, "INV-SMS-TIMEOUT", false
        );

        invoiceService.createAndRegisterInvoice(request, "IDEM-TIMEOUT");

        // Run worker
        outboxWorker.processPendingBatch();

        List<InvoiceNotificationOutbox> outboxList = outboxRepository.findAll();
        InvoiceNotificationOutbox outbox = outboxList.get(outboxList.size() - 1);

        assertEquals(NotificationStatus.SUBMISSION_UNKNOWN, outbox.getStatus());
        assertEquals(FailureClassification.PROVIDER_UNKNOWN_OUTCOME, outbox.getFailureClassification());

        int priorInvocations = mockSmsProvider.getInvocationCount();

        // Second worker run should NOT blindly resubmit unknown outcomes
        outboxWorker.processPendingBatch();
        assertEquals(priorInvocations, mockSmsProvider.getInvocationCount(), "Unknown outcome must not be immediately retried");
    }

    @Test
    @DisplayName("Failure independence invariant: permanent SMS failure leaves invoice REGISTERED")
    void testFailureIndependenceInvariant() {
        mockSmsProvider.setScenario(MockGeezSmsProvider.MockScenario.INVALID_PHONE);

        CreateInvoiceRequest request = new CreateInvoiceRequest(
                TransactionType.B2C,
                "CASH",
                "IMMEDIATE",
                new CreateInvoiceRequest.BuyerRequest("Buyer", null, null, null, "TIN", "0911000000", null, "ET", null, null),
                List.of(new CreateInvoiceRequest.LineItemRequest("ITEM", "Desc", "GOODS", "PCS", BigDecimal.ONE, new BigDecimal("500.00"), BigDecimal.ZERO, "VAT15", BigDecimal.ZERO)),
                null, null, "INV-SMS-FAIL-INV", false
        );

        var response = invoiceService.createAndRegisterInvoice(request, "IDEM-FAIL-INV");
        UUID invoiceId = response.id();

        outboxWorker.processPendingBatch();

        List<InvoiceNotificationOutbox> outboxList = outboxRepository.findAllByTenantIdAndInvoiceId(tenantId, invoiceId);
        assertEquals(1, outboxList.size());
        assertEquals(NotificationStatus.PERMANENTLY_FAILED, outboxList.get(0).getStatus());

        // CRITICAL INVARIANT: Authoritative invoice status MUST remain REGISTERED
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus(), "SMS permanent failure must NEVER alter invoice fiscal status");
    }

    @Test
    @DisplayName("Public verification converges on existing verification model using token")
    void testPublicVerificationByToken() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                TransactionType.B2C,
                "CASH",
                "IMMEDIATE",
                new CreateInvoiceRequest.BuyerRequest("Public Buyer", null, null, null, "TIN", "0911555444", null, "ET", null, null),
                List.of(new CreateInvoiceRequest.LineItemRequest("ITEM", "Public Item", "GOODS", "PCS", BigDecimal.ONE, new BigDecimal("1200.00"), BigDecimal.ZERO, "VAT15", BigDecimal.ZERO)),
                null, null, "INV-SMS-VERIFY", false
        );

        var response = invoiceService.createAndRegisterInvoice(request, "IDEM-VERIFY-1");
        Invoice invoice = invoiceRepository.findById(response.id()).orElseThrow();
        String token = invoice.getPublicVerificationToken();
        assertNotNull(token);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("192.168.1.100");

        var verifyResponse = publicVerificationController.verifyInvoiceByToken(token, servletRequest);
        assertEquals(200, verifyResponse.getStatusCode().value());
        assertTrue(verifyResponse.getBody() instanceof PublicInvoiceVerificationDto);

        PublicInvoiceVerificationDto dto = (PublicInvoiceVerificationDto) verifyResponse.getBody();
        assertEquals(invoice.getIrn(), dto.irn(), "Must expose unmasked authoritative IRN");
        assertEquals("INV-SMS-VERIFY", dto.documentNumber());
        assertEquals("VALID_REGISTERED", dto.verificationStatus());
    }
}
