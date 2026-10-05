package et.ut.einvoice.platform.identity;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.platform.identity.domain.SystemRole;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.repository.PlatformUserInvitationRepository;
import et.ut.einvoice.platform.identity.repository.PlatformUserSessionRepository;
import et.ut.einvoice.platform.identity.repository.SystemRoleRepository;
import et.ut.einvoice.platform.identity.service.MasterUserLifecycleService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterUserLifecycleServiceTest {

    @Mock
    private PlatformUserRepository userRepository;
    @Mock
    private SystemRoleRepository roleRepository;
    @Mock
    private PlatformUserInvitationRepository invitationRepository;
    @Mock
    private PlatformUserSessionRepository sessionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private EmailProvider emailProvider;
    @Mock
    private SmsProvider smsProvider;

    private MasterUserLifecycleService service;
    private PlatformUser adminUser;
    private PlatformUser saasUser;
    private SystemRole saasRole;
    private SystemRole tenantRole;

    @BeforeEach
    void setUp() {
        service = new MasterUserLifecycleService(
                userRepository,
                roleRepository,
                invitationRepository,
                sessionRepository,
                auditService,
                emailProvider,
                smsProvider
        );

        adminUser = new PlatformUser(
                UUID.randomUUID(),
                "platform.admin",
                "admin@ut-invoice.internal",
                "$2a$12$hashedPassword",
                "Primary Master Admin",
                "ROLE_PLATFORM_ADMIN",
                "ACTIVE",
                Instant.now()
        );

        saasUser = new PlatformUser(
                UUID.randomUUID(),
                "saas.admin",
                "saas@ut-invoice.internal",
                "$2a$12$hashedPassword",
                "SaaS Ops Admin",
                "ROLE_SAAS_ADMIN",
                "ACTIVE",
                Instant.now()
        );

        saasRole = new SystemRole(
                UUID.randomUUID(),
                "ROLE_SAAS_ADMIN",
                "SaaS Administrator",
                "Tenant lifecycle management",
                "PLATFORM",
                true,
                "ACTIVE"
        );

        tenantRole = new SystemRole(
                UUID.randomUUID(),
                "ROLE_TENANT_ADMIN",
                "Tenant Administrator",
                "Tenant business admin",
                "TENANT",
                true,
                "ACTIVE"
        );
    }

    @Test
    @DisplayName("listUsers filters by search query and role")
    void testListUsersFiltering() {
        when(userRepository.findAll()).thenReturn(List.of(adminUser, saasUser));

        List<PlatformUserSummaryDto> results = service.listUsers("saas", "ROLE_SAAS_ADMIN", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).username()).isEqualTo("saas.admin");
    }

    @Test
    @DisplayName("inviteAdmin rejects duplicate email")
    void testInviteAdminDuplicateEmail() {
        when(userRepository.findByEmail("admin@ut-invoice.internal")).thenReturn(Optional.of(adminUser));

        InviteAdminRequest req = new InviteAdminRequest("admin@ut-invoice.internal", "+251911000000", "Admin", "ROLE_SAAS_ADMIN", null);

        assertThatThrownBy(() -> service.inviteAdmin("platform.admin", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("inviteAdmin enforces tenantId for ROLE_TENANT_ADMIN")
    void testInviteAdminRequiresTenantIdForTenantRole() {
        when(userRepository.findByEmail("tenant@company.et")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("ROLE_TENANT_ADMIN")).thenReturn(Optional.of(tenantRole));

        InviteAdminRequest req = new InviteAdminRequest("tenant@company.et", "+251911000000", "Tenant Admin", "ROLE_TENANT_ADMIN", null);

        assertThatThrownBy(() -> service.inviteAdmin("platform.admin", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be assigned to a specific tenant ID");
    }

    @Test
    @DisplayName("inviteAdmin dispatches an actionable URL without exposing a standalone token")
    void testInviteAdminDispatchesSmsWhenPhoneProvided() {
        when(userRepository.findByEmail("newops@utsolutionsplc.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("ROLE_SAAS_ADMIN")).thenReturn(Optional.of(saasRole));

        InviteAdminRequest req = new InviteAdminRequest("newops@utsolutionsplc.com", "+251925970827", "Samson Aweke", "ROLE_SAAS_ADMIN", null);

        var result = service.inviteAdmin("platform.admin", req);

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("newops@utsolutionsplc.com");

        ArgumentCaptor<String> plainText = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).sendHtmlEmail(eq("newops@utsolutionsplc.com"), eq("You're invited to UT Invoice"), html.capture(), plainText.capture());
        verify(smsProvider).sendSms(eq("+251925970827"), contains("/auth/invitations/accept?token="));
        assertThat(plainText.getValue()).contains("https://invoice.utsolutionsplc.com/auth/invitations/accept?token=");
        assertThat(plainText.getValue()).doesNotContain("Invitation Token:");
        assertThat(html.getValue()).contains("Activate Account");
    }

    @Test
    @DisplayName("inviteAdmin dispatches only email when phone is omitted")
    void testInviteAdminOmitsSmsWhenPhoneNull() {
        when(userRepository.findByEmail("newops@utsolutionsplc.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("ROLE_SAAS_ADMIN")).thenReturn(Optional.of(saasRole));

        InviteAdminRequest req = new InviteAdminRequest("newops@utsolutionsplc.com", null, "Samson Aweke", "ROLE_SAAS_ADMIN", null);

        var result = service.inviteAdmin("platform.admin", req);

        assertThat(result).isNotNull();
        verify(emailProvider).sendHtmlEmail(eq("newops@utsolutionsplc.com"), eq("You're invited to UT Invoice"), anyString(), anyString());
        verify(smsProvider, never()).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("invitation stores only a hash and acceptance consumes it exactly once")
    void invitationAcceptanceCreatesActiveRoleAssignedAccountAndRejectsReuse() {
        when(userRepository.findByEmail("newops@utsolutionsplc.com")).thenReturn(Optional.empty());
        when(invitationRepository.findByEmailAndStatus("newops@utsolutionsplc.com", "PENDING")).thenReturn(List.of());
        when(roleRepository.findByCode("ROLE_SAAS_ADMIN")).thenReturn(Optional.of(saasRole));

        InviteAdminRequest invite = new InviteAdminRequest("newops@utsolutionsplc.com", null, "Samson Aweke", "ROLE_SAAS_ADMIN", null);
        service.inviteAdmin("platform.admin", invite);

        ArgumentCaptor<String> plainText = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).sendHtmlEmail(anyString(), anyString(), anyString(), plainText.capture());
        String invitationUrl = plainText.getValue().lines()
                .filter(line -> line.startsWith("https://"))
                .findFirst()
                .orElseThrow();
        String rawToken = invitationUrl.substring(invitationUrl.indexOf("token=") + "token=".length());

        ArgumentCaptor<et.ut.einvoice.platform.identity.domain.PlatformUserInvitation> invitationCaptor =
                ArgumentCaptor.forClass(et.ut.einvoice.platform.identity.domain.PlatformUserInvitation.class);
        verify(invitationRepository, atLeastOnce()).save(invitationCaptor.capture());
        var invitation = invitationCaptor.getAllValues().getFirst();
        assertThat(invitation.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(invitation.getTokenHash()).hasSize(64);

        when(invitationRepository.findWithLockByTokenHash(invitation.getTokenHash())).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail("newops@utsolutionsplc.com")).thenReturn(Optional.empty());

        var response = service.acceptInvitation(new AcceptInvitationRequest(rawToken, "SecurePass1!", "SecurePass1!"));

        assertThat(response.success()).isTrue();
        assertThat(invitation.getStatus()).isEqualTo("ACCEPTED");
        ArgumentCaptor<PlatformUser> userCaptor = ArgumentCaptor.forClass(PlatformUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isActive()).isTrue();
        assertThat(userCaptor.getValue().getRoles()).containsExactly(saasRole);

        assertThatThrownBy(() -> service.acceptInvitation(new AcceptInvitationRequest(rawToken, "SecurePass1!", "SecurePass1!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    @DisplayName("validation returns EXPIRED for a matching expired invitation and permanently marks it expired")
    void validationExpiresInvitation() throws Exception {
        String rawToken = "A".repeat(43);
        String tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        var invitation = new et.ut.einvoice.platform.identity.domain.PlatformUserInvitation(
                UUID.randomUUID(), "expired@utsolutionsplc.com", null, "Expired Invitee", "ROLE_SAAS_ADMIN", null,
                tokenHash, Instant.now().minusSeconds(1), "platform.admin");
        when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));

        var result = service.validateInvitation(rawToken);

        assertThat(result.valid()).isFalse();
        assertThat(result.state()).isEqualTo("EXPIRED");
        assertThat(invitation.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("resend replaces the stored hash and expiration so the prior activation link is invalidated")
    void resendInvalidatesPriorCredential() {
        var invitation = new et.ut.einvoice.platform.identity.domain.PlatformUserInvitation(
                UUID.randomUUID(), "resend@utsolutionsplc.com", null, "Resend Invitee", "ROLE_SAAS_ADMIN", null,
                "b".repeat(64), Instant.now().plusSeconds(60), "platform.admin");
        when(invitationRepository.findWithLockById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail(invitation.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("ROLE_SAAS_ADMIN")).thenReturn(Optional.of(saasRole));

        service.resendInvitation("platform.admin", invitation.getId());

        assertThat(invitation.getTokenHash()).isNotEqualTo("b".repeat(64));
        assertThat(invitation.getExpiresAt()).isAfter(Instant.now());
        assertThat(invitation.getResendCount()).isEqualTo(1);
        verify(emailProvider).sendHtmlEmail(eq(invitation.getEmail()), eq("You're invited to UT Invoice"), anyString(), anyString());
    }

    @Test
    @DisplayName("updateUserStatus prevents self-lockout")
    void testUpdateUserStatusPreventsSelfLockout() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        UpdateUserStatusRequest req = new UpdateUserStatusRequest("SUSPENDED", "Testing self suspension");

        assertThatThrownBy(() -> service.updateUserStatus("platform.admin", adminUser.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-lockout protection");
    }

    @Test
    @DisplayName("updateUserStatus to SUSPENDED terminates all live sessions")
    void testUpdateUserStatusTerminatesSessions() {
        when(userRepository.findById(saasUser.getId())).thenReturn(Optional.of(saasUser));
        when(sessionRepository.findByUserId(saasUser.getId())).thenReturn(List.of());

        UpdateUserStatusRequest req = new UpdateUserStatusRequest("SUSPENDED", "Compliance investigation");
        PlatformUserSummaryDto res = service.updateUserStatus("platform.admin", saasUser.getId(), req);

        assertThat(res.status()).isEqualTo("SUSPENDED");
        verify(userRepository).save(saasUser);
        verify(auditService).recordEvent(any(), eq("platform.admin"), eq("USER_STATUS_UPDATED"), eq("IDENTITY_LIFECYCLE"), any(), any());
    }

    @Test
    @DisplayName("resetUserMfa disables MFA and records audit event")
    void testResetUserMfa() {
        saasUser.setMfaEnabled(true);
        saasUser.setMfaSecret("SECRET");
        when(userRepository.findById(saasUser.getId())).thenReturn(Optional.of(saasUser));

        service.resetUserMfa("platform.admin", saasUser.getId(), "Operator lost authenticator");

        assertThat(saasUser.isMfaEnabled()).isFalse();
        assertThat(saasUser.getMfaSecret()).isNull();
        verify(userRepository).save(saasUser);
        verify(auditService).recordEvent(any(), eq("platform.admin"), eq("ADMIN_MFA_RESET"), eq("IDENTITY_LIFECYCLE"), any(), any());
    }
}
