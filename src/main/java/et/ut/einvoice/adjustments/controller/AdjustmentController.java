package et.ut.einvoice.adjustments.controller;

import et.ut.einvoice.adjustments.domain.NoteType;
import et.ut.einvoice.adjustments.domain.TaxAdjustment;
import et.ut.einvoice.adjustments.dto.CreateAdjustmentRequest;
import et.ut.einvoice.adjustments.repository.TaxAdjustmentRepository;
import et.ut.einvoice.adjustments.service.AdjustmentService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/adjustments")
@Tag(name = "Adjustments", description = "Tax Debit and Credit Notes (Directive No. 1142/2026 Art. 25)")
public class AdjustmentController {

    private final AdjustmentService adjustmentService;
    private final TaxAdjustmentRepository adjustmentRepository;

    public AdjustmentController(AdjustmentService adjustmentService, TaxAdjustmentRepository adjustmentRepository) {
        this.adjustmentService = adjustmentService;
        this.adjustmentRepository = adjustmentRepository;
    }

    @PostMapping("/credit-notes")
    @PreAuthorize("hasAuthority('SCOPE_invoice:adjust') or hasAuthority('SCOPE_adjustment:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Issue Tax Credit Note", description = "Issues a Credit Note against an existing registered invoice.")
    public ResponseEntity<TaxAdjustment> issueCreditNote(@Valid @RequestBody CreateAdjustmentRequest request) {
        TaxAdjustment adjustment = adjustmentService.createAdjustment(NoteType.CREDIT_NOTE, request);
        return new ResponseEntity<>(adjustment, HttpStatus.CREATED);
    }

    @PostMapping("/debit-notes")
    @PreAuthorize("hasAuthority('SCOPE_invoice:adjust') or hasAuthority('SCOPE_adjustment:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Issue Tax Debit Note", description = "Issues a Debit Note for upward price adjustments.")
    public ResponseEntity<TaxAdjustment> issueDebitNote(@Valid @RequestBody CreateAdjustmentRequest request) {
        TaxAdjustment adjustment = adjustmentService.createAdjustment(NoteType.DEBIT_NOTE, request);
        return new ResponseEntity<>(adjustment, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasAuthority('SCOPE_adjustment:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "List Tax Adjustments")
    public ResponseEntity<Page<TaxAdjustment>> listAdjustments(@PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(adjustmentRepository.findAllByTenantId(tenantId, pageable));
    }
}
