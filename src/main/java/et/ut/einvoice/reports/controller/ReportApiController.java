package et.ut.einvoice.reports.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.reports.domain.ReportDefinition;
import et.ut.einvoice.reports.domain.ReportJob;
import et.ut.einvoice.reports.dto.ReportDefinitionResponseDto;
import et.ut.einvoice.reports.dto.ReportJobResponseDto;
import et.ut.einvoice.reports.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Taxpayer Reports API", description = "Live database-backed compliance and operational reporting")
public class ReportApiController {

    private final ReportGenerationService reportGenerationService;

    public ReportApiController(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    public record GenerateReportRequest(
            @NotBlank(message = "Report definition ID is required")
            @Size(max = 64)
            String reportId,
            @Size(max = 16)
            @jakarta.validation.constraints.Pattern(regexp = "^$|^(CSV|EXCEL|JSON|PDF)$", message = "Unsupported report format")
            String format,
            Instant startDate,
            Instant endDate
    ) {}

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active report definitions catalog from database")
    public ResponseEntity<List<ReportDefinitionResponseDto>> getActiveDefinitions() {
        return ResponseEntity.ok(reportGenerationService.getActiveDefinitions().stream().map(ReportDefinitionResponseDto::fromEntity).toList());
    }

    @GetMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List report generation jobs for the current active tenant")
    public ResponseEntity<List<ReportJobResponseDto>> getTenantJobs() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(reportGenerationService.getTenantJobs(tenantId).stream().map(ReportJobResponseDto::fromEntity).toList());
    }

    @PostMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Generate a compliance report job from live database records")
    public ResponseEntity<ReportJobResponseDto> generateReport(@Valid @RequestBody GenerateReportRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        ReportJob job = reportGenerationService.generateReport(
                tenantId,
                request.reportId(),
                request.format() != null ? request.format() : "CSV",
                request.startDate(),
                request.endDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportJobResponseDto.fromEntity(job));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get report generation job details by ID")
    public ResponseEntity<ReportJobResponseDto> getJobDetails(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        ReportJob job = reportGenerationService.getJob(id)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "Report job not found.", HttpStatus.NOT_FOUND));

        if (!tenantId.equals(job.getTenantId())) {
            throw new BusinessException("ACCESS_DENIED", "Access to this report job is prohibited.", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(ReportJobResponseDto.fromEntity(job));
    }

    @GetMapping("/jobs/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download generated report artifact content")
    public ResponseEntity<String> downloadReport(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        ReportJob job = reportGenerationService.getJob(id)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "Report job not found.", HttpStatus.NOT_FOUND));

        if (!tenantId.equals(job.getTenantId())) {
            throw new BusinessException("ACCESS_DENIED", "Access to this report job is prohibited.", HttpStatus.FORBIDDEN);
        }

        String content = job.getReportContent() != null ? job.getReportContent() : "";
        String ext = job.getFormat() != null ? job.getFormat().toLowerCase() : "csv";
        String filename = "report-" + job.getId().toString().substring(0, 8) + "." + ext;

        MediaType mediaType = "json".equalsIgnoreCase(ext) ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(content);
    }
}
