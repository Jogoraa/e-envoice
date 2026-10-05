package et.ut.einvoice.compliance.controller;

import et.ut.einvoice.compliance.dto.ProviderComplianceTierDto;
import et.ut.einvoice.compliance.dto.ProviderDashboardSummaryDto;
import et.ut.einvoice.compliance.dto.ProviderTierStatusDto;
import et.ut.einvoice.compliance.service.ProviderComplianceTierService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing SaaS Provider regulatory compliance tiering and threshold tracking.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 14(6).
 */
@RestController
@RequestMapping("/api/v1/master/provider-tiers")
public class ProviderTierComplianceController {

    private final ProviderComplianceTierService tierService;

    public ProviderTierComplianceController(ProviderComplianceTierService tierService) {
        this.tierService = tierService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ProviderDashboardSummaryDto> getDashboardSummary() {
        return ResponseEntity.ok(tierService.getDashboardSummary());
    }

    @GetMapping
    public ResponseEntity<List<ProviderComplianceTierDto>> getAllTiers() {
        return ResponseEntity.ok(tierService.getAllTiers());
    }

    @GetMapping("/history")
    public ResponseEntity<List<ProviderTierStatusDto>> getStatusHistory() {
        return ResponseEntity.ok(tierService.getStatusHistory());
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ProviderTierStatusDto> evaluateCurrentTier() {
        return ResponseEntity.ok(tierService.evaluateCurrentTier());
    }

    @PostMapping("/{id}/notify")
    public ResponseEntity<ProviderTierStatusDto> markNotified(@PathVariable UUID id) {
        return ResponseEntity.ok(tierService.markNotified(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ProviderTierStatusDto> confirmTierTransition(@PathVariable UUID id) {
        return ResponseEntity.ok(tierService.confirmTierTransition(id));
    }
}
