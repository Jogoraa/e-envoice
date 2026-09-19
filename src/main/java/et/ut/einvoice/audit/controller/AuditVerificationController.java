package et.ut.einvoice.audit.controller;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.export.AuditExportPackage;
import et.ut.einvoice.audit.export.AuditExportService;
import et.ut.einvoice.audit.export.AuditExportVerifier;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.service.AuditChainVerifier;
import et.ut.einvoice.audit.service.AuditCheckpointService;
import et.ut.einvoice.audit.service.AuditCheckpointVerifier;
import et.ut.einvoice.audit.signature.CheckpointSigner;
import et.ut.einvoice.audit.signature.SignedCheckpoint;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticated Administrative and Internal APIs for Audit Verification, Checkpoint Generation,
 * and Independent Audit Evidence Export.
 * <p>
 * Strictly enforces tenant boundaries and RBAC (ROLE_TENANT_ADMIN, ROLE_PLATFORM_ADMIN, ROLE_AUTHORITY_AUDITOR).
 * Never permits historical event modification, deletion, or rewriting.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/audit/verification")
@Tag(name = "Audit Verification Interface", description = "Cryptographic Audit Verification & Evidence APIs")
@SecurityRequirement(name = "BearerAuth")
public class AuditVerificationController {

    private final AuditEventRepository auditEventRepository;
    private final AuditChainVerifier auditChainVerifier;
    private final AuditCheckpointService checkpointService;
    private final AuditCheckpointVerifier checkpointVerifier;
    private final CheckpointSigner checkpointSigner;
    private final AuditExportService exportService;
    private final AuditExportVerifier exportVerifier;

    public AuditVerificationController(AuditEventRepository auditEventRepository,
                                       AuditChainVerifier auditChainVerifier,
                                       AuditCheckpointService checkpointService,
                                       AuditCheckpointVerifier checkpointVerifier,
                                       CheckpointSigner checkpointSigner,
                                       AuditExportService exportService,
                                       AuditExportVerifier exportVerifier) {
        this.auditEventRepository = auditEventRepository;
        this.auditChainVerifier = auditChainVerifier;
        this.checkpointService = checkpointService;
        this.checkpointVerifier = checkpointVerifier;
        this.checkpointSigner = checkpointSigner;
        this.exportService = exportService;
        this.exportVerifier = exportVerifier;
    }

    @GetMapping("/stream/{streamId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHORITY_AUDITOR')")
    @Operation(summary = "Verify Audit Stream Cryptographic Chain Integrity")
    public ResponseEntity<AuditChainVerifier.StreamVerificationResult> verifyStream(@PathVariable String streamId) {
        UUID tenantId = resolveTenantId();
        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        AuditChainVerifier.StreamVerificationResult result = auditChainVerifier.verifyStreamChain(tenantId, streamId, events);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHORITY_AUDITOR')")
    @Operation(summary = "Verify Individual Audit Event Hash")
    public ResponseEntity<?> verifyEvent(@PathVariable UUID eventId) {
        UUID tenantId = resolveTenantId();
        Optional<AuditEvent> eventOpt = auditEventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AuditEvent event = eventOpt.get();
        if (!event.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied to cross-tenant audit event"));
        }

        boolean valid = auditChainVerifier.verifySingleEvent(event);
        return ResponseEntity.ok(Map.of(
                "eventId", event.getId(),
                "sequenceNumber", event.getSequenceNumber(),
                "isValid", valid,
                "eventHash", event.getEventHash()
        ));
    }

    @PostMapping("/checkpoint/{streamId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Generate Cryptographic Audit Checkpoint")
    public ResponseEntity<?> generateCheckpoint(@PathVariable String streamId) {
        UUID tenantId = resolveTenantId();
        Optional<AuditCheckpoint> checkpointOpt = checkpointService.generateCheckpoint(tenantId, streamId);
        if (checkpointOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No audit events found to checkpoint"));
        }

        AuditCheckpoint cp = checkpointOpt.get();
        String signature = checkpointSigner.sign(cp.getChainStateHash());
        SignedCheckpoint signed = new SignedCheckpoint(cp, signature, checkpointSigner.getKeyId(), checkpointSigner.getAlgorithm());

        return ResponseEntity.ok(signed);
    }

    @GetMapping("/checkpoint/{streamId}/{checkpointId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHORITY_AUDITOR')")
    @Operation(summary = "Verify Cryptographic Audit Checkpoint")
    public ResponseEntity<?> verifyCheckpoint(@PathVariable String streamId, @PathVariable UUID checkpointId) {
        UUID tenantId = resolveTenantId();
        boolean valid = checkpointVerifier.verifyCheckpoint(tenantId, streamId, checkpointId);
        return ResponseEntity.ok(Map.of(
                "checkpointId", checkpointId,
                "tenantId", tenantId,
                "streamId", streamId,
                "isValid", valid
        ));
    }

    @GetMapping("/export/{streamId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHORITY_AUDITOR')")
    @Operation(summary = "Export Deterministic Audit Evidence Package")
    public ResponseEntity<?> exportEvidence(@PathVariable String streamId) {
        UUID tenantId = resolveTenantId();
        Optional<AuditExportPackage> pkgOpt = exportService.generateExport(tenantId, streamId);
        if (pkgOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pkgOpt.get());
    }

    @PostMapping("/verify-export")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHORITY_AUDITOR')")
    @Operation(summary = "Independently Verify Exported Audit Evidence Package")
    public ResponseEntity<?> verifyExportPackage(@RequestBody AuditExportPackage pkg) {
        UUID tenantId = resolveTenantId();
        if (!pkg.tenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tenant mismatch in exported package"));
        }
        boolean valid = exportVerifier.verifyPackage(pkg);
        return ResponseEntity.ok(Map.of(
                "exportId", pkg.exportId(),
                "isValid", valid
        ));
    }

    private UUID resolveTenantId() {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalStateException("Unauthenticated: tenant context required");
        }
        return ctx.tenantId();
    }
}
