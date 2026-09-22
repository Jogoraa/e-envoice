package et.ut.einvoice.tenancy.controller;

import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.reports.domain.ReportDefinition;
import et.ut.einvoice.reports.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/saas/reports")
@Tag(name = "SaaS Report Template Management API", description = "Endpoints for configuring dynamic database report definitions")
public class SaasReportManagementController {

    private final ReportGenerationService reportGenerationService;

    public SaasReportManagementController(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    public record ReportDefinitionDto(
            String id,
            String title,
            String amharicTitle,
            String description,
            String iconName,
            String category,
            String exportFormats,
            Boolean isActive,
            Integer displayOrder
    ) {}

    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_SAAS_OPERATOR')")
    @Operation(summary = "List all report definitions for platform administration")
    public ResponseEntity<List<ReportDefinition>> listAllDefinitions() {
        return ResponseEntity.ok(reportGenerationService.getAllDefinitions());
    }

    @PostMapping("/definitions")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Create a new database report definition")
    public ResponseEntity<ReportDefinition> createDefinition(@RequestBody ReportDefinitionDto dto) {
        if (dto.id() == null || dto.id().isBlank()) {
            throw new BusinessException("INVALID_ID", "Report definition ID is required.", HttpStatus.BAD_REQUEST);
        }
        if (dto.title() == null || dto.title().isBlank()) {
            throw new BusinessException("INVALID_TITLE", "Report definition title is required.", HttpStatus.BAD_REQUEST);
        }

        String id = dto.id().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        ReportDefinition def = new ReportDefinition(
                id,
                dto.title().trim(),
                dto.amharicTitle() != null ? dto.amharicTitle().trim() : null,
                dto.description() != null ? dto.description().trim() : null,
                dto.iconName() != null && !dto.iconName().isBlank() ? dto.iconName().trim() : "assessment",
                dto.category() != null && !dto.category().isBlank() ? dto.category().trim().toUpperCase() : "COMPLIANCE",
                dto.exportFormats() != null && !dto.exportFormats().isBlank() ? dto.exportFormats().trim().toUpperCase() : "PDF,EXCEL,CSV,JSON",
                dto.isActive() != null ? dto.isActive() : true,
                dto.displayOrder() != null ? dto.displayOrder() : 0
        );

        ReportDefinition saved = reportGenerationService.saveDefinition(def);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/definitions/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Update an existing report definition")
    public ResponseEntity<ReportDefinition> updateDefinition(@PathVariable("id") String id, @RequestBody ReportDefinitionDto dto) {
        ReportDefinition def = reportGenerationService.getAllDefinitions().stream()
                .filter(d -> d.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Report definition not found: " + id, HttpStatus.NOT_FOUND));

        if (dto.title() != null && !dto.title().isBlank()) def.setTitle(dto.title().trim());
        if (dto.amharicTitle() != null) def.setAmharicTitle(dto.amharicTitle().trim());
        if (dto.description() != null) def.setDescription(dto.description().trim());
        if (dto.iconName() != null && !dto.iconName().isBlank()) def.setIconName(dto.iconName().trim());
        if (dto.category() != null && !dto.category().isBlank()) def.setCategory(dto.category().trim().toUpperCase());
        if (dto.exportFormats() != null && !dto.exportFormats().isBlank()) def.setExportFormats(dto.exportFormats().trim().toUpperCase());
        if (dto.isActive() != null) def.setActive(dto.isActive());
        if (dto.displayOrder() != null) def.setDisplayOrder(dto.displayOrder());

        return ResponseEntity.ok(reportGenerationService.saveDefinition(def));
    }

    @DeleteMapping("/definitions/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Delete a report definition")
    public ResponseEntity<Map<String, Object>> deleteDefinition(@PathVariable("id") String id) {
        reportGenerationService.deleteDefinition(id);
        return ResponseEntity.ok(Map.of("message", "Report definition deleted successfully.", "id", id));
    }

    @PatchMapping("/definitions/{id}/toggle")
    @PreAuthorize("hasAnyRole('ROLE_SAAS_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Toggle active/inactive status of a report definition")
    public ResponseEntity<ReportDefinition> toggleActive(@PathVariable("id") String id) {
        ReportDefinition def = reportGenerationService.getAllDefinitions().stream()
                .filter(d -> d.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Report definition not found: " + id, HttpStatus.NOT_FOUND));

        def.setActive(!def.isActive());
        return ResponseEntity.ok(reportGenerationService.saveDefinition(def));
    }
}
