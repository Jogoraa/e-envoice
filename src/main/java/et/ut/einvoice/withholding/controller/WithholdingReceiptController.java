package et.ut.einvoice.withholding.controller;

import et.ut.einvoice.withholding.domain.WithholdingReceipt;
import et.ut.einvoice.withholding.domain.WithholdingType;
import et.ut.einvoice.withholding.dto.CreateWithholdingReceiptRequest;
import et.ut.einvoice.withholding.dto.WithholdingReceiptResponse;
import et.ut.einvoice.withholding.service.WithholdingReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withholding-receipts")
public class WithholdingReceiptController {

    private final WithholdingReceiptService withholdingReceiptService;

    public WithholdingReceiptController(WithholdingReceiptService withholdingReceiptService) {
        this.withholdingReceiptService = withholdingReceiptService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    public ResponseEntity<WithholdingReceiptResponse> issueWithholdingReceipt(@Valid @RequestBody CreateWithholdingReceiptRequest request) {
        WithholdingReceipt receipt = withholdingReceiptService.issueWithholdingReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(WithholdingReceiptResponse.fromEntity(receipt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<WithholdingReceiptResponse> getWithholdingReceipt(@PathVariable UUID id) {
        WithholdingReceipt receipt = withholdingReceiptService.getWithholdingReceipt(id);
        return ResponseEntity.ok(WithholdingReceiptResponse.fromEntity(receipt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<List<WithholdingReceiptResponse>> listWithholdingReceipts(
            @RequestParam(required = false) WithholdingType type) {
        List<WithholdingReceiptResponse> receipts = withholdingReceiptService.listWithholdingReceipts(type)
                .stream()
                .map(WithholdingReceiptResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(receipts);
    }
}
