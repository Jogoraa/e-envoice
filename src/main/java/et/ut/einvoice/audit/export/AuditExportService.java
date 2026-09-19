package et.ut.einvoice.audit.export;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditCheckpointRepository;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service producing deterministic audit evidence export packages.
 * Two exports of the same immutable ledger state produce equivalent canonical content.
 */
@Service
public class AuditExportService {

    private static final Logger log = LoggerFactory.getLogger(AuditExportService.class);

    private final AuditEventRepository auditEventRepository;
    private final AuditCheckpointRepository auditCheckpointRepository;

    public AuditExportService(AuditEventRepository auditEventRepository,
                              AuditCheckpointRepository auditCheckpointRepository) {
        this.auditEventRepository = auditEventRepository;
        this.auditCheckpointRepository = auditCheckpointRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuditExportPackage> generateExport(UUID tenantId, String streamId) {
        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        if (events.isEmpty()) {
            log.warn("Cannot export audit evidence for tenant {} stream {}: no events found", tenantId, streamId);
            return Optional.empty();
        }

        AuditEvent first = events.get(0);
        AuditEvent last = events.get(events.size() - 1);

        List<AuditCheckpoint> checkpoints = auditCheckpointRepository
                .findByTenantIdAndStreamIdOrderByLastEventSequenceDesc(tenantId, streamId);
        AuditCheckpoint latestCheckpoint = checkpoints.isEmpty() ? null : checkpoints.get(0);

        String packageContentHash = computePackageContentHash(tenantId, streamId, events);

        AuditExportPackage exportPackage = new AuditExportPackage(
                UUID.randomUUID(),
                tenantId,
                streamId,
                events.size(),
                first.getSequenceNumber(),
                last.getSequenceNumber(),
                first.getEventHash(),
                last.getEventHash(),
                latestCheckpoint,
                events,
                packageContentHash,
                1,
                "1.0.0-RELEASE",
                Instant.now()
        );

        log.info("Generated deterministic audit export package for tenant {} stream {} with {} events (hash: {})",
                tenantId, streamId, events.size(), packageContentHash);

        return Optional.of(exportPackage);
    }

    public static String computePackageContentHash(UUID tenantId, String streamId, List<AuditEvent> events) {
        String eventHashes = events.stream()
                .map(e -> String.format("%d:%s", e.getSequenceNumber(), e.getEventHash()))
                .collect(Collectors.joining(";"));

        String canonical = String.format("tenantId=%s|streamId=%s|eventCount=%d|events=[%s]",
                tenantId != null ? tenantId.toString() : "",
                streamId != null ? streamId : "",
                events.size(),
                eventHashes
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
