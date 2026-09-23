package et.ut.einvoice.platform.config;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EthioTelecomSmsProvider;
import et.ut.einvoice.notifications.provider.GeezSmsProvider;
import et.ut.einvoice.notifications.provider.MockGeezSmsProvider;
import et.ut.einvoice.notifications.provider.SmsProviderRouter;
import et.ut.einvoice.notifications.provider.SmtpEmailProvider;
import et.ut.einvoice.platform.config.repository.ConfigurationEntryRepository;
import et.ut.einvoice.platform.config.service.MasterMessagingDiagnosticsService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MasterMessagingDiagnosticsTest {

    private SmtpEmailProvider smtpEmailProvider;
    private SmsProviderRouter smsProviderRouter;
    private MockGeezSmsProvider mockGeezSmsProvider;
    private EthioTelecomSmsProvider ethioTelecomSmsProvider;
    private GeezSmsProvider geezSmsProvider;
    private PlatformUserRepository userRepository;
    private ConfigurationEntryRepository entryRepository;
    private AuditService auditService;
    private MasterMessagingDiagnosticsService diagnosticsService;

    private PlatformUser verifiedAdmin;
    private PlatformUser unverifiedAdmin;

    @BeforeEach
    void setUp() {
        smtpEmailProvider = mock(SmtpEmailProvider.class);
        smsProviderRouter = mock(SmsProviderRouter.class);
        mockGeezSmsProvider = new MockGeezSmsProvider();
        ethioTelecomSmsProvider = mock(EthioTelecomSmsProvider.class);
        geezSmsProvider = mock(GeezSmsProvider.class);
        userRepository = mock(PlatformUserRepository.class);
        entryRepository = mock(ConfigurationEntryRepository.class);
        auditService = mock(AuditService.class);

        when(smtpEmailProvider.getProviderName()).thenReturn("SMTP Email Transport");
        when(smtpEmailProvider.getHost()).thenReturn("mail.utsolutionsplc.com");
        when(smtpEmailProvider.getPort()).thenReturn(587);
        when(smtpEmailProvider.getFromEmail()).thenReturn("no-reply@ut-invoice.et");
        when(smtpEmailProvider.getMaskedUsername()).thenReturn("ad***@ut-invoice.et");
        when(smtpEmailProvider.isConfigured()).thenReturn(true);

        when(smtpEmailProvider.checkConnection()).thenReturn(
                new SmtpEmailProvider.EmailDeliveryResult(true, "CONFIGURED", null, "SMTP probe OK")
        );
        when(smtpEmailProvider.sendEmailWithResult(anyString(), anyString(), anyString())).thenReturn(
                new SmtpEmailProvider.EmailDeliveryResult(true, "ACCEPTED", "<msg-001@ut-invoice.et>", "Delivered")
        );

        when(smsProviderRouter.getProviderName()).thenReturn("Mock GeezSMS Provider");
        when(smsProviderRouter.sendTransactionalSms(any())).thenReturn(
                et.ut.einvoice.notifications.provider.SmsSendResult.accepted("SMS-MSG-001")
        );

        verifiedAdmin = new PlatformUser(
                UUID.randomUUID(),
                "dave.admin",
                "dave@utsolutionsplc.com",
                "hash",
                "Dave Platform Admin",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );
        verifiedAdmin.setPhone("+251911223344");
        verifiedAdmin.setPhoneVerified(true);
        verifiedAdmin.setEmailVerified(true);

        unverifiedAdmin = new PlatformUser(
                UUID.randomUUID(),
                "pending.admin",
                "pending@utsolutionsplc.com",
                "hash",
                "Pending Admin",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );
        unverifiedAdmin.setPhone("+251922334455");
        unverifiedAdmin.setPhoneVerified(false);
        unverifiedAdmin.setEmailVerified(false);

        when(userRepository.findByUsername("dave.admin")).thenReturn(Optional.of(verifiedAdmin));
        when(userRepository.findByUsername("pending.admin")).thenReturn(Optional.of(unverifiedAdmin));

        diagnosticsService = new MasterMessagingDiagnosticsService(
                smtpEmailProvider,
                smsProviderRouter,
                mockGeezSmsProvider,
                ethioTelecomSmsProvider,
                geezSmsProvider,
                userRepository,
                entryRepository,
                auditService,
                "mock"
        );
    }

    @Test
    @DisplayName("1. Email status returns configured metadata with zero secret leakage")
    void testEmailStatusSafeMetadata() {
        var status = diagnosticsService.getEmailStatus("dave.admin");

        assertThat(status.status()).isEqualTo("CONFIGURED");
        assertThat(status.host()).isEqualTo("mail.utsolutionsplc.com");
        assertThat(status.port()).isEqualTo(587);
        assertThat(status.fromAddress()).isEqualTo("no-reply@ut-invoice.et");
        assertThat(status.usernameMasked()).isEqualTo("ad***@ut-invoice.et");
        assertThat(status.verifiedAdminEmail()).isEqualTo("da***@utsolutionsplc.com");
        assertThat(status.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("2. Send test email dispatches strictly to authoritative verified administrator address")
    void testSendTestEmailAuthoritativeDestination() {
        var resp = diagnosticsService.sendTestEmail("dave.admin", "192.168.1.10", "TEST-CORR-01");

        assertThat(resp.success()).isTrue();
        assertThat(resp.status()).isEqualTo("ACCEPTED");
        assertThat(resp.recipientMasked()).isEqualTo("da***@utsolutionsplc.com");

        verify(smtpEmailProvider).sendEmailWithResult(
                eq("dave@utsolutionsplc.com"),
                contains("Operational Email Delivery Verification Test"),
                contains("da***@utsolutionsplc.com")
        );
        verify(auditService).recordEvent(any(), eq("dave.admin"), eq("EMAIL_TEST_DISPATCHED"), eq("MESSAGING_DIAGNOSTICS"), any(), any());
    }

    @Test
    @DisplayName("3. Send test email fails closed if administrator email is unverified (prevents open relay)")
    void testSendTestEmailUnverifiedRejected() {
        assertThatThrownBy(() -> diagnosticsService.sendTestEmail("pending.admin", "127.0.0.1", "CORR-UNVERIF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is unverified");

        verify(smtpEmailProvider, never()).sendEmailWithResult(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("4. SMS status returns MOCK_ACTIVE when no live provider is configured (dev/test mode)")
    void testSmsStatusMockActiveWhenNotConfigured() {
        var smsStatus = diagnosticsService.getSmsStatus("dave.admin");

        assertThat(smsStatus.status()).isEqualTo("MOCK_ACTIVE");
        assertThat(smsStatus.safetyLockActive()).isTrue(); // still true in mock mode
        assertThat(smsStatus.activeProvider()).isEqualTo("Mock GeezSMS Provider");
        assertThat(smsStatus.operationalMessage()).contains("SMS_PROVIDER=GEEZSMS");
        assertThat(smsStatus.verifiedAdminPhone()).isEqualTo("+25191***3344");
    }

    @Test
    @DisplayName("5. Send test SMS dispatches strictly to verified phone and records audit log")
    void testSendTestSmsAuthoritativeDestination() {
        var resp = diagnosticsService.sendTestSms("dave.admin", "192.168.1.10", "SMS-CORR-01");

        assertThat(resp.success()).isTrue();
        assertThat(resp.status()).isEqualTo("SUBMITTED");
        assertThat(resp.recipientMasked()).isEqualTo("+25191***3344");

        verify(smsProviderRouter).sendTransactionalSms(argThat(req ->
                req.recipientPhone().equals("+251911223344") && req.senderId().equals("MoR-EIRS")
        ));
        verify(auditService).recordEvent(any(), eq("dave.admin"), eq("SMS_TEST_DISPATCHED"), eq("MESSAGING_DIAGNOSTICS"), any(), any());
    }

    @Test
    @DisplayName("6. Send test SMS fails closed if phone is unverified")
    void testSendTestSmsUnverifiedPhoneRejected() {
        assertThatThrownBy(() -> diagnosticsService.sendTestSms("pending.admin", "127.0.0.1", "SMS-CORR-UNVERIF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phone number is unverified");

        verify(smsProviderRouter, never()).sendTransactionalSms(any());
    }
}
