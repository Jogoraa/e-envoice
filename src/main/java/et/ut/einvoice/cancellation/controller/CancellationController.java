package et.ut.einvoice.cancellation.controller;

import et.ut.einvoice.cancellation.domain.CancellationRequest;
import et.ut.einvoice.cancellation.dto.CreateCancellationRequestDto;
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
    public ResponseEntity<CancellationRequest> requestCancellation(@Valid @RequestBody CreateCancellationRequestDto request) {
        CancellationRequest result = cancellationService.requestCancellation(request);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Get Cancellation Request by ID")
    public ResponseEntity<CancellationRequest> getCancellation(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CancellationRequest req = cancellationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("CANCELLATION_NOT_FOUND", "Cancellation request not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "List Cancellation Requests")
    public ResponseEntity<Page<CancellationRequest>> listCancellations(@PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(cancellationRepository.findAllByTenantId(tenantId, pageable));
    }
}
