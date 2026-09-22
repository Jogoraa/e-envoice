package et.ut.einvoice.platform.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.identity.domain.PlatformUserInvitation;
import et.ut.einvoice.platform.identity.domain.SystemRole;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.PlatformUserInvitationRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.identity.repository.SystemRoleRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class MasterUserLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(MasterUserLifecycleService.class);

    private final PlatformUserRepository userRepository;
    private final SystemRoleRepository roleRepository;
    private final PlatformUserInvitationRepository invitationRepository;
    private final PlatformUserSessionRepository sessionRepository;
    private final AuditService auditService;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterUserLifecycleService(
            PlatformUserRepository userRepository,
            SystemRoleRepository roleRepository,
            PlatformUserInvitationRepository invitationRepository,
            PlatformUserSessionRepository sessionRepository,
            AuditService auditService,
            EmailProvider emailProvider,
            SmsProvider smsProvider
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.invitationRepository = invitationRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
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

        SystemRole role = roleRepository.findByCode(req.initialRoleCode())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + req.initialRoleCode()));

        // Non-escalation validation: Tenant admins require tenantId
        if ("ROLE_TENANT_ADMIN".equalsIgnoreCase(role.getCode()) && req.tenantId() == null) {
            throw new IllegalArgumentException("Tenant Administrator must be assigned to a specific tenant ID.");
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256Hex(rawToken);

        Instant expiresAt = Instant.now().plusSeconds(72 * 3600); // 72 hours

        PlatformUserInvitation invitation = new PlatformUserInvitation(
                UUID.randomUUID(),
                cleanEmail,
                req.phone() != null ? req.phone().trim() : null,
                req.fullName().trim(),
                role.getCode(),
                req.tenantId(),
                tokenHash,
                expiresAt,
                adminUsername
        );
        invitationRepository.save(invitation);

        try {
            String subject = "[UT-INVOICE] Platform Administrator Invitation";
            String body = "Dear " + req.fullName() + ",\n\n"
                    + "You have been invited by " + adminUsername + " to join the UT Electronic Invoicing Platform as "
                    + role.getName() + ".\n\n"
                    + "Invitation Token: " + rawToken + "\n"
                    + "This link expires in 72 hours.\n\n"
                    + "Directive No. 1142/2026 Core Infrastructure Security";
            emailProvider.sendEmail(cleanEmail, subject, body);
        } catch (Exception e) {
            log.warn("Failed to dispatch invitation email to {}: {}", cleanEmail, e.getMessage());
        }

        recordAudit(adminUsername, "ADMIN_INVITATION_ISSUED", Map.of(
                "invitedEmail", cleanEmail,
                "roleCode", role.getCode(),
                "tenantId", req.tenantId() != null ? req.tenantId().toString() : "GLOBAL"
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

    public List<InvitationSummaryDto> listInvitations(String status) {
        List<PlatformUserInvitation> invitations;
        if (status != null && !status.isBlank()) {
            invitations = invitationRepository.findByStatus(status.trim().toUpperCase(Locale.ROOT));
        } else {
            invitations = invitationRepository.findAll();
        }
        return invitations.stream().map(this::toInvitationDto).toList();
    }

    @Transactional
    public void revokeInvitation(String adminUsername, UUID invitationId) {
        PlatformUserInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        invitation.setStatus("REVOKED");
        invitationRepository.save(invitation);

        recordAudit(adminUsername, "INVITATION_REVOKED", Map.of(
                "invitationId", invitationId.toString(),
                "email", invitation.getEmail()
        ));
    }

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
                user.getPhone(),
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
                inv.getPhone(),
                inv.getFullName(),
                inv.getInitialRoleCode(),
                inv.getTenantId(),
                inv.getExpiresAt(),
                inv.getStatus(),
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
