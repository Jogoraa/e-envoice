package et.ut.einvoice.taxpayer.controller;

import et.ut.einvoice.taxpayer.domain.TenantLifecycleEvent;
import et.ut.einvoice.taxpayer.service.TenantLifecycleNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-lifecycle")
public class TenantLifecycleController {

    private final TenantLifecycleNotificationService lifecycleService;

    public TenantLifecycleController(TenantLifecycleNotificationService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/commence/{tenantId}")
    public ResponseEntity<TenantLifecycleEvent> triggerCommencement(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(lifecycleService.triggerCommencementNotification(tenantId));
    }

    @PostMapping("/terminate/{tenantId}")
    public ResponseEntity<TenantLifecycleEvent> triggerTermination(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(lifecycleService.triggerTerminationNotification(tenantId));
    }

    @GetMapping("/events/{tenantId}")
    public ResponseEntity<List<TenantLifecycleEvent>> getEvents(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(lifecycleService.getEventsForTenant(tenantId));
    }

    @PostMapping("/acknowledge/{eventId}")
    public ResponseEntity<TenantLifecycleEvent> recordAcknowledgement(
            @PathVariable UUID eventId,
            @RequestBody Map<String, String> body
    ) {
        String ackRef = body.get("ackReference");
        String payload = body.get("payload");
        return ResponseEntity.ok(lifecycleService.recordGovernmentAcknowledgement(eventId, ackRef, payload));
    }
}
