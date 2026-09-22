package et.ut.einvoice.platform.identity.controller;

import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.service.MasterUserLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/master/users")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
public class MasterUserManagementController {

    private final MasterUserLifecycleService userLifecycleService;

    public MasterUserManagementController(MasterUserLifecycleService userLifecycleService) {
        this.userLifecycleService = userLifecycleService;
    }

    @GetMapping
    public ResponseEntity<List<PlatformUserSummaryDto>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(userLifecycleService.listUsers(search, role, status));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PlatformUserSummaryDto> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userLifecycleService.getUser(userId));
    }

    @PostMapping("/invite")
    public ResponseEntity<InvitationSummaryDto> inviteAdmin(
            Authentication auth,
            @RequestBody InviteAdminRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(userLifecycleService.inviteAdmin(adminUsername, req));
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<PlatformUserSummaryDto> updateUserStatus(
            Authentication auth,
            @PathVariable UUID userId,
            @RequestBody UpdateUserStatusRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(userLifecycleService.updateUserStatus(adminUsername, userId, req));
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<PlatformUserSummaryDto> updateUserRoles(
            Authentication auth,
            @PathVariable UUID userId,
            @RequestBody UpdateUserRolesRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(userLifecycleService.updateUserRoles(adminUsername, userId, req));
    }

    @PostMapping("/{userId}/reset-mfa")
    public ResponseEntity<Map<String, Object>> resetUserMfa(
            Authentication auth,
            @PathVariable UUID userId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String adminUsername = resolveUsername(auth);
        String reason = body != null ? body.get("reason") : "Administrative reset";
        userLifecycleService.resetUserMfa(adminUsername, userId, reason);
        return ResponseEntity.ok(Map.of("success", true, "message", "MFA reset successfully."));
    }

    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationSummaryDto>> listInvitations(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(userLifecycleService.listInvitations(status));
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeInvitation(
            Authentication auth,
            @PathVariable UUID invitationId
    ) {
        String adminUsername = resolveUsername(auth);
        userLifecycleService.revokeInvitation(adminUsername, invitationId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Invitation revoked."));
    }

    private String resolveUsername(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return "platform.admin";
        }
        return auth.getName();
    }
}
