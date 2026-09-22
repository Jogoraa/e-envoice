package et.ut.einvoice.platform.identity.controller;

import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.service.MasterRbacService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/master/rbac")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
public class MasterRbacController {

    private final MasterRbacService rbacService;

    public MasterRbacController(MasterRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<SystemRoleDto>> listRoles() {
        return ResponseEntity.ok(rbacService.listRoles());
    }

    @GetMapping("/roles/{code}")
    public ResponseEntity<SystemRoleDto> getRole(@PathVariable String code) {
        return ResponseEntity.ok(rbacService.getRole(code));
    }

    @PostMapping("/roles")
    public ResponseEntity<SystemRoleDto> createRole(
            Authentication auth,
            @RequestBody CreateRoleRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(rbacService.createRole(adminUsername, req));
    }

    @PutMapping("/roles/{code}")
    public ResponseEntity<SystemRoleDto> updateRole(
            Authentication auth,
            @PathVariable String code,
            @RequestBody UpdateRoleRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(rbacService.updateRole(adminUsername, code, req));
    }

    @DeleteMapping("/roles/{code}")
    public ResponseEntity<Map<String, Object>> retireRole(
            Authentication auth,
            @PathVariable String code
    ) {
        String adminUsername = resolveUsername(auth);
        rbacService.retireRole(adminUsername, code);
        return ResponseEntity.ok(Map.of("success", true, "message", "Role retired successfully."));
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<SystemPermissionDto>> listPermissions(
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(rbacService.listPermissions(category));
    }

    @GetMapping("/effective-access/{userId}")
    public ResponseEntity<EffectiveAccessDto> getEffectiveAccess(@PathVariable UUID userId) {
        return ResponseEntity.ok(rbacService.calculateEffectiveAccess(userId));
    }

    private String resolveUsername(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return "platform.admin";
        }
        return auth.getName();
    }
}
