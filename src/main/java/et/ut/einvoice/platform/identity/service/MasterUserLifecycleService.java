package et.ut.einvoice.platform.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.config.service.MasterMfaOtpService;
import et.ut.einvoice.platform.identity.domain.PlatformUserInvitation;
import et.ut.einvoice.platform.identity.domain.SystemRole;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.PlatformUserInvitationRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.identity.repository.SystemRoleRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MasterUserLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(MasterUserLifecycleService.class);
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,128}$");
    private static final Pattern INVITATION_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final ZoneId ADDIS_ABABA = ZoneId.of("Africa/Addis_Ababa");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu 'at' h:mm a z", Locale.ENGLISH);

    private final PlatformUserRepository userRepository;
    private final SystemRoleRepository roleRepository;
    private final PlatformUserInvitationRepository invitationRepository;
    private final PlatformUserSessionRepository sessionRepository;
    private final AuditService auditService;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final PasswordEncoder passwordEncoder;
    private final String appPublicUrl;
    private final long invitationExpirationHours;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public MasterUserLifecycleService(
            PlatformUserRepository userRepository,
            SystemRoleRepository roleRepository,
            PlatformUserInvitationRepository invitationRepository,
            PlatformUserSessionRepository sessionRepository,
            AuditService auditService,
            EmailProvider emailProvider,
            SmsProvider smsProvider,
            PasswordEncoder passwordEncoder,
            @Value("${platform.invitation.public-url}") String appPublicUrl,
            @Value("${platform.invitation.expiration-hours:72}") long invitationExpirationHours
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.invitationRepository = invitationRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
        this.passwordEncoder = passwordEncoder;
        this.appPublicUrl = validateAppPublicUrl(appPublicUrl);
        this.invitationExpirationHours = Math.max(1, invitationExpirationHours);
    }

    /** Convenience constructor retained for focused unit tests. */
    public MasterUserLifecycleService(
            PlatformUserRepository userRepository,
            SystemRoleRepository roleRepository,
            PlatformUserInvitationRepository invitationRepository,
            PlatformUserSessionRepository sessionRepository,
            AuditService auditService,
            EmailProvider emailProvider,
            SmsProvider smsProvider
    ) {
        this(userRepository, roleRepository, invitationRepository, sessionRepository, auditService, emailProvider,
                smsProvider, new BCryptPasswordEncoder(), "https://invoice.utsolutionsplc.com", 72);
    }

    public List<PlatformUserSummaryDto> listUsers(String search, String role, String status) {
        List<PlatformUser> all = userRepository.findAll();
        String searchLower = (search != null && !search.isBlank()) ? search.trim().toLowerCase(Locale.ROOT) : null;
        String roleFilter = (role != null && !role.isBlank()) ? role.trim().toUpperCase(Locale.ROOT) : null;
        String statusFilter = (status != null && !status.isBlank()) ? status.trim().toUpperCase(Locale.ROOT) : null;

        return all.stream()
                .filter(u -> {
                    if (searchLower != null) {
                        boolean matchName = u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(searchLower);
                        boolean matchUser = u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(searchLower);
                        boolean matchEmail = u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(searchLower);
                        if (!matchName && !matchUser && !matchEmail) return false;
                    }
                    if (statusFilter != null && !statusFilter.equalsIgnoreCase(u.getStatus())) {
                        return false;
                    }
                    if (roleFilter != null) {
                        boolean hasRole = false;
                        if (u.getRole() != null && u.getRole().equalsIgnoreCase(roleFilter)) hasRole = true;
                        if (u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getCode().equalsIgnoreCase(roleFilter))) hasRole = true;
                        if (!hasRole) return false;
                    }
                    return true;
                })
                .map(this::toSummaryDto)
                .toList();
    }

    public PlatformUserSummaryDto getUser(UUID userId) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return toSummaryDto(user);
    }

    @Transactional
    public InvitationSummaryDto inviteAdmin(String adminUsername, InviteAdminRequest req) {
        if (req.email() == null || !req.email().contains("@")) {
            throw new IllegalArgumentException("Valid email address is required.");
        }
        String cleanEmail = req.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            throw new IllegalArgumentException("An administrator account already exists with email: " + cleanEmail);
        }

        if (!invitationRepository.findByEmailAndStatus(cleanEmail, "PENDING").isEmpty()) {
            throw new IllegalStateException("A pending invitation already exists for this email. Resend or revoke it first.");
        }

        SystemRole role = roleRepository.findByCode(req.initialRoleCode())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + req.initialRoleCode()));

        // Non-escalation validation: Tenant admins require tenantId
        if ("ROLE_TENANT_ADMIN".equalsIgnoreCase(role.getCode()) && req.tenantId() == null) {
            throw new IllegalArgumentException("Tenant Administrator must be assigned to a specific tenant ID.");
        }

        InvitationCredential credential = newInvitationCredential();

        PlatformUserInvitation invitation = new PlatformUserInvitation(
                UUID.randomUUID(),
                cleanEmail,
                req.phone() != null ? req.phone().trim() : null,
                req.fullName().trim(),
                role.getCode(),
                req.tenantId(),
                credential.tokenHash(),
                credential.expiresAt(),
                adminUsername
        );
        invitationRepository.save(invitation);
        dispatchInvitation(adminUsername, invitation, role, credential.rawToken(), "INVITATION_CREATED");

        recordAudit(adminUsername, "INVITATION_CREATED", Map.of(
                "invitationId", invitation.getId().toString(),
                "invitedEmail", maskEmail(cleanEmail),
                "roleCode", role.getCode(),
                "tenantId", req.tenantId() != null ? req.tenantId().toString() : "GLOBAL"
        ));

        return toInvitationDto(invitation);
    }

    /**
     * Returns only safe invitation metadata. The bearer token is deliberately never logged,
     * persisted outside its SHA-256 digest, or returned in this response.
     */
    @Transactional
    public InvitationValidationDto validateInvitation(String rawToken) {
        if (!isWellFormedInvitationToken(rawToken)) {
            return invalidInvitation("INVALID");
        }

        String tokenHash = sha256Hex(rawToken);
        Optional<PlatformUserInvitation> invitationOpt = invitationRepository.findByTokenHash(tokenHash);
        if (invitationOpt.isEmpty()) {
            return invalidInvitation("INVALID");
        }

        PlatformUserInvitation invitation = invitationOpt.get();
        if (!MessageDigest.isEqual(
                invitation.getTokenHash().getBytes(StandardCharsets.UTF_8),
                tokenHash.getBytes(StandardCharsets.UTF_8))) {
            return invalidInvitation("INVALID");
        }

        String state = invitationState(invitation);
        if (!"VALID".equals(state)) {
            return invalidInvitation(state);
        }

        recordAudit("SYSTEM", "INVITATION_VALIDATED", Map.of(
                "invitationId", invitation.getId().toString(),
                "invitedEmail", maskEmail(invitation.getEmail())
        ));

        return new InvitationValidationDto(
                true,
                "VALID",
                invitation.getEmail(),
                invitation.getFullName(),
                invitation.getInitialRoleCode(),
                invitation.getTenantId() == null ? "UT Invoice Platform" : "Assigned UT Invoice tenant",
                invitation.getExpiresAt(),
                false
        );
    }

    /** Atomically creates the account and consumes the invitation exactly once. */
    @Transactional
    public InvitationAcceptanceResponse acceptInvitation(AcceptInvitationRequest request) {
        if (!isWellFormedInvitationToken(request.token())) {
            throw new IllegalArgumentException("This invitation link is invalid.");
        }
        if (!Objects.equals(request.password(), request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (!PASSWORD_PATTERN.matcher(request.password()).matches()) {
            throw new IllegalArgumentException(
                    "Password must be at least 12 characters and include uppercase, lowercase, number, and special character.");
        }

        String tokenHash = sha256Hex(request.token());
        PlatformUserInvitation invitation = invitationRepository.findWithLockByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("This invitation link is invalid."));
        if (!MessageDigest.isEqual(
                invitation.getTokenHash().getBytes(StandardCharsets.UTF_8),
                tokenHash.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("This invitation link is invalid.");
        }

        String state = invitationState(invitation);
        if (!"VALID".equals(state)) {
            throw new IllegalStateException(invitationStateMessage(state));
        }
        if (userRepository.findByEmail(invitation.getEmail()).isPresent()) {
            recordAudit("SYSTEM", "INVITATION_FAILED", Map.of(
                    "invitationId", invitation.getId().toString(),
                    "invitedEmail", maskEmail(invitation.getEmail()),
                    "reason", "ACCOUNT_ALREADY_EXISTS"
            ));
            throw new IllegalStateException("An account already exists for this invitation.");
        }

        SystemRole role = roleRepository.findByCode(invitation.getInitialRoleCode())
                .filter(existingRole -> "ACTIVE".equalsIgnoreCase(existingRole.getStatus()))
                .orElseThrow(() -> new IllegalStateException("The role assigned to this invitation is no longer available."));

        Instant now = Instant.now();
        PlatformUser user = new PlatformUser(
                UUID.randomUUID(),
                nextUsername(invitation),
                invitation.getEmail(),
                passwordEncoder.encode(request.password()),
                invitation.getFullName(),
                role.getCode(),
                "ACTIVE",
                now
        );
        user.setPhone(invitation.getPhone());
        user.setEmailVerified(true);
        user.setPasswordChangedAt(now);
        user.setRoles(Set.of(role));
        userRepository.save(user);

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(now);
        invitationRepository.save(invitation);

        recordAudit("SYSTEM", "INVITATION_ACCEPTED", Map.of(
                "invitationId", invitation.getId().toString(),
                "targetUserId", user.getId().toString(),
                "invitedEmail", maskEmail(invitation.getEmail()),
                "roleCode", role.getCode()
        ));

        return new InvitationAcceptanceResponse(true, false, loginPathFor(role.getCode()));
    }

    @Transactional
    public InvitationSummaryDto resendInvitation(String adminUsername, UUID invitationId) {
        PlatformUserInvitation invitation = invitationRepository.findWithLockById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found."));
        if ("ACCEPTED".equalsIgnoreCase(invitation.getStatus()) || "REVOKED".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalStateException("Only pending or expired invitations can be resent.");
        }
        if (userRepository.findByEmail(invitation.getEmail()).isPresent()) {
            throw new IllegalStateException("An account already exists for this invitation.");
        }

        SystemRole role = roleRepository.findByCode(invitation.getInitialRoleCode())
                .filter(existingRole -> "ACTIVE".equalsIgnoreCase(existingRole.getStatus()))
                .orElseThrow(() -> new IllegalStateException("The role assigned to this invitation is no longer available."));
        InvitationCredential credential = newInvitationCredential();
        invitation.setTokenHash(credential.tokenHash());
        invitation.setExpiresAt(credential.expiresAt());
        invitation.setStatus("PENDING");
        invitation.setAcceptedAt(null);
        invitation.setResendCount(invitation.getResendCount() + 1);
        invitationRepository.save(invitation);

        dispatchInvitation(adminUsername, invitation, role, credential.rawToken(), "INVITATION_RESENT");
        recordAudit(adminUsername, "INVITATION_RESENT", Map.of(
                "invitationId", invitation.getId().toString(),
                "invitedEmail", maskEmail(invitation.getEmail()),
                "roleCode", role.getCode()
        ));
        return toInvitationDto(invitation);
    }

    @Transactional
    public PlatformUserSummaryDto updateUserStatus(String adminUsername, UUID userId, UpdateUserStatusRequest req) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Self-action protection
        if (adminUsername.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("Self-lockout protection: You cannot modify your own administrative status.");
        }

        String newStatus = req.status().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "SUSPENDED", "LOCKED", "DISABLED").contains(newStatus)) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        // Prevent disabling sole active PLATFORM_ADMIN
        if (!"ACTIVE".equals(newStatus) && "ROLE_PLATFORM_ADMIN".equalsIgnoreCase(user.getRole())) {
            long activePlatformAdmins = userRepository.findAll().stream()
                    .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()) && "ROLE_PLATFORM_ADMIN".equalsIgnoreCase(u.getRole()))
                    .count();
            if (activePlatformAdmins <= 1) {
                throw new IllegalStateException("Cannot deactivate the sole remaining active Platform Administrator.");
            }
        }

        String oldStatus = user.getStatus();
        user.setStatus(newStatus);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Terminate all live sessions if account is suspended, locked, or disabled
        if (!"ACTIVE".equals(newStatus)) {
            var sessions = sessionRepository.findByUserId(user.getId());
            for (var sess : sessions) {
                if (!sess.isRevoked()) {
                    sess.setRevoked(true);
                    sess.setRevokedAt(Instant.now());
                    sess.setRevocationReason("STATUS_CHANGED_TO_" + newStatus);
                    sessionRepository.save(sess);
                }
            }
        }

        recordAudit(adminUsername, "USER_STATUS_UPDATED", Map.of(
                "targetUser", user.getUsername(),
                "oldStatus", oldStatus,
                "newStatus", newStatus,
                "reason", req.reason() != null ? req.reason() : "N/A"
        ));

        return toSummaryDto(user);
    }

    @Transactional
    public PlatformUserSummaryDto updateUserRoles(String adminUsername, UUID userId, UpdateUserRolesRequest req) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        if (req.roleCodes() == null || req.roleCodes().isEmpty()) {
            throw new IllegalArgumentException("At least one administrative role must be assigned.");
        }

        Set<SystemRole> newRoles = new HashSet<>();
        for (String code : req.roleCodes()) {
            SystemRole r = roleRepository.findByCode(code.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Role code not recognized: " + code));
            newRoles.add(r);
        }

        user.setRoles(newRoles);
        // Sync primary role to the first assigned role
        user.setRole(req.roleCodes().get(0).trim());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Force session refresh by revoking active sessions so new claims/scopes take effect
        var sessions = sessionRepository.findByUserId(user.getId());
        for (var sess : sessions) {
            if (!sess.isRevoked()) {
                sess.setRevoked(true);
                sess.setRevokedAt(Instant.now());
                sess.setRevocationReason("ROLES_MODIFIED");
                sessionRepository.save(sess);
            }
        }

        recordAudit(adminUsername, "USER_ROLES_UPDATED", Map.of(
                "targetUser", user.getUsername(),
                "newRoles", req.roleCodes()
        ));

        return toSummaryDto(user);
    }

    @Transactional
    public void resetUserMfa(String adminUsername, UUID userId, String reason) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        recordAudit(adminUsername, "ADMIN_MFA_RESET", Map.of(
                "targetUser", user.getUsername(),
                "reason", reason != null ? reason : "Administrative MFA reset"
        ));
    }

    @Transactional
    public List<InvitationSummaryDto> listInvitations(String status) {
        List<PlatformUserInvitation> invitations;
        if (status != null && !status.isBlank()) {
            invitations = invitationRepository.findByStatus(status.trim().toUpperCase(Locale.ROOT));
        } else {
            invitations = invitationRepository.findAll();
        }
        return invitations.stream().map(invitation -> {
            invitationState(invitation);
            return toInvitationDto(invitation);
        }).toList();
    }

    @Transactional
    public void revokeInvitation(String adminUsername, UUID invitationId) {
        PlatformUserInvitation invitation = invitationRepository.findWithLockById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found."));

        if ("ACCEPTED".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalStateException("An accepted invitation cannot be revoked.");
        }

        invitation.setStatus("REVOKED");
        invitationRepository.save(invitation);

        recordAudit(adminUsername, "INVITATION_REVOKED", Map.of(
                "invitationId", invitationId.toString(),
                "invitedEmail", maskEmail(invitation.getEmail())
        ));
    }

    private void dispatchInvitation(
            String actor,
            PlatformUserInvitation invitation,
            SystemRole role,
            String rawToken,
            String lifecycleAction
    ) {
        String invitationUrl = invitationUrl(rawToken);
        InvitationEmail email = invitationEmail(invitation, role, invitationUrl);
        invitation.setLastDeliveryAttemptAt(Instant.now());
        invitation.setDeliveryError(null);

        try {
            boolean accepted = emailProvider.sendHtmlEmail(invitation.getEmail(), "You're invited to UT Invoice", email.html(), email.plainText());
            invitation.setEmailDeliveryStatus(accepted ? "SENT" : "FAILED");
            if (accepted) {
                recordAudit(actor, "INVITATION_EMAIL_SENT", Map.of(
                        "invitationId", invitation.getId().toString(),
                        "invitedEmail", maskEmail(invitation.getEmail()),
                        "lifecycleAction", lifecycleAction
                ));
            } else {
                invitation.setDeliveryError("EMAIL_DELIVERY_FAILED");
                recordAudit(actor, "INVITATION_FAILED", Map.of(
                        "invitationId", invitation.getId().toString(),
                        "channel", "EMAIL",
                        "reason", "DELIVERY_FAILED"
                ));
            }
        } catch (Exception e) {
            invitation.setEmailDeliveryStatus("FAILED");
            invitation.setDeliveryError("EMAIL_DELIVERY_FAILED");
            log.warn("Invitation email dispatch failed for {} ({})", maskEmail(invitation.getEmail()), e.getClass().getSimpleName());
            recordAudit(actor, "INVITATION_FAILED", Map.of(
                    "invitationId", invitation.getId().toString(),
                    "channel", "EMAIL",
                    "reason", "DELIVERY_FAILED"
            ));
        }

        if (invitation.getPhone() == null || invitation.getPhone().isBlank()) {
            invitation.setSmsDeliveryStatus("NOT_CONFIGURED");
            invitationRepository.save(invitation);
            return;
        }

        try {
            String sms = "UT Invoice: You were invited as " + role.getName() + ". Activate your account: "
                    + invitationUrl + ". Expires " + formatExpiry(invitation.getExpiresAt()) + ".";
            boolean accepted = smsProvider.sendSms(invitation.getPhone(), sms);
            invitation.setSmsDeliveryStatus(accepted ? "SENT" : "FAILED");
            if (accepted) {
                recordAudit(actor, "INVITATION_SMS_SENT", Map.of(
                        "invitationId", invitation.getId().toString(),
                        "phoneMasked", MasterMfaOtpService.maskPhone(invitation.getPhone()),
                        "lifecycleAction", lifecycleAction
                ));
            } else {
                recordAudit(actor, "INVITATION_FAILED", Map.of(
                        "invitationId", invitation.getId().toString(),
                        "channel", "SMS",
                        "reason", "DELIVERY_FAILED"
                ));
            }
        } catch (Exception e) {
            invitation.setSmsDeliveryStatus("FAILED");
            log.warn("Invitation SMS dispatch failed for {} ({})", MasterMfaOtpService.maskPhone(invitation.getPhone()), e.getClass().getSimpleName());
            recordAudit(actor, "INVITATION_FAILED", Map.of(
                    "invitationId", invitation.getId().toString(),
                    "channel", "SMS",
                    "reason", "DELIVERY_FAILED"
            ));
        }
        invitationRepository.save(invitation);
    }

    private InvitationCredential newInvitationCredential() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new InvitationCredential(rawToken, sha256Hex(rawToken), Instant.now().plusSeconds(invitationExpirationHours * 3600));
    }

    private String invitationUrl(String rawToken) {
        return UriComponentsBuilder.fromUriString(appPublicUrl)
                .path("/auth/invitations/accept")
                .queryParam("token", rawToken)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    private InvitationEmail invitationEmail(PlatformUserInvitation invitation, SystemRole role, String invitationUrl) {
        String displayName = htmlEscape(invitation.getFullName());
        String roleName = htmlEscape(role.getName());
        String safeUrl = htmlEscape(invitationUrl);
        String expiry = formatExpiry(invitation.getExpiresAt());
        String plainText = "Hello " + invitation.getFullName() + ",\n\n"
                + "You have been invited by Platform Administration to join UT Invoice as " + role.getName() + ".\n\n"
                + "To activate your account, open this secure one-time link:\n" + invitationUrl + "\n\n"
                + "Expires: " + expiry + "\n\n"
                + "For security, this link can only be used once. If you were not expecting this invitation, you can ignore this message.\n\n"
                + "UT Invoice\nUT Solutions PLC\nElectronic Invoicing Platform";
        String html = """
                <!doctype html><html><body style=\"margin:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#172033\">
                <table role=\"presentation\" width=\"100%%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:28px 12px\">
                <table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden\">
                <tr><td style=\"background:#0b2545;padding:24px 32px;color:#ffffff;font-weight:bold;font-size:22px\">UT INVOICE</td></tr>
                <tr><td style=\"padding:32px\"><h1 style=\"font-size:24px;margin:0 0 20px\">Activate your account</h1>
                <p>Hello %s,</p><p>You have been invited by Platform Administration to join UT Invoice as <strong>%s</strong>.</p>
                <p>Use the secure one-time link below to create your password and activate your account.</p>
                <p style=\"margin:28px 0\"><a href=\"%s\" style=\"display:inline-block;background:#0b5cab;color:#ffffff;text-decoration:none;padding:14px 22px;border-radius:5px;font-weight:bold\">Activate Account</a></p>
                <p style=\"font-size:13px;word-break:break-all\">If the button does not work, open this link: <a href=\"%s\">%s</a></p>
                <p><strong>Expires:</strong> %s</p><p style=\"color:#4b5563\">For security, this invitation can only be used once. If you were not expecting it, you can ignore this message.</p>
                </td></tr><tr><td style=\"padding:20px 32px;background:#f4f6f8;color:#667085;font-size:12px\">UT Invoice &mdash; Electronic Invoicing Platform<br>UT Solutions PLC</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(displayName, roleName, safeUrl, safeUrl, safeUrl, htmlEscape(expiry));
        return new InvitationEmail(plainText, html);
    }

    private String invitationState(PlatformUserInvitation invitation) {
        if ("PENDING".equalsIgnoreCase(invitation.getStatus()) && Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
            recordAudit("SYSTEM", "INVITATION_EXPIRED", Map.of(
                    "invitationId", invitation.getId().toString(),
                    "invitedEmail", maskEmail(invitation.getEmail())
            ));
        }
        return switch (invitation.getStatus().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "VALID";
            case "ACCEPTED" -> "ALREADY_USED";
            case "EXPIRED" -> "EXPIRED";
            case "REVOKED" -> "REVOKED";
            default -> "INVALID";
        };
    }

    private InvitationValidationDto invalidInvitation(String state) {
        return new InvitationValidationDto(false, state, null, null, null, null, null, false);
    }

    private String invitationStateMessage(String state) {
        return switch (state) {
            case "ALREADY_USED" -> "This invitation has already been used.";
            case "EXPIRED" -> "This invitation has expired.";
            case "REVOKED" -> "This invitation has been revoked.";
            default -> "This invitation link is invalid.";
        };
    }

    private boolean isWellFormedInvitationToken(String rawToken) {
        return rawToken != null && INVITATION_TOKEN_PATTERN.matcher(rawToken).matches();
    }

    private String nextUsername(PlatformUserInvitation invitation) {
        String localPart = invitation.getEmail().substring(0, invitation.getEmail().indexOf('@'))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (localPart.isBlank()) localPart = "user";
        localPart = localPart.substring(0, Math.min(localPart.length(), 48));
        String candidate = localPart;
        int suffix = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            String numberedSuffix = "-" + suffix++;
            candidate = localPart.substring(0, Math.min(localPart.length(), 64 - numberedSuffix.length())) + numberedSuffix;
        }
        return candidate;
    }

    private String loginPathFor(String roleCode) {
        return "ROLE_PLATFORM_ADMIN".equalsIgnoreCase(roleCode) ? "/admin/login" : "/saas/login";
    }

    private String formatExpiry(Instant instant) {
        return EXPIRY_FORMAT.format(instant.atZone(ADDIS_ABABA));
    }

    private String validateAppPublicUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalStateException("APP_PUBLIC_URL must be configured for invitation delivery.");
        }
        String normalized = candidate.trim().replaceAll("/+$", "");
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            if (!uri.isAbsolute() || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (Exception e) {
            throw new IllegalStateException("APP_PUBLIC_URL must be an absolute HTTP(S) URL.");
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, 1) + "***" + email.substring(at);
    }

    private String htmlEscape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private record InvitationCredential(String rawToken, String tokenHash, Instant expiresAt) {}

    private record InvitationEmail(String plainText, String html) {}

    private PlatformUserSummaryDto toSummaryDto(PlatformUser user) {
        List<String> roleCodes = new ArrayList<>();
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            roleCodes = user.getRoles().stream().map(SystemRole::getCode).toList();
        } else if (user.getRole() != null) {
            roleCodes = List.of(user.getRole());
        }

        return new PlatformUserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                roleCodes,
                user.getStatus(),
                user.isMfaEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }

    private InvitationSummaryDto toInvitationDto(PlatformUserInvitation inv) {
        return new InvitationSummaryDto(
                inv.getId(),
                inv.getEmail(),
                inv.getFullName(),
                inv.getInitialRoleCode(),
                inv.getTenantId(),
                inv.getExpiresAt(),
                inv.getStatus(),
                inv.getEmailDeliveryStatus(),
                inv.getSmsDeliveryStatus(),
                inv.getLastDeliveryAttemptAt(),
                inv.getResendCount(),
                inv.getCreatedAt()
        );
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
                    "IDENTITY_LIFECYCLE",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to write user lifecycle audit log: {}", e.getMessage());
        }
    }
}
