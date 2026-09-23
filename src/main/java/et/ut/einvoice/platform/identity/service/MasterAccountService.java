package et.ut.einvoice.platform.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.SendStepUpOtpResponse;
import et.ut.einvoice.platform.config.service.MasterMfaOtpService;
import et.ut.einvoice.platform.identity.domain.PlatformAccountVerification;
import et.ut.einvoice.platform.identity.domain.PlatformUserRecoveryCode;
import et.ut.einvoice.platform.identity.domain.SystemRole;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.PlatformAccountVerificationRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserRecoveryCodeRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MasterAccountService {

    private static final Logger log = LoggerFactory.getLogger(MasterAccountService.class);
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,128}$");

    private final PlatformUserRepository userRepository;
    private final PlatformUserRecoveryCodeRepository recoveryCodeRepository;
    private final PlatformAccountVerificationRepository verificationRepository;
    private final PlatformUserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final MasterMfaOtpService mfaOtpService;
    private final AuditService auditService;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterAccountService(
            PlatformUserRepository userRepository,
            PlatformUserRecoveryCodeRepository recoveryCodeRepository,
            PlatformAccountVerificationRepository verificationRepository,
            PlatformUserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            MasterMfaOtpService mfaOtpService,
            AuditService auditService,
            EmailProvider emailProvider,
            SmsProvider smsProvider
    ) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.verificationRepository = verificationRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.mfaOtpService = mfaOtpService;
        this.auditService = auditService;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
    }

    public MasterProfileResponse getProfile(String username) {
        PlatformUser user = findUser(username);
        List<String> roleCodes = new ArrayList<>();
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            roleCodes = user.getRoles().stream().map(SystemRole::getCode).toList();
        } else if (user.getRole() != null) {
            roleCodes = List.of(user.getRole());
        }

        return new MasterProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                roleCodes,
                user.getPhone(),
                user.isPhoneVerified(),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.getTimezone(),
                user.getDateFormat(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getPasswordChangedAt()
        );
    }

    @Transactional
    public MasterProfileResponse updateProfile(String username, UpdateProfileRequest req) {
        PlatformUser user = findUser(username);

        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName().trim());
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            user.setTimezone(req.timezone().trim());
        }
        if (req.dateFormat() != null && !req.dateFormat().isBlank()) {
            user.setDateFormat(req.dateFormat().trim());
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        recordAudit(username, "PROFILE_UPDATED", Map.of(
                "fullName", user.getFullName(),
                "timezone", user.getTimezone(),
                "dateFormat", user.getDateFormat()
        ));

        return getProfile(username);
    }

    @Transactional
    public void initiateEmailChange(String username, String newEmail) {
        if (newEmail == null || !newEmail.contains("@") || newEmail.length() > 128) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        String cleanEmail = newEmail.trim().toLowerCase(Locale.ROOT);
        PlatformUser user = findUser(username);

        if (cleanEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("New email must be different from current email.");
        }
        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already registered by another account.");
        }

        verificationRepository.deleteByUserIdAndVerificationType(user.getId(), "EMAIL_CHANGE");

        int codeNumber = 100000 + secureRandom.nextInt(900000);
        String code = String.valueOf(codeNumber);
        String hash = sha256Hex(code);

        PlatformAccountVerification verification = new PlatformAccountVerification(
                UUID.randomUUID(),
                user.getId(),
                "EMAIL_CHANGE",
                cleanEmail,
                hash,
                Instant.now().plusSeconds(900) // 15 mins
        );
        verificationRepository.save(verification);

        try {
            String subject = "[UT-INVOICE] Verify Your New Email Address";
            String body = "Dear " + user.getFullName() + ",\n\n"
                    + "You requested to change your platform administration email address to " + cleanEmail + ".\n"
                    + "Your verification code is: " + code + "\n\n"
                    + "This code is valid for 15 minutes.\n"
                    + "Directive No. 1142/2026 Core Infrastructure Security";
            emailProvider.sendEmail(cleanEmail, subject, body);
        } catch (Exception e) {
            log.warn("Failed to dispatch email change verification: {}", e.getMessage());
        }

        recordAudit(username, "EMAIL_CHANGE_INITIATED", Map.of(
                "newEmailMasked", MasterMfaOtpService.maskEmail(cleanEmail)
        ));
    }

    @Transactional
    public void confirmEmailChange(String username, ConfirmEmailChangeRequest req) {
        PlatformUser user = findUser(username);
        List<PlatformAccountVerification> pendingList = verificationRepository
                .findByUserIdAndVerificationTypeAndUsedFalseAndExpiresAtAfter(
                        user.getId(), "EMAIL_CHANGE", Instant.now());

        if (pendingList.isEmpty()) {
            throw new IllegalStateException("No active email verification found or code has expired.");
        }

        PlatformAccountVerification verification = pendingList.get(0);
        verification.setAttemptsCount(verification.getAttemptsCount() + 1);

        if (verification.getAttemptsCount() > 5) {
            verification.setUsed(true);
            verificationRepository.save(verification);
            throw new IllegalStateException("Too many incorrect attempts. Please initiate verification again.");
        }

        String inputHash = sha256Hex(req.verificationCode().trim());
        if (!MessageDigest.isEqual(inputHash.getBytes(StandardCharsets.UTF_8), verification.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            verificationRepository.save(verification);
            throw new IllegalArgumentException("Invalid verification code. Attempts remaining: " + (5 - verification.getAttemptsCount()));
        }

        verification.setUsed(true);
        verification.setUsedAt(Instant.now());
        verificationRepository.save(verification);

        user.setEmail(verification.getTargetValue());
        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        recordAudit(username, "EMAIL_CHANGE_COMPLETED", Map.of(
                "newEmailMasked", MasterMfaOtpService.maskEmail(user.getEmail())
        ));
    }

    @Transactional
    public void initiatePhoneVerification(String username, String phone) {
        if (phone == null || phone.length() < 9) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }
        String cleanPhone = phone.trim();
        PlatformUser user = findUser(username);

        verificationRepository.deleteByUserIdAndVerificationType(user.getId(), "PHONE_CHANGE");

        int codeNumber = 100000 + secureRandom.nextInt(900000);
        String code = String.valueOf(codeNumber);
        String hash = sha256Hex(code);

        PlatformAccountVerification verification = new PlatformAccountVerification(
                UUID.randomUUID(),
                user.getId(),
                "PHONE_CHANGE",
                cleanPhone,
                hash,
                Instant.now().plusSeconds(600) // 10 mins
        );
        verificationRepository.save(verification);

        try {
            String sms = "[UT-INVOICE] Phone verification code: " + code + ". Valid for 10 min.";
            smsProvider.sendSms(cleanPhone, sms);
        } catch (Exception e) {
            log.warn("Failed to dispatch phone verification SMS: {}", e.getMessage());
        }

        recordAudit(username, "PHONE_VERIFICATION_INITIATED", Map.of(
                "phoneMasked", MasterMfaOtpService.maskPhone(cleanPhone)
        ));
    }

    @Transactional
    public void confirmPhoneVerification(String username, ConfirmPhoneVerificationRequest req) {
        PlatformUser user = findUser(username);
        List<PlatformAccountVerification> pendingList = verificationRepository
                .findByUserIdAndVerificationTypeAndUsedFalseAndExpiresAtAfter(
                        user.getId(), "PHONE_CHANGE", Instant.now());

        if (pendingList.isEmpty()) {
            throw new IllegalStateException("No active phone verification found or code has expired.");
        }

        PlatformAccountVerification verification = pendingList.get(0);
        verification.setAttemptsCount(verification.getAttemptsCount() + 1);

        if (verification.getAttemptsCount() > 5) {
            verification.setUsed(true);
            verificationRepository.save(verification);
            throw new IllegalStateException("Too many incorrect attempts. Please initiate verification again.");
        }

        String inputHash = sha256Hex(req.verificationCode().trim());
        if (!MessageDigest.isEqual(inputHash.getBytes(StandardCharsets.UTF_8), verification.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            verificationRepository.save(verification);
            throw new IllegalArgumentException("Invalid verification code. Attempts remaining: " + (5 - verification.getAttemptsCount()));
        }

        verification.setUsed(true);
        verification.setUsedAt(Instant.now());
        verificationRepository.save(verification);

        user.setPhone(verification.getTargetValue());
        user.setPhoneVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        recordAudit(username, "PHONE_VERIFICATION_COMPLETED", Map.of(
                "phoneMasked", MasterMfaOtpService.maskPhone(user.getPhone())
        ));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest req) {
        if (req.newPassword() == null || !req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match.");
        }
        if (!PASSWORD_PATTERN.matcher(req.newPassword()).matches()) {
            throw new IllegalArgumentException(
                    "Password does not meet NIST SP 800-63B / Directive No. 1142 complexity: "
                            + "must be at least 12 characters, include uppercase, lowercase, digit, and special character.");
        }

        PlatformUser user = findUser(username);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            recordAudit(username, "PASSWORD_CHANGE_FAILED", Map.of("reason", "INVALID_CURRENT_PASSWORD"));
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be identical to the previous password.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Invalidate active sessions for this user upon password change
        var sessions = sessionRepository.findByUserId(user.getId());
        for (var sess : sessions) {
            if (!sess.isRevoked()) {
                sess.setRevoked(true);
                sess.setRevokedAt(Instant.now());
                sess.setRevocationReason("PASSWORD_CHANGED");
                sessionRepository.save(sess);
            }
        }

        recordAudit(username, "PASSWORD_CHANGED", Map.of("sessionsRevokedCount", sessions.size()));
    }

    @Transactional
    public MfaSetupResponse setupMfa(String username) {
        PlatformUser user = findUser(username);
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        userRepository.save(user);

        String otpAuthUri = totpService.getOtpAuthUri(secret, user.getUsername(), "UT-Invoice");

        // Generate 10 recovery codes
        recoveryCodeRepository.deleteByUserId(user.getId());
        List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String code = generateRecoveryCode();
            rawCodes.add(code);
            PlatformUserRecoveryCode rc = new PlatformUserRecoveryCode(
                    UUID.randomUUID(),
                    user.getId(),
                    sha256Hex(code)
            );
            recoveryCodeRepository.save(rc);
        }

        recordAudit(username, "MFA_SETUP_INITIATED", Map.of("backupCodesGenerated", 10));

        return new MfaSetupResponse(secret, otpAuthUri, rawCodes);
    }

    public SendStepUpOtpResponse sendMfaOtp(String username) {
        PlatformUser user = findUser(username);
        if (mfaOtpService == null) {
            throw new IllegalStateException("MFA OTP service unconfigured.");
        }
        return mfaOtpService.dispatchStepUpOtp(user, user.getPhone(), "INTERNAL", UUID.randomUUID().toString());
    }

    @Transactional
    public void verifyMfaSetup(String username, String code) {
        PlatformUser user = findUser(username);
        if (user.getMfaSecret() == null) {
            throw new IllegalStateException("MFA setup was not initiated.");
        }

        boolean valid = (totpService != null && totpService.verifyCode(user.getMfaSecret(), code))
                || (mfaOtpService != null && mfaOtpService.verifyOtp(username, code));
        if (!valid) {
            throw new IllegalArgumentException("Invalid verification code. Please check your authenticator clock or SMS OTP.");
        }

        user.setMfaEnabled(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        recordAudit(username, "MFA_ENROLLED_SUCCESS", Map.of("method", "TOTP_OR_OTP"));
    }

    @Transactional
    public void disableMfa(String username, String password, String code) {
        PlatformUser user = findUser(username);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Password is incorrect.");
        }

        boolean codeValid = totpService.verifyCode(user.getMfaSecret(), code)
                || mfaOtpService.verifyOtp(username, code);

        if (!codeValid) {
            throw new IllegalArgumentException("Invalid verification code.");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        recoveryCodeRepository.deleteByUserId(user.getId());

        recordAudit(username, "MFA_DISABLED", Map.of("operator", username));
    }

    @Transactional
    public RegenerateRecoveryCodesResponse regenerateRecoveryCodes(String username) {
        PlatformUser user = findUser(username);
        if (!user.isMfaEnabled()) {
            throw new IllegalStateException("MFA is not enabled on this account.");
        }

        recoveryCodeRepository.deleteByUserId(user.getId());
        List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String code = generateRecoveryCode();
            rawCodes.add(code);
            PlatformUserRecoveryCode rc = new PlatformUserRecoveryCode(
                    UUID.randomUUID(),
                    user.getId(),
                    sha256Hex(code)
            );
            recoveryCodeRepository.save(rc);
        }

        recordAudit(username, "RECOVERY_CODES_REGENERATED", Map.of("count", 10));

        return new RegenerateRecoveryCodesResponse(rawCodes);
    }

    private PlatformUser findUser(String username) {
        return userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    private String generateRecoveryCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append("-");
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing from JVM", e);
        }
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
                    "IDENTITY_ACCOUNT",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to write identity audit log: {}", e.getMessage());
        }
    }
}
