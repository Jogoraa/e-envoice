package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.compliance.domain.AuthorityInvestigationExport;
import et.ut.einvoice.compliance.dto.AuthorityExportJobDto;
import et.ut.einvoice.compliance.dto.AuthorityExportRequestDto;
import et.ut.einvoice.compliance.repository.AuthorityInvestigationExportRepository;
import et.ut.einvoice.compliance.service.AuthorityInvestigationService;
import et.ut.einvoice.customer.domain.Customer;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.config.service.SecretEncryptionService;
import et.ut.einvoice.platform.security.JwtTokenService;
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
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthorityInvestigationExportTestSuite {

    @Autowired
    private AuthorityInvestigationService investigationService;

    @Autowired
    private AuthorityInvestigationExportRepository exportRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SecretEncryptionService encryptionService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private UUID tenantId;
    private String auditorToken;

    @BeforeEach
    void setUp() {
        exportRepository.deleteAll();
        customerRepository.deleteAll();

        tenantId = UUID.randomUUID();
        String tin = "TIN-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = new Tenant(tenantId, "ORG-AUTH-TEST", "Auth Audit PLC", "AuthTrade", tin);
        tenant.activate();
        tenantRepository.save(tenant);

        auditorToken = jwtTokenService.generateMasterToken(
                "insa-auditor-99",
                Set.of("ROLE_AUTHORITY_AUDITOR"),
                Set.of("authority:read", "authority:audit"),
                3600
        );
    }

    @Test
    @DisplayName("Stage 16: Authority Customer Inspection requires mandatory case reference and reason")
    void test_CustomerInspection_RequiresMandatoryCaseReference() throws Exception {
        mockMvc.perform(get("/api/v1/authority/customers")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Stage 16: Authority Customer Inspection returns matching customer and emits audit evidence")
    void test_CustomerInspection_WithCaseReference_SuccessAndAudited() throws Exception {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setTin("0099887766");
        customer.setLegalName("Addis Retail Trading PLC");
        customer.setCity("Addis Ababa");
        customer.setStatus("ACTIVE");
        customerRepository.save(customer);

        long auditCountBefore = auditEventRepository.count();

        mockMvc.perform(get("/api/v1/authority/customers")
                        .param("caseReference", "CR-2026-00441")
                        .param("reason", "Statutory cross-taxpayer tax compliance audit")
                        .param("tenantId", tenantId.toString())
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tin", is("0099887766")))
                .andExpect(jsonPath("$.content[0].legalName", is("Addis Retail Trading PLC")));

        long auditCountAfter = auditEventRepository.count();
        assertTrue(auditCountAfter > auditCountBefore, "Customer inspection must produce an authoritative audit record");
    }

    @Test
    @DisplayName("Stage 16: Asynchronous Encrypted Investigation Export generates AES-256-GCM ciphertext and SHA-256")
    void test_CreateInvestigationExport_EncryptedAndChecksummed() throws Exception {
        AuthorityExportRequestDto request = new AuthorityExportRequestDto(
                "CR-INSA-2026-887",
                "Cross-border transaction audit pursuant to Art. 15(5)",
                tenantId,
                null,
                null,
                null,
                null,
                null,
                null
        );

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/authority/investigations/exports")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReference", is("CR-INSA-2026-887")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.encryptionAlgorithm", is("AES/GCM/NoPadding")))
                .andExpect(jsonPath("$.payloadEncryptedBase64").isNotEmpty())
                .andExpect(jsonPath("$.sha256Checksum").isNotEmpty());

        AuthorityInvestigationExport saved = exportRepository.findAll().get(0);
        assertEquals("COMPLETED", saved.getStatus());
        assertNotNull(saved.getPayloadEncryptedBase64());

        // Verify that the payload is properly encrypted and can be decrypted
        String decrypted = encryptionService.decryptSecret(saved.getPayloadEncryptedBase64());
        assertNotNull(decrypted);
        assertTrue(decrypted.contains("CR-INSA-2026-887"));
    }

    @Test
    @DisplayName("Stage 16: Retrieve Export Job artifact audits access and returns encrypted payload")
    void test_GetExportJob_Audited() throws Exception {
        AuthorityInvestigationExport job = new AuthorityInvestigationExport(
                UUID.randomUUID(),
                "CR-CASE-1122",
                "Inspection reason",
                "auditor-1",
                tenantId,
                null,
                null,
                null,
                null,
                null,
                null
        );
        job.markCompleted(0, encryptionService.encryptSecret("{\"data\":\"test\"}"), "abc123sha256");
        AuthorityInvestigationExport saved = exportRepository.save(job);

        mockMvc.perform(get("/api/v1/authority/investigations/exports/" + saved.getId())
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReference", is("CR-CASE-1122")))
                .andExpect(jsonPath("$.sha256Checksum", is("abc123sha256")));
    }

    @Test
    @DisplayName("Stage 16: List Authority Investigation Export Jobs returns paginated results")
    void test_ListExportJobs_Paginated() throws Exception {
        AuthorityInvestigationExport job1 = new AuthorityInvestigationExport(
                UUID.randomUUID(), "CR-01", "Reason 1", "auditor-1", tenantId, null, null, null, null, null, null
        );
        AuthorityInvestigationExport job2 = new AuthorityInvestigationExport(
                UUID.randomUUID(), "CR-02", "Reason 2", "auditor-2", tenantId, null, null, null, null, null, null
        );
        exportRepository.save(job1);
        exportRepository.save(job2);

        mockMvc.perform(get("/api/v1/authority/investigations/exports")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)));
    }

    @Test
    @DisplayName("Stage 16: Anonymous access to Authority investigation endpoints is rejected with 401")
    void test_UnauthenticatedAccess_RejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/authority/customers")
                        .param("caseReference", "CR-TEST")
                        .param("reason", "Reason")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/authority/investigations/exports")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Stage 16: Non-auditor tenant token is rejected with 403 Forbidden")
    void test_TenantUser_CannotAccessAuthorityInvestigation() throws Exception {
        String tenantUserToken = jwtTokenService.generateToken(
                tenantId,
                "tenant-user-1",
                Set.of("ROLE_TENANT_USER"),
                Set.of("invoices:read"),
                3600
        );

        mockMvc.perform(get("/api/v1/authority/customers")
                        .param("caseReference", "CR-TEST")
                        .param("reason", "Reason")
                        .header("Authorization", "Bearer " + tenantUserToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
