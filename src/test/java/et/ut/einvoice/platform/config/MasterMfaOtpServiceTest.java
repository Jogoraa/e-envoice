package et.ut.einvoice.platform.config;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.config.service.MasterMfaOtpService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasterMfaOtpServiceTest {

    private SmsProvider smsProvider;
    private EmailProvider emailProvider;
    private AuditService auditService;
    private MasterMfaOtpService otpService;

    private PlatformUser testAdmin;

    @BeforeEach
    void setUp() {
        smsProvider = mock(SmsProvider.class);
        emailProvider = mock(EmailProvider.class);
        auditService = mock(AuditService.class);

        when(emailProvider.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(smsProvider.sendSms(anyString(), anyString())).thenReturn(true);

        otpService = new MasterMfaOtpService(smsProvider, emailProvider, auditService);

        testAdmin = new PlatformUser(
                UUID.randomUUID(),
                "platform.admin",
                "admin@ut-invoice.internal",
                "hashedPass",
                "Platform Administrator",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );
        testAdmin.setPhone("+251911000000");
        testAdmin.setPhoneVerified(true);
        testAdmin.setEmailVerified(true);
    }

    @Test
    @DisplayName("1. Dispatch OTP sends out-of-band message via Email and SMS providers with masked destinations")
    void testDispatchOtpSendsEmailAndSms() {
        var response = otpService.dispatchStepUpOtp(testAdmin, "+251911000000", "127.0.0.1", "CORR-01");

        assertThat(response.dispatched()).isTrue();
        assertThat(response.maskedEmail()).isEqualTo("ad***@ut-invoice.internal");
        assertThat(response.maskedPhone()).contains("***");
        assertThat(response.cooldownSeconds()).isEqualTo(60);

        ArgumentCaptor<String> emailBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).sendEmail(eq("admin@ut-invoice.internal"), contains("Verification Code"), emailBodyCaptor.capture());
        String emailBody = emailBodyCaptor.getValue();
        assertThat(emailBody).contains("verification code is: ");

        verify(smsProvider).sendSms(eq("+251911000000"), contains("Master Admin verification code"));
        assertThat(otpService.hasPendingOtp("platform.admin")).isTrue();
    }

    @Test
    @DisplayName("2. Rate limiting rejects dispatch within 60-second cooldown period")
    void testRateLimitingEnforced() {
        otpService.dispatchStepUpOtp(testAdmin, "+251911000000", "127.0.0.1", "CORR-02A");

        assertThatThrownBy(() -> otpService.dispatchStepUpOtp(testAdmin, "+251911000000", "127.0.0.1", "CORR-02B"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rate limit exceeded. Please wait");
    }

    @Test
    @DisplayName("3. Verify OTP succeeds with captured code and consumes it (single-use)")
    void testVerifyOtpSuccessAndSingleUse() {
        otpService.dispatchStepUpOtp(testAdmin, "+251911000000", "127.0.0.1", "CORR-03");

        ArgumentCaptor<String> smsCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsProvider).sendSms(eq("+251911000000"), smsCaptor.capture());
        String smsText = smsCaptor.getValue();
        // Format: [UT-INVOICE] Master Admin verification code: XXXXXX. Valid for 5 min.
        String code = smsText.replaceAll("[^0-9]", "").substring(0, 6);

        // Verification must succeed
        assertThat(otpService.verifyOtp("platform.admin", code)).isTrue();

        // Must be single-use: replay verification fails
        assertThat(otpService.hasPendingOtp("platform.admin")).isFalse();
        assertThat(otpService.verifyOtp("platform.admin", code)).isFalse();
    }

    @Test
    @DisplayName("4. Verify OTP fails with wrong code and locks out after 5 invalid attempts")
    void testVerifyOtpBruteForceLockout() {
        otpService.dispatchStepUpOtp(testAdmin, "+251911000000", "127.0.0.1", "CORR-04");

        for (int i = 0; i < 4; i++) {
            assertThat(otpService.verifyOtp("platform.admin", "000000")).isFalse();
            assertThat(otpService.hasPendingOtp("platform.admin")).isTrue();
        }

        // 5th failed attempt exhausts quota and invalidates code
        assertThat(otpService.verifyOtp("platform.admin", "000000")).isFalse();
        assertThat(otpService.hasPendingOtp("platform.admin")).isFalse();
    }

    @Test
    @DisplayName("5. Masking utilities preserve privacy according to Directives")
    void testMaskingHelpers() {
        assertThat(MasterMfaOtpService.maskEmail("john.doe@example.com")).isEqualTo("jo***@example.com");
        assertThat(MasterMfaOtpService.maskEmail("a@b.com")).isEqualTo("a***@b.com");
        assertThat(MasterMfaOtpService.maskPhone("+251911223344")).isEqualTo("+25191***3344");
    }

    @Test
    @DisplayName("6. Security: Unauthorized requestedPhone override is rejected; authoritative verified phone is used")
    void testUnauthorizedPhoneOverrideRejected() {
        // Attacker attempts to pass +251999887766 in request body
        var response = otpService.dispatchStepUpOtp(testAdmin, "+251999887766", "10.0.0.1", "OVERRIDE-01");

        assertThat(response.dispatched()).isTrue();
        // SMS was dispatched strictly to authoritative +251911000000, NOT +251999887766
        verify(smsProvider).sendSms(eq("+251911000000"), contains("verification code"));
        verify(smsProvider, never()).sendSms(eq("+251999887766"), anyString());
    }

    @Test
    @DisplayName("7. Security: Unverified email rejects OTP dispatch (fail-closed)")
    void testUnverifiedEmailRejectsDispatch() {
        testAdmin.setEmailVerified(false);

        assertThatThrownBy(() -> otpService.dispatchStepUpOtp(testAdmin, null, "127.0.0.1", "UNVERIF-EMAIL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is unverified");
    }

    @Test
    @DisplayName("8. Security: Unverified phone omits SMS dispatch without failing verified email dispatch")
    void testUnverifiedPhoneOmitsSmsDispatch() {
        testAdmin.setPhoneVerified(false);

        var response = otpService.dispatchStepUpOtp(testAdmin, null, "127.0.0.1", "UNVERIF-PHONE");

        assertThat(response.dispatched()).isTrue();
        assertThat(response.maskedPhone()).isEqualTo("UNVERIFIED");
        verify(emailProvider).sendEmail(eq("admin@ut-invoice.internal"), anyString(), anyString());
        verify(smsProvider, never()).sendSms(anyString(), anyString());
    }
}
