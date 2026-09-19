package et.ut.einvoice.offline.controller;

import et.ut.einvoice.offline.domain.OfflineTransactionBuffer;
import et.ut.einvoice.offline.dto.SyncOfflineBatchRequest;
import et.ut.einvoice.offline.repository.OfflineTransactionBufferRepository;
import et.ut.einvoice.offline.service.OfflineSyncService;
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
@RequestMapping("/api/v1/offline")
@Tag(name = "Offline Continuity", description = "Offline Synchronization and Resiliency (Directive No. 1142/2026 Art. 4(4))")
public class OfflineSyncController {

    private final OfflineSyncService offlineSyncService;
    private final OfflineTransactionBufferRepository bufferRepository;

    public OfflineSyncController(OfflineSyncService offlineSyncService, OfflineTransactionBufferRepository bufferRepository) {
        this.offlineSyncService = offlineSyncService;
        this.bufferRepository = bufferRepository;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('SCOPE_invoice:create') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "Submit Offline Batch for Synchronization", description = "Buffers offline transactions for automated 72-hour reconciliation.")
    public ResponseEntity<List<OfflineTransactionBuffer>> syncOfflineTransactions(@Valid @RequestBody SyncOfflineBatchRequest request) {
        List<OfflineTransactionBuffer> buffers = offlineSyncService.bufferOfflineTransactions(request);
        return new ResponseEntity<>(buffers, HttpStatus.ACCEPTED);
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('SCOPE_invoice:read') or hasRole('TENANT_ADMIN') or hasRole('CASHIER')")
    @Operation(summary = "List Queued Offline Transactions")
    public ResponseEntity<Page<OfflineTransactionBuffer>> listQueue(@PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(bufferRepository.findAllByTenantId(tenantId, pageable));
    }
}
