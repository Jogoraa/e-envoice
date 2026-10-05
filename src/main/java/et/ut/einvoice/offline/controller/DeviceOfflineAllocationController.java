package et.ut.einvoice.offline.controller;

import et.ut.einvoice.offline.domain.DeviceOfflineAllocation;
import et.ut.einvoice.offline.dto.DeviceOfflineAllocationResponseDto;
import et.ut.einvoice.offline.dto.RequestOfflineAllocationDto;
import et.ut.einvoice.offline.service.DeviceOfflineAllocationService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offline/allocations")
@Tag(name = "Offline Allocations", description = "Pre-Allocated Temporary Offline Document Sequences (Directive No. 1142/2026 Art. 4(4) & Art. 22)")
public class DeviceOfflineAllocationController {

    private final DeviceOfflineAllocationService allocationService;

    public DeviceOfflineAllocationController(DeviceOfflineAllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasAuthority('SCOPE_invoice:create') or hasAnyRole('TENANT_ADMIN', 'CASHIER')")
    @Operation(summary = "Request Pre-Allocated Temporary Offline Sequence Range")
    public ResponseEntity<DeviceOfflineAllocationResponseDto> requestAllocation(@Valid @RequestBody RequestOfflineAllocationDto request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Duration duration = request.validityDays() != null ? Duration.ofDays(request.validityDays()) : Duration.ofDays(7);
        long blockSize = request.blockSize() > 0 ? request.blockSize() : 100;

        DeviceOfflineAllocation allocation = allocationService.allocateRange(
                tenantId,
                request.deviceId(),
                blockSize,
                duration,
                request.authorityRef()
        );
        return new ResponseEntity<>(DeviceOfflineAllocationResponseDto.fromEntity(allocation), HttpStatus.CREATED);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasAnyRole('TENANT_ADMIN', 'CASHIER')")
    @Operation(summary = "Get Active Offline Sequence Allocation for Device")
    public ResponseEntity<DeviceOfflineAllocationResponseDto> getActiveAllocation(@RequestParam UUID deviceId) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return allocationService.getActiveAllocation(tenantId, deviceId)
                .map(alloc -> ResponseEntity.ok(DeviceOfflineAllocationResponseDto.fromEntity(alloc)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{allocationId}/revoke")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Revoke Pre-Allocated Offline Sequence Range")
    public ResponseEntity<Void> revokeAllocation(
            @PathVariable String allocationId,
            @RequestParam(required = false, defaultValue = "Operator requested revocation") String reason
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        allocationService.revokeAllocation(tenantId, allocationId, reason);
        return ResponseEntity.noContent().build();
    }
}
