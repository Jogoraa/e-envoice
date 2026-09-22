package et.ut.einvoice.reports.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.reports.domain.ReportDefinition;
import et.ut.einvoice.reports.domain.ReportJob;
import et.ut.einvoice.reports.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
            String reportId,
            String format,
            Instant startDate,
            Instant endDate
    ) {}

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active report definitions catalog from database")
    public ResponseEntity<List<ReportDefinition>> getActiveDefinitions() {
        return ResponseEntity.ok(reportGenerationService.getActiveDefinitions());
    }

    @GetMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List report generation jobs for the current active tenant")
    public ResponseEntity<List<ReportJob>> getTenantJobs() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(reportGenerationService.getTenantJobs(tenantId));
    }

    @PostMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Generate a compliance report job from live database records")
    public ResponseEntity<ReportJob> generateReport(@RequestBody GenerateReportRequest request) {
        if (request.reportId() == null || request.reportId().isBlank()) {
            throw new BusinessException("INVALID_REPORT_ID", "Report definition ID is required.", HttpStatus.BAD_REQUEST);
        }
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        ReportJob job = reportGenerationService.generateReport(
                tenantId,
                request.reportId(),
                request.format() != null ? request.format() : "CSV",
                request.startDate(),
                request.endDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get report generation job details by ID")
    public ResponseEntity<ReportJob> getJobDetails(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        ReportJob job = reportGenerationService.getJob(id)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "Report job not found.", HttpStatus.NOT_FOUND));

        if (!tenantId.equals(job.getTenantId())) {
            throw new BusinessException("ACCESS_DENIED", "Access to this report job is prohibited.", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(job);
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
