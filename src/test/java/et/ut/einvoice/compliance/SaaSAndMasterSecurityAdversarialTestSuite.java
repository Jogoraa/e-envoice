package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.controller.SaasSubscriptionController;
import et.ut.einvoice.tenancy.controller.SaasTenantManagementController;
import et.ut.einvoice.tenancy.domain.Subscription;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.SubscriptionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.service.DelegatedTenantSessionService;
import et.ut.einvoice.tenancy.service.SaasTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SaaSAndMasterSecurityAdversarialTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private DelegatedTenantSessionService delegatedTenantSessionService;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantAId;
    private UUID tenantBId;
    private String masterUserId = "master-operator-007";
    private String tenantTokenA;
    private String tenantTokenB;
    private String masterToken;

    @BeforeEach
    void setUp() {
        apiClientRepository.deleteAll();
        subscriptionRepository.deleteAll();
        tenantRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();

        // 1. Setup Tenant A
        Tenant tenantA = new Tenant(tenantAId, "ORG-A", "Tenant Alpha PLC", "Alpha Trade", "0011223344", "SME");
        tenantA.activate();
        tenantRepository.save(tenantA);

        TaxpayerProfile profileA = new TaxpayerProfile(
                tenantAId, "0011223344", "VAT-001", "Tenant Alpha PLC", "Alpha Trade",
                "13", "01", "+251911000001", "contact@alpha.et", "SYS-001", "POS"
        );
        taxpayerProfileRepository.save(profileA);

        Subscription subA = new Subscription(
                UUID.randomUUID(), tenantAId, "ENTERPRISE", "MONTHLY", 10000, 50, true, false, "ACTIVE"
        );
        subscriptionRepository.save(subA);

        // 2. Setup Tenant B
        Tenant tenantB = new Tenant(tenantBId, "ORG-B", "Tenant Beta S.C.", "Beta Trade", "0055667788", "SME");
        tenantB.activate();
        tenantRepository.save(tenantB);

        TaxpayerProfile profileB = new TaxpayerProfile(
                tenantBId, "0055667788", "VAT-002", "Tenant Beta S.C.", "Beta Trade",
                "13", "02", "+251911000002", "contact@beta.et", "SYS-002", "POS"
        );
        taxpayerProfileRepository.save(profileB);

        Subscription subB = new Subscription(
                UUID.randomUUID(), tenantBId, "STARTER", "MONTHLY", 2000, 10, false, false, "ACTIVE"
        );
        subscriptionRepository.save(subB);

        // 3. Issue Tokens
        tenantTokenA = jwtTokenService.generateToken(
                tenantAId, "user-alpha", Set.of("ROLE_TENANT_USER", "ROLE_TENANT_ADMIN"),
                Set.of("invoice:create", "invoice:read", "customer:read", "customer:create"), 3600
        );

        tenantTokenB = jwtTokenService.generateToken(
                tenantBId, "user-beta", Set.of("ROLE_TENANT_USER", "ROLE_TENANT_ADMIN"),
                Set.of("invoice:create", "invoice:read", "customer:read", "customer:create"), 3600
        );

        masterToken = jwtTokenService.generateMasterToken(
                masterUserId, Set.of("ROLE_SAAS_ADMIN", "ROLE_PLATFORM_ADMIN"),
                Set.of("platform:admin", "saas:manage", "tenant:support"), 3600
        );

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenReturn(GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                        "IRN-" + UUID.randomUUID(), "RRN-101", "2026-09-21T10:00:00Z", "qr", "sig"));
    }

    private CreateInvoiceRequest sampleInvoiceRequest() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "SKU-01", "High Grade Commodity", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO, "VAT15", null
        ));
        return new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);
    }

    @Test
    @DisplayName("Context 1: Normal Tenant Token can create invoice on its own tenant")
    void testTenantTokenCanCreateInvoiceOnOwnTenant() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + tenantTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleInvoiceRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.irn").exists())
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    @DisplayName("Boundary 1: Tenant Token CANNOT access SaaS Master Platform APIs")
    void testTenantTokenCannotAccessSaasMasterApis() throws Exception {
        mockMvc.perform(get("/api/v1/saas/tenants")
                        .header("Authorization", "Bearer " + tenantTokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_CANNOT_ACCESS_MASTER_API"));
    }

    @Test
    @DisplayName("Context 2: Master Token can access SaaS Master Platform APIs")
    void testMasterTokenCanAccessSaasMasterApis() throws Exception {
        mockMvc.perform(get("/api/v1/saas/tenants")
                        .header("Authorization", "Bearer " + masterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Boundary 2: Direct Master Token CANNOT access Tenant Business APIs directly without delegation")
    void testDirectMasterTokenCannotAccessTenantApisDirectly() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleInvoiceRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MASTER_DIRECT_TENANT_ACCESS_FORBIDDEN"));
    }

    @Test
    @DisplayName("Context 3: Master can request server-issued Delegated Support Session for Tenant A")
    void testMasterCanRequestDelegatedSupportSession() throws Exception {
        var payload = new SaasTenantManagementController.RequestSupportSessionPayload(
                "TESTING", null, "Testing POS line calculations", 1800L
        );

        String responseStr = mockMvc.perform(post("/api/v1/saas/tenants/" + tenantAId + "/support-session")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.targetTenantId").value(tenantAId.toString()))
                .andExpect(jsonPath("$.accessType").value("TESTING"))
                .andExpect(jsonPath("$.masterUserId").value(masterUserId))
                .andReturn().getResponse().getContentAsString();

        String delegatedToken = objectMapper.readTree(responseStr).get("token").asText();
        assertNotNull(delegatedToken);

        // Verify delegated token can operate inside Tenant A
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + delegatedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleInvoiceRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    @DisplayName("Boundary 3: Delegated Token for Tenant A CANNOT access Tenant B")
    void testDelegatedTokenCannotAccessDifferentTenant() throws Exception {
        var sessionResult = delegatedTenantSessionService.requestSupportSession(
                masterUserId, tenantAId, null, "TESTING", "Support", 1800L
        );

        // Attempting to forge X-Tenant-ID header to Tenant B
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + sessionResult.token())
                        .header("X-Tenant-ID", tenantBId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleInvoiceRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_TENANT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Boundary 4: Read-Only Delegated Token CANNOT mutate tenant invoices")
    void testReadOnlyDelegatedTokenCannotMutateInvoices() throws Exception {
        var sessionResult = delegatedTenantSessionService.requestSupportSession(
                masterUserId, tenantAId, null, "READ_ONLY_SUPPORT", "Supervisory Read-Only Check", 1800L
        );

        // GET is permitted
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + sessionResult.token()))
                .andExpect(status().isOk());

        // POST mutation is blocked server-side
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + sessionResult.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleInvoiceRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("READ_ONLY_DELEGATED_ACCESS"));
    }

    @Test
    @DisplayName("Lifecycle 1: Terminating delegated session immediately invalidates the token")
    void testTerminatedDelegatedSessionIsBlocked() throws Exception {
        var sessionResult = delegatedTenantSessionService.requestSupportSession(
                masterUserId, tenantAId, null, "TESTING", "Test Before Revoke", 1800L
        );

        // Token works initially
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + sessionResult.token()))
                .andExpect(status().isOk());

        // Master explicitly terminates session
        mockMvc.perform(post("/api/v1/saas/support-session/" + sessionResult.sessionId() + "/terminate")
                        .header("Authorization", "Bearer " + masterToken))
                .andExpect(status().isNoContent());

        // Token is immediately rejected
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + sessionResult.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DELEGATED_SESSION_INVALID"));
    }

    @Test
    @DisplayName("Audit 1: Delegated support access records dual identities in immutable audit trail")
    void testDelegatedAccessAuditingRecordsDualIdentities() {
        var sessionResult = delegatedTenantSessionService.requestSupportSession(
                masterUserId, tenantAId, null, "TESTING", "Forensic Inspection", 1800L
        );

        List<AuditEvent> events = auditEventRepository.findByTenantIdOrderBySequenceNumberAsc(tenantAId);
        assertFalse(events.isEmpty());

        AuditEvent grantEvent = events.stream()
                .filter(e -> "MASTER_TENANT_ACCESS_GRANTED".equals(e.getAction()))
                .findFirst()
                .orElseThrow();

        assertEquals(masterUserId, grantEvent.getActorId());
        assertEquals("MASTER_DELEGATED_OPERATOR", grantEvent.getActorType());
        assertEquals(tenantAId, grantEvent.getTenantId());
        assertTrue(grantEvent.getPayloadJson().contains(masterUserId));
        assertTrue(grantEvent.getPayloadJson().contains(tenantAId.toString()));
    }

    @Test
    @DisplayName("Master Admin 1: Master Operator can manage and mutate tenant subscriptions")
    void testMasterCanManageTenantSubscriptions() throws Exception {
        var updateReq = new SaasSubscriptionController.UpdateSubscriptionRequest(
                "ENTERPRISE_PLUS", "ANNUAL", 50000, 100, true, "ACTIVE"
        );

        mockMvc.perform(put("/api/v1/saas/subscriptions/" + tenantAId)
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("ENTERPRISE_PLUS"))
                .andExpect(jsonPath("$.billingCycle").value("ANNUAL"))
                .andExpect(jsonPath("$.maxMonthlyInvoices").value(50000))
                .andExpect(jsonPath("$.rateLimitRps").value(100))
                .andExpect(jsonPath("$.offlineAllowed").value(true));

        Subscription updated = subscriptionRepository.findByTenantId(tenantAId).orElseThrow();
        assertEquals("ENTERPRISE_PLUS", updated.getPlanCode());
        assertEquals(50000, updated.getMaxMonthlyInvoices());
    }

    @Test
    @DisplayName("Master Admin 2: Master Operator can onboard a brand new tenant with subscription")
    void testMasterCanOnboardNewTenant() throws Exception {
        var onboardReq = new SaasTenantService.OnboardTenantRequest(
                "ORG-HAWASSA", "Hawassa Agro Trading PLC", "Hawassa Agro", "0099887766",
                "GROWTH", "MONTHLY", 15000, 40, true, "KEY_HAWASSA", "sec_hawassa_123"
        );

        String res = mockMvc.perform(post("/api/v1/saas/tenants")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tin").value("0099887766"))
                .andExpect(jsonPath("$.legalName").value("Hawassa Agro Trading PLC"))
                .andExpect(jsonPath("$.planCode").value("GROWTH"))
                .andReturn().getResponse().getContentAsString();

        String newTenantId = objectMapper.readTree(res).get("id").asText();
        assertNotNull(newTenantId);
        assertTrue(tenantRepository.findByTin("0099887766").isPresent());
    }

    @Test
    @DisplayName("Master Admin 3: Master Operator can transition tenant lifecycle state")
    void testMasterCanTransitionTenantLifecycle() throws Exception {
        var transitionReq = new SaasTenantService.LifecycleTransitionRequest(
                "SUSPENDED", "Non-payment of SaaS invoice for 60 days"
        );

        mockMvc.perform(post("/api/v1/saas/tenants/" + tenantAId + "/lifecycle-transition")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transitionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Subsequent invoice operations on suspended tenant are blocked
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + tenantTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleInvoiceRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }
}
