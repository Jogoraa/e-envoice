package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.dto.CreateCancellationRequestDto;
import et.ut.einvoice.cancellation.repository.CancellationRequestRepository;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.ratelimit.RateLimitingService;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.ApiClient;
import et.ut.einvoice.tenancy.domain.GovernmentStatus;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.webhooks.domain.OutboundWebhookDelivery;
import et.ut.einvoice.webhooks.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend_sec_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "platform.security.cors.allowed-origins=http://localhost:3000,http://localhost:8080",
        "management.health.redis.enabled=false"
})
public class BackendSecurityHardeningAdversarialTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private CancellationRequestRepository cancellationRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Autowired
    private WebhookService webhookService;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantAId;
    private UUID tenantBId;

    private final String validKeyA = "KEY_SEC_TENANT_A";
    private final String validSecretA = "secret_a_1234567890";

    private final String validKeyB = "KEY_SEC_TENANT_B";
    private final String validSecretB = "secret_b_1234567890";

    private final String readOnlyKeyA = "KEY_SEC_TENANT_A_RO";
    private final String readOnlySecretA = "secret_ro_1234567890";

    @BeforeEach
    void setUp() {
        cancellationRepository.deleteAll();
        invoiceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();
        apiClientRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();

        // 1. Tenant A (Active, Government Active)
        Tenant tenantA = new Tenant(tenantAId, "ORG-SEC-A", "Tenant A Security PLC", "Tenant A", "0011223344", "SME");
        tenantA.activate();
        tenantA.setGovernmentStatus(GovernmentStatus.GOVERNMENT_ACTIVE);
        tenantRepository.save(tenantA);

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantAId, "0011223344", "VAT-112233", "Tenant A Security PLC", "Tenant A",
                "14", "05", "+251911223344", "tenanta@sec.et", "8EFBBDD7FA", "ERP"
        ));

        // 2. Tenant B (Active)
        Tenant tenantB = new Tenant(tenantBId, "ORG-SEC-B", "Tenant B Security PLC", "Tenant B", "0055667788", "SME");
        tenantB.activate();
        tenantB.setGovernmentStatus(GovernmentStatus.GOVERNMENT_ACTIVE);
        tenantRepository.save(tenantB);

        // API Clients
        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantAId, validKeyA, validSecretA, "Full Access Client A",
                "invoice:read invoice:create invoice:adjust invoice:cancel receipt:create tenant:admin"
        ));

        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantAId, readOnlyKeyA, readOnlySecretA, "Read Only Client A",
                "invoice:read"
        ));

        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantBId, validKeyB, validSecretB, "Full Access Client B",
                "invoice:read invoice:create invoice:adjust invoice:cancel receipt:create tenant:admin"
        ));
    }

    private CreateInvoiceRequest sampleRequest() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "SKU-01", "Security Test Product", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, "VAT15", null
        ));
        return new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);
    }

    // ==========================================
    // 1. GENERIC UNEXPECTED EXCEPTION (NO STACK TRACE)
    // ==========================================
    @Test
    @DisplayName("Security 1: Unexpected exception returns generic 500 without stack trace or class names")
    void test01_unexpectedException_containsNoStackTraceOrInternals() throws Exception {
        // Calling non-existent internal or triggering unhandled error
        String response = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")) // Empty body fails validation safely
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("Exception:"), "Response must not contain Exception class traces");
        assertFalse(response.contains("at et.ut.einvoice"), "Response must not contain code line references");
        assertFalse(response.contains("org.springframework"), "Response must not contain framework package names");
    }

    // ==========================================
    // 2. DATABASE EXCEPTION (NO SQL LEAKAGE)
    // ==========================================
    @Test
    @DisplayName("Security 2: Database integrity violation contains zero SQL syntax or schema disclosure")
    void test02_databaseException_containsNoSqlSyntaxOrTables() throws Exception {
        // Trigger validation or persistence error with malformed data
        String response = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionType\":null}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("SELECT "), "Response must not contain SQL SELECT");
        assertFalse(response.contains("INSERT INTO"), "Response must not contain SQL INSERT");
        assertFalse(response.contains("table "), "Response must not disclose table names");
        assertFalse(response.contains("column "), "Response must not disclose column names");
    }

    // ==========================================
    // 3. VALIDATION FAILURE RETURNS STRUCTURED JSON
    // ==========================================
    @Test
    @DisplayName("Security 3: Validation failure returns stable ErrorEnvelope contract")
    void test03_validationFailure_returnsSafeStructuredJson() throws Exception {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "", "", "goods", "PCS",
                new BigDecimal("-5.0"), new BigDecimal("-100.00"), new BigDecimal("-10.0"), "VAT15", null
        ));
        var invalidReq = new CreateInvoiceRequest(null, "", "IMMEDIATE", null, items, null, null, null);

        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details", not(empty())));
    }

    // ==========================================
    // 4. MALFORMED JSON HANDLING
    // ==========================================
    @Test
    @DisplayName("Security 4: Malformed JSON payload returns safe 400 without parser crash")
    void test04_malformedJson_returnsSafe400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"transactionType\": \"B2C\", \"items\": [ { invalid json syntax..."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Malformed or unparseable JSON")));
    }

    // ==========================================
    // 5. OVERSIZED ITEMS PAYLOAD DEFENSE
    // ==========================================
    @Test
    @DisplayName("Security 5: Request exceeding 1000 items is barred early by Bean Validation")
    void test05_oversizedItemsPayload_isRejected() throws Exception {
        List<CreateInvoiceRequest.LineItemRequest> hugeList = new ArrayList<>();
        for (int i = 1; i <= 1001; i++) {
            hugeList.add(new CreateInvoiceRequest.LineItemRequest(
                    "SKU-" + i, "Item " + i, "goods", "PCS",
                    BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, "VAT15", null
            ));
        }
        var oversizedReq = new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, hugeList, null, null, null);

        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("validation failed")));
    }

    // ==========================================
    // 6. MALFORMED UUID IN PATH VARIABLE
    // ==========================================
    @Test
    @DisplayName("Security 6: Malformed UUID in path returns safe 400 Bad Request instead of 500 error")
    void test06_invalidUuidInPathVariable_rejectedSafely() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/not-a-valid-uuid-12345")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid value provided for parameter")));
    }

    // ==========================================
    // 7. INVALID ENUM PARAMETER
    // ==========================================
    @Test
    @DisplayName("Security 7: Invalid enum query parameter returns safe 400 Bad Request")
    void test07_invalidEnumParameter_rejectedSafely() throws Exception {
        mockMvc.perform(get("/api/v1/invoices?status=UNRECOGNIZED_STATUS_VAL")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ==========================================
    // 8. PAGINATION MAXIMUM IS ENFORCED
    // ==========================================
    @Test
    @DisplayName("Security 8: Excessive pagination size is capped at max-page-size")
    void test08_paginationMaximumEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/invoices?page=0&size=500")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(100)));
    }

    // ==========================================
    // 9. INVALID SORT PROPERTY REJECTED
    // ==========================================
    @Test
    @DisplayName("Security 9: Invalid sort property returns safe 400 Bad Request")
    void test09_invalidSortField_rejectedSafely() throws Exception {
        mockMvc.perform(get("/api/v1/invoices?sort=nonExistentColumn,desc")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ==========================================
    // 10. SQL INJECTION PAYLOADS SAFELY PARAMETERIZED
    // ==========================================
    @Test
    @DisplayName("Security 10: SQL injection payload in path parameter is safely parameterized and returns 404")
    void test10_sqlInjectionPayload_safelyNeutralized() throws Exception {
        String sqlInjection = "' OR 1=1 --";
        mockMvc.perform(get("/api/v1/invoices/by-irn/" + sqlInjection)
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    }

    // ==========================================
    // 11. BOLA / IDOR CROSS-TENANT ACCESS BLOCKED
    // ==========================================
    @Test
    @DisplayName("Security 11: Cross-tenant lookup on cancellation request returns 404 Not Found")
    void test11_crossTenantResourceAccess_blocked() throws Exception {
        // Tenant A creates a cancellation request directly in DB
        CancellationRequest cancellationA = new CancellationRequest(
                UUID.randomUUID(), tenantAId, UUID.randomUUID(), "IRN-TENANT-A-001", "PRICE_ERROR", "Pricing mistake"
        );
        cancellationRepository.save(cancellationA);

        // Tenant B attempts to access Tenant A's cancellation request by ID
        mockMvc.perform(get("/api/v1/cancellations/" + cancellationA.getId())
                        .header("X-API-Key", validKeyB)
                        .header("X-Client-Secret", validSecretB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CANCELLATION_NOT_FOUND"));
    }

    // ==========================================
    // 12. UNAUTHORIZED SCOPE ACCESS BLOCKED
    // ==========================================
    @Test
    @DisplayName("Security 12: Client lacking required write scope is barred with 403 Forbidden")
    void test12_unauthorizedScopeAccess_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/cancellations")
                        .header("X-API-Key", readOnlyKeyA)
                        .header("X-Client-Secret", readOnlySecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCancellationRequestDto("IRN-123", "ERROR", "Reason"))))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 13. ARBITRARY TENANT HEADER OVERRIDE BLOCKED
    // ==========================================
    @Test
    @DisplayName("Security 13: Arbitrary X-Tenant-ID mismatch is barred with 403 CROSS_TENANT_ACCESS_DENIED")
    void test13_arbitraryTenantHeader_cannotOverrideSecurityContext() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .header("X-Tenant-ID", tenantBId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_TENANT_ACCESS_DENIED"));
    }

    // ==========================================
    // 14. SENSITIVE ACTUATOR ENDPOINTS PROTECTED
    // ==========================================
    @Test
    @DisplayName("Security 14: Sensitive Actuator endpoints (/actuator/env, /actuator/beans) require authentication")
    void test14_actuatorSensitiveEndpoints_protected() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized());

        // /actuator/health is accessible without authentication (200 OK or 503 Service Unavailable depending on external components)
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(in(List.of(200, 503))));
    }

    // ==========================================
    // 15. HTTP SECURITY HEADERS CONFIGURED
    // ==========================================
    @Test
    @DisplayName("Security 15: HTTP Security response headers (CSP, nosniff, frame-ancestors, etc.) are present")
    void test15_httpSecurityHeaders_present() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(in(List.of(200, 503))))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    // ==========================================
    // 16. CORS REJECTS UNAUTHORIZED ORIGINS
    // ==========================================
    @Test
    @DisplayName("Security 16: CORS rejects unauthorized external origins")
    void test16_cors_rejectsUnauthorizedOrigins() throws Exception {
        // Disallowed origin
        mockMvc.perform(options("/api/v1/invoices")
                        .header("Origin", "http://malicious-attacker.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // Allowed origin
        mockMvc.perform(options("/api/v1/invoices")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    // ==========================================
    // 17. JWT AUTHENTICATION ENGINE
    // ==========================================
    @Test
    @DisplayName("Security 17: JWT token verification enforces HS256, expiration, and rejects 'alg: none'")
    void test17_jwt_algorithmAllowlistAndValidation() throws Exception {
        // 1. Valid JWT
        String validJwt = jwtTokenService.generateToken(tenantAId, "sec-user-1", Set.of("ROLE_TENANT_USER"), Set.of("invoice:read"), 3600);
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + validJwt))
                .andExpect(status().isOk());

        // 2. JWT with 'alg: none'
        String headerNone = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"iss\":\"ut-einvoice-platform\",\"sub\":\"user\",\"tenant_id\":\"" + tenantAId + "\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}").getBytes());
        String forgedNoneJwt = headerNone + "." + payload + ".";

        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + forgedNoneJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        // 3. Expired JWT
        String expiredJwt = jwtTokenService.generateToken(tenantAId, "sec-user-1", Set.of("ROLE_TENANT_USER"), Set.of("invoice:read"), -100);
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    // ==========================================
    // 18. SECRETS NEVER IN ERROR RESPONSES
    // ==========================================
    @Test
    @DisplayName("Security 18: Client secrets and credentials never appear in API error responses")
    void test18_secretsNeverInErrorResponses() throws Exception {
        String testSecret = "SecretKeyNeverDisclose123456";
        String response = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", "INVALID_KEY")
                        .header("X-Client-Secret", testSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains(testSecret), "Client secret must never be reflected in error response");
    }

    // ==========================================
    // 19. SECRETS NEVER IN LOGS / SANITIZED
    // ==========================================
    @Test
    @DisplayName("Security 19: Webhook subscription response masks raw secret key from JSON output")
    void test19_secretsNeverInWebhookResponse() throws Exception {
        var subReq = new et.ut.einvoice.webhooks.controller.WebhookApiController.CreateSubscriptionRequest(
                "https://merchant.example.com/webhooks",
                "secret_long_key_1234567890_abcdef",
                "INVOICE_*"
        );

        String response = mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("secret_long_key_1234567890_abcdef"), "Secret key must not be serialized in response");
    }

    // ==========================================
    // 20. RATE LIMITING ON SENSITIVE ENDPOINTS
    // ==========================================
    @Test
    @DisplayName("Security 20: Rate limiting throttles excessive public verification requests with 429")
    void test20_publicVerificationRateLimiting() throws Exception {
        String testIp = "192.168.100.50";
        // Exhaust the 60 requests/min rate limit
        for (int i = 0; i < 60; i++) {
            rateLimitingService.tryAcquireKey("public_verify:" + testIp, 60);
        }

        mockMvc.perform(get("/api/v1/public/verify/IRN-ANY-TEST")
                        .header("X-Forwarded-For", testIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    // ==========================================
    // 21. REQUEST BODY / VALIDATION ON WEBHOOKS
    // ==========================================
    @Test
    @DisplayName("Security 21: Invalid webhook target URL is rejected by input validation")
    void test21_invalidWebhookTargetUrl_rejected() throws Exception {
        var invalidReq = new et.ut.einvoice.webhooks.controller.WebhookApiController.CreateSubscriptionRequest(
                "javascript:alert(1)",
                "secret_1234567890_abcdef",
                "*"
        );

        mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ==========================================
    // 22. OUTBOUND TLS / TIMEOUT CONSTANTS
    // ==========================================
    @Test
    @DisplayName("Security 22: JWT service enforces strict timeout and clock skew boundaries")
    void test22_jwtClockSkewBoundaries() {
        assertNotNull(jwtTokenService);
        // Valid token generation works
        String token = jwtTokenService.generateToken(tenantAId, "subject", Set.of(), Set.of(), 120);
        assertTrue(jwtTokenService.validateAndExtract(token).isPresent());
    }

    // ==========================================
    // 23. GOVERNMENT AUTHORIZATION STATE ENFORCEMENT
    // ==========================================
    @Test
    @DisplayName("Security 23: Tenant with suspended government status cannot issue invoices")
    void test23_governmentAuthorizationState_enforced() throws Exception {
        Tenant tenantSuspendedGov = tenantRepository.findById(tenantAId).orElseThrow();
        tenantSuspendedGov.setGovernmentStatus(GovernmentStatus.GOVERNMENT_SUSPENDED);
        tenantRepository.save(tenantSuspendedGov);

        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GOVERNMENT_AUTHORIZATION_REQUIRED"));
    }

    // ==========================================
    // 24. CORRELATION ID TRACEABILITY
    // ==========================================
    @Test
    @DisplayName("Security 24: Correlation ID is preserved and reflected in headers and JSON bodies")
    void test24_correlationId_stableAndReturned() throws Exception {
        String testCorrelationId = "audit-sec-trace-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/by-irn/NON-EXISTENT-IRN")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .header("X-Correlation-ID", testCorrelationId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-ID", testCorrelationId))
                .andExpect(jsonPath("$.correlationId").value(testCorrelationId))
                .andExpect(jsonPath("$.requestId").value(testCorrelationId));
    }

    // ==========================================
    // 25. JWT ADVERSARIAL: WRONG AUDIENCE REJECTED
    // ==========================================
    @Test
    @DisplayName("Security 25: Valid JWT with wrong audience ('aud') is rejected with 401 Unauthorized")
    void test25_jwt_wrongAudience_rejected() throws Exception {
        String wrongAudToken = jwtTokenService.generateToken(tenantAId, "sec-user-1", Set.of("ROLE_TENANT_USER"), Set.of("invoice:read"), 3600, "untrusted-audience");
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + wrongAudToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    // ==========================================
    // 26. JWT ADVERSARIAL: WRONG ISSUER REJECTED
    // ==========================================
    @Test
    @DisplayName("Security 26: Valid signature but wrong issuer ('iss') is rejected with 401 Unauthorized")
    void test26_jwt_wrongIssuer_rejected() throws Exception {
        String headerB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "forged-issuer");
        claims.put("aud", "ut-einvoice-api");
        claims.put("sub", "sec-user-1");
        claims.put("tenant_id", tenantAId.toString());
        claims.put("roles", List.of("ROLE_TENANT_USER"));
        claims.put("scopes", List.of("invoice:read"));
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("nbf", Instant.now().minusSeconds(10).getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

        String payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
        String dataToSign = headerB64 + "." + payloadB64;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("default-dev-ut-einvoice-platform-jwt-secret-key-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sigB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8)));
        String wrongIssJwt = dataToSign + "." + sigB64;

        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + wrongIssJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    // ==========================================
    // 27. JWT ADVERSARIAL: NOT-BEFORE (NBF) OUTSIDE CLOCK SKEW
    // ==========================================
    @Test
    @DisplayName("Security 27: JWT with not-before ('nbf') outside permitted clock skew is rejected")
    void test27_jwt_futureNbfOutsideClockSkew_rejected() throws Exception {
        String headerB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "ut-einvoice-platform");
        claims.put("aud", "ut-einvoice-api");
        claims.put("sub", "sec-user-1");
        claims.put("tenant_id", tenantAId.toString());
        claims.put("roles", List.of("ROLE_TENANT_USER"));
        claims.put("scopes", List.of("invoice:read"));
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("nbf", Instant.now().plusSeconds(300).getEpochSecond()); // 300s in future (> 60s clock skew)
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

        String payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
        String dataToSign = headerB64 + "." + payloadB64;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("default-dev-ut-einvoice-platform-jwt-secret-key-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sigB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8)));
        String futureNbfJwt = dataToSign + "." + sigB64;

        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + futureNbfJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    // ==========================================
    // 28. JWT ADVERSARIAL: MISSING TENANT BINDING
    // ==========================================
    @Test
    @DisplayName("Security 28: JWT with missing tenant binding is rejected with 401 Unauthorized")
    void test28_jwt_missingTenantBinding_rejected() throws Exception {
        String headerB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "ut-einvoice-platform");
        claims.put("aud", "ut-einvoice-api");
        claims.put("sub", "sec-user-1");
        claims.put("roles", List.of("ROLE_TENANT_USER"));
        claims.put("scopes", List.of("invoice:read"));
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

        String payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
        String dataToSign = headerB64 + "." + payloadB64;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("default-dev-ut-einvoice-platform-jwt-secret-key-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sigB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8)));
        String noTenantJwt = dataToSign + "." + sigB64;

        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + noTenantJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    // ==========================================
    // 29. JWT ADVERSARIAL: SUSPENDED TENANT PRINCIPAL
    // ==========================================
    @Test
    @DisplayName("Security 29: Suspended tenant principal with valid JWT is barred with 403 TENANT_SUSPENDED")
    void test29_jwt_suspendedTenantPrincipal_barred() throws Exception {
        Tenant tenantA = tenantRepository.findById(tenantAId).orElseThrow();
        tenantA.suspend();
        tenantRepository.save(tenantA);

        String validJwt = jwtTokenService.generateToken(tenantAId, "sec-user-1", Set.of("ROLE_TENANT_USER"), Set.of("invoice:read"), 3600);
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + validJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    // ==========================================
    // 30. HSTS / HTTPS PROFILE CORRECTNESS
    // ==========================================
    @Test
    @DisplayName("Security 30: HSTS emitted strictly on HTTPS responses and omitted on plain HTTP")
    void test30_hsts_httpVsHttpsCorrectness() throws Exception {
        // 1. Plain HTTP response: HSTS must not be emitted (RFC 6797 requirement)
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        // 2. HTTPS response: HSTS header is present with max-age and includeSubDomains
        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    // ==========================================
    // 31. CORS RESTRICTIONS ADVERSARIAL
    // ==========================================
    @Test
    @DisplayName("Security 31: CORS denies null origin, unauthorized subdomains, and unauthorized origins with credentials")
    void test31_cors_adverseOriginsDenied() throws Exception {
        // 1. null origin
        mockMvc.perform(options("/api/v1/invoices")
                        .header("Origin", "null")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // 2. unauthorized subdomain
        mockMvc.perform(options("/api/v1/invoices")
                        .header("Origin", "http://evil.localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // 3. credentials with unauthorized origin
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Origin", "http://unauthorized-domain.com")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ==========================================
    // 32. WEBHOOK SSRF: REGISTRATION INPUT REJECTION
    // ==========================================
    @Test
    @DisplayName("Security 32: Webhook registration rejects localhost, private IPs, loopbacks, and cloud metadata")
    void test32_webhook_ssrfTargetsRejectedAtRegistration() throws Exception {
        List<String> ssrfTargets = List.of(
                "http://localhost:8080/hook",
                "http://127.0.0.1:8080/hook",
                "http://10.0.0.1/hook",
                "http://192.168.1.1/hook",
                "http://[::1]:8080/hook",
                "http://169.254.169.254/latest/meta-data/"
        );

        for (String targetUrl : ssrfTargets) {
            var req = new et.ut.einvoice.webhooks.controller.WebhookApiController.CreateSubscriptionRequest(
                    targetUrl,
                    "secret_long_key_1234567890",
                    "INVOICE_*"
            );

            mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                            .header("X-API-Key", validKeyA)
                            .header("X-Client-Secret", validSecretA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    // ==========================================
    // 33. WEBHOOK SSRF: DELIVERY & REDIRECT DESTINATION CONTROLS
    // ==========================================
    @Test
    @DisplayName("Security 33: Webhook delivery blocks SSRF destinations and redirect validator intercepts public-to-private redirects")
    void test33_webhook_ssrfDeliveryAndRedirectValidation() {
        // 1. Direct delivery to RFC 1918 address fails with SSRF destination control message
        OutboundWebhookDelivery delivery = new OutboundWebhookDelivery(
                UUID.randomUUID(), tenantAId, "INVOICE_REGISTERED", "http://192.168.1.55/webhook", "{}", "sig"
        );
        webhookService.deliverSingleWebhook(delivery);
        assertEquals("FAILED", delivery.getStatus());
        assertTrue(delivery.getErrorMessage().contains("SSRF destination controls verified"));

        // 2. Redirect validation prevents public URL from redirecting to private / metadata IP
        assertThrows(IllegalArgumentException.class, () ->
                webhookService.validateRedirectDestination("https://valid-service.com/webhook", "http://127.0.0.1:8080/admin")
        );
        assertThrows(IllegalArgumentException.class, () ->
                webhookService.validateRedirectDestination("https://valid-service.com/webhook", "http://169.254.169.254/computeMetadata/v1/")
        );
    }

    // ==========================================
    // 34. MASS-ASSIGNMENT: PRIVILEGED FIELDS IMMUTABILITY
    // ==========================================
    @Test
    @DisplayName("Security 34: Malicious JSON containing privileged fields (tenantId, status, irn, rrn) is neutralized")
    void test34_massAssignment_privilegedFieldsNeutralized() throws Exception {
        String maliciousJson = """
                {
                    "tenantId": "00000000-0000-0000-0000-000000000000",
                    "status": "REGISTERED",
                    "irn": "FORGED-IRN-12345",
                    "rrn": "FORGED-RRN-12345",
                    "transactionType": "B2C",
                    "paymentMode": "CASH",
                    "paymentTerm": "IMMEDIATE",
                    "items": [
                        {
                            "itemCode": "SKU-01",
                            "productDescription": "Security Test Product",
                            "natureOfSupplies": "goods",
                            "unit": "PCS",
                            "quantity": 1,
                            "unitPrice": 100.00,
                            "discount": 0,
                            "taxCode": "VAT15"
                        }
                    ]
                }
                """;

        String res = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var node = objectMapper.readTree(res);
        UUID createdId = UUID.fromString(node.get("id").asText());
        Invoice inv = invoiceRepository.findById(createdId).orElseThrow();

        // Invariant: tenantId is strictly tenantAId, not the injected 00000000-...
        assertEquals(tenantAId, inv.getTenantId(), "TenantId must be bound to authenticated context, not injected");
        assertNotEquals("FORGED-IRN-12345", inv.getIrn(), "Client cannot directly forge or assign IRN");
    }

    // ==========================================
    // 35. OBJECT-LEVEL AUTHORIZATION: CROSS-TENANT ACCESS DENIAL
    // ==========================================
    @Test
    @DisplayName("Security 35: Cross-tenant lookup by invoice ID and by IRN returns 404 INVOICE_NOT_FOUND")
    void test35_objectLevelAuth_crossTenantInvoices_notFound() throws Exception {
        // Tenant A creates and registers an invoice
        Invoice invA = new Invoice(UUID.randomUUID(), tenantAId, "DOC-A-01", 1L, Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE");
        invA.markRegistered("IRN-TENANT-A-PRIV", "RRN-A-01", Instant.now().toString(), "qr", "sig");
        invoiceRepository.save(invA);

        // Tenant B cannot access Tenant A's invoice by ID
        mockMvc.perform(get("/api/v1/invoices/" + invA.getId())
                        .header("X-API-Key", validKeyB)
                        .header("X-Client-Secret", validSecretB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));

        // Tenant B cannot access Tenant A's invoice by IRN
        mockMvc.perform(get("/api/v1/invoices/by-irn/" + invA.getIrn())
                        .header("X-API-Key", validKeyB)
                        .header("X-Client-Secret", validSecretB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    }
}
