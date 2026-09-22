package et.ut.einvoice.platform.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.domain.FailureClassification;
import et.ut.einvoice.notifications.provider.*;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.*;
import et.ut.einvoice.platform.config.repository.ConfigurationEntryRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service for safe administrative diagnostics and test dispatch of email and SMS channels.
 * Strictly prevents open-relay abuse by tying test recipients exclusively to the authenticated
 * administrator's verified account records, and preserves all statutory safety invariants.
 */
@Service
public class MasterMessagingDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(MasterMessagingDiagnosticsService.class);

    private final SmtpEmailProvider smtpEmailProvider;
    private final SmsProviderRouter smsProviderRouter;
    private final MockGeezSmsProvider mockGeezSmsProvider;
    private final EthioTelecomSmsProvider ethioTelecomSmsProvider;
    private final PlatformUserRepository userRepository;
    private final ConfigurationEntryRepository entryRepository;
    private final AuditService auditService;
    private final String activeSmsProviderName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterMessagingDiagnosticsService(
            SmtpEmailProvider smtpEmailProvider,
            SmsProviderRouter smsProviderRouter,
            MockGeezSmsProvider mockGeezSmsProvider,
            EthioTelecomSmsProvider ethioTelecomSmsProvider,
            PlatformUserRepository userRepository,
            ConfigurationEntryRepository entryRepository,
            AuditService auditService,
            @Value("${notifications.sms.active-provider:mock}") String activeSmsProviderName
    ) {
        this.smtpEmailProvider = smtpEmailProvider;
        this.smsProviderRouter = smsProviderRouter;
        this.mockGeezSmsProvider = mockGeezSmsProvider;
        this.ethioTelecomSmsProvider = ethioTelecomSmsProvider;
        this.userRepository = userRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
        this.activeSmsProviderName = activeSmsProviderName;
    }

    public EmailDiagnosticStatusDto getEmailStatus(String adminUsername) {
        String verifiedEmail = resolveAdminEmail(adminUsername);
        boolean deliveryEnabled = entryRepository.findByKeyName("EMAIL_DELIVERY_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);

        if (!smtpEmailProvider.isConfigured()) {
            return new EmailDiagnosticStatusDto(
                    "NOT_CONFIGURED",
                    smtpEmailProvider.getProviderName(),
                    smtpEmailProvider.getHost(),
                    smtpEmailProvider.getPort(),
                    smtpEmailProvider.getFromEmail(),
                    smtpEmailProvider.getMaskedUsername(),
                    deliveryEnabled,
                    false,
                    verifiedEmail != null ? MasterMfaOtpService.maskEmail(verifiedEmail) : "NOT_ENROLLED",
                    "SMTP host or authentication credentials are not configured. Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME, and SMTP_PASSWORD in environment.",
                    Instant.now()
            );
        }

        SmtpEmailProvider.EmailDeliveryResult probe = smtpEmailProvider.checkConnection();
        String diagnosticStatus = probe.status();
        String message = probe.message();

        return new EmailDiagnosticStatusDto(
                diagnosticStatus,
                smtpEmailProvider.getProviderName(),
                smtpEmailProvider.getHost(),
                smtpEmailProvider.getPort(),
                smtpEmailProvider.getFromEmail(),
                smtpEmailProvider.getMaskedUsername(),
                deliveryEnabled,
                true,
                verifiedEmail != null ? MasterMfaOtpService.maskEmail(verifiedEmail) : "NOT_ENROLLED",
                message,
                Instant.now()
        );
    }

    public EmailTestSendResponse sendTestEmail(String adminUsername, String ipAddress, String correlationId) {
        PlatformUser user = userRepository.findByUsername(adminUsername.toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Administrator account not found: " + adminUsername));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Authoritative administrator email is not configured.");
        }
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Authoritative administrator email is unverified. Cannot dispatch test email.");
        }

        boolean deliveryEnabled = entryRepository.findByKeyName("EMAIL_DELIVERY_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);
        if (!deliveryEnabled) {
            throw new IllegalStateException("Email delivery is currently suspended by emergency kill switch (EMAIL_DELIVERY_ENABLED=false).");
        }

        String recipient = user.getEmail().trim();
        String subject = "[UT-INVOICE] Operational Email Delivery Verification Test";
        String body = "Dear Platform Administrator (" + user.getFullName() + "),\n\n"
                + "This is an automated operational diagnostic message from the UT Electronic Invoicing Platform.\n"
                + "Your email transport provider configuration is functional and verified.\n\n"
                + "Recipient: " + MasterMfaOtpService.maskEmail(recipient) + "\n"
                + "Timestamp: " + Instant.now() + "\n"
                + "Correlation ID: " + correlationId + "\n\n"
                + "Directive No. 1142/2026 Core Infrastructure Security";

        SmtpEmailProvider.EmailDeliveryResult result = smtpEmailProvider.sendEmailWithResult(recipient, subject, body);

        recordAudit(adminUsername, "EMAIL_TEST_DISPATCHED", Map.of(
                "recipientMasked", MasterMfaOtpService.maskEmail(recipient),
                "status", result.status(),
                "success", result.success(),
                "ipAddress", ipAddress != null ? ipAddress : "127.0.0.1",
                "correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString()
        ));

        return new EmailTestSendResponse(
                result.success(),
                result.status(),
                MasterMfaOtpService.maskEmail(recipient),
                result.providerMessageId(),
                result.message(),
                Instant.now()
        );
    }

    public SmsDiagnosticStatusDto getSmsStatus(String adminUsername) {
        String verifiedPhone = resolveAdminPhone(adminUsername);
        boolean deliveryEnabled = entryRepository.findByKeyName("SMS_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);

        boolean isEthio = "ethio-telecom".equalsIgnoreCase(activeSmsProviderName) || "ethio".equalsIgnoreCase(activeSmsProviderName);

        if (!isEthio) {
            // GeezSMS safety invariant: permanently blocked by policy
            return new SmsDiagnosticStatusDto(
                    "BLOCKED_BY_POLICY",
                    "Mock GeezSMS Provider",
                    "BLOCKED_BY_STATUTORY_POLICY",
                    "MoR-EIRS",
                    true,
                    deliveryEnabled,
                    verifiedPhone != null ? MasterMfaOtpService.maskPhone(verifiedPhone) : "NOT_ENROLLED",
                    "STATUTORY SAFETY LOCK: GeezSMS live third-party egress is permanently blocked. Deterministic mock transport active for compliance testing.",
                    Instant.now()
            );
        }

        boolean isConfigured = ethioTelecomSmsProvider.isConfigured();
        return new SmsDiagnosticStatusDto(
                isConfigured ? "CONFIGURED" : "NOT_CONFIGURED",
                "Ethio Telecom SMS Gateway",
                isConfigured ? "https://gateway.ethiotelecom.et/v1/sms" : "NOT_CONFIGURED",
                "MoR-EIRS",
                false,
                deliveryEnabled,
                verifiedPhone != null ? MasterMfaOtpService.maskPhone(verifiedPhone) : "NOT_ENROLLED",
                isConfigured ? "Ethio Telecom SMS Gateway configured and ready for transactional dispatch." : "Ethio Telecom endpoint (ETHIO_TELECOM_SMS_URL) or API key is not configured.",
                Instant.now()
        );
    }

    public SmsTestSendResponse sendTestSms(String adminUsername, String ipAddress, String correlationId) {
        PlatformUser user = userRepository.findByUsername(adminUsername.toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Administrator account not found: " + adminUsername));

        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new IllegalStateException("Authoritative administrator phone number is not configured.");
        }
        if (!user.isPhoneVerified()) {
            throw new IllegalStateException("Authoritative administrator phone number is unverified. Cannot dispatch test SMS.");
        }

        boolean deliveryEnabled = entryRepository.findByKeyName("SMS_ENABLED")
                .map(e -> "true".equalsIgnoreCase(e.getCurrentValue())).orElse(true);
        if (!deliveryEnabled) {
            throw new IllegalStateException("SMS delivery is currently suspended by emergency kill switch (SMS_ENABLED=false).");
        }

        String recipientPhone = user.getPhone().trim();
        String testMessage = "[UT-INVOICE] Operational SMS test successful. Ref: " + UUID.randomUUID().toString().substring(0, 8);

        SmsSendResult sendResult = smsProviderRouter.sendTransactionalSms(
                new SmsSendRequest(recipientPhone, testMessage, "MoR-EIRS", null)
        );

        String diagnosticStatus;
        if (sendResult.success()) {
            diagnosticStatus = "SUBMITTED";
        } else if (sendResult.failureClassification() == FailureClassification.PROVIDER_CONFIGURATION_FAILURE) {
            diagnosticStatus = "AUTHENTICATION_FAILED";
        } else if (sendResult.failureClassification() == FailureClassification.PROVIDER_CAPACITY_FAILURE) {
            diagnosticStatus = "INSUFFICIENT_BALANCE";
        } else {
            diagnosticStatus = "SUBMISSION_FAILED";
        }

        recordAudit(adminUsername, "SMS_TEST_DISPATCHED", Map.of(
                "recipientMasked", MasterMfaOtpService.maskPhone(recipientPhone),
                "status", diagnosticStatus,
                "provider", smsProviderRouter.getProviderName(),
                "ipAddress", ipAddress != null ? ipAddress : "127.0.0.1",
                "correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString()
        ));

        return new SmsTestSendResponse(
                sendResult.success(),
                diagnosticStatus,
                MasterMfaOtpService.maskPhone(recipientPhone),
                sendResult.providerMessageId(),
                sendResult.success()
                        ? "Test SMS successfully accepted by provider transport."
                        : "Test SMS submission failed: " + sendResult.errorMessage(),
                Instant.now()
        );
    }

    private String resolveAdminEmail(String username) {
        if (username == null) return null;
        return userRepository.findByUsername(username.toLowerCase().trim())
                .filter(PlatformUser::isEmailVerified)
                .map(PlatformUser::getEmail)
                .orElse(null);
    }

    private String resolveAdminPhone(String username) {
        if (username == null) return null;
        return userRepository.findByUsername(username.toLowerCase().trim())
                .filter(PlatformUser::isPhoneVerified)
                .map(PlatformUser::getPhone)
                .orElse(null);
    }

    private void recordAudit(String username, String action, Map<String, Object> details) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(details);
            payload.put("action", action);
            payload.put("operator", username);

            auditService.recordEvent(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    username,
                    action,
                    "MESSAGING_DIAGNOSTICS",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to record messaging diagnostics audit log: {}", e.getMessage());
        }
    }
}
