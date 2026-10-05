package et.ut.einvoice.taxpayer.controller;

import et.ut.einvoice.taxpayer.domain.BusinessSector;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.service.BusinessSectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sectors")
public class BusinessSectorController {

    private final BusinessSectorService sectorService;

    public BusinessSectorController(BusinessSectorService sectorService) {
        this.sectorService = sectorService;
    }

    @GetMapping
    public ResponseEntity<List<BusinessSector>> getAllActiveSectors() {
        return ResponseEntity.ok(sectorService.getAllActiveSectors());
    }

    @GetMapping("/mandatory-offline")
    public ResponseEntity<List<BusinessSector>> getMandatoryOfflineSectors() {
        return ResponseEntity.ok(sectorService.getMandatoryOfflineSectors());
    }

    @GetMapping("/{sectorCode}")
    public ResponseEntity<BusinessSector> getSectorByCode(@PathVariable String sectorCode) {
        return ResponseEntity.ok(sectorService.getSectorByCode(sectorCode));
    }

    @PostMapping("/assign")
    public ResponseEntity<TaxpayerProfile> assignSector(
            @RequestParam(required = false) UUID tenantId,
            @RequestBody Map<String, String> body
    ) {
        String sectorCode = body.get("sectorCode");
        return ResponseEntity.ok(sectorService.assignSectorToTaxpayer(tenantId, sectorCode));
    }
}
