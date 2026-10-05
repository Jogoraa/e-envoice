package et.ut.einvoice.compliance.controller;

import et.ut.einvoice.compliance.domain.ProviderTenantTransition;
import et.ut.einvoice.compliance.dto.*;
import et.ut.einvoice.compliance.service.ProviderExitGovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for Provider Exit Strategy & Transition Governance
 * pursuant to FDRE MoR Directive No. 1142/2026 Art. 17.
 */
@RestController
@RequestMapping("/api/v1/master/provider-exit")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SAAS_ADMIN', 'MASTER_ADMIN', 'SYSTEM_ADMIN', 'OPERATOR', 'AUDITOR', 'ROLE_PLATFORM_ADMIN')")
public class ProviderExitGovernanceController {

    private final ProviderExitGovernanceService exitGovernanceService;

    public ProviderExitGovernanceController(ProviderExitGovernanceService exitGovernanceService) {
        this.exitGovernanceService = exitGovernanceService;
    }

    @PostMapping("/plans")
    public ResponseEntity<ProviderExitPlanResponseDto> createPlan(@Valid @RequestBody CreateExitPlanRequestDto dto) {
        ProviderExitPlanResponseDto created = exitGovernanceService.createExitPlan(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/plans/{planId}/strategy")
    public ResponseEntity<ProviderExitPlanResponseDto> submitStrategy(
            @PathVariable UUID planId,
            @Valid @RequestBody SubmitExitStrategyRequestDto dto) {
        ProviderExitPlanResponseDto updated = exitGovernanceService.submitStrategy(planId, dto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/plans/{planId}/approval")
    public ResponseEntity<ProviderExitPlanResponseDto> recordApproval(
            @PathVariable UUID planId,
            @Valid @RequestBody ApproveExitStrategyRequestDto dto) {
        ProviderExitPlanResponseDto updated = exitGovernanceService.recordAuthorityApproval(planId, dto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/plans/{planId}/notify-taxpayers")
    public ResponseEntity<ProviderExitPlanResponseDto> notifyTaxpayers(@PathVariable UUID planId) {
        ProviderExitPlanResponseDto updated = exitGovernanceService.notifyTaxpayers(planId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/plans/{planId}/tenants/{tenantId}/data-retrieved")
    public ResponseEntity<ProviderTenantTransition> recordDataRetrieved(
            @PathVariable UUID planId,
            @PathVariable UUID tenantId) {
        ProviderTenantTransition transition = exitGovernanceService.recordTenantDataRetrieval(planId, tenantId);
        return ResponseEntity.ok(transition);
    }

    @PostMapping("/plans/{planId}/tenants/migration")
    public ResponseEntity<ProviderTenantTransition> recordMigration(
            @PathVariable UUID planId,
            @Valid @RequestBody RecordMigrationRequestDto dto) {
        ProviderTenantTransition transition = exitGovernanceService.recordTenantMigration(planId, dto);
        return ResponseEntity.ok(transition);
    }

    @PostMapping("/plans/{planId}/confirm-cessation")
    public ResponseEntity<ProviderExitPlanResponseDto> confirmCessation(
            @PathVariable UUID planId,
            @Valid @RequestBody ConfirmCessationRequestDto dto) {
        ProviderExitPlanResponseDto updated = exitGovernanceService.confirmCessation(planId, dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/plans/latest")
    public ResponseEntity<ProviderExitPlanResponseDto> getLatestPlan() {
        ProviderExitPlanResponseDto latest = exitGovernanceService.getLatestExitPlan();
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<ProviderExitPlanResponseDto> getPlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(exitGovernanceService.getExitPlan(planId));
    }

    @GetMapping("/plans/{planId}/tenants")
    public ResponseEntity<List<ProviderTenantTransition>> getTenantTransitions(@PathVariable UUID planId) {
        return ResponseEntity.ok(exitGovernanceService.getTenantTransitions(planId));
    }
}
