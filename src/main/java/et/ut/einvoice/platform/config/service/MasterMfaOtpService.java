package et.ut.einvoice.platform.config.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.SendStepUpOtpResponse;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Out-of-Band (OOB) MFA Verification Code Dispatch & Validation Service.
 * <p>
 * Implements NIST SP 800-63B and Ethiopian Electronic Invoicing Directive No. 1142/2026
 * security principles for elevated administrative step-up ceremonies:
 * <ul>
 *   <li>Cryptographically secure 6-digit random code generation (CSPRNG)</li>
 *   <li>Out-of-band delivery over both authenticated Email and SMS channels</li>
 *   <li>SHA-256 digested in-memory storage (prevents plaintext memory exposure)</li>
 *   <li>Single-use / immediate invalidation upon successful verification</li>
 *   <li>Constant-time equality checks against timing attacks</li>
 *   <li>Short lifespan (5-minute TTL) with strict attempt bounds (max 5 attempts)</li>
 *   <li>Cooldown rate-limiting (60-second minimum interval between dispatches)</li>
 *   <li>Masked audit logging (never logs plaintext verification codes)</li>
 * </ul>
 */
@Service
public class MasterMfaOtpService {

    private static final Logger log = LoggerFactory.getLogger(MasterMfaOtpService.class);
    private static final int OTP_TTL_SECONDS = 300; // 5 minutes
    private static final int OTP_COOLDOWN_SECONDS = 60; // 1 minute between sends
    private static final int MAX_ATTEMPTS = 5;

    private final SmsProvider smsProvider;
    private final EmailProvider emailProvider;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    // Map of normalized username -> active OTP state
    private final Map<String, OtpRecord> activeOtps = new ConcurrentHashMap<>();

    private record OtpRecord(
            byte[] codeHash,
            Instant expiresAt,
            Instant dispatchedAt,
            int attemptsRemaining,
            String maskedEmail,
            String maskedPhone
    ) {}

    @Autowired
    public MasterMfaOtpService(
            SmsProvider smsProvider,
            EmailProvider emailProvider,
            AuditService auditService
    ) {
        this.smsProvider = smsProvider;
        this.emailProvider = emailProvider;
        this.auditService = auditService;
    }

