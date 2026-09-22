package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.tenancy.domain.ApiClient;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiSecurityAdversarialTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantAId;
    private UUID tenantBId;

    private final String validKeyA = "KEY_TENANT_A_001";
    private final String validSecretA = "secret_a_12345678";

    private final String readOnlyKeyA = "KEY_TENANT_A_READONLY";
    private final String readOnlySecretA = "secret_ro_12345678";

    private final String suspendedClientKey = "KEY_SUSPENDED_CLIENT";
    private final String suspendedClientSecret = "secret_susp_1234";

    private final String suspendedTenantKey = "KEY_SUSPENDED_TENANT";
    private final String suspendedTenantSecret = "secret_ten_susp";

    @BeforeEach
    void setUp() {
        apiClientRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();

        // 1. Active Tenant A
        Tenant tenantA = new Tenant(tenantAId, "ORG-A", "Tenant A PLC", "Tenant A", "0011223344", "SME");
        tenantA.activate();
        tenantRepository.save(tenantA);

        // 2. Active Tenant B
        Tenant tenantB = new Tenant(tenantBId, "ORG-B", "Tenant B PLC", "Tenant B", "0055667788", "SME");
        tenantB.activate();
        tenantRepository.save(tenantB);

        // 3. Suspended Tenant
        UUID suspendedTenantId = UUID.randomUUID();
        Tenant tenantSusp = new Tenant(suspendedTenantId, "ORG-SUSP", "Suspended Tenant PLC", "Suspended", "0099001122", "SME");
        tenantSusp.suspend();
        tenantRepository.save(tenantSusp);

        // API Clients
        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantAId, validKeyA, validSecretA, "Full Access Client A",
                "invoice:read invoice:create invoice:adjust invoice:cancel receipt:create tenant:admin"
        ));

        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantAId, readOnlyKeyA, readOnlySecretA, "Read Only Client A",
                "invoice:read"
        ));

        ApiClient revokedClient = new ApiClient(
                UUID.randomUUID(), tenantAId, suspendedClientKey, suspendedClientSecret, "Revoked Client",
                "invoice:read invoice:create"
        );
        revokedClient.suspend();
        apiClientRepository.save(revokedClient);

        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), suspendedTenantId, suspendedTenantKey, suspendedTenantSecret, "Client for Suspended Tenant",
                "invoice:read invoice:create"
        ));

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenReturn(GovernmentRegistrationProvider.GovernmentRegistrationResult.success(
                        "IRN-" + UUID.randomUUID(), "RRN-123", "2026-09-18T12:00:00Z", "qr", "sig"));
    }

    private CreateInvoiceRequest sampleRequest() {
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "SKU-01", "Standard Product", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, "VAT15", null
        ));
        return new CreateInvoiceRequest(TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null);
    }

    @Test
    @DisplayName("Security 1: Missing credentials is barred with 401 Unauthorized")
    void test_MissingCredentials_RejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Security 2: Invalid API Key is barred with 401 Unauthorized")
    void test_InvalidApiKey_RejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", "NON_EXISTENT_KEY")
                        .header("X-Client-Secret", "some_secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Security 3: Invalid Client Secret is barred with 401 Unauthorized")
    void test_InvalidClientSecret_RejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", "WRONG_SECRET_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Security 4: Suspended / Revoked Client is barred with 401 Unauthorized")
    void test_SuspendedClient_RejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", suspendedClientKey)
                        .header("X-Client-Secret", suspendedClientSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_REVOKED"));
    }

    @Test
    @DisplayName("Security 5: Client for Suspended Tenant is barred with 403 Forbidden")
    void test_SuspendedTenant_RejectedWith403() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", suspendedTenantKey)
                        .header("X-Client-Secret", suspendedTenantSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("Security 6: Forged X-Tenant-ID Mismatch is barred with 403 Forbidden")
    void test_ForgedTenantId_RejectedWith403() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .header("X-Tenant-ID", tenantBId.toString()) // Attempting to act on Tenant B
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_TENANT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Security 7: Insufficient Scope is barred with 403 Forbidden")
    void test_InsufficientScope_RejectedWith403() throws Exception {
        // Read-only client attempting write operation (POST /api/v1/invoices)
        mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", readOnlyKeyA)
                        .header("X-Client-Secret", readOnlySecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security 8: Cross-Tenant Resource Query returns empty or 404")
    void test_CrossTenant_ResourceAccess_Blocked() throws Exception {
        // First, Tenant A creates an invoice
        String responseStr = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", validKeyA)
                        .header("X-Client-Secret", validSecretA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String invoiceId = objectMapper.readTree(responseStr).get("id").asText();

        // Tenant B registers client and attempts to fetch Tenant A's invoice
        String validKeyB = "KEY_TENANT_B_001";
        String validSecretB = "secret_b_87654321";
        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantBId, validKeyB, validSecretB, "Tenant B Client",
                "invoice:read invoice:create"
        ));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("X-API-Key", validKeyB)
                        .header("X-Client-Secret", validSecretB))
                .andExpect(status().isNotFound());
    }
}
