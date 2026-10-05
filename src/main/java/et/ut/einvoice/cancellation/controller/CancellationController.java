package et.ut.einvoice.cancellation.controller;

import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.dto.CreateCancellationRequestDto;
import et.ut.einvoice.cancellation.dto.CancellationResponseDto;
import et.ut.einvoice.cancellation.repository.CancellationRequestRepository;
import et.ut.einvoice.cancellation.service.CancellationService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cancellations")
@Tag(name = "Cancellations", description = "Invoice Cancellation State Machine (Directive No. 1142/2026 Art. 26)")
public class CancellationController {

    private final CancellationService cancellationService;
    private final CancellationRequestRepository cancellationRepository;

    public CancellationController(CancellationService cancellationService, CancellationRequestRepository cancellationRepository) {
        this.cancellationService = cancellationService;
        this.cancellationRepository = cancellationRepository;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:cancel') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Submit Cancellation Request", description = "Initiates an invoice cancellation workflow subject to MoR approval.")
    public ResponseEntity<CancellationResponseDto> requestCancellation(@Valid @RequestBody CreateCancellationRequestDto request) {
        CancellationRequest result = cancellationService.requestCancellation(request);
        return new ResponseEntity<>(CancellationResponseDto.fromEntity(result), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Get Cancellation Request by ID")
    public ResponseEntity<CancellationResponseDto> getCancellation(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CancellationRequest req = cancellationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("CANCELLATION_NOT_FOUND", "Cancellation request not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(CancellationResponseDto.fromEntity(req));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "List Cancellation Requests")
    public ResponseEntity<Page<CancellationResponseDto>> listCancellations(@PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(cancellationRepository.findAllByTenantId(tenantId, pageable).map(CancellationResponseDto::fromEntity));
    }

    @PostMapping("/{id}/demand-evidence")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Authority Demands Additional Evidence (Art. 26(3))", description = "Initiates the statutory 48-hour countdown clock for evidence submission.")
    public ResponseEntity<et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto> demandEvidence(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "48") long hours
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CancellationRequest req = cancellationService.demandAuthorityEvidence(tenantId, id, java.time.Duration.ofHours(hours));
        return ResponseEntity.ok(et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto.fromEntity(req));
    }

    @PostMapping("/evidence")
    @PreAuthorize("hasAuthority('SCOPE_invoice:cancel') or hasAnyRole('TENANT_ADMIN', 'CASHIER')")
    @Operation(summary = "Submit Evidence Attachments for Cancellation", description = "Uploads verified evidence within the 48-hour statutory window.")
    public ResponseEntity<et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto> submitEvidence(
            @Valid @RequestBody et.ut.einvoice.cancellation.dto.SubmitEvidenceDto dto
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CancellationRequest req = cancellationService.submitCancellationEvidence(tenantId, dto);
        return ResponseEntity.ok(et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto.fromEntity(req));
    }

    @PostMapping("/{id}/authority-approve")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Authority Approves Cancellation", description = "Fiscally marks invoice CANCELLED upon authoritative approval.")
    public ResponseEntity<et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto> approveCancellation(
            @PathVariable UUID id,
            @RequestParam String cancellationRef
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CancellationRequest req = cancellationService.finalizeAuthorityApproval(tenantId, id, cancellationRef);
        return ResponseEntity.ok(et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto.fromEntity(req));
    }

    @PostMapping("/{id}/authority-reject")
    @PreAuthorize("hasAnyAuthority('ROLE_AUTHORITY_AUDITOR', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Authority Rejects Cancellation", description = "Rejection keeps invoice registered and immutable.")
    public ResponseEntity<et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto> rejectCancellation(
            @PathVariable UUID id,
            @RequestParam String reason
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CancellationRequest req = cancellationService.finalizeAuthorityRejection(tenantId, id, reason);
        return ResponseEntity.ok(et.ut.einvoice.cancellation.dto.CancellationStatusResponseDto.fromEntity(req));
    }
}
