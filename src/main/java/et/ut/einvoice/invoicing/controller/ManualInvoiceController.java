package et.ut.einvoice.invoicing.controller;

import et.ut.einvoice.invoicing.domain.ManualFiscalDocument;
import et.ut.einvoice.invoicing.dto.ManualBatchRequestDto;
import et.ut.einvoice.invoicing.dto.ManualFiscalDocumentResponseDto;
import et.ut.einvoice.invoicing.dto.ManualReprintResponseDto;
import et.ut.einvoice.invoicing.repository.ManualFiscalDocumentRepository;
import et.ut.einvoice.invoicing.service.ManualInvoiceReconciliationService;
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
@Tag(name = "Manual Invoice Reconciliation", description = "Manual Paper/QR Fallback and Post-Outage Reconciliation (Directive No. 1142/2026 Art. 22)")
public class ManualInvoiceController {

    private final ManualInvoiceReconciliationService manualService;
    private final ManualFiscalDocumentRepository manualRepository;

    public ManualInvoiceController(
            ManualInvoiceReconciliationService manualService,
            ManualFiscalDocumentRepository manualRepository
    ) {
        this.manualService = manualService;
        this.manualRepository = manualRepository;
    }

    @PostMapping({"/api/v1/invoices/manual-batch", "/api/v1/invoices/manual/batch"})
    @PreAuthorize("hasAuthority('SCOPE_invoice:create') or hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    @Operation(summary = "Import and Reconcile Manual Paper Invoices", description = "Reconciles post-outage manual invoices within the 72-hour statutory window under Art. 22.")
    public ResponseEntity<List<ManualFiscalDocumentResponseDto>> reconcileManualBatch(@Valid @RequestBody ManualBatchRequestDto batchRequest) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        List<ManualFiscalDocumentResponseDto> response = manualService.reconcileManualBatch(tenantId, batchRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/invoices/manual/{id}/reprint")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    @Operation(summary = "Reprint Manual Invoice with Mandatory DUPLICATE Marking", description = "Subsequent reprint copies are visibly marked DUPLICATE without altering original fiscal totals.")
    public ResponseEntity<ManualReprintResponseDto> reprintManualInvoice(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        ManualReprintResponseDto response = manualService.reprintManualInvoice(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/invoices/manual")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    @Operation(summary = "List Reconciled Manual Invoices")
    public ResponseEntity<Page<ManualFiscalDocumentResponseDto>> listManualInvoices(@PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Page<ManualFiscalDocument> page = manualRepository.findAllByTenantId(tenantId, pageable);
        return ResponseEntity.ok(page.map(ManualFiscalDocumentResponseDto::fromEntity));
    }
}
