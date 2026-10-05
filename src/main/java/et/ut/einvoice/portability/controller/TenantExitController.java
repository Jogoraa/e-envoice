package et.ut.einvoice.portability.controller;

import et.ut.einvoice.portability.domain.PurgeAuditCertificate;
import et.ut.einvoice.portability.domain.TenantExitRequest;
import et.ut.einvoice.portability.service.TenantDecommissioningService;
import et.ut.einvoice.portability.service.TenantDecommissioningService.RetentionClassificationSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portability/exit")
public class TenantExitController {

    private final TenantDecommissioningService decommissioningService;

    public TenantExitController(TenantDecommissioningService decommissioningService) {
        this.decommissioningService = decommissioningService;
    }

    @PostMapping("/request")
    public ResponseEntity<TenantExitRequest> requestExit(@RequestBody Map<String, String> body) {
        String destinationProvider = body.get("destinationProvider");
        String reason = body.get("reason");
        return ResponseEntity.ok(decommissioningService.requestExit(destinationProvider, reason));
    }

    @PostMapping("/verify-archive/{exitRequestId}")
    public ResponseEntity<TenantExitRequest> verifyArchive(
            @PathVariable UUID exitRequestId,
            @RequestBody Map<String, String> body
    ) {
        String checksum = body.get("archiveChecksum");
        return ResponseEntity.ok(decommissioningService.verifyAndConfirmArchive(exitRequestId, checksum));
    }

    @GetMapping("/retention-summary/{tenantId}")
    public ResponseEntity<RetentionClassificationSummary> getRetentionSummary(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(decommissioningService.evaluateRetention(tenantId));
    }

    @PostMapping("/execute-purge/{exitRequestId}")
    public ResponseEntity<PurgeAuditCertificate> executePurge(
            @PathVariable UUID exitRequestId,
            @RequestBody Map<String, String> body
    ) {
        String tenantAdminApproval = body.get("tenantAdminApproval");
        String platformAdminApproval = body.get("platformAdminApproval");
        return ResponseEntity.ok(decommissioningService.executeDualAuthorizedPurge(
                exitRequestId, tenantAdminApproval, platformAdminApproval
        ));
    }
}
