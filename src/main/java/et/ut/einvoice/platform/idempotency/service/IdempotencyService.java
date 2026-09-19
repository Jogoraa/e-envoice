package et.ut.einvoice.platform.idempotency.service;

import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.idempotency.domain.IdempotencyRecord;
import et.ut.einvoice.platform.idempotency.domain.IdempotencyStatus;
import et.ut.einvoice.platform.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;
    private final InsaDigitalSignatureService signatureService;

    public IdempotencyService(IdempotencyRecordRepository repository, InsaDigitalSignatureService signatureService) {
        this.repository = repository;
        this.signatureService = signatureService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> checkAndLock(UUID tenantId, String clientId, String idempotencyKey, String payloadJson) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String effectiveClientId = (clientId != null && !clientId.isBlank()) ? clientId : "DEFAULT_CLIENT";
        String requestHash = signatureService.computeSha256Hash(payloadJson != null ? payloadJson : "");
        Optional<IdempotencyRecord> existing = repository.findByTenantIdAndClientIdAndIdempotencyKey(tenantId, effectiveClientId, idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict for tenant {} client {} key {}: payload hash mismatch", tenantId, effectiveClientId, idempotencyKey);
                throw new BusinessException(
                        "IDEMPOTENCY_PAYLOAD_MISMATCH",
                        "A different request payload was previously submitted with the same idempotency key.",
                        "በዚህ የማረጋገጫ ቁልፍ (idempotency key) የተለየ ይዘት ያለው ጥያቄ አስቀድሞ ቀርቧል።",
                        HttpStatus.CONFLICT
                );
            }
            if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new BusinessException(
                        "CONCURRENT_REQUEST_PROCESSING",
                        "A request with this idempotency key is currently being processed. Please retry shortly.",
                        "ይህ ጥያቄ አሁን በመከናወን ላይ ነው፤ እባክዎ ጥቂት ቆይተው እንደገና ይሞክሩ።",
                        HttpStatus.CONFLICT
                );
            }
            return Optional.of(record);
        }

        IdempotencyRecord newRecord = new IdempotencyRecord(
                UUID.randomUUID(),
                tenantId,
                effectiveClientId,
                idempotencyKey,
                requestHash,
                IdempotencyStatus.PROCESSING,
                Instant.now().plus(Duration.ofHours(24))
        );
        try {
            repository.saveAndFlush(newRecord);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.info("Concurrent insert collision for idempotency key {}: {}", idempotencyKey, ex.getMessage());
            throw new BusinessException(
                    "CONCURRENT_REQUEST_PROCESSING",
                    "A request with this idempotency key is currently being processed. Please retry shortly.",
                    "ይህ ጥያቄ አሁን በመከናወን ላይ ነው፤ እባክዎ ጥቂት ቆይተው እንደገና ይሞክሩ።",
                    HttpStatus.CONFLICT
            );
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID tenantId, String clientId, String idempotencyKey, String resourceId, String responsePayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        String effectiveClientId = (clientId != null && !clientId.isBlank()) ? clientId : "DEFAULT_CLIENT";
        repository.findByTenantIdAndClientIdAndIdempotencyKey(tenantId, effectiveClientId, idempotencyKey).ifPresent(r -> {
            r.markCompleted(resourceId, responsePayload);
            repository.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID tenantId, String clientId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        String effectiveClientId = (clientId != null && !clientId.isBlank()) ? clientId : "DEFAULT_CLIENT";
        repository.findByTenantIdAndClientIdAndIdempotencyKey(tenantId, effectiveClientId, idempotencyKey).ifPresent(r -> {
            r.markFailed();
            repository.save(r);
        });
    }
}
