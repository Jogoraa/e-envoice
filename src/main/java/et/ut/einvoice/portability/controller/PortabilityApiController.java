package et.ut.einvoice.portability.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.portability.domain.ExportJob;
import et.ut.einvoice.portability.service.DataPortabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portability")
@Tag(name = "Data Portability API", description = "Endpoints for initiating asynchronous tenant data exports")
public class PortabilityApiController {

    private final DataPortabilityService portabilityService;

    public PortabilityApiController(DataPortabilityService portabilityService) {
        this.portabilityService = portabilityService;
    }

    @PostMapping("/exports")
    @PreAuthorize("hasAuthority('SCOPE_tenant:admin') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Initiate an asynchronous legally exportable data archive packaging job")
    public ResponseEntity<ExportJob> initiateExport() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(portabilityService.initiateExport(tenantId));
    }
}
