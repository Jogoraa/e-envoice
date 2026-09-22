package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditStream;
import et.ut.einvoice.audit.repository.AuditStreamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Autonomous transaction initializer for audit stream genesis rows.
 * Executes in an isolated REQUIRES_NEW transaction to prevent poisoning the caller's
 * business transaction during concurrent initialization races.
 */
@Component
public class AuditStreamInitializer {

    private static final Logger log = LoggerFactory.getLogger(AuditStreamInitializer.class);

    private final AuditStreamRepository streamRepository;
    private final AuditHashService hashService;

    public AuditStreamInitializer(AuditStreamRepository streamRepository, AuditHashService hashService) {
        this.streamRepository = streamRepository;
        this.hashService = hashService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void initStreamIfAbsent(UUID tenantId, String streamId) {
        AuditStream.AuditStreamId id = new AuditStream.AuditStreamId(tenantId, streamId);
        if (streamRepository.findById(id).isEmpty()) {
            String genesisHash = hashService.computeGenesisHash(tenantId, streamId);
            AuditStream newStream = new AuditStream(tenantId, streamId, genesisHash);
            streamRepository.saveAndFlush(newStream);
            log.info("Initialized genesis audit stream for tenant {} stream {}", tenantId, streamId);
        }
    }
}
