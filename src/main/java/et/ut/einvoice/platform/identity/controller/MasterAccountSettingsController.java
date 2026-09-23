package et.ut.einvoice.platform.identity.controller;

import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.service.MasterAccountService;
import et.ut.einvoice.platform.identity.service.MasterSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/master/account")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
public class MasterAccountSettingsController {

    private final MasterAccountService accountService;
    private final MasterSessionService sessionService;

    public MasterAccountSettingsController(
            MasterAccountService accountService,
            MasterSessionService sessionService
    ) {
        this.accountService = accountService;
        this.sessionService = sessionService;
    }

    @GetMapping("/profile")
    public ResponseEntity<MasterProfileResponse> getProfile(Authentication auth) {
        String username = resolveUsername(auth);
        return ResponseEntity.ok(accountService.getProfile(username));
    }

    @PutMapping("/profile")
    public ResponseEntity<MasterProfileResponse> updateProfile(
            Authentication auth,
            @RequestBody UpdateProfileRequest req
    ) {
        String username = resolveUsername(auth);
        return ResponseEntity.ok(accountService.updateProfile(username, req));
    }

    @PostMapping("/email/initiate")
    public ResponseEntity<Map<String, Object>> initiateEmailChange(
            Authentication auth,
            @RequestBody InitiateEmailChangeRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.initiateEmailChange(username, req.newEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Verification code dispatched to your new email address."
        ));
    }

    @PostMapping("/email/confirm")
    public ResponseEntity<Map<String, Object>> confirmEmailChange(
            Authentication auth,
            @RequestBody ConfirmEmailChangeRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.confirmEmailChange(username, req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email address verified and updated successfully."
        ));
    }

    @PostMapping("/phone/initiate")
    public ResponseEntity<Map<String, Object>> initiatePhoneVerification(
            Authentication auth,
            @RequestBody InitiatePhoneVerificationRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.initiatePhoneVerification(username, req.phone());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Verification code dispatched via SMS."
        ));
    }

    @PostMapping("/phone/confirm")
    public ResponseEntity<Map<String, Object>> confirmPhoneVerification(
            Authentication auth,
            @RequestBody ConfirmPhoneVerificationRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.confirmPhoneVerification(username, req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Phone number verified successfully."
        ));
    }

    @PostMapping("/password/change")
    public ResponseEntity<Map<String, Object>> changePassword(
            Authentication auth,
            @RequestBody ChangePasswordRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.changePassword(username, req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password changed successfully. All previous sessions have been invalidated."
        ));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa(Authentication auth) {
        String username = resolveUsername(auth);
        return ResponseEntity.ok(accountService.setupMfa(username));
    }

    @PostMapping("/mfa/send-otp")
    public ResponseEntity<Map<String, Object>> sendMfaOtp(Authentication auth) {
        String username = resolveUsername(auth);
        var resp = accountService.sendMfaOtp(username);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", resp.message(),
                "maskedEmail", resp.maskedEmail(),
                "maskedPhone", resp.maskedPhone(),
                "expiresAt", resp.expiresAt().toString(),
                "cooldownSeconds", resp.cooldownSeconds()
        ));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<Map<String, Object>> verifyMfaSetup(
            Authentication auth,
            @RequestBody VerifyMfaSetupRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.verifyMfaSetup(username, req.code());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Multi-Factor Authentication enrolled and enabled successfully."
        ));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Map<String, Object>> disableMfa(
            Authentication auth,
            @RequestBody DisableMfaRequest req
    ) {
        String username = resolveUsername(auth);
        accountService.disableMfa(username, req.password(), req.code());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Multi-Factor Authentication disabled."
        ));
    }

    @PostMapping("/mfa/recovery-codes/regenerate")
    public ResponseEntity<RegenerateRecoveryCodesResponse> regenerateRecoveryCodes(Authentication auth) {
        String username = resolveUsername(auth);
        return ResponseEntity.ok(accountService.regenerateRecoveryCodes(username));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<PlatformSessionDto>> getSessions(
            Authentication auth,
            HttpServletRequest request
    ) {
        String username = resolveUsername(auth);
        String tokenHash = extractTokenHash(request);
        return ResponseEntity.ok(sessionService.listUserSessions(username, tokenHash));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeSession(
            Authentication auth,
            @PathVariable UUID sessionId,
            @RequestBody(required = false) RevokeSessionRequest req
    ) {
        String username = resolveUsername(auth);
        String reason = req != null && req.reason() != null ? req.reason() : "Operator terminated session";
        sessionService.revokeSession(username, sessionId, reason);
        return ResponseEntity.ok(Map.of("success", true, "message", "Session terminated."));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<Map<String, Object>> revokeOtherSessions(
            Authentication auth,
            HttpServletRequest request
    ) {
        String username = resolveUsername(auth);
        String tokenHash = extractTokenHash(request);
        sessionService.revokeAllOtherSessions(username, tokenHash);
        return ResponseEntity.ok(Map.of("success", true, "message", "All other active sessions have been terminated."));
    }

    private String resolveUsername(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return "platform.admin";
        }
        return auth.getName();
    }

    private String extractTokenHash(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException ignored) {
            }
        }
        return null;
    }
}
