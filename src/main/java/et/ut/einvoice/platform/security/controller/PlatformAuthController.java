package et.ut.einvoice.platform.security.controller;

import et.ut.einvoice.platform.security.dto.PlatformAuthResponse;
import et.ut.einvoice.platform.security.dto.PlatformLoginRequest;
import et.ut.einvoice.platform.security.dto.TenantAuthResponse;
import et.ut.einvoice.platform.security.dto.TenantLoginRequest;
import et.ut.einvoice.platform.security.service.PlatformAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    public PlatformAuthController(PlatformAuthService platformAuthService) {
        this.platformAuthService = platformAuthService;
    }

    /**
     * Authenticate Tenant User (TIN + Username + Password)
     */
    @PostMapping({"/auth/login", "/public/auth/login"})
    public ResponseEntity<TenantAuthResponse> loginTenant(@Valid @RequestBody TenantLoginRequest request) {
        TenantAuthResponse response = platformAuthService.authenticateTenantUser(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticate SaaS / Platform Operator (Username/Email + Password)
     */
    @PostMapping({"/saas/auth/login", "/master/auth/login", "/public/saas/auth/login"})
    public ResponseEntity<PlatformAuthResponse> loginPlatform(@Valid @RequestBody PlatformLoginRequest request) {
        PlatformAuthResponse response = platformAuthService.authenticatePlatformOperator(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Dispatch Login Verification Code (SMS / Email OTP)
     */
    @PostMapping({"/saas/auth/send-otp", "/master/auth/send-otp", "/public/saas/auth/send-otp"})
    public ResponseEntity<java.util.Map<String, Object>> sendLoginOtp(
            @RequestBody java.util.Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String usernameOrEmail = body.get("usernameOrEmail");
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            usernameOrEmail = body.get("username");
        }
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            usernameOrEmail = body.get("email");
        }
        String ipAddress = request.getRemoteAddr();
        String correlationId = java.util.UUID.randomUUID().toString();
        var resp = platformAuthService.sendLoginOtp(usernameOrEmail, ipAddress, correlationId);
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", resp.message(),
                "maskedEmail", resp.maskedEmail(),
                "maskedPhone", resp.maskedPhone(),
                "expiresAt", resp.expiresAt().toString(),
                "cooldownSeconds", resp.cooldownSeconds()
        ));
    }
}