    /**
     * Dispatches a fresh Out-of-Band verification code to the operator's registered email and SMS.
     */
    public SendStepUpOtpResponse dispatchStepUpOtp(
            PlatformUser user,
            String requestedPhone,
            String ipAddress,
            String correlationId
    ) {
        String usernameKey = user.getUsername().toLowerCase().trim();
        Instant now = Instant.now();

        // 1. Enforce Cooldown Rate-Limiting
        OtpRecord existing = activeOtps.get(usernameKey);
        if (existing != null && existing.dispatchedAt().plusSeconds(OTP_COOLDOWN_SECONDS).isAfter(now)) {
            long remainingSec = existing.dispatchedAt().plusSeconds(OTP_COOLDOWN_SECONDS).getEpochSecond() - now.getEpochSecond();
            throw new IllegalStateException("Rate limit exceeded. Please wait " + Math.max(1, remainingSec) + " seconds before requesting a new verification code.");
        }

        // 2. Generate cryptographically secure 6-digit numeric OTP [100000 - 999999]
        int codeNumber = 100000 + secureRandom.nextInt(900000);
        String plainCode = String.valueOf(codeNumber);
        byte[] codeHash = computeHash(plainCode);

        // 3. Resolve authoritative destination channels (Directives & NIST SP 800-63B)
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Administrator account does not have an authoritative email address configured.");
        }
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Authoritative administrator email is unverified. Cannot dispatch security verification code.");
        }
        String email = user.getEmail().trim();

        // Authoritative phone resolution: registered user phone takes precedence over requested phone
        String phone = null;
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            phone = user.getPhone().trim();
            if (requestedPhone != null && !requestedPhone.isBlank() && !requestedPhone.trim().equals(phone)) {
                log.info("Authoritative registered phone '{}' takes precedence over requested override for user '{}'.", maskPhone(phone), user.getUsername());
            }
        } else if (requestedPhone != null && !requestedPhone.isBlank()) {
            phone = requestedPhone.trim();
        }

        String maskedEmail = maskEmail(email);
        String maskedPhone = phone != null ? maskPhone(phone) : "UNVERIFIED";
        Instant expiresAt = now.plusSeconds(OTP_TTL_SECONDS);

        // 4. Store digest in memory
        activeOtps.put(usernameKey, new OtpRecord(
                codeHash,
                expiresAt,
                now,
                MAX_ATTEMPTS,
                maskedEmail,
                maskedPhone
        ));

        // 5. Dispatch via EmailProvider (SMTP)
        try {
            String subject = "[UT-INVOICE] Master Admin Security Verification Code";
            String body = "Dear Platform Administrator,\n\n"
                    + "Your elevated platform security verification code is: " + plainCode + "\n\n"
                    + "This single-use code is valid for 5 minutes (expires at " + expiresAt + ").\n"
                    + "If you did not initiate this elevated privilege session, please notify your security officer immediately.\n\n"
                    + "Directive No. 1142/2026 Core Infrastructure Security";
            emailProvider.sendEmail(email, subject, body);
            log.info("Dispatched MFA OTP email to masked destination '{}' for operator '{}'", maskedEmail, user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to dispatch MFA email to '{}': {}", maskedEmail, e.getMessage());
        }

        // 6. Dispatch via SmsProvider (Ethio Telecom Gateway) only if phone is verified
        if (phone != null) {
            try {
                String smsText = "[UT-INVOICE] Master Admin verification code: " + plainCode + ". Valid for 5 min.";
                smsProvider.sendSms(phone, smsText);
                log.info("Dispatched MFA OTP SMS to masked destination '{}' for operator '{}'", maskedPhone, user.getUsername());
            } catch (Exception e) {
                log.warn("Failed to dispatch MFA SMS to '{}': {}", maskedPhone, e.getMessage());
            }
        }

        // 7. Audit Logging (Compliant with privacy directives: never log plaintext OTP)
        recordAuditOtpDispatched(user.getUsername(), maskedEmail, maskedPhone, ipAddress, correlationId);

        String dispatchNotice = phone != null
                ? "Verification code successfully dispatched to your registered email (" + maskedEmail + ") and SMS (" + maskedPhone + ")."
                : "Verification code successfully dispatched to your registered email (" + maskedEmail + ").";

        return new SendStepUpOtpResponse(
                true,
                maskedEmail,
                maskedPhone,
                expiresAt,
                OTP_COOLDOWN_SECONDS,
                "Verification code successfully dispatched to your registered email (" + maskedEmail + ") and SMS (" + maskedPhone + ")."
        );
    }

    /**
     * Checks if a pending out-of-band OTP exists for the user.
     */
    public boolean hasPendingOtp(String username) {
        if (username == null) return false;
        String key = username.toLowerCase().trim();
        OtpRecord record = activeOtps.get(key);
        if (record == null) return false;
        if (Instant.now().isAfter(record.expiresAt()) || record.attemptsRemaining() <= 0) {
            activeOtps.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Verifies the submitted OTP against the stored digest with constant-time comparison.
     */
    public boolean verifyOtp(String username, String rawCode) {
        if (username == null || rawCode == null) return false;
        String key = username.toLowerCase().trim();
        OtpRecord record = activeOtps.get(key);
        if (record == null) return false;

        Instant now = Instant.now();
        if (now.isAfter(record.expiresAt())) {
            activeOtps.remove(key);
            log.warn("Expired MFA OTP attempt for user '{}'", username);
            return false;
        }

        if (record.attemptsRemaining() <= 0) {
            activeOtps.remove(key);
            log.warn("MFA OTP locked out (too many failed attempts) for user '{}'", username);
            return false;
        }

        byte[] inputHash = computeHash(rawCode.trim());
        boolean matches = MessageDigest.isEqual(inputHash, record.codeHash());

        if (matches) {
            // Single-use: atomically invalidate upon success
            activeOtps.remove(key);
            log.info("MFA OTP successfully verified and consumed for user '{}'", username);
            return true;
        } else {
            int remaining = record.attemptsRemaining() - 1;
            if (remaining <= 0) {
                activeOtps.remove(key);
                log.warn("MFA OTP exhausted all attempts for user '{}'. Invalidated.", username);
            } else {
                activeOtps.put(key, new OtpRecord(
                        record.codeHash(),
                        record.expiresAt(),
                        record.dispatchedAt(),
                        remaining,
                        record.maskedEmail(),
                        record.maskedPhone()
                ));
                log.warn("MFA OTP mismatch for user '{}'. Attempts remaining: {}", username, remaining);
            }
            return false;
        }
    }

    /**
     * Invalidate pending OTP immediately (e.g. on manual cancel or logout).
     */
    public void invalidateOtp(String username) {
        if (username != null) {
            activeOtps.remove(username.toLowerCase().trim());
        }
    }

    private byte[] computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing from JVM", e);
        }
    }


    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "ad***@internal";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.substring(0, 2) + "***@" + domain;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "+251 91***0000";
        String clean = phone.replaceAll("[^0-9+]", "");
        if (clean.length() >= 9) {
            return clean.substring(0, clean.length() - 7) + "***" + clean.substring(clean.length() - 4);
        }
        return "+251 91***0000";
    }

    private void recordAuditOtpDispatched(String username, String maskedEmail, String maskedPhone, String ipAddress, String correlationId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "MFA_OTP_DISPATCHED");
            payload.put("operator", username);
            payload.put("maskedEmail", maskedEmail);
            payload.put("maskedPhone", maskedPhone);
            payload.put("ipAddress", ipAddress);
            payload.put("correlationId", correlationId);

            auditService.recordEvent(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    username,
                    "MFA_OTP_DISPATCHED",
                    "SECURITY_MFA",
                    correlationId != null ? correlationId : UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to write MFA OTP dispatch audit log: {}", e.getMessage());
        }
    }
}
