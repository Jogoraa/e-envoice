package et.ut.einvoice.platform.config;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.config.controller.MasterEnvironmentController;
import et.ut.einvoice.platform.config.domain.*;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.*;
import et.ut.einvoice.platform.config.repository.ConfigurationEntryRepository;
import et.ut.einvoice.platform.config.repository.ConfigurationRevisionEntryRepository;
import et.ut.einvoice.platform.config.repository.ConfigurationRevisionRepository;
import et.ut.einvoice.platform.config.repository.PrivilegedConfigurationSessionRepository;
import et.ut.einvoice.platform.config.service.ConfigurationDefinitionRegistry;
import et.ut.einvoice.platform.config.service.EnvironmentConfigurationService;
import et.ut.einvoice.platform.config.service.MasterMfaOtpService;
import et.ut.einvoice.platform.config.service.MasterMessagingDiagnosticsService;
import et.ut.einvoice.platform.config.service.PrivilegedSessionService;
import et.ut.einvoice.platform.config.service.PrivilegedSessionService.PrivilegedSessionDto;
import et.ut.einvoice.platform.config.service.SecretEncryptionService;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.platform.security.SsrfValidator;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasterEnvironmentAndSecretsSecurityTest {

    private PrivilegedConfigurationSessionRepository sessionRepository;
    private ConfigurationEntryRepository entryRepository;
    private ConfigurationRevisionRepository revisionRepository;
    private ConfigurationRevisionEntryRepository revisionEntryRepository;
    private PlatformUserRepository platformUserRepository;
    private AuditService auditService;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService jwtTokenService;
    private SsrfValidator ssrfValidator;
    private SecretEncryptionService encryptionService;
    private ConfigurationDefinitionRegistry registry;
    private PrivilegedSessionService privilegedSessionService;
    private EnvironmentConfigurationService configService;
    private MasterEnvironmentController controller;

    private PlatformUser masterAdmin;
    private PlatformUser tenantAdmin;
    private String rawPassword = "StrongMasterPassword123!";
    private Map<String, ConfigurationEntry> dbEntries;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(PrivilegedConfigurationSessionRepository.class);
        entryRepository = mock(ConfigurationEntryRepository.class);
        revisionRepository = mock(ConfigurationRevisionRepository.class);
        revisionEntryRepository = mock(ConfigurationRevisionEntryRepository.class);
        platformUserRepository = mock(PlatformUserRepository.class);
        auditService = mock(AuditService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenService = new JwtTokenService(
                "test-secure-256-bit-long-secret-key-for-jwt-engine-testing-purpose",
                "ut-einvoice-platform",
                "ut-invoice-tenant",
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
        ssrfValidator = new SsrfValidator();
        encryptionService = new SecretEncryptionService(
                "test-super-secret-master-vault-encryption-key-32-bytes",
                "test-jwt-secret"
        );
        registry = new ConfigurationDefinitionRegistry();

        et.ut.einvoice.notifications.provider.SmsProvider smsProvider = mock(et.ut.einvoice.notifications.provider.SmsProvider.class);
        et.ut.einvoice.notifications.provider.EmailProvider emailProvider = mock(et.ut.einvoice.notifications.provider.EmailProvider.class);
        when(emailProvider.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(smsProvider.sendSms(anyString(), anyString())).thenReturn(true);

        MasterMfaOtpService mfaOtpService = new MasterMfaOtpService(smsProvider, emailProvider, auditService);

        privilegedSessionService = new PrivilegedSessionService(
                sessionRepository,
                platformUserRepository,
                passwordEncoder,
                jwtTokenService,
                auditService,
                mfaOtpService
        );

        configService = new EnvironmentConfigurationService(
                entryRepository,
                revisionRepository,
                revisionEntryRepository,
                registry,
                encryptionService,
                ssrfValidator,
                auditService
        );

        MasterMessagingDiagnosticsService messagingDiagnosticsService = mock(MasterMessagingDiagnosticsService.class);
        controller = new MasterEnvironmentController(privilegedSessionService, configService, messagingDiagnosticsService);

        // Seed users
        masterAdmin = new PlatformUser(
                UUID.randomUUID(),
                "platform.admin",
                "admin@utsolutionsplc.com",
                passwordEncoder.encode(rawPassword),
                "Lead Security Admin",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );

        tenantAdmin = new PlatformUser(
                UUID.randomUUID(),
                "tenant.admin",
                "tenant@abyssinia.et",
                passwordEncoder.encode(rawPassword),
                "Tenant General Admin",
                "ROLE_TENANT_ADMIN",
                "ACTIVE",
                Instant.now()
        );

        when(platformUserRepository.findByUsernameOrEmail(eq("platform.admin"), anyString()))
                .thenReturn(Optional.of(masterAdmin));
        when(platformUserRepository.findByUsernameOrEmail(eq("tenant.admin"), anyString()))
                .thenReturn(Optional.of(tenantAdmin));

        // Seed DB entries mock
        dbEntries = new HashMap<>();
        seedMockEntries();

        when(entryRepository.findAllByOrderByScopeAscKeyNameAsc()).thenAnswer(inv -> new ArrayList<>(dbEntries.values()));
        when(entryRepository.findByKeyName(anyString())).thenAnswer(inv -> Optional.ofNullable(dbEntries.get(inv.getArgument(0))));
        when(revisionRepository.findMaxRevisionNumber()).thenReturn(1L);
    }

    private void seedMockEntries() {
        ConfigurationEntry sms = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.SMS, "SMS_ENABLED", ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.HIGH, "true", false, true, false,
                "SMS dispatch switch", null, null, null, "SYSTEM"
        );
        dbEntries.put("SMS_ENABLED", sms);

        ConfigurationEntry smsBlocked = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.SMS, "SMS_LIVE_INTEGRATION_BLOCKED", ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.CRITICAL, "true", false, false, false,
                "GeezSMS block", null, null, null, "SYSTEM"
        );
        dbEntries.put("SMS_LIVE_INTEGRATION_BLOCKED", smsBlocked);

        ConfigurationEntry jwtSecret = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.AUTHENTICATION, "JWT_SECRET", ConfigurationValueType.SECRET,
                ConfigurationClassification.CRITICAL, null, true, true, false,
                "JWT signing secret", null, null, null, "SYSTEM"
        );
        jwtSecret.setEncryptedSecretPayload(encryptionService.encryptSecret("initial-super-secret-jwt-key"));
        jwtSecret.setSecretFingerprint(encryptionService.computeFingerprint("initial-super-secret-jwt-key"));
        dbEntries.put("JWT_SECRET", jwtSecret);

        ConfigurationEntry dbUrl = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.DATABASE, "DATABASE_URL", ConfigurationValueType.URL,
                ConfigurationClassification.CRITICAL, "jdbc:postgresql://localhost:5435/ut_einvoice_db",
                false, false, true, "JDBC URL", null, null, null, "SYSTEM"
        );
        dbEntries.put("DATABASE_URL", dbUrl);

        ConfigurationEntry poolSize = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.DATABASE, "DATABASE_POOL_SIZE", ConfigurationValueType.INTEGER,
                ConfigurationClassification.HIGH, "20", false, false, true,
                "Pool size", null, 5L, 100L, "SYSTEM"
        );
        dbEntries.put("DATABASE_POOL_SIZE", poolSize);

        ConfigurationEntry morUrl = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.MOEIRS, "MOR_GATEWAY_URL", ConfigurationValueType.URL,
                ConfigurationClassification.CRITICAL, "https://core.mor.gov.et",
                false, true, false, "MoR URL", null, null, null, "SYSTEM"
        );
        dbEntries.put("MOR_GATEWAY_URL", morUrl);

        ConfigurationEntry morKillSwitch = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.MOEIRS, "MOR_INTEGRATION_ENABLED", ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.CRITICAL, "true", false, true, false,
                "MoR kill switch", null, null, null, "SYSTEM"
        );
        dbEntries.put("MOR_INTEGRATION_ENABLED", morKillSwitch);

        ConfigurationEntry emailKillSwitch = new ConfigurationEntry(
                UUID.randomUUID(), ConfigurationScope.EMAIL, "EMAIL_DELIVERY_ENABLED", ConfigurationValueType.BOOLEAN,
                ConfigurationClassification.HIGH, "true", false, true, false,
                "Email kill switch", null, null, null, "SYSTEM"
        );
        dbEntries.put("EMAIL_DELIVERY_ENABLED", emailKillSwitch);
    }

    private PrivilegedSessionDto establishPrivilegedSession() {
        var res = privilegedSessionService.initiateStepUpCeremony(
                "platform.admin",
                rawPassword,
                "123456",
                "192.168.1.50",
                "Mozilla/5.0 TestBrowser",
                "CORR-STEPUP-001"
        );
        PrivilegedConfigurationSession session = new PrivilegedConfigurationSession(
                res.sessionId(),
                masterAdmin.getId(),
                masterAdmin.getUsername(),
                res.mfaVerifiedAt(),
                res.expiresAt(),
                "192.168.1.50",
                "Mozilla/5.0 TestBrowser"
        );
        when(sessionRepository.findById(eq(res.sessionId()))).thenReturn(Optional.of(session));
        return res;
    }

    @Test
    @DisplayName("1. Unauthenticated request without privileged token is rejected with 403 AccessDenied")
    void testUnauthenticatedAccessFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> controller.getAllConfigurations(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Privileged environment session token missing");
    }

    @Test
    @DisplayName("2. Tenant Admin cannot initiate step-up ceremony (role isolation)")
    void testTenantAdminCannotStepUp() {
        assertThatThrownBy(() -> privilegedSessionService.initiateStepUpCeremony(
                "tenant.admin",
                rawPassword,
                "123456",
                "10.0.0.1",
                "TestAgent",
                "CORR-002"
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only Master Platform Administrators have privilege");
    }

    @Test
    @DisplayName("3. Step-up MFA fails on invalid master password")
    void testStepUpFailsOnBadPassword() {
        assertThatThrownBy(() -> privilegedSessionService.initiateStepUpCeremony(
                "platform.admin",
                "WrongPassword!",
                "123456",
                "10.0.0.1",
                "TestAgent",
                "CORR-003"
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid master credentials");
    }

    @Test
    @DisplayName("4. Step-up MFA fails on invalid MFA code format")
    void testStepUpFailsOnBadMfaCode() {
        assertThatThrownBy(() -> privilegedSessionService.initiateStepUpCeremony(
                "platform.admin",
                rawPassword,
                "1234", // Not 6 digits
                "10.0.0.1",
                "TestAgent",
                "CORR-004"
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired MFA verification code");
    }

    @Test
    @DisplayName("5. Step-up MFA succeeds and issues short-lived privileged session")
    void testStepUpSuccess() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        assertThat(sessionDto.sessionId()).isNotNull();
        assertThat(sessionDto.privilegedToken()).isNotBlank();
        assertThat(sessionDto.durationSeconds()).isEqualTo(900L); // 15 minutes
        assertThat(sessionDto.username()).isEqualTo("platform.admin");
    }

    @Test
    @DisplayName("6. Plaintext secrets are NEVER returned in GET responses")
    void testSecretsAreMaskedInGetResponses() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        var response = controller.getAllConfigurations(request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        var items = response.getBody();
        assertThat(items).isNotEmpty();

        var jwtItem = items.stream().filter(i -> "JWT_SECRET".equals(i.keyName())).findFirst().orElseThrow();
        assertThat(jwtItem.isSecret()).isTrue();
        assertThat(jwtItem.currentValue()).isNull(); // NEVER plaintext
        assertThat(jwtItem.maskedValue()).isEqualTo("••••••••••••••••");
        assertThat(jwtItem.isConfigured()).isTrue();
        assertThat(jwtItem.secretFingerprint()).startsWith("HMAC:");
    }

    @Test
    @DisplayName("7. Expired privileged session cannot access configuration")
    void testExpiredPrivilegedSessionRejected() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        // Force session to appear expired in database mock
        PrivilegedConfigurationSession expiredSession = new PrivilegedConfigurationSession(
                sessionDto.sessionId(),
                masterAdmin.getId(),
                masterAdmin.getUsername(),
                Instant.now().minusSeconds(1000),
                Instant.now().minusSeconds(100), // Expired
                "192.168.1.50",
                "Mozilla/5.0 TestBrowser"
        );
        when(sessionRepository.findById(eq(sessionDto.sessionId()))).thenReturn(Optional.of(expiredSession));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        assertThatThrownBy(() -> controller.getAllConfigurations(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("8. Revoked privileged session cannot access configuration")
    void testRevokedPrivilegedSessionRejected() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        PrivilegedConfigurationSession revokedSession = new PrivilegedConfigurationSession(
                sessionDto.sessionId(),
                masterAdmin.getId(),
                masterAdmin.getUsername(),
                Instant.now().minusSeconds(100),
                Instant.now().plusSeconds(800),
                "192.168.1.50",
                "Mozilla/5.0 TestBrowser"
        );
        revokedSession.setRevoked(true);
        revokedSession.setRevocationReason("User logout");
        when(sessionRepository.findById(eq(sessionDto.sessionId()))).thenReturn(Optional.of(revokedSession));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        assertThatThrownBy(() -> controller.getAllConfigurations(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("9. Arbitrary filesystem path traversal is strictly rejected")
    void testFilesystemPathTraversalRejected() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                1L,
                Map.of("PLATFORM_DEFAULT_CURRENCY", "../../etc/passwd"),
                "Exploit attempt"
        );

        assertThatThrownBy(() -> controller.updateConfiguration(updateReq, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("path traversal is strictly prohibited");
    }

    @Test
    @DisplayName("10. Arbitrary shell command injection characters are strictly rejected")
    void testShellCommandInjectionRejected() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                1L,
                Map.of("PLATFORM_DEFAULT_CURRENCY", "ETB; rm -rf / ;"),
                "Exploit attempt"
        );

        assertThatThrownBy(() -> controller.updateConfiguration(updateReq, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Command execution characters are strictly rejected");
    }

    @Test
    @DisplayName("11. Unknown / unallowlisted environment variables are rejected")
    void testUnknownEnvironmentVariableRejected() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                1L,
                Map.of("ARBITRARY_VARIABLE_NOT_IN_ALLOWLIST", "malicious-value"),
                "Inject variable"
        );

        assertThatThrownBy(() -> controller.updateConfiguration(updateReq, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown or unallowlisted configuration variable");
    }

    @Test
    @DisplayName("12. Invalid typed numeric bounds are rejected")
    void testInvalidNumericBoundsRejected() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        // DATABASE_POOL_SIZE has bounds [5, 100]
        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                1L,
                Map.of("DATABASE_POOL_SIZE", "500"),
                "Too large pool size"
        );

        assertThatThrownBy(() -> controller.updateConfiguration(updateReq, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be <= 100");
    }

    @Test
    @DisplayName("13. SSRF destination validator blocks metadata IP and private ranges on external URL fields")
    void testSsrfProtectionOnUrls() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        // AWS/GCP metadata service IP 169.254.169.254
        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                1L,
                Map.of("MOR_GATEWAY_URL", "http://169.254.169.254/latest/meta-data"),
                "SSRF attempt"
        );

        assertThatThrownBy(() -> controller.updateConfiguration(updateReq, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or unsafe destination URL");
    }

    @Test
    @DisplayName("14. Optimistic locking detects concurrent revision updates (prevents lost updates)")
    void testOptimisticLockingPreventsLostUpdates() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        // Max revision is 1L, but user passes stale expectedRevisionNumber 0L
        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                0L, // Stale revision
                Map.of("CORS_ALLOWED_ORIGINS", "https://app.utsolutionsplc.com"),
                "Update CORS"
        );

        assertThatThrownBy(() -> controller.updateConfiguration(updateReq, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuration changed since you opened this page");
    }

    @Test
    @DisplayName("15. Secret rotation encrypts with AES-256-GCM and generates new fingerprint without exposing plaintext")
    void testSecretRotationSecurity() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        SecretRotationRequest rotReq = new SecretRotationRequest(
                "JWT_SECRET",
                "NewSuperSecretKey256BitsLongForPlatformProductionSigning!",
                "Scheduled key rotation"
        );

        var response = controller.rotateSecret(rotReq, request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ConfigurationEntry entry = dbEntries.get("JWT_SECRET");
        assertThat(entry.getEncryptedSecretPayload()).startsWith("AESGCM:v1:");
        assertThat(entry.getSecretFingerprint()).startsWith("HMAC:");

        // Verify decrypted secret matches without ever exposing it on DTOs
        String decrypted = encryptionService.decryptSecret(entry.getEncryptedSecretPayload());
        assertThat(decrypted).isEqualTo("NewSuperSecretKey256BitsLongForPlatformProductionSigning!");
    }

    @Test
    @DisplayName("16. GeezSMS live egress cannot be unblocked without simultaneously setting SMS_PROVIDER=GEEZSMS (dual-key guard)")
    void testGeezSmsLiveEgressRequiresDualKeyAuthorization() {
        // Attempt 1: unblock alone — must be rejected (missing SMS_PROVIDER=GEEZSMS in same batch)
        PrivilegedSessionDto session1 = establishPrivilegedSession();
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("X-Privileged-Token", session1.privilegedToken());

        ConfigurationUpdateRequest unblockAlone = new ConfigurationUpdateRequest(
                1L,
                Map.of("SMS_LIVE_INTEGRATION_BLOCKED", "false"),
                "Attempt to unblock without provider selection"
        );
        assertThatThrownBy(() -> controller.updateConfiguration(unblockAlone, request1))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("GeezSMS live egress cannot be unblocked");

        // Attempt 2: unblock with wrong provider — must also be rejected
        // Requires a fresh privileged session as each step-up session is single-use.
        PrivilegedSessionDto session2 = establishPrivilegedSession();
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("X-Privileged-Token", session2.privilegedToken());

        ConfigurationUpdateRequest unblockWithWrongProvider = new ConfigurationUpdateRequest(
                1L,
                Map.of("SMS_LIVE_INTEGRATION_BLOCKED", "false", "SMS_PROVIDER", "MOCK_GEEZSMS"),
                "Attempt to unblock with mock provider"
        );
        assertThatThrownBy(() -> controller.updateConfiguration(unblockWithWrongProvider, request2))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("GeezSMS live egress cannot be unblocked");
    }

    @Test
    @DisplayName("17. Emergency Kill Switches toggle safely and report status in health check")
    void testEmergencyKillSwitches() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        // Toggle SMS Kill Switch OFF
        ConfigurationUpdateRequest updateReq = new ConfigurationUpdateRequest(
                1L,
                Map.of("SMS_ENABLED", "false"),
                "Emergency SMS provider incident"
        );

        controller.updateConfiguration(updateReq, request);

        var healthRes = controller.getHealth(request);
        var health = healthRes.getBody();
        assertThat(health).isNotNull();
        assertThat(health.smsKillSwitchActive()).isTrue();
        assertThat(health.smsStatus()).contains("EMERGENCY_HALTED");
    }

    @Test
    @DisplayName("18. Rollback non-secret configurations creates a new revision")
    void testRollbackCreatesNewRevision() {
        PrivilegedSessionDto sessionDto = establishPrivilegedSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privileged-Token", sessionDto.privilegedToken());

        ConfigurationRevision targetRev = new ConfigurationRevision(
                UUID.randomUUID(),
                1L,
                "SYSTEM",
                "Baseline",
                null,
                "APPLIED"
        );
        when(revisionRepository.findByRevisionNumber(eq(1L))).thenReturn(Optional.of(targetRev));
        when(revisionRepository.findMaxRevisionNumber()).thenReturn(2L); // Active revision is 2

        ConfigurationRevisionEntry entry = new ConfigurationRevisionEntry(
                UUID.randomUUID(), targetRev, "SMS_ENABLED", "UPDATED", "HIGH", "HIGH", "false", "true"
        );
        when(revisionEntryRepository.findByRevisionIdOrderByKeyNameAsc(targetRev.getId()))
                .thenReturn(List.of(entry));

        var res = controller.rollback(1L, request);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody().getRevisionNumber()).isEqualTo(3L);
        assertThat(res.getBody().getRollbackFromRevision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("19. Out-of-band Step-up OTP dispatch and verification successfully enforces security")
    void testOutOfBandOtpDispatchAndStepUp() {
        MockHttpServletRequest sendReq = new MockHttpServletRequest();
        sendReq.addHeader("X-Correlation-ID", "CORR-OTP-01");

        var sendResponse = controller.sendStepUpOtp(
                new SendStepUpOtpRequest("platform.admin", "+251911000000"),
                sendReq
        );

        assertThat(sendResponse.getStatusCode().is2xxSuccessful()).isTrue();
        var otpBody = sendResponse.getBody();
        assertThat(otpBody).isNotNull();
        assertThat(otpBody.dispatched()).isTrue();
        assertThat(otpBody.maskedEmail()).isEqualTo("ad***@utsolutionsplc.com");
        assertThat(otpBody.cooldownSeconds()).isEqualTo(60);

        // When bad code is submitted, step-up fails
        MockHttpServletRequest stepUpReq = new MockHttpServletRequest();
        assertThatThrownBy(() -> controller.initiateStepUp(
                new StepUpMfaRequest(rawPassword, "999999"),
                stepUpReq
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired MFA verification code");
    }

    @Test
    @DisplayName("20. Step-up MFA succeeds using Authenticator App (TOTP) alternative directly")
    void testAuthenticatorTotpAlternativeSuccess() {
        // Without requesting Out-of-Band OTP, operator directly provides Authenticator App code
        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = controller.initiateStepUp(
                new StepUpMfaRequest(rawPassword, "654321"),
                request
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.privilegedToken()).isNotBlank();
        assertThat(body.durationSeconds()).isEqualTo(900L);
    }
}
