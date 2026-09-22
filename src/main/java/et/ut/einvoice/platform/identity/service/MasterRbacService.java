package et.ut.einvoice.platform.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.identity.domain.SystemPermission;
import et.ut.einvoice.platform.identity.domain.SystemRole;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.SystemPermissionRepository;
import et.ut.einvoice.platform.identity.repository.SystemRoleRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MasterRbacService {

    private static final Logger log = LoggerFactory.getLogger(MasterRbacService.class);

    private final SystemRoleRepository roleRepository;
    private final SystemPermissionRepository permissionRepository;
    private final PlatformUserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterRbacService(
            SystemRoleRepository roleRepository,
            SystemPermissionRepository permissionRepository,
            PlatformUserRepository userRepository,
            AuditService auditService
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<SystemRoleDto> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::toRoleDto)
                .toList();
    }

    public SystemRoleDto getRole(String code) {
        SystemRole role = roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + code));
        return toRoleDto(role);
    }

    public List<SystemPermissionDto> listPermissions(String category) {
        List<SystemPermission> list;
        if (category != null && !category.isBlank()) {
            list = permissionRepository.findByCategory(category.trim().toUpperCase(Locale.ROOT));
        } else {
            list = permissionRepository.findAll();
        }
        return list.stream().map(this::toPermissionDto).toList();
    }

    @Transactional
    public SystemRoleDto createRole(String adminUsername, CreateRoleRequest req) {
        if (req.code() == null || !req.code().startsWith("ROLE_")) {
            throw new IllegalArgumentException("Role code must start with 'ROLE_' prefix.");
        }
        String cleanCode = req.code().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByCode(cleanCode)) {
            throw new IllegalArgumentException("Role with code " + cleanCode + " already exists.");
        }

        Set<SystemPermission> permissions = new HashSet<>();
        if (req.permissionCodes() != null) {
            for (String permCode : req.permissionCodes()) {
                permissionRepository.findByCode(permCode.trim())
                        .ifPresent(permissions::add);
            }
        }

        String scope = req.scope() != null ? req.scope().trim().toUpperCase(Locale.ROOT) : "PLATFORM";

        SystemRole role = new SystemRole(
                UUID.randomUUID(),
                cleanCode,
                req.name().trim(),
                req.description(),
                scope,
                false, // Custom roles are non-system
                "ACTIVE"
        );
        role.setPermissions(permissions);
        roleRepository.save(role);

        recordAudit(adminUsername, "ROLE_CREATED", Map.of(
                "roleCode", role.getCode(),
                "scope", role.getScope(),
                "permissionsCount", permissions.size()
        ));

        return toRoleDto(role);
    }

    @Transactional
    public SystemRoleDto updateRole(String adminUsername, String code, UpdateRoleRequest req) {
        SystemRole role = roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + code));

        if (req.name() != null && !req.name().isBlank()) {
            role.setName(req.name().trim());
        }
        if (req.description() != null) {
            role.setDescription(req.description().trim());
        }

        // Only allow modifying permissions if not core immutable system role
        if (req.permissionCodes() != null) {
            if (role.isSystem() && "ROLE_PLATFORM_ADMIN".equalsIgnoreCase(role.getCode())) {
                throw new IllegalStateException("Platform Administrator role permissions are immutable.");
            }

            Set<SystemPermission> newPerms = new HashSet<>();
            for (String pCode : req.permissionCodes()) {
                permissionRepository.findByCode(pCode.trim())
                        .ifPresent(newPerms::add);
            }
            role.setPermissions(newPerms);
        }

        role.setUpdatedAt(Instant.now());
        roleRepository.save(role);

        recordAudit(adminUsername, "ROLE_UPDATED", Map.of(
                "roleCode", role.getCode(),
                "permissionsCount", role.getPermissions().size()
        ));

        return toRoleDto(role);
    }

    @Transactional
    public void retireRole(String adminUsername, String code) {
        SystemRole role = roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + code));

        if (role.isSystem()) {
            throw new IllegalStateException("System canonical roles cannot be retired.");
        }

        // Check active users assigned this role
        boolean hasAssignedUsers = userRepository.findAll().stream()
                .anyMatch(u -> u.getRoles() != null && u.getRoles().contains(role));
        if (hasAssignedUsers) {
            throw new IllegalStateException("Cannot retire role: Currently assigned to one or more platform administrators.");
        }

        role.setStatus("RETIRED");
        role.setUpdatedAt(Instant.now());
        roleRepository.save(role);

        recordAudit(adminUsername, "ROLE_RETIRED", Map.of("roleCode", role.getCode()));
    }

    public EffectiveAccessDto calculateEffectiveAccess(UUID userId) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<String> assignedRoleCodes = new ArrayList<>();
        Set<SystemPermission> allPerms = new HashSet<>();

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            for (SystemRole r : user.getRoles()) {
                assignedRoleCodes.add(r.getCode());
                allPerms.addAll(r.getPermissions());
            }
        } else if (user.getRole() != null) {
            assignedRoleCodes.add(user.getRole());
            roleRepository.findByCode(user.getRole())
                    .ifPresent(r -> allPerms.addAll(r.getPermissions()));
        }

        Set<String> effective = allPerms.stream()
                .map(SystemPermission::getCode)
                .collect(Collectors.toSet());

        Set<String> prohibited = new HashSet<>();

        // If user is not ACTIVE, all permissions are revoked/prohibited
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            prohibited.addAll(effective);
            effective.clear();
        }

        // Tenant scope defense-in-depth: Tenant admins can NEVER have platform configuration permissions
        boolean isTenantAdmin = assignedRoleCodes.contains("ROLE_TENANT_ADMIN") && !assignedRoleCodes.contains("ROLE_PLATFORM_ADMIN");
        if (isTenantAdmin) {
            List<String> platformForbidden = List.of(
                    "ENVIRONMENT_VIEW", "ENVIRONMENT_EDIT", "ENVIRONMENT_SECRET_ROTATE",
                    "ENVIRONMENT_ROLLBACK", "ENVIRONMENT_APPLY", "SECURITY_POLICY_EDIT",
                    "USER_SUSPEND", "USER_DISABLE", "ACCESS_REVIEW_MANAGE"
            );
            for (String f : platformForbidden) {
                if (effective.remove(f)) {
                    prohibited.add(f);
                }
            }
        }

        return new EffectiveAccessDto(
                user.getId(),
                user.getUsername(),
                assignedRoleCodes,
                effective,
                prohibited,
                Instant.now()
        );
    }

    private SystemRoleDto toRoleDto(SystemRole role) {
        List<SystemPermissionDto> perms = role.getPermissions() != null
                ? role.getPermissions().stream().map(this::toPermissionDto).toList()
                : List.of();

        return new SystemRoleDto(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getScope(),
                role.isSystem(),
                role.getStatus(),
                perms.size(),
                perms
        );
    }

    private SystemPermissionDto toPermissionDto(SystemPermission p) {
        return new SystemPermissionDto(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getCategory(),
                p.getRiskLevel()
        );
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
                    "IDENTITY_RBAC",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.warn("Failed to write RBAC audit log: {}", e.getMessage());
        }
    }
}
