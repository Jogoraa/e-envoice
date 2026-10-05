package et.ut.einvoice.compliance.controller;

import et.ut.einvoice.compliance.domain.ExemptSectorAuthorization;
import et.ut.einvoice.compliance.domain.ExemptSectorSummaryReport;
import et.ut.einvoice.compliance.dto.ExemptSectorAuthorizationRequestDto;
import et.ut.einvoice.compliance.dto.GenerateSummaryReportRequestDto;
import et.ut.einvoice.compliance.dto.ReviewSummaryReportRequestDto;
import et.ut.einvoice.compliance.service.ExemptSectorReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@Tag(name = "Exempt Sector Summary Reporting", description = "High-Volume Exempt-Sector Summary Reporting Interface under Directive No. 1142/2026 Art. 20")
public class ExemptSectorReportingController {

    private final ExemptSectorReportingService reportingService;

    public ExemptSectorReportingController(ExemptSectorReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @PostMapping({"/api/v1/master/exempt-sectors/authorizations", "/api/v1/compliance/exempt-sectors/authorizations"})
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Grant Art. 20 high-volume exempt sector authorization to a tenant")
    public ResponseEntity<ExemptSectorAuthorization> grantAuthorization(
            @RequestBody ExemptSectorAuthorizationRequestDto request,
            Authentication authentication
    ) {
        String authorizedBy = authentication != null ? authentication.getName() : "PLATFORM_ADMIN";
        ExemptSectorAuthorization auth = reportingService.grantAuthorization(
                request.getTenantId(),
                request.getSectorCode(),
                authorizedBy,
                request.getAuthorizationReference(),
                request.getReportingFrequency(),
                request.getEffectiveFrom(),
                request.getEffectiveTo()
        );
        return ResponseEntity.ok(auth);
    }

    @GetMapping({"/api/v1/master/exempt-sectors/authorizations/{tenantId}", "/api/v1/compliance/exempt-sectors/authorizations/{tenantId}"})
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_AUTHORITY_AUDITOR')")
    @Operation(summary = "Retrieve Art. 20 exempt sector authorization for a tenant")
    public ResponseEntity<ExemptSectorAuthorization> getAuthorization(@PathVariable UUID tenantId) {
        Optional<ExemptSectorAuthorization> auth = reportingService.getAuthorization(tenantId);
        return auth.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/compliance/exempt-sectors/reports/generate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ROLE_TENANT_ADMIN', 'PLATFORM_ADMIN', 'SAAS_ADMIN')")
    @Operation(summary = "Generate draft aggregate summary sales report for authorized high-volume tenant")
    public ResponseEntity<ExemptSectorSummaryReport> generateDraftReport(
            @RequestBody GenerateSummaryReportRequestDto request
    ) {
        ExemptSectorSummaryReport report = reportingService.generateDraftSummaryReport(
                request.getTenantId(),
                request.getReportPeriodLabel(),
                request.getPeriodFrom(),
                request.getPeriodTo()
        );
        return ResponseEntity.ok(report);
    }

    @PostMapping("/api/v1/compliance/exempt-sectors/reports/{reportId}/submit")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ROLE_TENANT_ADMIN', 'PLATFORM_ADMIN', 'SAAS_ADMIN')")
    @Operation(summary = "Submit and cryptographically freeze periodic summary sales report to Authority")
    public ResponseEntity<ExemptSectorSummaryReport> submitReport(
            @PathVariable UUID reportId,
            Authentication authentication
    ) {
        String submittedBy = authentication != null ? authentication.getName() : "SYSTEM";
        ExemptSectorSummaryReport report = reportingService.submitSummaryReport(reportId, submittedBy);
        return ResponseEntity.ok(report);
    }

    @PostMapping({"/api/v1/authority/exempt-sectors/reports/{reportId}/review", "/api/v1/compliance/exempt-sectors/reports/{reportId}/review"})
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Accept or reject submitted periodic summary report (Authority Auditors Only)")
    public ResponseEntity<ExemptSectorSummaryReport> reviewReport(
            @PathVariable UUID reportId,
            @RequestBody ReviewSummaryReportRequestDto request
    ) {
        ExemptSectorSummaryReport report = reportingService.reviewSummaryReport(
                reportId,
                request.isAccept(),
                request.getReason()
        );
        return ResponseEntity.ok(report);
    }
}
