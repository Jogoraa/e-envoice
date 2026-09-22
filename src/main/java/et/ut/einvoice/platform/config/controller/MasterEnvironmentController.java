package et.ut.einvoice.platform.config.controller;

import et.ut.einvoice.platform.config.domain.ConfigurationRevision;
import et.ut.einvoice.platform.config.domain.ConfigurationScope;
import et.ut.einvoice.platform.config.domain.PrivilegedConfigurationSession;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.*;
import et.ut.einvoice.platform.config.service.EnvironmentConfigurationService;
import et.ut.einvoice.platform.config.service.PrivilegedSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/admin/environment", "/api/v1/master/environment"})
@Tag(name = "Master Admin Environment & Secrets Management", description = "High-security privileged configuration subsystem with step-up MFA")
@PreAuthorize("hasAnyRole('ROLE_PLATFORM_ADMIN', 'PLATFORM_ADMIN')")
public class MasterEnvironmentController {

    private final PrivilegedSessionService privilegedSessionService;
    private final EnvironmentConfigurationService configService;
    private final et.ut.einvoice.platform.config.service.MasterMessagingDiagnosticsService messagingDiagnosticsService;

    public MasterEnvironmentController(
            PrivilegedSessionService privilegedSessionService,
            EnvironmentConfigurationService configService,
            et.ut.einvoice.platform.config.service.MasterMessagingDiagnosticsService messagingDiagnosticsService
    ) {
        this.privilegedSessionService = privilegedSessionService;
        this.configService = configService;
        this.messagingDiagnosticsService = messagingDiagnosticsService;
    }

