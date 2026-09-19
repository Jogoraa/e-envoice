package et.ut.einvoice.audit.ledger;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Future integration adapter boundary for an independent immutable ledger (immudb).
 * <p>
 * In this DEVELOPMENT-STAGE CODEBASE ONLY phase, external immutable database clusters,
 * TLS mutual authentication, and immudb container deployments are strictly deferred
 * to the infrastructure deployment phase.
 * </p>
 */
@Component
@ConditionalOnProperty(name = "ut.audit.ledger.provider", havingValue = "immudb")
public class ImmudbAuditLedgerAdapter implements ImmutableAuditLedger {

    private static final Logger log = LoggerFactory.getLogger(ImmudbAuditLedgerAdapter.class);

    public ImmudbAuditLedgerAdapter() {
        log.warn("ImmudbAuditLedgerAdapter initialized - external immudb cluster connection deferred to deployment phase.");
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        throw new UnsupportedOperationException("immudb production cluster integration is deferred to deployment phase");
    }

    @Override
    public List<AuditEvent> appendBatch(List<AuditEvent> events) {
        throw new UnsupportedOperationException("immudb production cluster integration is deferred to deployment phase");
    }

    @Override
    public boolean verify(AuditEvent event) {
        throw new UnsupportedOperationException("immudb production cluster integration is deferred to deployment phase");
    }

    @Override
    public boolean verifyStream(UUID tenantId, String streamId) {
        throw new UnsupportedOperationException("immudb production cluster integration is deferred to deployment phase");
    }

    @Override
    public Optional<AuditCheckpoint> getCheckpoint(UUID tenantId, String streamId) {
        throw new UnsupportedOperationException("immudb production cluster integration is deferred to deployment phase");
    }

    @Override
    public boolean verifyCheckpoint(UUID tenantId, String streamId, UUID checkpointId) {
        throw new UnsupportedOperationException("immudb production cluster integration is deferred to deployment phase");
    }
}
