package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.compliance.domain.ExemptSectorAuthorization;
import et.ut.einvoice.compliance.domain.ExemptSectorSummaryReport;
import et.ut.einvoice.compliance.dto.ExemptSectorAuthorizationRequestDto;
import et.ut.einvoice.compliance.dto.GenerateSummaryReportRequestDto;
import et.ut.einvoice.compliance.dto.ReviewSummaryReportRequestDto;
import et.ut.einvoice.compliance.repository.ExemptSectorAuthorizationRepository;
import et.ut.einvoice.compliance.repository.ExemptSectorReportLineRepository;
import et.ut.einvoice.compliance.repository.ExemptSectorSummaryReportRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test Suite for FDRE MoR Directive No. 1142/2026 Art. 20:
 * High-Volume Exempt-Sector Summary Reporting.
 *
 * Verifies:
 * 1. Granting of Art. 20 exempt-sector authorization (banking, telecom, etc.)
 * 2. Drafting of periodic aggregate sales summary reports
 * 3. Exclusion of B2B transactions from periodic summary reports (Art. 20(5))
 * 4. Freezing and cryptographic SHA-256 generation upon submission
 * 5. Review (Acceptance / Rejection) by Authority Auditors
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExemptSectorSummaryReportingTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ExemptSectorAuthorizationRepository authRepository;

    @Autowired
    private ExemptSectorSummaryReportRepository summaryReportRepository;

    @Autowired
    private ExemptSectorReportLineRepository reportLineRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private et.ut.einvoice.platform.security.JwtTokenService jwtTokenService;

    private UUID tenantId;
    private String adminToken;
    private String tenantAdminToken;
    private String auditorToken;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        String tin = "TIN-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = new Tenant(tenantId, "ORG-TELECOM", "Ethio Telecom Enterprise", "EthioTelecom", tin);
        tenant.activate();
        tenantRepository.save(tenant);

        adminToken = jwtTokenService.generateMasterToken(
                "platform-admin-1",
                Set.of("ROLE_PLATFORM_ADMIN"),
                Set.of("platform:admin"),
                3600
        );

        tenantAdminToken = jwtTokenService.generateToken(
                tenantId,
                "tenant-admin-1",
                Set.of("ROLE_TENANT_ADMIN"),
                Set.of("tenant:admin"),
                3600
        );

        auditorToken = jwtTokenService.generateMasterToken(
                "authority-auditor-1",
                Set.of("ROLE_AUTHORITY_AUDITOR"),
                Set.of("authority:read", "authority:audit"),
                3600
        );
    }

    @Test
    @DisplayName("Art. 20(1)-(2): Platform admin can grant exempt sector authorization")
    void test_GrantExemptSectorAuthorization() throws Exception {
        ExemptSectorAuthorizationRequestDto dto = new ExemptSectorAuthorizationRequestDto();
        dto.setTenantId(tenantId);
        dto.setSectorCode("SEC-TELECOM");
        dto.setAuthorizationReference("MoR/EIRS/EXEMPT/2026/001");
        dto.setReportingFrequency("DAILY");
        dto.setEffectiveFrom(Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/master/exempt-sectors/authorizations")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorCode", is("SEC-TELECOM")))
                .andExpect(jsonPath("$.reportingFrequency", is("DAILY")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        assertTrue(authRepository.findByTenantId(tenantId).isPresent());
    }

    @Test
    @DisplayName("Art. 20(3)-(5): Generates draft summary report and excludes B2B transactions")
    void test_GenerateDraftSummaryReport_ExcludesB2B() throws Exception {
        // 1. Setup authorization
        ExemptSectorAuthorization auth = new ExemptSectorAuthorization(
                UUID.randomUUID(), tenantId, "SEC-TELECOM", "ADMIN", "REF-001", "DAILY"
        );
        authRepository.save(auth);

        Instant periodFrom = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant periodTo = Instant.now().plus(1, ChronoUnit.DAYS);

        // 2. Create a B2C invoice (should be included in summary)
        Invoice b2cInvoice = new Invoice();
        b2cInvoice.setId(UUID.randomUUID());
        b2cInvoice.setTenantId(tenantId);
        b2cInvoice.setDocumentNumber("DOC-001");
        b2cInvoice.setInvoiceCounter(1L);
        b2cInvoice.setTransactionType(et.ut.einvoice.invoicing.domain.TransactionType.B2C);
        b2cInvoice.setBuyerTin(null);
        b2cInvoice.setPreTaxTotal(new BigDecimal("100.00"));
        b2cInvoice.setTaxTotal(new BigDecimal("15.00"));
        b2cInvoice.setGrandTotal(new BigDecimal("115.00"));
        b2cInvoice.setStatus(InvoiceStatus.VALIDATED);
        b2cInvoice.setInvoiceDate(Instant.now());
        invoiceRepository.save(b2cInvoice);

        // 3. Create a B2B invoice (must be EXCLUDED per Art. 20(5))
        Invoice b2bInvoice = new Invoice();
        b2bInvoice.setId(UUID.randomUUID());
        b2bInvoice.setTenantId(tenantId);
        b2bInvoice.setDocumentNumber("DOC-002");
        b2bInvoice.setInvoiceCounter(2L);
        b2bInvoice.setTransactionType(et.ut.einvoice.invoicing.domain.TransactionType.B2B);
        b2bInvoice.setBuyerTin("0011223344");
        b2bInvoice.setPreTaxTotal(new BigDecimal("5000.00"));
        b2bInvoice.setTaxTotal(new BigDecimal("750.00"));
        b2bInvoice.setGrandTotal(new BigDecimal("5750.00"));
        b2bInvoice.setStatus(InvoiceStatus.VALIDATED);
        b2bInvoice.setInvoiceDate(Instant.now());
        invoiceRepository.save(b2bInvoice);

        // 4. Request summary generation
        GenerateSummaryReportRequestDto req = new GenerateSummaryReportRequestDto();
        req.setTenantId(tenantId);
        req.setReportPeriodLabel("2026-10-05");
        req.setPeriodFrom(periodFrom);
        req.setPeriodTo(periodTo);

        mockMvc.perform(post("/api/v1/compliance/exempt-sectors/reports/generate")
                        .header("Authorization", "Bearer " + tenantAdminToken)
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.totalInvoiceCount", is(1)))
                .andExpect(jsonPath("$.totalGrossAmount", is(100.0)))
                .andExpect(jsonPath("$.totalTaxAmount", is(15.0)))
                .andExpect(jsonPath("$.totalGrandTotal", is(115.0)));
    }

    @Test
    @DisplayName("Art. 20(3)(b): Submits summary report, generates SHA-256, freezes status")
    void test_SubmitSummaryReport() throws Exception {
        ExemptSectorAuthorization auth = new ExemptSectorAuthorization(
                UUID.randomUUID(), tenantId, "SEC-BANKING", "ADMIN", "REF-002", "DAILY"
        );
        authRepository.save(auth);

        ExemptSectorSummaryReport report = new ExemptSectorSummaryReport(
                UUID.randomUUID(), tenantId, auth.getId(),
                "2026-10-06", "DAILY",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now()
        );
        summaryReportRepository.save(report);

        mockMvc.perform(post("/api/v1/compliance/exempt-sectors/reports/" + report.getId() + "/submit")
                        .header("Authorization", "Bearer " + tenantAdminToken)
                        .header("X-Tenant-Id", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.sha256Checksum").isNotEmpty());

        ExemptSectorSummaryReport submitted = summaryReportRepository.findById(report.getId()).orElseThrow();
        assertEquals("SUBMITTED", submitted.getStatus());
        assertNotNull(submitted.getSha256Checksum());
    }

    @Test
    @DisplayName("Art. 20(3): Authority Auditor can accept or reject submitted summary report")
    void test_ReviewSummaryReport() throws Exception {
        ExemptSectorAuthorization auth = new ExemptSectorAuthorization(
                UUID.randomUUID(), tenantId, "SEC-BANKING", "ADMIN", "REF-003", "DAILY"
        );
        authRepository.save(auth);

        ExemptSectorSummaryReport report = new ExemptSectorSummaryReport(
                UUID.randomUUID(), tenantId, auth.getId(),
                "2026-10-07", "DAILY",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now()
        );
        report.submit("FINANCE_OFFICER", "dummy-sha256");
        summaryReportRepository.save(report);

        ReviewSummaryReportRequestDto reviewDto = new ReviewSummaryReportRequestDto();
        reviewDto.setAccept(true);
        reviewDto.setReason("Reconciled against banking ledger");

        mockMvc.perform(post("/api/v1/authority/exempt-sectors/reports/" + report.getId() + "/review")
                        .header("Authorization", "Bearer " + auditorToken)
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")));

        ExemptSectorSummaryReport accepted = summaryReportRepository.findById(report.getId()).orElseThrow();
        assertEquals("ACCEPTED", accepted.getStatus());
    }
}
