package et.ut.einvoice.tenancy.controller;

import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.tenancy.domain.Subscription;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantUser;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.SubscriptionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.repository.TenantUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SaasMasterDataControllerTest {

    private TenantRepository tenantRepository;
    private SubscriptionRepository subscriptionRepository;
    private InvoiceRepository invoiceRepository;
    private AuditEventRepository auditEventRepository;
    private TenantUserRepository tenantUserRepository;
    private ApiClientRepository apiClientRepository;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;

    private SaasMasterDataController controller;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        auditEventRepository = mock(AuditEventRepository.class);
        tenantUserRepository = mock(TenantUserRepository.class);
        apiClientRepository = mock(ApiClientRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);

        controller = new SaasMasterDataController(
                tenantRepository,
                subscriptionRepository,
                invoiceRepository,
                auditEventRepository,
                tenantUserRepository,
                apiClientRepository,
                passwordEncoder,
                auditService
        );
    }

    @Test
    @DisplayName("GET /api/v1/saas/telemetry returns dynamic live counts from DB")
    void testGetSaasTelemetry() {
        Tenant t1 = new Tenant(UUID.randomUUID(), "ORG-1", "Company One PLC", "Company One", "0011223344");
        t1.activate();
        when(tenantRepository.findAll()).thenReturn(List.of(t1));

        Subscription s1 = new Subscription(UUID.randomUUID(), t1.getId(), "ENTERPRISE", "ANNUAL", 100000, 50, true, false, "ACTIVE");
        when(subscriptionRepository.findAll()).thenReturn(List.of(s1));

        when(invoiceRepository.findAll()).thenReturn(List.of());

        ResponseEntity<SaasMasterDataController.SaasTelemetryDto> response = controller.getSaasTelemetry();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = Objects.requireNonNull(response.getBody());
        assertEquals(1, body.totalTenants());
        assertEquals(1, body.activeTenants());
        assertEquals(1, body.planDistribution().get("ENTERPRISE"));
    }

    @Test
    @DisplayName("GET /api/v1/saas/usage returns real tenant quota consumption")
    void testGetSaasUsage() {
        Tenant t1 = new Tenant(UUID.randomUUID(), "ORG-1", "Company One PLC", "Company One", "0011223344");
        when(tenantRepository.findAll()).thenReturn(List.of(t1));

        Subscription s1 = new Subscription(UUID.randomUUID(), t1.getId(), "ENTERPRISE", "ANNUAL", 100000, 50, true, false, "ACTIVE");
        when(subscriptionRepository.findAll()).thenReturn(List.of(s1));

        ResponseEntity<List<SaasMasterDataController.TenantUsageDto>> response = controller.getSaasUsage();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = Objects.requireNonNull(response.getBody());
        assertEquals(1, body.size());
        assertEquals("0011223344", body.get(0).tin());
        assertEquals("ENTERPRISE", body.get(0).plan());
        assertEquals(100000, body.get(0).invoiceQuota());
    }

    @Test
    @DisplayName("POST /api/v1/saas/tenants/onboard creates real entities in database")
    void testOnboardTenantFull() {
        when(tenantRepository.findByTin("0099887766")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        SaasMasterDataController.FullOnboardTenantRequest request = new SaasMasterDataController.FullOnboardTenantRequest(
                new SaasMasterDataController.FullOnboardTenantRequest.TenantSection("0099887766", "New Tech Corp", "New Tech", "Addis LTO", true),
                new SaasMasterDataController.FullOnboardTenantRequest.BranchSection("HQ", "BR-01", "Addis Ababa", 5),
                new SaasMasterDataController.FullOnboardTenantRequest.SubscriptionSection("GROWTH", "ANNUAL"),
                new SaasMasterDataController.FullOnboardTenantRequest.AdminSection("John Doe", "john@newtech.et", "Secret123!", "ROLE_TENANT_ADMIN")
        );

        ResponseEntity<Map<String, Object>> response = controller.onboardTenantFull(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = Objects.requireNonNull(response.getBody());
        assertEquals("0099887766", body.get("tin"));
        assertEquals("ACTIVE", body.get("status"));

        verify(tenantRepository, times(1)).save(any(Tenant.class));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        verify(tenantUserRepository, times(1)).save(any(TenantUser.class));
    }

    @Test
    @DisplayName("GET /api/v1/master/telemetry returns live regulatory status")
    void testGetMasterTelemetry() {
        when(invoiceRepository.findAll()).thenReturn(List.of());

        ResponseEntity<SaasMasterDataController.MasterTelemetryDto> response = controller.getMasterTelemetry();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = Objects.requireNonNull(response.getBody());
        assertEquals("ONLINE", body.gatewayStatus());
        assertEquals("ONLINE", body.hsmStatus());
    }
}
