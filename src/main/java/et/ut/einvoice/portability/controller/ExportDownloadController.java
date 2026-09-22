package et.ut.einvoice.portability.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.portability.service.DataPortabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portability")
@Tag(name = "Data Portability API", description = "Endpoints for downloading complete tenant archive ZIP bundles")
public class ExportDownloadController {

    private final DataPortabilityService portabilityService;

    public ExportDownloadController(DataPortabilityService portabilityService) {
        this.portabilityService = portabilityService;
    }

    @GetMapping("/exports/{jobId}/download")
    @PreAuthorize("hasAuthority('SCOPE_exports:read') or hasAuthority('SCOPE_tenant:admin') or hasRole('TENANT_ADMIN') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Download legally exportable tenant archive ZIP bundle with SHA-256 verification")
    public void downloadExportArchive(
            @PathVariable("jobId") UUID jobId,
            HttpServletResponse response) throws Exception {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        File archiveFile = portabilityService.getExportArchiveFile(tenantId, jobId);

        response.setContentType("application/zip");
        response.setContentLengthLong(archiveFile.length());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tenant_export_" + jobId + ".zip\"");

        try (FileInputStream fis = new FileInputStream(archiveFile);
                OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
    }
}
