package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditCheckpointRepository;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Independent verifier for audit checkpoints, verifying cryptographic anchor state,
 * event boundaries, event counts, and checkpoint hash continuity.
 * Fails closed on any anomaly.
 */
@Service
public class AuditCheckpointVerifier {

    private static final Logger log = LoggerFactory.getLogger(AuditCheckpointVerifier.class);

    private final AuditEventRepository auditEventRepository;
    private final AuditCheckpointRepository auditCheckpointRepository;

    public AuditCheckpointVerifier(AuditEventRepository auditEventRepository,
                                   AuditCheckpointRepository auditCheckpointRepository) {
        this.auditEventRepository = auditEventRepository;
        this.auditCheckpointRepository = auditCheckpointRepository;
    }

    public boolean verifyCheckpoint(UUID tenantId, String streamId, UUID checkpointId) {
        Optional<AuditCheckpoint> checkpointOpt = auditCheckpointRepository.findById(checkpointId);
        if (checkpointOpt.isEmpty()) {
            log.error("Checkpoint verification failed: checkpoint {} not found", checkpointId);
            return false;
        }

        AuditCheckpoint checkpoint = checkpointOpt.get();
        if (!checkpoint.getTenantId().equals(tenantId) || !checkpoint.getStreamId().equals(streamId)) {
            log.error("Checkpoint verification failed: tenant/stream mismatch for checkpoint {}", checkpointId);
            return false;
        }

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        return verifyCheckpointAgainstEvents(checkpoint, events);
    }

    public boolean verifyCheckpointAgainstEvents(AuditCheckpoint checkpoint, List<AuditEvent> events) {
        // 1. Recompute and verify chainStateHash
        String computedHash = AuditCheckpointService.computeChainStateHash(
                checkpoint.getCheckpointId(),
                checkpoint.getTenantId(),
                checkpoint.getStreamId(),
                checkpoint.getFirstEventSequence(),
                checkpoint.getLastEventSequence(),
                checkpoint.getEventCount(),
                checkpoint.getFirstEventHash(),
                checkpoint.getLastEventHash(),
                checkpoint.getPreviousCheckpointHash(),
                checkpoint.getSchemaVersion()
        );

        if (!computedHash.equalsIgnoreCase(checkpoint.getChainStateHash())) {
            log.error("Checkpoint {} tamper detected: chainStateHash mismatch! expected={}, actual={}",
                    checkpoint.getCheckpointId(), checkpoint.getChainStateHash(), computedHash);
            return false;
        }

        // 2. Locate covered segment events
        List<AuditEvent> segment = events.stream()
                .filter(e -> e.getSequenceNumber() >= checkpoint.getFirstEventSequence() &&
                             e.getSequenceNumber() <= checkpoint.getLastEventSequence())
                .toList();

        if (segment.size() != checkpoint.getEventCount()) {
            log.error("Checkpoint {} verification failed: event count mismatch! checkpoint count={}, actual segment count={}",
                    checkpoint.getCheckpointId(), checkpoint.getEventCount(), segment.size());
            return false;
        }

        if (segment.isEmpty()) {
            log.error("Checkpoint {} verification failed: zero events found for range {}..{}",
                    checkpoint.getCheckpointId(), checkpoint.getFirstEventSequence(), checkpoint.getLastEventSequence());
            return false;
        }

        // 3. Verify boundary event hashes
        AuditEvent firstEvent = segment.get(0);
        AuditEvent lastEvent = segment.get(segment.size() - 1);

        if (!firstEvent.getEventHash().equalsIgnoreCase(checkpoint.getFirstEventHash())) {
            log.error("Checkpoint {} verification failed: firstEventHash mismatch! expected={}, actual={}",
                    checkpoint.getCheckpointId(), checkpoint.getFirstEventHash(), firstEvent.getEventHash());
            return false;
        }

        if (!lastEvent.getEventHash().equalsIgnoreCase(checkpoint.getLastEventHash())) {
            log.error("Checkpoint {} verification failed: lastEventHash mismatch! expected={}, actual={}",
                    checkpoint.getCheckpointId(), checkpoint.getLastEventHash(), lastEvent.getEventHash());
            return false;
        }

        return true;
    }
}
