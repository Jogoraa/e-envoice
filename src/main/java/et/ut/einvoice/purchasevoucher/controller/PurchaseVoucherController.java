package et.ut.einvoice.purchasevoucher.controller;

import et.ut.einvoice.purchasevoucher.domain.PurchaseVoucher;
import et.ut.einvoice.purchasevoucher.dto.CreatePurchaseVoucherRequest;
import et.ut.einvoice.purchasevoucher.dto.PurchaseVoucherResponse;
import et.ut.einvoice.purchasevoucher.service.PurchaseVoucherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-vouchers")
public class PurchaseVoucherController {

    private final PurchaseVoucherService purchaseVoucherService;

    public PurchaseVoucherController(PurchaseVoucherService purchaseVoucherService) {
        this.purchaseVoucherService = purchaseVoucherService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    public ResponseEntity<PurchaseVoucherResponse> createPurchaseVoucher(@Valid @RequestBody CreatePurchaseVoucherRequest request) {
        PurchaseVoucher voucher = purchaseVoucherService.createPurchaseVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PurchaseVoucherResponse.fromEntity(voucher));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<PurchaseVoucherResponse> getPurchaseVoucher(@PathVariable UUID id) {
        PurchaseVoucher voucher = purchaseVoucherService.getPurchaseVoucher(id);
        return ResponseEntity.ok(PurchaseVoucherResponse.fromEntity(voucher));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<List<PurchaseVoucherResponse>> listPurchaseVouchers(
            @RequestParam(required = false) String supplierTin) {
        List<PurchaseVoucherResponse> vouchers = purchaseVoucherService.listPurchaseVouchers(supplierTin)
                .stream()
                .map(PurchaseVoucherResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(vouchers);
    }
}
