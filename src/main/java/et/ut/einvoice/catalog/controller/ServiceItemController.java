package et.ut.einvoice.catalog.controller;

import et.ut.einvoice.catalog.dto.CreateServiceRequest;
import et.ut.einvoice.catalog.dto.ServiceItemDto;
import et.ut.einvoice.catalog.service.ServiceItemService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/services")
@Tag(name = "Service Catalog Management", description = "Tenant-scoped non-physical service catalog, hourly/flat rates, and tax categorization")
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    public ServiceItemController(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GetMapping
    @Operation(summary = "Search services with pagination, query, and category filtering")
    public ResponseEntity<Page<ServiceItemDto>> searchServices(
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "category", required = false) String categoryCode,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(serviceItemService.searchServices(tenantId, branchId, query, categoryCode, isActive, pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all services for current tenant")
    public ResponseEntity<List<ServiceItemDto>> getAllServices() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(serviceItemService.getAllServices(tenantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID")
    public ResponseEntity<ServiceItemDto> getServiceById(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(serviceItemService.getServiceById(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('CASHIER') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Register a new service in the tenant catalog")
    public ResponseEntity<ServiceItemDto> registerService(@Valid @RequestBody CreateServiceRequest request) {
        var ctx = TenantContextHolder.getRequiredContext();
        ServiceItemDto created = serviceItemService.registerService(ctx.tenantId(), request, ctx.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Update an existing catalog service")
    public ResponseEntity<ServiceItemDto> updateService(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateServiceRequest request
    ) {
        var ctx = TenantContextHolder.getRequiredContext();
        return ResponseEntity.ok(serviceItemService.updateService(ctx.tenantId(), id, request, ctx.userId()));
    }
}
