package et.ut.einvoice.tenancy.config.controller;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.tenancy.config.domain.TenantConfigurationOverride;
import et.ut.einvoice.tenancy.config.domain.TenantFeatureFlag;
import et.ut.einvoice.tenancy.config.service.TenantConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Tenant Configuration & Feature Flags", description = "Centralized tenant-scoped configuration and server-side feature flag evaluation")
public class TenantConfigurationController {

    private final TenantConfigurationService configurationService;

    public TenantConfigurationController(TenantConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/configuration")
    @PreAuthorize("hasAnyRole('ROLE_TENANT_ADMIN', 'ROLE_TENANT_USER', 'ROLE_API_CLIENT')")
    @Operation(summary = "Get Effective Tenant Configuration", description = "Resolves effective configuration merging global defaults with tenant overrides")
    public ResponseEntity<Map<String, String>> getEffectiveConfiguration() {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        Map<String, String> config = configurationService.getEffectiveConfiguration(ctx.tenantId());
        return ResponseEntity.ok(config);
    }

    @PatchMapping("/configuration")
    @PreAuthorize("hasRole('ROLE_TENANT_ADMIN')")
    @Operation(summary = "Update Tenant Configuration Override", description = "Sets or updates a tenant-specific configuration override. Rejects IMMUTABLE_REGULATORY controls.")
    public ResponseEntity<TenantConfigurationOverride> updateConfigurationOverride(@Valid @RequestBody UpdateConfigOverrideRequest request) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        TenantConfigurationOverride override = configurationService.setTenantOverride(
                ctx.tenantId(),
                request.configKey(),
                request.configValue(),
                request.expectedVersion(),
                ctx.userId()
        );
        return ResponseEntity.ok(override);
    }

    @GetMapping("/features/{featureKey}/enabled")
    @PreAuthorize("hasAnyRole('ROLE_TENANT_ADMIN', 'ROLE_TENANT_USER', 'ROLE_API_CLIENT')")
    @Operation(summary = "Check Feature Flag Status", description = "Evaluates server-side whether a feature is active for the current tenant")
    public ResponseEntity<FeatureStatusResponse> isFeatureEnabled(@PathVariable String featureKey) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        boolean enabled = configurationService.isFeatureEnabled(ctx.tenantId(), featureKey);
        return ResponseEntity.ok(new FeatureStatusResponse(featureKey, enabled));
    }

    @PatchMapping("/features")
    @PreAuthorize("hasRole('ROLE_TENANT_ADMIN')")
    @Operation(summary = "Update Tenant Feature Flag", description = "Updates a tenant-level feature flag. Rejects disabling SECURITY_CONTROL or REGULATORY_FEATURE flags.")
    public ResponseEntity<TenantFeatureFlag> updateFeatureFlag(@Valid @RequestBody UpdateFeatureFlagRequest request) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        TenantFeatureFlag flag = configurationService.setFeatureFlag(
                ctx.tenantId(),
                request.featureKey(),
                request.enabled(),
                request.expectedVersion(),
                ctx.userId()
        );
        return ResponseEntity.ok(flag);
    }

    public record UpdateConfigOverrideRequest(
            @NotBlank(message = "configKey is mandatory") String configKey,
            @NotBlank(message = "configValue is mandatory") String configValue,
            Long expectedVersion
    ) {}

    public record UpdateFeatureFlagRequest(
            @NotBlank(message = "featureKey is mandatory") String featureKey,
            boolean enabled,
            Long expectedVersion
    ) {}

    public record FeatureStatusResponse(String featureKey, boolean enabled) {}
}
