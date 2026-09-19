package et.ut.einvoice.audit.export;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.service.AuditChainVerifier;
import et.ut.einvoice.audit.service.AuditCheckpointVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Independent verification tooling for exported audit evidence packages.
 * Verifies exported evidence packages offline without requiring database access.
 * Fails closed on any corruption, truncation, tampering, or sequence discontinuity.
 */
@Component
public class AuditExportVerifier {

    private static final Logger log = LoggerFactory.getLogger(AuditExportVerifier.class);

    private final AuditChainVerifier chainVerifier;
    private final AuditCheckpointVerifier checkpointVerifier;

    public AuditExportVerifier(AuditChainVerifier chainVerifier,
                               AuditCheckpointVerifier checkpointVerifier) {
        this.chainVerifier = chainVerifier;
        this.checkpointVerifier = checkpointVerifier;
    }

    public boolean verifyPackage(AuditExportPackage pkg) {
        if (pkg == null) {
            log.error("Audit export verification failed: null package");
            return false;
        }

        List<AuditEvent> events = pkg.events();
        if (events == null || events.isEmpty()) {
            log.error("Audit export verification failed: empty event list");
            return false;
        }

        if (events.size() != pkg.eventCount()) {
            log.error("Audit export verification failed: event count mismatch (pkg: {}, actual: {})",
                    pkg.eventCount(), events.size());
            return false;
        }

        // 1. Verify packageContentHash
        String computedPackageHash = AuditExportService.computePackageContentHash(pkg.tenantId(), pkg.streamId(), events);
        if (!computedPackageHash.equalsIgnoreCase(pkg.packageContentHash())) {
            log.error("Audit export verification failed: packageContentHash mismatch! expected={}, actual={}",
                    pkg.packageContentHash(), computedPackageHash);
            return false;
        }

        // 2. Verify chain continuity and event hashes via AuditChainVerifier
        AuditChainVerifier.ChainVerificationResult chainResult = chainVerifier.verifyChain(events);
        if (!chainResult.valid()) {
            log.error("Audit export verification failed: hash chain invalid! error={}, sequence={}",
                    chainResult.errorMessage(), chainResult.errorSequence());
            return false;
        }

        // 3. Verify boundary conditions
        AuditEvent firstEvent = events.get(0);
        AuditEvent lastEvent = events.get(events.size() - 1);

        if (!firstEvent.getSequenceNumber().equals(pkg.firstSequence())) {
            log.error("Audit export verification failed: firstSequence mismatch (pkg: {}, actual: {})",
                    pkg.firstSequence(), firstEvent.getSequenceNumber());
            return false;
        }

        if (!lastEvent.getSequenceNumber().equals(pkg.lastSequence())) {
            log.error("Audit export verification failed: lastSequence mismatch (pkg: {}, actual: {})",
                    pkg.lastSequence(), lastEvent.getSequenceNumber());
            return false;
        }

        if (!firstEvent.getEventHash().equalsIgnoreCase(pkg.firstHash())) {
            log.error("Audit export verification failed: firstHash mismatch");
            return false;
        }

        if (!lastEvent.getEventHash().equalsIgnoreCase(pkg.lastHash())) {
            log.error("Audit export verification failed: lastHash mismatch");
            return false;
        }

        // 4. Verify checkpoint alignment if checkpoint exists
        if (pkg.checkpoint() != null) {
            boolean checkpointValid = checkpointVerifier.verifyCheckpointAgainstEvents(pkg.checkpoint(), events);
            if (!checkpointValid) {
                log.error("Audit export verification failed: embedded checkpoint validation failed");
                return false;
            }
        }

        log.info("Audit export package {} verified successfully (tenant: {}, stream: {}, events: {})",
                pkg.exportId(), pkg.tenantId(), pkg.streamId(), events.size());
        return true;
    }
}
