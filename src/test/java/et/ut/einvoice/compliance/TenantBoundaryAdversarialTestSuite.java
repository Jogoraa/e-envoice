package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.service.InvoiceService;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.GovernmentStatus;
import et.ut.einvoice.tenancy.domain.SubscriptionStatus;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adversarial test suite proving strict enforcement of tenant, subscription, and government boundaries
 * pursuant to Directive No. 1142/2018 EC (2026 GC) Art. 4(2), 15(6), 19(5), 20(3)(g), 23(2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Isolated H2 database: prevents accumulated invoice / invoice_line data from
// preceding suites (in the shared ut_test_db context) from skewing the
// grand-total calculation in testPublicVerificationEndpointOpenAccess.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:tenant_boundary_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
public class TenantBoundaryAdversarialTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        tenantRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantId = UUID.randomUUID();

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");
        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "IRN-" + UUID.randomUUID();
                    String rrn = "RRN-" + UUID.randomUUID();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice");
                });
    }

    private CreateInvoiceRequest createValidRequest() {
        return new CreateInvoiceRequest(
                TransactionType.B2C,
                "CASH",
                "IMMEDIATE",
                null,
                List.of(new CreateInvoiceRequest.LineItemRequest(
                        "ITEM-001",
                        "High Grade Teff",
                        "goods",
                        "KG",
                        new BigDecimal("2.00"),
                        new BigDecimal("150.00"),
                        BigDecimal.ZERO,
                        "VAT15",
                        BigDecimal.ZERO
                )),
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("Scenario 1: Fully authorized tenant (Active + Active Subscription + Active Government) succeeds")
    void testFullyAuthorizedTenantSucceeds() {
        Tenant tenant = new Tenant(tenantId, "ORG-001", "Abyssinia Trading PLC", "Abyssinia", "0012345678", "SME");
        tenant.activate();
        tenant.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_ACTIVE);
        tenant.setGovernmentStatus(GovernmentStatus.GOVERNMENT_ACTIVE);
        tenantRepository.save(tenant);

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantId, "0012345678", "VAT-12345", "Abyssinia Trading PLC", "Abyssinia",
                "14", "02", "+251911000111", "contact@abyssinia.et", "SYS-001", "ERP"
        ));

        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId, "CLIENT-001", Set.of("ROLE_TENANT_USER"), Set.of("invoice:create"), "corr-1"
        ));
        try {
            InvoiceResponseDto response = invoiceService.createAndRegisterInvoice(createValidRequest(), "IDEM-" + UUID.randomUUID());
            assertNotNull(response);
            assertNotNull(response.id());
            assertNotNull(response.irn());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Scenario 2: Suspended tenant is rejected with 403 TENANT_SUSPENDED")
    void testSuspendedTenantRejected() {
        Tenant tenant = new Tenant(tenantId, "ORG-002", "Suspended Trading PLC", "Suspended", "0022345678", "SME");
        tenant.suspend();
        tenant.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_ACTIVE);
        tenant.setGovernmentStatus(GovernmentStatus.GOVERNMENT_ACTIVE);
        tenantRepository.save(tenant);

        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId, "CLIENT-001", Set.of("ROLE_TENANT_USER"), Set.of("invoice:create"), "corr-2"
        ));
        try {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    invoiceService.createAndRegisterInvoice(createValidRequest(), "IDEM-" + UUID.randomUUID()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
            assertEquals("TENANT_SUSPENDED", ex.getCode());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Scenario 3: Expired/Suspended commercial subscription is rejected with 402 SUBSCRIPTION_SUSPENDED")
    void testSuspendedSubscriptionRejected() {
        Tenant tenant = new Tenant(tenantId, "ORG-003", "NonPaying Trading PLC", "NonPaying", "0032345678", "SME");
        tenant.activate();
        tenant.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_SUSPENDED);
        tenant.setGovernmentStatus(GovernmentStatus.GOVERNMENT_ACTIVE);
        tenantRepository.save(tenant);

        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId, "CLIENT-001", Set.of("ROLE_TENANT_USER"), Set.of("invoice:create"), "corr-3"
        ));
        try {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    invoiceService.createAndRegisterInvoice(createValidRequest(), "IDEM-" + UUID.randomUUID()));
            assertEquals(HttpStatus.PAYMENT_REQUIRED, ex.getHttpStatus());
            assertEquals("SUBSCRIPTION_SUSPENDED", ex.getCode());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Scenario 4: Active commercial subscription CANNOT bypass government authorization (403 GOVERNMENT_AUTHORIZATION_REQUIRED)")
    void testActiveSubscriptionCannotBypassGovernmentAuthorization() {
        Tenant tenant = new Tenant(tenantId, "ORG-004", "Paid But Unauthorized PLC", "Unauthorized", "0042345678", "SME");
        tenant.activate();
        // Fully paid SaaS subscription!
        tenant.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_ACTIVE);
        // BUT government accreditation/authorization is revoked or pending!
        tenant.setGovernmentStatus(GovernmentStatus.GOVERNMENT_REVOKED);
        tenantRepository.save(tenant);

        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId, "CLIENT-001", Set.of("ROLE_TENANT_USER"), Set.of("invoice:create"), "corr-4"
        ));
        try {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    invoiceService.createAndRegisterInvoice(createValidRequest(), "IDEM-" + UUID.randomUUID()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
            assertEquals("GOVERNMENT_AUTHORIZATION_REQUIRED", ex.getCode());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Scenario 5: Public verification endpoint returns verification metadata without authentication")
    void testPublicVerificationEndpointOpenAccess() throws Exception {
        Tenant tenant = new Tenant(tenantId, "ORG-PUB", "Public Verification PLC", "Public PLC", "0055555555", "SME");
        tenant.activate();
        tenantRepository.save(tenant);

        TaxpayerProfile profile = new TaxpayerProfile(
                tenantId, "0055555555", "VAT-55555", "Public Verification PLC", "Public PLC",
                "14", "05", "+251911555555", "pub@test.et", "SYS-PUB", "ERP"
        );
        taxpayerProfileRepository.save(profile);

        TenantContextHolder.setContext(TenantContext.createWithClient(
                tenantId, "CLIENT-001", Set.of("ROLE_TENANT_USER"), Set.of("invoice:create"), "corr-5"
        ));
        InvoiceResponseDto registered;
        try {
            registered = invoiceService.createAndRegisterInvoice(createValidRequest(), "IDEM-" + UUID.randomUUID());
        } finally {
            TenantContextHolder.clear();
        }

        assertNotNull(registered.irn());

        // Perform public verification call with NO credentials, NO API Key, NO headers
        mockMvc.perform(get("/api/v1/public/verify/" + registered.irn())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.irn").value(registered.irn()))
                .andExpect(jsonPath("$.sellerTin").value("0055555555"))
                .andExpect(jsonPath("$.sellerLegalName").value("Public Verification PLC"))
                .andExpect(jsonPath("$.verificationStatus").value("VALID_REGISTERED"))
                .andExpect(jsonPath("$.grandTotal").value(345.00))
                // Must NOT contain buyer PII or private tenant identifiers
                .andExpect(jsonPath("$.buyer").doesNotExist())
                .andExpect(jsonPath("$.buyerPhone").doesNotExist())
                .andExpect(jsonPath("$.buyerEmail").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    @DisplayName("Scenario 6: Public verification endpoint returns 404 for unknown IRN")
    void testPublicVerificationNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/verify/IRN-NON-EXISTENT-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INVOICE_NOT_FOUND"));
    }
}
