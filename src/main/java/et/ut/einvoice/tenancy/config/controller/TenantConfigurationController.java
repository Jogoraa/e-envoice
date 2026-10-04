package et.ut.einvoice.tenancy.config.controller;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.tenancy.config.service.TenantConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Tenant Configuration & Feature Flags", description = "Centralized tenant-scoped configuration and server-side feature flag evaluation")
public class TenantConfigurationController {

    private final TenantConfigurationService configurationService;

    public TenantConfigurationController(TenantConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/configuration")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER', 'API_CLIENT')")
    @Operation(summary = "Get Effective Tenant Configuration", description = "Resolves effective configuration merging global defaults with tenant overrides")
    public ResponseEntity<Map<String, String>> getEffectiveConfiguration() {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        Map<String, String> config = configurationService.getEffectiveConfiguration(ctx.tenantId());
        return ResponseEntity.ok(config);
    }

    @PatchMapping("/configuration")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update Tenant Configuration Override", description = "Sets or updates a tenant-specific configuration override. Rejects IMMUTABLE_REGULATORY controls.")
    public ResponseEntity<ConfigurationOverrideResponse> updateConfigurationOverride(@Valid @RequestBody UpdateConfigOverrideRequest request) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        var override = configurationService.setTenantOverride(
                ctx.tenantId(),
                request.configKey(),
                request.configValue(),
                request.expectedVersion(),
                ctx.userId()
        );
        return ResponseEntity.ok(new ConfigurationOverrideResponse(
                override.getConfigKey(), override.getConfigValue(), override.getSafetyClassification(),
                override.getVersion(), override.getUpdatedAt()
        ));
    }

    @GetMapping("/features/{featureKey}/enabled")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER', 'API_CLIENT')")
    @Operation(summary = "Check Feature Flag Status", description = "Evaluates server-side whether a feature is active for the current tenant")
    public ResponseEntity<FeatureStatusResponse> isFeatureEnabled(@PathVariable String featureKey) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        boolean enabled = configurationService.isFeatureEnabled(ctx.tenantId(), featureKey);
        return ResponseEntity.ok(new FeatureStatusResponse(featureKey, enabled));
    }

    @PatchMapping("/features")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update Tenant Feature Flag", description = "Updates a tenant-level feature flag. Rejects disabling SECURITY_CONTROL or REGULATORY_FEATURE flags.")
    public ResponseEntity<FeatureFlagResponse> updateFeatureFlag(@Valid @RequestBody UpdateFeatureFlagRequest request) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        var flag = configurationService.setFeatureFlag(
                ctx.tenantId(),
                request.featureKey(),
                request.enabled(),
                request.expectedVersion(),
                ctx.userId()
        );
        return ResponseEntity.ok(new FeatureFlagResponse(
                flag.getFeatureKey(), flag.isEnabled(), flag.getCategory(), flag.getVersion(), flag.getUpdatedAt()
        ));
    }

    public record UpdateConfigOverrideRequest(
            @NotBlank(message = "configKey is mandatory") @jakarta.validation.constraints.Size(max = 128) String configKey,
            @NotBlank(message = "configValue is mandatory") @jakarta.validation.constraints.Size(max = 20_000) String configValue,
            Long expectedVersion
    ) {}

    public record UpdateFeatureFlagRequest(
            @NotBlank(message = "featureKey is mandatory") @jakarta.validation.constraints.Size(max = 128) String featureKey,
            @NotNull(message = "enabled is mandatory") Boolean enabled,
            Long expectedVersion
    ) {}

    public record FeatureStatusResponse(String featureKey, boolean enabled) {}

    public record ConfigurationOverrideResponse(
            String configKey,
            String configValue,
            et.ut.einvoice.tenancy.config.domain.ConfigSafetyClassification safetyClassification,
            Long version,
            java.time.Instant updatedAt
    ) {}

    public record FeatureFlagResponse(
            String featureKey,
            boolean enabled,
            et.ut.einvoice.tenancy.config.domain.FeatureFlagCategory category,
            Long version,
            java.time.Instant updatedAt
    ) {}
}
