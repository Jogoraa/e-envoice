package et.ut.einvoice.invoicing.service;

import et.ut.einvoice.invoicing.domain.TenantInvoiceSequence;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.invoicing.repository.TenantInvoiceSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autonomous tenant sequence allocation service.
 * Executes in an isolated, autonomous transaction (REQUIRES_NEW) combined with per-tenant
 * synchronization and pessimistic database row locking, mirroring native database sequence semantics.
 */
@Service
public class TenantSequenceService {

    private static final Logger log = LoggerFactory.getLogger(TenantSequenceService.class);

    private final TenantInvoiceSequenceRepository sequenceRepository;
    private final InvoiceRepository invoiceRepository;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public TenantSequenceService(
            TenantInvoiceSequenceRepository sequenceRepository,
            InvoiceRepository invoiceRepository
    ) {
        this.sequenceRepository = sequenceRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Allocates the next monotonic invoice counter for the tenant.
     * Committed immediately in an autonomous transaction to prevent uncommitted read anomalies under high concurrency.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long allocateNextCounter(UUID tenantId) {
        synchronized (locks.computeIfAbsent(tenantId, k -> new Object())) {
            Optional<TenantInvoiceSequence> seqOpt = sequenceRepository.findByTenantIdForUpdate(tenantId);
            TenantInvoiceSequence seq;
            if (seqOpt.isPresent()) {
                seq = seqOpt.get();
                seq.setCurrentCounter(seq.getCurrentCounter() + 1);
            } else {
                Long maxCounter = invoiceRepository.findMaxInvoiceCounter(tenantId);
                long initial = (maxCounter != null ? maxCounter : 0L) + 1L;
                seq = new TenantInvoiceSequence(tenantId, initial);
                log.info("Initialized invoice sequence for tenant {} at initial counter {}", tenantId, initial);
            }
            try {
                sequenceRepository.saveAndFlush(seq);
                return seq.getCurrentCounter();
            } catch (Exception e) {
                log.warn("Sequence initialization race detected for tenant {}. Retrying lock acquisition...", tenantId);
                seqOpt = sequenceRepository.findByTenantIdForUpdate(tenantId);
                if (seqOpt.isPresent()) {
                    seq = seqOpt.get();
                    seq.setCurrentCounter(seq.getCurrentCounter() + 1);
                    sequenceRepository.saveAndFlush(seq);
                    return seq.getCurrentCounter();
                }
                throw e;
            }
        }
    }

    /**
     * Synchronously adjusts the tenant sequence during government sequence mismatch recovery.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void adjustCounterIfHigher(UUID tenantId, long expectedNextCounter) {
        synchronized (locks.computeIfAbsent(tenantId, k -> new Object())) {
            Optional<TenantInvoiceSequence> seqOpt = sequenceRepository.findByTenantIdForUpdate(tenantId);
            seqOpt.ifPresent(seq -> {
                if (expectedNextCounter > seq.getCurrentCounter()) {
                    seq.setCurrentCounter(expectedNextCounter);
                    sequenceRepository.saveAndFlush(seq);
                    log.warn("Adjusted sequence counter for tenant {} to {}", tenantId, expectedNextCounter);
                }
            });
        }
    }
}
