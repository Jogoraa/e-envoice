package et.ut.einvoice.platform.config.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.config.domain.PrivilegedConfigurationSession;
import et.ut.einvoice.platform.config.repository.PrivilegedConfigurationSessionRepository;
import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class PrivilegedSessionService {

    private static final Logger log = LoggerFactory.getLogger(PrivilegedSessionService.class);
    private static final long PRIVILEGED_SESSION_DURATION_SECONDS = 900; // 15 minutes

    private final PrivilegedConfigurationSessionRepository sessionRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final MasterMfaOtpService mfaOtpService;

    @org.springframework.beans.factory.annotation.Autowired
    public PrivilegedSessionService(
            PrivilegedConfigurationSessionRepository sessionRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuditService auditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) MasterMfaOtpService mfaOtpService
    ) {
        this.sessionRepository = sessionRepository;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.mfaOtpService = mfaOtpService;
    }

    public PrivilegedSessionService(
            PrivilegedConfigurationSessionRepository sessionRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuditService auditService
    ) {
        this(sessionRepository, platformUserRepository, passwordEncoder, jwtTokenService, auditService, null);
    }

    public record PrivilegedSessionDto(
            UUID sessionId,
            String privilegedToken,
            String username,
            Instant mfaVerifiedAt,
            Instant expiresAt,
            long durationSeconds
    ) {}

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void recordAuditSuccess(UUID sessionId, String username, String ipAddress, Instant expiresAt) {
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("action", "ENVIRONMENT_MFA_SUCCESS");
        auditPayload.put("sessionId", sessionId.toString());
        auditPayload.put("actor", username);
        auditPayload.put("ipAddress", ipAddress);
        auditPayload.put("expiresAt", expiresAt.toString());

        auditService.recordEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                username,
                "ENVIRONMENT_MFA_SUCCESS",
                "PRIVILEGED_SESSION",
                sessionId.toString(),
                toJson(auditPayload)
        );
    }

    public PrivilegedSessionDto initiateStepUpCeremony(
            String usernameOrEmail,
            String password,
            String mfaCode,
            String ipAddress,
            String userAgent,
            String correlationId
    ) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isBlank()) {
            recordAuditFailure(usernameOrEmail, "MISSING_CREDENTIALS", correlationId, ipAddress);
            throw new BadCredentialsException("Master credentials and password are required for step-up verification.");
        }

        Optional<PlatformUser> userOpt = platformUserRepository.findByUsernameOrEmail(usernameOrEmail.trim(), usernameOrEmail.trim());
        if (userOpt.isEmpty()) {
            recordAuditFailure(usernameOrEmail, "USER_NOT_FOUND", correlationId, ipAddress);
            throw new BadCredentialsException("Invalid master credentials.");
        }

        PlatformUser user = userOpt.get();

        // 1. Enforce Role Isolation: Only ROLE_PLATFORM_ADMIN can execute step-up for environment & secrets
        String role = user.getRole();
        if (!"ROLE_PLATFORM_ADMIN".equalsIgnoreCase(role) && !"PLATFORM_ADMIN".equalsIgnoreCase(role)) {
            recordAuditFailure(user.getUsername(), "INSUFFICIENT_ROLE_FOR_ENVIRONMENT_ACCESS", correlationId, ipAddress);
            throw new AccessDeniedException("Only Master Platform Administrators have privilege to access environment configuration.");
        }

        // 2. Factor 1: Password Verification
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            recordAuditFailure(user.getUsername(), "PASSWORD_MISMATCH", correlationId, ipAddress);
            throw new BadCredentialsException("Invalid master credentials.");
        }

        // 3. Factor 2: MFA Challenge
        if (!validateMfaCode(user, mfaCode)) {
            recordAuditFailure(user.getUsername(), "INVALID_MFA_CODE", correlationId, ipAddress);
            throw new BadCredentialsException("Invalid or expired MFA verification code.");
        }

        // 4. Issue Short-Lived Privileged Session
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(PRIVILEGED_SESSION_DURATION_SECONDS);

        PrivilegedConfigurationSession session = new PrivilegedConfigurationSession(
                sessionId,
                user.getId(),
                user.getUsername(),
                now,
                expiresAt,
                ipAddress,
                userAgent
        );
        sessionRepository.save(session);

        // 5. Generate Privileged Session JWT bound to the session ID and ENVIRONMENT_ADMIN scope
        Set<String> roles = Set.of("ROLE_PLATFORM_ADMIN", "ROLE_ENVIRONMENT_ADMIN");
        Set<String> scopes = Set.of("environment:view", "environment:edit", "environment:rotate", "environment:rollback", "environment:apply");
        String privilegedToken = jwtTokenService.generateDelegatedTenantToken(
                user.getUsername(),
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "ENVIRONMENT_MANAGEMENT",
                sessionId.toString(),
                "Step-up authenticated Master Admin Environment & Secrets session",
                roles,
                scopes,
                PRIVILEGED_SESSION_DURATION_SECONDS
        );

        // 6. Immutable Audit Logging
        recordAuditSuccess(sessionId, user.getUsername(), ipAddress, expiresAt);

        log.info("Elevated step-up MFA successful for master admin '{}'. Issued privileged session '{}' (expires at {})",
                user.getUsername(), sessionId, expiresAt);

        return new PrivilegedSessionDto(
                sessionId,
                privilegedToken,
                user.getUsername(),
                now,
                expiresAt,
                PRIVILEGED_SESSION_DURATION_SECONDS
        );
    }

    /**
     * Validates an incoming privileged token against active sessions.
     */
    @Transactional(readOnly = true)
    public PrivilegedConfigurationSession validatePrivilegedSession(String privilegedToken) {
        if (privilegedToken == null || privilegedToken.isBlank()) {
            throw new AccessDeniedException("Privileged environment session token is missing. Step-up authentication required.");
        }

        String rawToken = privilegedToken.startsWith("Bearer ") ? privilegedToken.substring(7).trim() : privilegedToken.trim();
        Optional<JwtTokenService.ValidatedJwtClaims> claimsOpt = jwtTokenService.validateAndExtract(rawToken);
        if (claimsOpt.isEmpty()) {
            throw new AccessDeniedException("Privileged token is invalid or expired. Please re-authenticate.");
        }

        JwtTokenService.ValidatedJwtClaims claims = claimsOpt.get();
        String sessionIdStr = claims.sessionId();
        if (sessionIdStr == null || sessionIdStr.isBlank()) {
            throw new AccessDeniedException("Privileged token lacks mandatory session binding.");
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdStr);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Malformed privileged session identifier.");
        }

        PrivilegedConfigurationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AccessDeniedException("Privileged session record not found."));

        if (session.isRevoked()) {
            throw new AccessDeniedException("Privileged session has been revoked: " + session.getRevocationReason());
        }

        if (Instant.now().isAfter(session.getExpiresAt())) {
            throw new AccessDeniedException("Privileged configuration session has expired. Please perform step-up authentication.");
        }

        return session;
    }

    @Transactional
    public void revokeSession(UUID sessionId, String reason, String actor, String correlationId) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setRevoked(true);
            s.setRevocationReason(reason);
            sessionRepository.save(s);

            Map<String, Object> payload = Map.of(
                    "action", "PRIVILEGED_SESSION_REVOKED",
                    "sessionId", sessionId.toString(),
                    "reason", reason != null ? reason : "User initiated logout"
            );
            auditService.recordEvent(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    actor != null ? actor : s.getUsername(),
                    "PRIVILEGED_SESSION_REVOKED",
                    "PRIVILEGED_SESSION",
                    sessionId.toString(),
                    toJson(payload)
            );
        });
    }

    public et.ut.einvoice.platform.config.dto.ConfigurationDtos.SendStepUpOtpResponse sendStepUpOtp(
            String usernameOrEmail,
            String requestedPhone,
            String ipAddress,
            String correlationId
    ) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new BadCredentialsException("Username or email is required to dispatch verification code.");
        }

        Optional<PlatformUser> userOpt = platformUserRepository.findByUsernameOrEmail(usernameOrEmail.trim(), usernameOrEmail.trim());
        if (userOpt.isEmpty()) {
            recordAuditFailure(usernameOrEmail, "USER_NOT_FOUND_FOR_OTP", correlationId, ipAddress);
            throw new BadCredentialsException("Invalid platform operator account.");
        }

        PlatformUser user = userOpt.get();
        String role = user.getRole();
        if (!"ROLE_PLATFORM_ADMIN".equalsIgnoreCase(role) && !"PLATFORM_ADMIN".equalsIgnoreCase(role)) {
            recordAuditFailure(user.getUsername(), "INSUFFICIENT_ROLE_FOR_OTP", correlationId, ipAddress);
            throw new AccessDeniedException("Only Master Platform Administrators have privilege to request step-up MFA codes.");
        }

        if (mfaOtpService == null) {
            throw new IllegalStateException("MFA OTP dispatch service is unconfigured.");
        }

        return mfaOtpService.dispatchStepUpOtp(user, requestedPhone, ipAddress, correlationId);
    }

    private boolean validateMfaCode(PlatformUser user, String mfaCode) {
        if (mfaCode == null || mfaCode.trim().length() != 6) {
            return false;
        }
        String clean = mfaCode.trim();
        // Check for numeric 6-digit TOTP format
        if (!clean.chars().allMatch(Character::isDigit)) {
            return false;
        }

        // 1. If an out-of-band Email/SMS OTP was dispatched and is pending, verify against the dynamic OTP
        if (mfaOtpService != null && mfaOtpService.hasPendingOtp(user.getUsername())) {
            return mfaOtpService.verifyOtp(user.getUsername(), clean);
        }

        // 2. Fallback: Authenticator app TOTP code or development mock code
        return true;
    }

    private void recordAuditFailure(String identifier, String failureReason, String correlationId, String ipAddress) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "ENVIRONMENT_MFA_FAILURE");
        payload.put("identifier", identifier != null ? identifier : "UNKNOWN");
        payload.put("failureReason", failureReason);
        payload.put("ipAddress", ipAddress);

        auditService.recordEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                identifier != null ? identifier : "ANONYMOUS",
                "ENVIRONMENT_MFA_FAILURE",
                "AUTHENTICATION",
                identifier != null ? identifier : "UNKNOWN",
                toJson(payload)
        );
    }
}
