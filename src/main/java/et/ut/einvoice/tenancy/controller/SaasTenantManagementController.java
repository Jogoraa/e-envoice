package et.ut.einvoice.tenancy.controller;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.tenancy.service.DelegatedTenantSessionService;
import et.ut.einvoice.tenancy.service.SaasTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/saas")
@Tag(name = "SaaS Master Platform Management", description = "Tenant lifecycle, onboarding, and controlled support access endpoints")
public class SaasTenantManagementController {

    private final SaasTenantService saasTenantService;
    private final DelegatedTenantSessionService delegatedSessionService;

    public SaasTenantManagementController(
            SaasTenantService saasTenantService,
            DelegatedTenantSessionService delegatedSessionService
    ) {
        this.saasTenantService = saasTenantService;
        this.delegatedSessionService = delegatedSessionService;
    }

    private String resolveOperatorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        TenantContext ctx = TenantContextHolder.getContext();
        return (ctx != null && ctx.userId() != null) ? ctx.userId() : "SAAS_OPERATOR";
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_OPERATOR')")
    @Operation(summary = "List all registered tenants for SaaS platform administration")
    public ResponseEntity<List<SaasTenantService.TenantSummaryDto>> listTenants() {
        return ResponseEntity.ok(saasTenantService.listTenants());
    }

    @GetMapping("/tenants/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_OPERATOR')")
    @Operation(summary = "Get detailed information for a specific tenant")
    public ResponseEntity<SaasTenantService.TenantSummaryDto> getTenant(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(saasTenantService.getTenantDetails(id));
    }

    @PostMapping("/tenants")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Onboard and register a new enterprise tenant")
    public ResponseEntity<SaasTenantService.TenantSummaryDto> onboardTenant(
            @Valid @RequestBody SaasTenantService.OnboardTenantRequest request
    ) {
        String operatorId = resolveOperatorId();
        SaasTenantService.TenantSummaryDto created = saasTenantService.onboardTenant(request, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/tenants/{id}/lifecycle-transition")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Transition tenant lifecycle state (ACTIVE, SUSPENDED, DEACTIVATED)")
    public ResponseEntity<SaasTenantService.TenantSummaryDto> transitionLifecycle(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SaasTenantService.LifecycleTransitionRequest request
    ) {
        String operatorId = resolveOperatorId();
        return ResponseEntity.ok(saasTenantService.transitionLifecycle(id, request, operatorId));
    }

    @PostMapping("/tenants/{id}/support-session")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_OPERATOR')")
    @Operation(summary = "Issue a controlled, short-lived delegated tenant token for testing or support")
    public ResponseEntity<DelegatedTenantSessionService.DelegatedSessionResult> createSupportSession(
            @PathVariable("id") UUID targetTenantId,
            @RequestBody(required = false) RequestSupportSessionPayload payload
    ) {
        String operatorId = resolveOperatorId();
        String accessType = (payload != null && payload.accessType() != null) ? payload.accessType() : "TESTING";
        UUID branchId = (payload != null && payload.branchId() != null) ? payload.branchId() : null;
        String reason = (payload != null && payload.reason() != null) ? payload.reason() : "Operator diagnostic testing";
        long ttlSeconds = (payload != null && payload.ttlSeconds() != null) ? payload.ttlSeconds() : 1800;

        DelegatedTenantSessionService.DelegatedSessionResult result = delegatedSessionService.requestSupportSession(
                operatorId,
                targetTenantId,
                branchId,
                accessType,
                reason,
                ttlSeconds
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/support-session/{sessionId}/terminate")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_OPERATOR', 'ROLE_DELEGATED_OPERATOR')")
    @Operation(summary = "Terminate and revoke an active delegated support session")
    public ResponseEntity<Void> terminateSupportSession(@PathVariable("sessionId") UUID sessionId) {
        String operatorId = resolveOperatorId();
        delegatedSessionService.terminateSupportSession(sessionId, operatorId);
        return ResponseEntity.noContent().build();
    }

    public record RequestSupportSessionPayload(
            String accessType,
            UUID branchId,
            String reason,
            Long ttlSeconds
    ) {}
}
