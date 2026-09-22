package et.ut.einvoice.platform.identity;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.identity.domain.SystemPermission;
import et.ut.einvoice.platform.identity.domain.SystemRole;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.SystemPermissionRepository;
import et.ut.einvoice.platform.identity.repository.SystemRoleRepository;
import et.ut.einvoice.platform.identity.service.MasterRbacService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterRbacAndEffectiveAccessTest {

    @Mock
    private SystemRoleRepository roleRepository;
    @Mock
    private SystemPermissionRepository permissionRepository;
    @Mock
    private PlatformUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private MasterRbacService rbacService;

    private SystemPermission userViewPerm;
    private SystemPermission envEditPerm;
    private SystemPermission secPolicyPerm;
    private SystemRole platformAdminRole;
    private SystemRole tenantAdminRole;

    @BeforeEach
    void setUp() {
        rbacService = new MasterRbacService(
                roleRepository,
                permissionRepository,
                userRepository,
                auditService
        );

        userViewPerm = new SystemPermission(UUID.randomUUID(), "USER_VIEW", "View Users", "Desc", "IDENTITY", "NORMAL");
        envEditPerm = new SystemPermission(UUID.randomUUID(), "ENVIRONMENT_EDIT", "Edit Env", "Desc", "CONFIGURATION", "HIGH");
        secPolicyPerm = new SystemPermission(UUID.randomUUID(), "SECURITY_POLICY_EDIT", "Edit Sec", "Desc", "SECURITY", "CRITICAL");

        platformAdminRole = new SystemRole(
                UUID.randomUUID(),
                "ROLE_PLATFORM_ADMIN",
                "Platform Administrator",
                "Full operational control",
                "PLATFORM",
                true,
                "ACTIVE"
        );
        platformAdminRole.setPermissions(Set.of(userViewPerm, envEditPerm, secPolicyPerm));

        tenantAdminRole = new SystemRole(
                UUID.randomUUID(),
                "ROLE_TENANT_ADMIN",
                "Tenant Administrator",
                "Tenant business oversight",
                "TENANT",
                true,
                "ACTIVE"
        );
        // Tenant role inadvertently or maliciously containing a platform configuration permission
        tenantAdminRole.setPermissions(Set.of(userViewPerm, envEditPerm));
    }

    @Test
    @DisplayName("createRole enforces ROLE_ prefix and validates uniqueness")
    void testCreateRoleValidations() {
        CreateRoleRequest invalidPrefix = new CreateRoleRequest("INVALID_ROLE", "Name", "Desc", "PLATFORM", List.of());
        assertThatThrownBy(() -> rbacService.createRole("platform.admin", invalidPrefix))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with 'ROLE_' prefix");

        when(roleRepository.existsByCode("ROLE_EXISTING")).thenReturn(true);
        CreateRoleRequest duplicate = new CreateRoleRequest("ROLE_EXISTING", "Name", "Desc", "PLATFORM", List.of());
        assertThatThrownBy(() -> rbacService.createRole("platform.admin", duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("calculateEffectiveAccess aggregates permissions for active Platform Administrator")
    void testCalculateEffectiveAccessPlatformAdmin() {
        UUID userId = UUID.randomUUID();
        PlatformUser adminUser = new PlatformUser(
                userId, "platform.admin", "admin@ut.et", "hash", "Admin", "ROLE_PLATFORM_ADMIN", "ACTIVE", Instant.now()
        );
        adminUser.setRoles(Set.of(platformAdminRole));

        when(userRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        EffectiveAccessDto access = rbacService.calculateEffectiveAccess(userId);

        assertThat(access.userId()).isEqualTo(userId);
        assertThat(access.effectivePermissions()).contains("USER_VIEW", "ENVIRONMENT_EDIT", "SECURITY_POLICY_EDIT");
        assertThat(access.prohibitedPermissions()).isEmpty();
    }

    @Test
    @DisplayName("calculateEffectiveAccess strips platform configuration permissions from Tenant Administrator (Server-Side Defense-in-Depth)")
    void testCalculateEffectiveAccessTenantAdminIsolation() {
        UUID userId = UUID.randomUUID();
        PlatformUser tenantUser = new PlatformUser(
                userId, "tenant.admin", "tenant@org.et", "hash", "Tenant Admin", "ROLE_TENANT_ADMIN", "ACTIVE", Instant.now()
        );
        tenantUser.setRoles(Set.of(tenantAdminRole));

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));

        EffectiveAccessDto access = rbacService.calculateEffectiveAccess(userId);

        // ENVIRONMENT_EDIT must be removed and placed into prohibitedPermissions
        assertThat(access.effectivePermissions()).contains("USER_VIEW");
        assertThat(access.effectivePermissions()).doesNotContain("ENVIRONMENT_EDIT");
        assertThat(access.prohibitedPermissions()).contains("ENVIRONMENT_EDIT");
    }

    @Test
    @DisplayName("calculateEffectiveAccess revokes all permissions when account status is SUSPENDED")
    void testCalculateEffectiveAccessSuspendedAccountRevocation() {
        UUID userId = UUID.randomUUID();
        PlatformUser suspendedUser = new PlatformUser(
                userId, "locked.admin", "locked@ut.et", "hash", "Locked Admin", "ROLE_PLATFORM_ADMIN", "SUSPENDED", Instant.now()
        );
        suspendedUser.setRoles(Set.of(platformAdminRole));

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        EffectiveAccessDto access = rbacService.calculateEffectiveAccess(userId);

        // All effective permissions must be revoked and moved to prohibited
        assertThat(access.effectivePermissions()).isEmpty();
        assertThat(access.prohibitedPermissions()).contains("USER_VIEW", "ENVIRONMENT_EDIT", "SECURITY_POLICY_EDIT");
    }

    @Test
    @DisplayName("retireRole rejects retiring canonical system roles")
    void testRetireRoleRejectsSystemRole() {
        when(roleRepository.findByCode("ROLE_PLATFORM_ADMIN")).thenReturn(Optional.of(platformAdminRole));

        assertThatThrownBy(() -> rbacService.retireRole("platform.admin", "ROLE_PLATFORM_ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("System canonical roles cannot be retired");
    }
}
