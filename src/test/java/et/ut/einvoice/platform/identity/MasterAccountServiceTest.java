package et.ut.einvoice.platform.identity;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.config.service.MasterMfaOtpService;
import et.ut.einvoice.platform.identity.domain.PlatformAccountVerification;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.PlatformAccountVerificationRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserRecoveryCodeRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.identity.service.MasterAccountService;
import et.ut.einvoice.platform.identity.service.TotpService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterAccountServiceTest {

    @Mock
    private PlatformUserRepository userRepository;
    @Mock
    private PlatformUserRecoveryCodeRepository recoveryCodeRepository;
    @Mock
    private PlatformAccountVerificationRepository verificationRepository;
    @Mock
    private PlatformUserSessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TotpService totpService;
    @Mock
    private MasterMfaOtpService mfaOtpService;
    @Mock
    private AuditService auditService;
    @Mock
    private EmailProvider emailProvider;
    @Mock
    private SmsProvider smsProvider;

    private MasterAccountService service;
    private PlatformUser testUser;

    @BeforeEach
    void setUp() {
        service = new MasterAccountService(
                userRepository,
                recoveryCodeRepository,
                verificationRepository,
                sessionRepository,
                passwordEncoder,
                totpService,
                mfaOtpService,
                auditService,
                emailProvider,
                smsProvider
        );

        testUser = new PlatformUser(
                UUID.randomUUID(),
                "platform.admin",
                "admin@ut-invoice.internal",
                "$2a$12$hashedPassword",
                "System Master Administrator",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );
        testUser.setPhone("+251911000000");
    }

    @Test
    @DisplayName("getProfile returns complete profile information with regional preferences")
    void testGetProfile() {
        when(userRepository.findByUsername("platform.admin")).thenReturn(Optional.of(testUser));

        MasterProfileResponse profile = service.getProfile("platform.admin");

        assertThat(profile).isNotNull();
        assertThat(profile.username()).isEqualTo("platform.admin");
        assertThat(profile.email()).isEqualTo("admin@ut-invoice.internal");
        assertThat(profile.primaryRole()).isEqualTo("ROLE_PLATFORM_ADMIN");
        assertThat(profile.timezone()).isEqualTo("Africa/Addis_Ababa");
    }

    @Test
    @DisplayName("updateProfile persists name and timezone updates and audits change")
    void testUpdateProfile() {
        when(userRepository.findByUsername("platform.admin")).thenReturn(Optional.of(testUser));

        UpdateProfileRequest req = new UpdateProfileRequest("Updated Admin Name", "Africa/Addis_Ababa", "YYYY-MM-DD");
        MasterProfileResponse updated = service.updateProfile("platform.admin", req);

        assertThat(testUser.getFullName()).isEqualTo("Updated Admin Name");
        verify(userRepository).save(testUser);
        verify(auditService).recordEvent(any(), eq("platform.admin"), eq("PROFILE_UPDATED"), eq("IDENTITY_ACCOUNT"), any(), any());
    }

    @Test
    @DisplayName("changePassword enforces NIST SP 800-63B / Directive 1142 complexity")
    void testChangePasswordComplexity() {
        // Password too short or missing special character
        ChangePasswordRequest weakReq = new ChangePasswordRequest("OldPass@123", "weakpass", "weakpass");

        assertThatThrownBy(() -> service.changePassword("platform.admin", weakReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NIST SP 800-63B / Directive No. 1142");
    }

    @Test
    @DisplayName("changePassword updates password hash and revokes prior sessions")
    void testChangePasswordSuccess() {
        when(userRepository.findByUsername("platform.admin")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("CurrentPass@123", "$2a$12$hashedPassword")).thenReturn(true);
        when(passwordEncoder.matches("NewSecurePass@2026!", "$2a$12$hashedPassword")).thenReturn(false);
        when(passwordEncoder.encode("NewSecurePass@2026!")).thenReturn("$2a$12$newHashedPassword");
        when(sessionRepository.findByUserId(testUser.getId())).thenReturn(List.of());

        ChangePasswordRequest req = new ChangePasswordRequest("CurrentPass@123", "NewSecurePass@2026!", "NewSecurePass@2026!");
        service.changePassword("platform.admin", req);

        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newHashedPassword");
        assertThat(testUser.getPasswordChangedAt()).isNotNull();
        verify(userRepository).save(testUser);
        verify(auditService).recordEvent(any(), eq("platform.admin"), eq("PASSWORD_CHANGED"), eq("IDENTITY_ACCOUNT"), any(), any());
    }

    @Test
    @DisplayName("setupMfa generates secret, otpAuthUri, and 10 backup codes")
    void testSetupMfa() {
        when(userRepository.findByUsername("platform.admin")).thenReturn(Optional.of(testUser));
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.getOtpAuthUri(anyString(), anyString(), anyString())).thenReturn("otpauth://totp/UT-Invoice:platform.admin?secret=JBSWY3DPEHPK3PXP&issuer=UT-Invoice");

        MfaSetupResponse res = service.setupMfa("platform.admin");

        assertThat(res.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(res.otpAuthUri()).contains("otpauth://totp/");
        assertThat(res.backupCodes()).hasSize(10);
        assertThat(testUser.getMfaSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        verify(recoveryCodeRepository, times(10)).save(any());
    }

    @Test
    @DisplayName("verifyMfaSetup enables MFA on user upon valid TOTP verification")
    void testVerifyMfaSetup() {
        testUser.setMfaSecret("JBSWY3DPEHPK3PXP");
        when(userRepository.findByUsername("platform.admin")).thenReturn(Optional.of(testUser));
        when(totpService.verifyCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);

        service.verifyMfaSetup("platform.admin", "123456");

        assertThat(testUser.isMfaEnabled()).isTrue();
        verify(userRepository).save(testUser);
        verify(auditService).recordEvent(any(), eq("platform.admin"), eq("MFA_ENROLLED_SUCCESS"), eq("IDENTITY_ACCOUNT"), any(), any());
    }
}