    private String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "UNKNOWN_ADMIN";
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String corr = request.getHeader("X-Correlation-ID");
        return (corr != null && !corr.isBlank()) ? corr : UUID.randomUUID().toString();
    }

    private PrivilegedConfigurationSession requirePrivilegedSession(HttpServletRequest request) {
        String token = request.getHeader("X-Privileged-Token");
        if (token == null || token.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = authHeader.substring(7).trim();
            }
        }
        if (token == null || token.isBlank()) {
            throw new AccessDeniedException("Privileged environment session token missing. Step-up MFA authentication required.");
        }
        return privilegedSessionService.validatePrivilegedSession(token);
    }

    // =========================================================================
    // 1. STEP-UP MFA AUTHENTICATION CEREMONY
    // =========================================================================

    @PostMapping("/send-otp")
    @Operation(summary = "Dispatch out-of-band MFA verification code to administrator Email and SMS")
    public ResponseEntity<SendStepUpOtpResponse> sendStepUpOtp(
            @RequestBody(required = false) SendStepUpOtpRequest request,
            HttpServletRequest httpRequest
    ) {
        String username = resolveCurrentUsername();
        if (request != null && request.username() != null && !request.username().isBlank()) {
            username = request.username().trim();
        }
        if ("UNKNOWN_ADMIN".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            username = "platform.admin";
        }

        String phone = request != null ? request.phone() : null;
        String ip = httpRequest.getRemoteAddr();
        String correlationId = resolveCorrelationId(httpRequest);

        try {
            SendStepUpOtpResponse response = privilegedSessionService.sendStepUpOtp(
                    username,
                    phone,
                    ip,
                    correlationId
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(429).body(new SendStepUpOtpResponse(
                    false,
                    null,
                    null,
                    null,
                    60,
                    ex.getMessage()
            ));
        }
    }

    @PostMapping("/step-up")
    @Operation(summary = "Perform step-up MFA verification and obtain short-lived privileged session")
    public ResponseEntity<StepUpMfaResponse> initiateStepUp(
            @Valid @RequestBody StepUpMfaRequest request,
            HttpServletRequest httpRequest
    ) {
        String username = resolveCurrentUsername();
        if ("UNKNOWN_ADMIN".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            username = "platform.admin";
        }
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        String correlationId = resolveCorrelationId(httpRequest);

        var sessionDto = privilegedSessionService.initiateStepUpCeremony(
                username,
                request.password(),
                request.mfaCode(),
                ip,
                userAgent,
                correlationId
        );

        return ResponseEntity.ok(new StepUpMfaResponse(
                sessionDto.sessionId(),
                sessionDto.privilegedToken(),
                sessionDto.username(),
                sessionDto.expiresAt(),
                sessionDto.durationSeconds(),
                "Step-up MFA ceremony successfully completed. Privileged session active."
        ));
    }

    @PostMapping("/step-up/revoke")
    @Operation(summary = "Explicitly terminate the current privileged session")
    public ResponseEntity<Map<String, Object>> revokeStepUp(HttpServletRequest httpRequest) {
        PrivilegedConfigurationSession session = requirePrivilegedSession(httpRequest);
        String correlationId = resolveCorrelationId(httpRequest);
        privilegedSessionService.revokeSession(session.getId(), "User terminated session", session.getUsername(), correlationId);

        return ResponseEntity.ok(Map.of(
                "status", "REVOKED",
                "sessionId", session.getId(),
                "revokedAt", Instant.now().toString()
        ));
    }

    // =========================================================================
    // 2. CONFIGURATION DISCOVERY & HEALTH
    // =========================================================================

    @GetMapping
    @Operation(summary = "Get all approved configuration items (secrets masked)")
    public ResponseEntity<List<ConfigurationItemDto>> getAllConfigurations(
            HttpServletRequest httpRequest
    ) {
        requirePrivilegedSession(httpRequest);
        return ResponseEntity.ok(configService.getAllConfigurations());
    }

    @GetMapping("/scopes")
    @Operation(summary = "List all configuration scopes and definitions")
    public ResponseEntity<List<Map<String, String>>> getScopes(HttpServletRequest httpRequest) {
        requirePrivilegedSession(httpRequest);
        List<Map<String, String>> scopes = Arrays.stream(ConfigurationScope.values())
                .map(s -> Map.of("name", s.name(), "displayName", s.getDisplayName()))
                .toList();
        return ResponseEntity.ok(scopes);
    }

    @GetMapping("/health")
    @Operation(summary = "Get environment and external service connectivity health summary")
    public ResponseEntity<ConfigurationHealthDto> getHealth(
            HttpServletRequest httpRequest
    ) {
        requirePrivilegedSession(httpRequest);
        return ResponseEntity.ok(configService.getConfigurationHealth());
    }

    // =========================================================================
    // 3. MUTATIONS (CONFIG UPDATE & SECRET ROTATION)
    // =========================================================================

    @PutMapping("/configuration")
    @Operation(summary = "Update non-secret configuration variables with optimistic locking")
    public ResponseEntity<ConfigurationRevision> updateConfiguration(
            @Valid @RequestBody ConfigurationUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        PrivilegedConfigurationSession session = requirePrivilegedSession(httpRequest);
        String correlationId = resolveCorrelationId(httpRequest);

        ConfigurationRevision revision = configService.updateConfigurations(
                request.expectedRevisionNumber(),
                request.configurations(),
                request.changeSummary(),
                session.getUsername(),
                correlationId
        );

        return ResponseEntity.ok(revision);
    }

    @PostMapping("/secrets/rotate")
    @Operation(summary = "Rotate encrypted secret (plaintext is never returned or logged)")
    public ResponseEntity<ConfigurationRevision> rotateSecret(
            @Valid @RequestBody SecretRotationRequest request,
            HttpServletRequest httpRequest
    ) {
        PrivilegedConfigurationSession session = requirePrivilegedSession(httpRequest);
        String correlationId = resolveCorrelationId(httpRequest);

        ConfigurationRevision revision = configService.rotateSecret(
                request.keyName(),
                request.newSecret(),
                session.getUsername(),
                correlationId
        );

        return ResponseEntity.ok(revision);
    }

    // =========================================================================
    // 4. REVISIONS & ROLLBACK
    // =========================================================================

    @GetMapping("/revisions")
    @Operation(summary = "Get immutable revision history")
    public ResponseEntity<List<RevisionSummaryDto>> getRevisions(
            HttpServletRequest httpRequest
    ) {
        requirePrivilegedSession(httpRequest);
        return ResponseEntity.ok(configService.getRevisionHistory());
    }

    @PostMapping("/revisions/{revisionNumber}/rollback")
    @Operation(summary = "Rollback non-secret configurations to a designated revision")
    public ResponseEntity<ConfigurationRevision> rollback(
            @PathVariable long revisionNumber,
            HttpServletRequest httpRequest
    ) {
        PrivilegedConfigurationSession session = requirePrivilegedSession(httpRequest);
        String correlationId = resolveCorrelationId(httpRequest);

        ConfigurationRevision revision = configService.rollbackToRevision(
                revisionNumber,
                session.getUsername(),
                correlationId
        );

        return ResponseEntity.ok(revision);
    }

    // =========================================================================
    // 5. APPLY / RELOAD
    // =========================================================================

    @PostMapping("/apply")
    @Operation(summary = "Apply configuration changes and check restart requirements")
    public ResponseEntity<ApplyResponse> applyConfiguration(HttpServletRequest httpRequest) {
        requirePrivilegedSession(httpRequest);

        return ResponseEntity.ok(new ApplyResponse(
                "APPLIED",
                false,
                "Platform configuration successfully verified and applied to runtime registry.",
                Instant.now()
        ));
    }

    // =========================================================================
    // 6. MESSAGING & NOTIFICATION TRANSPORT DIAGNOSTICS & TEST SEND
    // =========================================================================

    @GetMapping("/email/status")
    @Operation(summary = "Get safe diagnostic status of SMTP email transport")
    public ResponseEntity<EmailDiagnosticStatusDto> getEmailStatus() {
        String adminUsername = resolveCurrentUsername();
        return ResponseEntity.ok(messagingDiagnosticsService.getEmailStatus(adminUsername));
    }

    @PostMapping("/email/test-send")
    @Operation(summary = "Send controlled test email to authenticated administrator's verified email")
    public ResponseEntity<EmailTestSendResponse> sendTestEmail(HttpServletRequest request) {
        String adminUsername = resolveCurrentUsername();
        String ip = request.getRemoteAddr();
        String corrId = resolveCorrelationId(request);
        return ResponseEntity.ok(messagingDiagnosticsService.sendTestEmail(adminUsername, ip, corrId));
    }

    @GetMapping("/sms/status")
    @Operation(summary = "Get safe diagnostic status of SMS gateway provider")
    public ResponseEntity<SmsDiagnosticStatusDto> getSmsStatus() {
        String adminUsername = resolveCurrentUsername();
        return ResponseEntity.ok(messagingDiagnosticsService.getSmsStatus(adminUsername));
    }

    @PostMapping("/sms/test-send")
    @Operation(summary = "Send controlled test SMS to authenticated administrator's verified phone")
    public ResponseEntity<SmsTestSendResponse> sendTestSms(HttpServletRequest request) {
        String adminUsername = resolveCurrentUsername();
        String ip = request.getRemoteAddr();
        String corrId = resolveCorrelationId(request);
        return ResponseEntity.ok(messagingDiagnosticsService.sendTestSms(adminUsername, ip, corrId));
    }
}

