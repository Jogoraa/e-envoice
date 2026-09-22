package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.bootstrap.PlatformBootstrapService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.dto.PlatformAuthResponse;
import et.ut.einvoice.platform.security.dto.PlatformLoginRequest;
import et.ut.einvoice.platform.security.dto.TenantAuthResponse;
import et.ut.einvoice.platform.security.dto.TenantLoginRequest;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import et.ut.einvoice.platform.security.service.PlatformAuthService;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantUser;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.SubscriptionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.repository.TenantUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PlatformAuthAndBootstrapSecurityTest {

    private TenantRepository tenantRepository;
    private TenantUserRepository tenantUserRepository;
    private PlatformUserRepository platformUserRepository;
    private SubscriptionRepository subscriptionRepository;
    private TaxpayerProfileRepository taxpayerProfileRepository;
    private ApiClientRepository apiClientRepository;
    private et.ut.einvoice.catalog.repository.CategoryRepository categoryRepository;
    private et.ut.einvoice.catalog.repository.ProductRepository productRepository;
    private et.ut.einvoice.catalog.repository.ServiceItemRepository serviceItemRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService jwtTokenService;
    private JdbcTemplate jdbcTemplate;

    private PlatformAuthService authService;
    private PlatformBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        tenantUserRepository = mock(TenantUserRepository.class);
        platformUserRepository = mock(PlatformUserRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        taxpayerProfileRepository = mock(TaxpayerProfileRepository.class);
        apiClientRepository = mock(ApiClientRepository.class);
        categoryRepository = mock(et.ut.einvoice.catalog.repository.CategoryRepository.class);
        productRepository = mock(et.ut.einvoice.catalog.repository.ProductRepository.class);
        serviceItemRepository = mock(et.ut.einvoice.catalog.repository.ServiceItemRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenService = new JwtTokenService(
                "super-secure-production-jwt-test-key-minimum-256-bits-length-guaranteed!",
                "ut-einvoice-platform",
                "ut-invoice-tenant",
                new ObjectMapper()
        );

        authService = new PlatformAuthService(
                tenantRepository,
                tenantUserRepository,
                platformUserRepository,
                passwordEncoder,
                jwtTokenService
        );

        bootstrapService = new PlatformBootstrapService(
                platformUserRepository,
                tenantRepository,
                tenantUserRepository,
                subscriptionRepository,
                taxpayerProfileRepository,
                apiClientRepository,
                categoryRepository,
                productRepository,
                serviceItemRepository,
                passwordEncoder,
                jdbcTemplate
        );
    }

    @Test
    @DisplayName("Password Generator produces strong, non-trivial passwords meeting entropy standards")
    void testPasswordGeneratorSecurity() {
        for (int i = 0; i < 50; i++) {
            String pass = PlatformBootstrapService.generateSecurePassword(20);
            assertEquals(20, pass.length());
            assertTrue(pass.chars().anyMatch(Character::isLowerCase), "Must contain lowercase");
            assertTrue(pass.chars().anyMatch(Character::isUpperCase), "Must contain uppercase");
            assertTrue(pass.chars().anyMatch(Character::isDigit), "Must contain digit");
            assertTrue(pass.chars().anyMatch(c -> "!@#$%^&*()-_=+".indexOf(c) >= 0), "Must contain special char");
        }
    }

    @Test
    @DisplayName("Platform Master Operator Authentication succeeds with correct credentials and returns master JWT")
    void testPlatformMasterAuthSuccess() {
        String rawPassword = "StrongMasterRandomPassword2026!#";
        String encodedHash = passwordEncoder.encode(rawPassword);

        PlatformUser master = new PlatformUser(
                UUID.randomUUID(),
                "platform.admin",
                "admin@ut-invoice.internal",
                encodedHash,
                "Master Admin",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );

        when(platformUserRepository.findByUsernameOrEmail("platform.admin", "platform.admin"))
                .thenReturn(Optional.of(master));

        PlatformLoginRequest request = new PlatformLoginRequest("platform.admin", rawPassword, null);
        PlatformAuthResponse response = authService.authenticatePlatformOperator(request);

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("platform.admin", response.username());
        assertEquals("ROLE_PLATFORM_ADMIN", response.role());
        assertTrue(response.scopes().contains("platform:superadmin"));

        // Validate JWT token claims
        var claimsOpt = jwtTokenService.validateAndExtract(response.accessToken());
        assertTrue(claimsOpt.isPresent());
        assertTrue(claimsOpt.get().isMasterToken());
        assertTrue(claimsOpt.get().roles().contains("ROLE_PLATFORM_ADMIN"));
    }

    @Test
    @DisplayName("Platform Master Operator Authentication fails with invalid password")
    void testPlatformMasterAuthBadPassword() {
        String encodedHash = passwordEncoder.encode("CorrectPassword123!");
        PlatformUser master = new PlatformUser(
                UUID.randomUUID(), "platform.admin", "admin@ut-invoice.internal", encodedHash, "Admin", "ROLE_PLATFORM_ADMIN", "ACTIVE", Instant.now()
        );
        when(platformUserRepository.findByUsernameOrEmail("platform.admin", "platform.admin"))
                .thenReturn(Optional.of(master));

        PlatformLoginRequest request = new PlatformLoginRequest("platform.admin", "WrongPassword!", null);
        assertThrows(BadCredentialsException.class, () -> authService.authenticatePlatformOperator(request));
    }

    @Test
    @DisplayName("Tenant User Authentication succeeds with valid TIN, username, and password")
    void testTenantUserAuthSuccess() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant(tenantId, "ORG-01", "Selam Trading PLC", "Selam Store", "0011223344", "SME");
        tenant.activate();

        String rawPass = "TenantSecretPass2026!";
        String passHash = passwordEncoder.encode(rawPass);
        TenantUser user = new TenantUser(
                UUID.randomUUID(), tenantId, "cashier.01", passHash, "cashier@selam.et", null, "Selam Cashier", "ROLE_TENANT_USER", "ACTIVE", Instant.now()
        );

        when(tenantRepository.findByTin("0011223344")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByTenantIdAndUsername(tenantId, "cashier.01")).thenReturn(Optional.of(user));

        TenantLoginRequest request = new TenantLoginRequest("0011223344", "cashier.01", rawPass, null, null);
        TenantAuthResponse response = authService.authenticateTenantUser(request);

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertEquals(tenantId, response.tenantId());
        assertEquals("0011223344", response.tin());
        assertEquals("cashier.01", response.username());

        // Validate JWT token claims
        var claimsOpt = jwtTokenService.validateAndExtract(response.accessToken());
        assertTrue(claimsOpt.isPresent());
        assertEquals(tenantId, claimsOpt.get().tenantId());
        assertTrue(claimsOpt.get().isTenantToken());
        assertTrue(claimsOpt.get().roles().contains("ROLE_TENANT_USER"));
    }

    @Test
    @DisplayName("Tenant User Authentication fails if Tenant is suspended")
    void testTenantSuspendedRejectsLogin() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant(tenantId, "ORG-01", "Suspended PLC", "Suspended", "0099887766", "SME");
        tenant.suspend();

        when(tenantRepository.findByTin("0099887766")).thenReturn(Optional.of(tenant));

        TenantLoginRequest request = new TenantLoginRequest("0099887766", "admin", "anyPass", null, null);
        assertThrows(DisabledException.class, () -> authService.authenticateTenantUser(request));
    }

    @Test
    @DisplayName("PlatformBootstrapService creates accounts and generates secure passwords idempotently")
    void testBootstrapExecution() {
        when(platformUserRepository.existsByRole("ROLE_PLATFORM_ADMIN")).thenReturn(false);
        when(tenantRepository.findByTin("0011223344")).thenReturn(Optional.empty());

        bootstrapService.run();

        // Verify Platform User created & saved
        verify(platformUserRepository, times(1)).save(any(PlatformUser.class));
        // Verify Tenant created & saved
        verify(tenantRepository, times(1)).save(any(Tenant.class));
        // Verify Tenant User created & saved
        verify(tenantUserRepository, times(1)).save(any(TenantUser.class));
        // Verify Subscription created & saved
        verify(subscriptionRepository, times(1)).save(any());
        // Verify ApiClient created & saved
        verify(apiClientRepository, times(1)).save(any());
    }
}
