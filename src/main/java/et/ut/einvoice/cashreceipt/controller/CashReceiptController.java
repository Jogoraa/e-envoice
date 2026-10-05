package et.ut.einvoice.cashreceipt.controller;

import et.ut.einvoice.cashreceipt.domain.CashReceipt;
import et.ut.einvoice.cashreceipt.domain.CashReceiptPurpose;
import et.ut.einvoice.cashreceipt.dto.CashReceiptResponse;
import et.ut.einvoice.cashreceipt.dto.CreateCashReceiptRequest;
import et.ut.einvoice.cashreceipt.service.CashReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash-receipts")
public class CashReceiptController {

    private final CashReceiptService cashReceiptService;

    public CashReceiptController(CashReceiptService cashReceiptService) {
        this.cashReceiptService = cashReceiptService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    public ResponseEntity<CashReceiptResponse> issueCashReceipt(@Valid @RequestBody CreateCashReceiptRequest request) {
        CashReceipt receipt = cashReceiptService.issueCashReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CashReceiptResponse.fromEntity(receipt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<CashReceiptResponse> getCashReceipt(@PathVariable UUID id) {
        CashReceipt receipt = cashReceiptService.getCashReceipt(id);
        return ResponseEntity.ok(CashReceiptResponse.fromEntity(receipt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<List<CashReceiptResponse>> listCashReceipts(
            @RequestParam(required = false) CashReceiptPurpose purpose) {
        List<CashReceiptResponse> receipts = cashReceiptService.listCashReceipts(purpose)
                .stream()
                .map(CashReceiptResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(receipts);
    }
}
