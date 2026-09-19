package et.ut.einvoice.audit.ledger;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditCheckpointRepository;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.service.AuditChainVerifier;
import et.ut.einvoice.audit.service.AuditCheckpointVerifier;
import et.ut.einvoice.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local development adapter for the Immutable Audit Ledger.
 * Persists audit events to PostgreSQL with database-level append-only triggers,
 * monotonic per-stream row locking, and deterministic SHA-256 hash chaining.
 */
@Component
@Primary
public class LocalImmutableAuditLedgerAdapter implements ImmutableAuditLedger {

    private static final Logger log = LoggerFactory.getLogger(LocalImmutableAuditLedgerAdapter.class);

    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;
    private final AuditChainVerifier auditChainVerifier;
    private final AuditCheckpointRepository auditCheckpointRepository;
    private final AuditCheckpointVerifier auditCheckpointVerifier;

    public LocalImmutableAuditLedgerAdapter(AuditService auditService,
                                           AuditEventRepository auditEventRepository,
                                           AuditChainVerifier auditChainVerifier,
                                           AuditCheckpointRepository auditCheckpointRepository,
                                           AuditCheckpointVerifier auditCheckpointVerifier) {
        this.auditService = auditService;
        this.auditEventRepository = auditEventRepository;
        this.auditChainVerifier = auditChainVerifier;
        this.auditCheckpointRepository = auditCheckpointRepository;
        this.auditCheckpointVerifier = auditCheckpointVerifier;
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        return auditService.recordEvent(
                event.getTenantId(),
                event.getStreamId(),
                event.getActorId(),
                event.getActorType(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getPayloadJson(),
                event.getCorrelationId(),
                event.getTraceId()
        );
    }

    @Override
    public List<AuditEvent> appendBatch(List<AuditEvent> events) {
        List<AuditEvent> results = new ArrayList<>(events.size());
        for (AuditEvent event : events) {
            results.add(append(event));
        }
        return results;
    }

    @Override
    public boolean verify(AuditEvent event) {
        if (event == null || event.getTenantId() == null || event.getStreamId() == null) {
            return false;
        }
        List<AuditEvent> streamEvents = auditEventRepository
                .findByTenantIdAndStreamIdOrderBySequenceNumberAsc(event.getTenantId(), event.getStreamId());
        AuditChainVerifier.ChainVerificationResult result = auditChainVerifier.verifyChain(streamEvents);
        return result.valid();
    }

    @Override
    public boolean verifyStream(UUID tenantId, String streamId) {
        List<AuditEvent> events = auditEventRepository
                .findByTenantIdAndStreamIdOrderBySequenceNumberAsc(tenantId, streamId);
        if (events.isEmpty()) {
            return true;
        }
        return auditChainVerifier.verifyChain(events).valid();
    }

    @Override
    public Optional<AuditCheckpoint> getCheckpoint(UUID tenantId, String streamId) {
        List<AuditCheckpoint> checkpoints = auditCheckpointRepository
                .findByTenantIdAndStreamIdOrderByLastEventSequenceDesc(tenantId, streamId);
        return checkpoints.isEmpty() ? Optional.empty() : Optional.of(checkpoints.get(0));
    }

    @Override
    public boolean verifyCheckpoint(UUID tenantId, String streamId, UUID checkpointId) {
        return auditCheckpointVerifier.verifyCheckpoint(tenantId, streamId, checkpointId);
    }
}
