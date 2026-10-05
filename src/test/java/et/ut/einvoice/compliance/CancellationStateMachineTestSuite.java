package et.ut.einvoice.compliance;

import et.ut.einvoice.cancellation.domain.CancellationEvidenceAttachment;
import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.domain.CancellationState;
import et.ut.einvoice.cancellation.dto.SubmitEvidenceDto;
import et.ut.einvoice.cancellation.repository.CancellationEvidenceAttachmentRepository;
import et.ut.einvoice.cancellation.repository.CancellationRequestRepository;
import et.ut.einvoice.cancellation.service.CancellationService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CancellationStateMachineTestSuite {

    @Autowired
    private CancellationService cancellationService;

    @Autowired
    private CancellationRequestRepository cancellationRepository;

    @Autowired
    private CancellationEvidenceAttachmentRepository evidenceRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private static final AtomicLong TIN_COUNTER = new AtomicLong(40000000L);

    private UUID tenantId;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        cancellationRepository.deleteAll();

        tenantId = UUID.randomUUID();
        String tin = "09" + TIN_COUNTER.incrementAndGet();
        Tenant tenant = new Tenant(tenantId, "ORG-CANCEL", "Cancel Test Corp", "Cancel Trade", tin);
        tenantRepository.save(tenant);

        testInvoice = new Invoice(
                UUID.randomUUID(),
                tenantId,
                "INV-CANCEL-" + UUID.randomUUID().toString().substring(0, 8),
                1001L,
                Instant.now(),
                TransactionType.B2B,
                "CASH",
                "IMMEDIATE"
        );
        testInvoice.setBuyerTin("0011223344");
        testInvoice.setBuyerLegalName("Buyer Corp");
        String irn = "IRN-CANCEL-" + UUID.randomUUID().toString().substring(0, 8);
        testInvoice.markRegistered(irn, "RRN-123", Instant.now().toString(), "QR-CODE", "SIGNED-INV");
        invoiceRepository.save(testInvoice);
    }

    @Test
    @DisplayName("Stage 14: Cancellation request created in REQUESTED state with baseline review window")
    void test_CreateCancellation_InitialState() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "DATA_ENTRY_ERROR",
                "Wrong TIN entered for buyer"
        );
        cancellationRepository.save(cancellation);

        assertEquals(CancellationState.REQUESTED, cancellation.getState());
        assertNull(cancellation.getAuthorityEvidenceRequestedAt());
        assertNull(cancellation.getEvidenceDeadline());
        assertNotNull(cancellation.getSlaDeadlineAt());
        assertTrue(cancellation.getSlaDeadlineAt().isAfter(Instant.now().plus(25, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("Stage 14: Authority demand starts the 48-hour statutory evidence countdown")
    void test_DemandAuthorityEvidence_Starts48HourClock() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "DUPLICATE_ISSUANCE",
                "Accidental double issuance"
        );
        cancellation = cancellationRepository.save(cancellation);

        CancellationRequest updated = cancellationService.demandAuthorityEvidence(tenantId, cancellation.getId(), Duration.ofHours(48));

        assertEquals(CancellationState.EVIDENCE_REQUESTED, updated.getState());
        assertNotNull(updated.getAuthorityEvidenceRequestedAt());
        assertNotNull(updated.getEvidenceDeadline());

        long hoursRemaining = Duration.between(Instant.now(), updated.getEvidenceDeadline()).toHours();
        assertTrue(hoursRemaining >= 47 && hoursRemaining <= 48, "Evidence deadline must be exactly 48 hours from request time");
    }

    @Test
    @DisplayName("Stage 14: Evidence submission within 48 hours stores attachment and SHA-256 hash")
    void test_SubmitEvidence_WithinDeadline_Succeeds() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "CUSTOMER_REJECTION",
                "Buyer rejected shipment"
        );
        cancellation = cancellationRepository.save(cancellation);
        cancellationService.demandAuthorityEvidence(tenantId, cancellation.getId(), Duration.ofHours(48));

        String dummyPdf = Base64.getEncoder().encodeToString("%PDF-1.4 sample evidence document".getBytes(StandardCharsets.UTF_8));
        SubmitEvidenceDto dto = new SubmitEvidenceDto(
                cancellation.getId(),
                "credit_note_agreement.pdf",
                "application/pdf",
                dummyPdf,
                "Signed return shipment notice"
        );

        CancellationRequest submitted = cancellationService.submitCancellationEvidence(tenantId, dto);
        assertEquals(CancellationState.EVIDENCE_SUBMITTED, submitted.getState());
        assertNotNull(submitted.getEvidenceSubmittedAt());

        List<CancellationEvidenceAttachment> attachments =
                evidenceRepository.findAllByTenantIdAndCancellationRequestId(tenantId, cancellation.getId());
        assertEquals(1, attachments.size());
        assertEquals("credit_note_agreement.pdf", attachments.get(0).getFileName());
        assertNotNull(attachments.get(0).getSha256Checksum());
    }

    @Test
    @DisplayName("Stage 14: Prohibited file type is rejected with UNSUPPORTED_FILE_TYPE")
    void test_SubmitEvidence_ProhibitedFileType_Rejected() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "ERROR",
                "Wrong invoice"
        );
        cancellation = cancellationRepository.save(cancellation);
        cancellationService.demandAuthorityEvidence(tenantId, cancellation.getId(), Duration.ofHours(48));

        String dummyExe = Base64.getEncoder().encodeToString("MZ malicious code".getBytes(StandardCharsets.UTF_8));
        SubmitEvidenceDto dto = new SubmitEvidenceDto(
                cancellation.getId(),
                "payload.exe",
                "application/x-msdownload",
                dummyExe,
                "Malicious file"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cancellationService.submitCancellationEvidence(tenantId, dto));
        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
    }

    @Test
    @DisplayName("Stage 14: Submitting evidence after 48-hour deadline expires transitions request to EXPIRED")
    void test_SubmitEvidence_AfterDeadline_ExpiresRequest() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "ERROR",
                "Outdated invoice"
        );
        cancellation.requestEvidence(Instant.now().minus(Duration.ofHours(50))); // Expired 2 hours ago
        cancellation = cancellationRepository.save(cancellation);

        String dummyPng = Base64.getEncoder().encodeToString("sample-png-data".getBytes(StandardCharsets.UTF_8));
        SubmitEvidenceDto dto = new SubmitEvidenceDto(
                cancellation.getId(),
                "return_slip.png",
                "image/png",
                dummyPng,
                "Late submission"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cancellationService.submitCancellationEvidence(tenantId, dto));
        assertEquals("EVIDENCE_DEADLINE_EXPIRED", ex.getCode());

        CancellationRequest expired = cancellationRepository.findById(cancellation.getId()).orElseThrow();
        assertEquals(CancellationState.EXPIRED, expired.getState());
    }

    @Test
    @DisplayName("Stage 14: Authority approval transitions state to APPROVED and fiscally marks invoice CANCELLED")
    void test_FinalizeAuthorityApproval_FiscallyCancelsInvoice() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "BILLING_ERROR",
                "Duplicate billing"
        );
        cancellation = cancellationRepository.save(cancellation);

        String ref = "MOR-CAN-REF-2026-999";
        CancellationRequest approved = cancellationService.finalizeAuthorityApproval(tenantId, cancellation.getId(), ref);

        assertEquals(CancellationState.APPROVED, approved.getState());
        assertEquals(ref, approved.getCancellationRef());
        assertNotNull(approved.getApprovedAt());

        Invoice updatedInvoice = invoiceRepository.findById(testInvoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.CANCELLED, updatedInvoice.getStatus());
    }

    @Test
    @DisplayName("Stage 14: Authority rejection transitions state to REJECTED and leaves invoice REGISTERED")
    void test_FinalizeAuthorityRejection_InvoiceRemainsRegistered() {
        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "SUSPICIOUS_REDUCTION",
                "Attempted tax reduction"
        );
        cancellation = cancellationRepository.save(cancellation);

        CancellationRequest rejected = cancellationService.finalizeAuthorityRejection(
                tenantId, cancellation.getId(), "Insufficient statutory justification for cancellation");

        assertEquals(CancellationState.REJECTED, rejected.getState());
        assertNotNull(rejected.getRejectionReason());

        Invoice updatedInvoice = invoiceRepository.findById(testInvoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.REGISTERED, updatedInvoice.getStatus(), "Invoice must remain REGISTERED when cancellation is rejected");
    }

    @Test
    @DisplayName("Stage 14: Cross-tenant cancellation isolation enforced")
    void test_CrossTenant_CancellationIsolation() {
        UUID otherTenantId = UUID.randomUUID();
        String otherTin = "08" + TIN_COUNTER.incrementAndGet();
        Tenant otherTenant = new Tenant(otherTenantId, "ORG-CROSS-CAN", "Cross Tenant Corp", "Cross Trade", otherTin);
        tenantRepository.save(otherTenant);

        CancellationRequest cancellation = new CancellationRequest(
                UUID.randomUUID(),
                tenantId,
                testInvoice.getId(),
                testInvoice.getIrn(),
                "ERROR",
                "Wrong invoice"
        );
        cancellation = cancellationRepository.save(cancellation);
        UUID cancellationId = cancellation.getId();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cancellationService.demandAuthorityEvidence(otherTenantId, cancellationId, Duration.ofHours(48)));
        assertEquals("CANCELLATION_NOT_FOUND", ex.getCode());
    }
}
