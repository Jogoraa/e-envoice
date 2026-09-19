package et.ut.einvoice.audit.service;

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

/**
 * Service for generating deterministic cryptographic audit checkpoints.
 */
@Service
public class AuditCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(AuditCheckpointService.class);
    public static final String GENESIS_CHECKPOINT_HASH = "0".repeat(64);

    private final AuditEventRepository auditEventRepository;
    private final AuditCheckpointRepository auditCheckpointRepository;

    public AuditCheckpointService(AuditEventRepository auditEventRepository,
                                  AuditCheckpointRepository auditCheckpointRepository) {
        this.auditEventRepository = auditEventRepository;
        this.auditCheckpointRepository = auditCheckpointRepository;
    }

    @Transactional
    public Optional<AuditCheckpoint> generateCheckpoint(UUID tenantId, String streamId) {
        List<AuditEvent> allEvents = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        if (allEvents.isEmpty()) {
            log.warn("Cannot generate checkpoint for tenant {} stream {}: no events recorded", tenantId, streamId);
            return Optional.empty();
        }

        List<AuditCheckpoint> existingCheckpoints = auditCheckpointRepository
                .findByTenantIdAndStreamIdOrderByLastEventSequenceDesc(tenantId, streamId);

        String previousCheckpointHash;
        long startSeq;

        if (existingCheckpoints.isEmpty()) {
            previousCheckpointHash = GENESIS_CHECKPOINT_HASH;
            startSeq = 1L;
        } else {
            AuditCheckpoint lastCheckpoint = existingCheckpoints.get(0);
            previousCheckpointHash = lastCheckpoint.getChainStateHash();
            startSeq = lastCheckpoint.getLastEventSequence() + 1L;
        }

        List<AuditEvent> segmentEvents = allEvents.stream()
                .filter(e -> e.getSequenceNumber() >= startSeq)
                .toList();

        if (segmentEvents.isEmpty()) {
            log.info("No new events since last checkpoint for tenant {} stream {}", tenantId, streamId);
            return Optional.of(existingCheckpoints.get(0));
        }

        AuditEvent firstEvent = segmentEvents.get(0);
        AuditEvent lastEvent = segmentEvents.get(segmentEvents.size() - 1);

        UUID checkpointId = UUID.randomUUID();
        long firstSeq = firstEvent.getSequenceNumber();
        long lastSeq = lastEvent.getSequenceNumber();
        long count = segmentEvents.size();
        String firstHash = firstEvent.getEventHash();
        String lastHash = lastEvent.getEventHash();
        int schemaVersion = 1;
        Instant now = Instant.now();

        String chainStateHash = computeChainStateHash(
                checkpointId, tenantId, streamId, firstSeq, lastSeq, count,
                firstHash, lastHash, previousCheckpointHash, schemaVersion
        );

        AuditCheckpoint checkpoint = new AuditCheckpoint(
                checkpointId, tenantId, streamId, firstSeq, lastSeq, count,
                firstHash, lastHash, chainStateHash, previousCheckpointHash,
                schemaVersion, now
        );

        AuditCheckpoint saved = auditCheckpointRepository.save(checkpoint);
        log.info("Generated audit checkpoint {} for tenant {} stream {} (seq {} - {}, count {})",
                checkpointId, tenantId, streamId, firstSeq, lastSeq, count);
        return Optional.of(saved);
    }

    public static String computeChainStateHash(UUID checkpointId, UUID tenantId, String streamId,
                                              long firstSeq, long lastSeq, long count,
                                              String firstHash, String lastHash,
                                              String prevCheckpointHash, int schemaVersion) {
        String canonical = String.format(
                "checkpointId=%s|tenantId=%s|streamId=%s|firstSeq=%d|lastSeq=%d|count=%d|firstHash=%s|lastHash=%s|prevCheckpointHash=%s|schemaVersion=%d",
                checkpointId != null ? checkpointId.toString() : "",
                tenantId != null ? tenantId.toString() : "",
                streamId != null ? streamId : "",
                firstSeq,
                lastSeq,
                count,
                firstHash != null ? firstHash : "",
                lastHash != null ? lastHash : "",
                prevCheckpointHash != null ? prevCheckpointHash : GENESIS_CHECKPOINT_HASH,
                schemaVersion
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
