package et.ut.einvoice.marketplace.controller;

import et.ut.einvoice.marketplace.domain.MarketplaceMerchant;
import et.ut.einvoice.marketplace.service.MarketplaceService;
import et.ut.einvoice.marketplace.service.MarketplaceService.MultiSellerOrderRequest;
import et.ut.einvoice.marketplace.service.MarketplaceService.MultiSellerOrderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    public MarketplaceController(MarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @PostMapping("/merchants")
    public ResponseEntity<MarketplaceMerchant> registerMerchant(@RequestBody MarketplaceMerchant merchant) {
        return ResponseEntity.ok(marketplaceService.registerMerchant(merchant));
    }

    @PostMapping("/orders/checkout")
    public ResponseEntity<MultiSellerOrderResponse> processOrder(@RequestBody MultiSellerOrderRequest request) {
        return ResponseEntity.ok(marketplaceService.processMultiSellerOrder(request));
    }

    @PostMapping("/merchants/{merchantId}/suspend")
    public ResponseEntity<MarketplaceMerchant> suspendMerchant(
            @PathVariable UUID merchantId,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        String authorityRef = body.get("authorityReference");
        return ResponseEntity.ok(marketplaceService.suspendMerchantByAuthority(merchantId, reason, authorityRef));
    }

    @PostMapping("/merchants/{merchantId}/reinstate")
    public ResponseEntity<MarketplaceMerchant> reinstateMerchant(
            @PathVariable UUID merchantId,
            @RequestBody Map<String, String> body
    ) {
        String authorityRef = body.get("authorityReference");
        return ResponseEntity.ok(marketplaceService.reinstateMerchantByAuthority(merchantId, authorityRef));
    }
}
