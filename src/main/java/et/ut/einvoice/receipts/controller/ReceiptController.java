package et.ut.einvoice.receipts.controller;

import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.receipts.domain.Receipt;
import et.ut.einvoice.receipts.domain.ReceiptType;
import et.ut.einvoice.receipts.dto.CreateReceiptRequest;
import et.ut.einvoice.receipts.repository.ReceiptRepository;
import et.ut.einvoice.receipts.service.ReceiptService;
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
@RequestMapping("/api/v1/receipts")
@Tag(name = "Receipts", description = "Sales and Withholding Receipts linked to Registered Invoices (IRC-P03, IRC-P04)")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptRepository receiptRepository;

    public ReceiptController(ReceiptService receiptService, ReceiptRepository receiptRepository) {
        this.receiptService = receiptService;
        this.receiptRepository = receiptRepository;
    }

    @PostMapping("/sales")
    @PreAuthorize("hasAuthority('SCOPE_receipt:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Register Sales Receipt", description = "Issues a sales receipt linked to an existing registered invoice.")
    public ResponseEntity<Receipt> createSalesReceipt(@Valid @RequestBody CreateReceiptRequest request) {
        Receipt receipt = receiptService.createReceipt(ReceiptType.SALES_RECEIPT, request);
        return new ResponseEntity<>(receipt, HttpStatus.CREATED);
    }

    @PostMapping("/withholding")
    @PreAuthorize("hasAuthority('SCOPE_receipt:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Register Withholding Tax Receipt", description = "Issues a withholding tax confirmation receipt.")
    public ResponseEntity<Receipt> createWithholdingReceipt(@Valid @RequestBody CreateReceiptRequest request) {
        Receipt receipt = receiptService.createReceipt(ReceiptType.WITHHOLDING_RECEIPT, request);
        return new ResponseEntity<>(receipt, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasAuthority('SCOPE_receipt:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "List Receipts")
    public ResponseEntity<Page<Receipt>> listReceipts(@PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(receiptRepository.findAllByTenantId(tenantId, pageable));
    }
}
