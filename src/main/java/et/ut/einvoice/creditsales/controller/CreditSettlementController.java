package et.ut.einvoice.creditsales.controller;

import et.ut.einvoice.creditsales.domain.CreditSettlement;
import et.ut.einvoice.creditsales.dto.CreditAccountSummaryResponse;
import et.ut.einvoice.creditsales.dto.CreditSettlementResponse;
import et.ut.einvoice.creditsales.dto.SettleCreditRequest;
import et.ut.einvoice.creditsales.service.CreditSettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit-settlements")
public class CreditSettlementController {

    private final CreditSettlementService creditSettlementService;

    public CreditSettlementController(CreditSettlementService creditSettlementService) {
        this.creditSettlementService = creditSettlementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER')")
    public ResponseEntity<CreditSettlementResponse> recordSettlement(@Valid @RequestBody SettleCreditRequest request) {
        CreditSettlement settlement = creditSettlementService.recordSettlement(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreditSettlementResponse.fromEntity(settlement));
    }

    @GetMapping("/summary/{invoiceId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ACCOUNTANT', 'CASHIER', 'AUDITOR', 'AUTHORITY_AUDITOR')")
    public ResponseEntity<CreditAccountSummaryResponse> getCreditAccountSummary(@PathVariable UUID invoiceId) {
        CreditAccountSummaryResponse summary = creditSettlementService.getCreditAccountSummary(invoiceId);
        return ResponseEntity.ok(summary);
    }
}
