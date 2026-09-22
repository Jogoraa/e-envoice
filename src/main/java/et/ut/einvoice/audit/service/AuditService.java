package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.domain.AuditStream;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.audit.repository.AuditStreamRepository;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative Audit Service enforcing database-backed, concurrent-safe,
 * append-only hash chains and transactional outbox enqueueing.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditRepository;
    private final AuditStreamRepository streamRepository;
    private final AuditPayloadSanitizer payloadSanitizer;
    private final AuditCanonicalizer canonicalizer;
    private final AuditHashService hashService;
    private final AuditOutboxService outboxService;
    private final AuditStreamInitializer streamInitializer;

    public AuditService(
            AuditEventRepository auditRepository,
            AuditStreamRepository streamRepository,
            AuditPayloadSanitizer payloadSanitizer,
            AuditCanonicalizer canonicalizer,
            AuditHashService hashService,
            AuditOutboxService outboxService,
            AuditStreamInitializer streamInitializer
    ) {
        this.auditRepository = auditRepository;
        this.streamRepository = streamRepository;
        this.payloadSanitizer = payloadSanitizer;
        this.canonicalizer = canonicalizer;
        this.hashService = hashService;
        this.outboxService = outboxService;
        this.streamInitializer = streamInitializer;
    }

    @Transactional
    public AuditEvent recordEvent(UUID tenantId, String actorId, String action, String resourceType, String resourceId, String payloadJson) {
        return recordEvent(tenantId, "MAIN", actorId, action, resourceType, resourceId, payloadJson, "127.0.0.1");
    }

    @Transactional
    public AuditEvent recordEvent(UUID tenantId, String actorId, String action, String resourceType, String resourceId, String payloadJson, String clientIp) {
        return recordEvent(tenantId, "MAIN", actorId, action, resourceType, resourceId, payloadJson, clientIp);
    }

    @Transactional
    public AuditEvent recordEvent(UUID tenantId, String streamId, String actorId, String action, String resourceType, String resourceId, String payloadJson, String clientIp) {
        return recordEventInternal(tenantId, streamId, actorId, "USER", action, resourceType, resourceId, payloadJson, clientIp, null, null);
    }

    @Transactional
    public AuditEvent recordEvent(UUID tenantId, String streamId, String actorId, String actorType, String action, String resourceType, String resourceId, String payloadJson, String clientIp) {
        return recordEventInternal(tenantId, streamId, actorId, actorType, action, resourceType, resourceId, payloadJson, clientIp, null, null);
    }

    @Transactional
    public AuditEvent recordFiscalEvent(UUID tenantId, String streamId, AuditAction action, String resourceType, String resourceId, String payloadJson) {
        return recordEventInternal(tenantId, streamId, "SYSTEM", "SYSTEM", action.name(), resourceType, resourceId, payloadJson, "127.0.0.1", null, null);
    }

    @Transactional
    public AuditEvent recordEvent(
            UUID tenantId,
            String streamId,
            String actorId,
            String actorType,
            String action,
            String resourceType,
            String resourceId,
            String payloadJson,
            String correlationId,
            String traceId
    ) {
        return recordEventInternal(tenantId, streamId, actorId, actorType, action, resourceType, resourceId, payloadJson, "127.0.0.1", correlationId, traceId);
    }

    @Transactional
    public AuditEvent recordEventInternal(
            UUID tenantId,
            String streamId,
            String actorId,
            String actorType,
            String action,
            String resourceType,
            String resourceId,
            String payloadJson,
            String clientIp,
            String correlationId,
            String traceId
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is REQUIRED for audit event creation: fail-closed");
        }
        String effectiveStream = (streamId != null && !streamId.isBlank()) ? streamId : "MAIN";

        // Extract context telemetry if available
        TenantContext ctx = TenantContextHolder.getContext();
        String effectiveActor;
        if (actorId != null && !actorId.isBlank()) {
            effectiveActor = actorId;
        } else if (ctx != null && ctx.userId() != null && !ctx.userId().isBlank()) {
            effectiveActor = ctx.userId();
        } else {
            throw new IllegalArgumentException("actorId is REQUIRED for audit event creation: fail-closed");
        }

        String effectiveActorType;
        if (actorType != null && !actorType.isBlank()) {
            effectiveActorType = actorType;
        } else if (ctx != null && ctx.roles() != null && !ctx.roles().isEmpty()) {
            effectiveActorType = "USER";
        } else {
            throw new IllegalArgumentException("actorType is REQUIRED for audit event creation: fail-closed");
        }

        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is REQUIRED for audit event creation: fail-closed");
        }
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType is REQUIRED for audit event creation: fail-closed");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId is REQUIRED for audit event creation: fail-closed");
        }

        String effectiveClientId = ctx != null ? ctx.clientId() : null;
        String effectiveDeviceId = ctx != null && ctx.deviceId() != null ? ctx.deviceId().toString() : null;
        String effectiveCorrId = correlationId != null ? correlationId : (ctx != null ? ctx.correlationId() : UUID.randomUUID().toString());
        String effectiveTraceId = traceId != null ? traceId : UUID.randomUUID().toString();

        // 1. Sanitize payload to strip any credentials, secrets, or keys
        String sanitizedPayload = payloadSanitizer.sanitize(payloadJson);
        String payloadHash = hashService.computePayloadHash(sanitizedPayload);

        // 2. Lock stream row to determine authoritative sequential ordering & previous hash
        AuditStream stream = acquireStreamLock(tenantId, effectiveStream);
        long nextSequence = stream.getLastSequenceNumber() + 1L;
        String previousHash = stream.getLastEventHash();

        // 3. Construct event
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        AuditEvent event = new AuditEvent(
                eventId,
                tenantId,
                effectiveStream,
                nextSequence,
                effectiveActor,
                effectiveActorType,
                effectiveClientId,
                effectiveDeviceId,
                action,
                resourceType,
                resourceId,
                clientIp != null ? clientIp : "127.0.0.1",
                null,
                payloadHash,
                previousHash,
                "", // will compute below
                effectiveCorrId,
                effectiveTraceId,
                "1.0.0-RELEASE",
                1,
                now
        );
        event.setPayloadJson(sanitizedPayload);

        // 4. Compute deterministic canonical hash
        String eventHash = hashService.computeEventHash(event);
        event.setEventHash(eventHash);

        // 5. Persist audit event
        AuditEvent saved = auditRepository.save(event);

        // 6. Advance stream head
        stream.advance(nextSequence, eventHash, eventId);
        streamRepository.save(stream);

        // 7. Atomically enqueue into transactional audit outbox
        String canonicalRepresentation = canonicalizer.canonicalizeToString(saved);
        outboxService.enqueueAuditOutbox(saved, canonicalRepresentation);

        log.info("Recorded immutable audit event {} [Tenant: {}, Stream: {}, Seq: {}, Action: {}, Hash: {}]",
                saved.getId(), tenantId, effectiveStream, nextSequence, action, eventHash.substring(0, 8));

        return saved;
    }

    private AuditStream acquireStreamLock(UUID tenantId, String streamId) {
        Optional<AuditStream> locked = streamRepository.findWithLock(tenantId, streamId);
        if (locked.isPresent()) {
            return locked.get();
        }

        try {
            streamInitializer.initStreamIfAbsent(tenantId, streamId);
        } catch (Exception e) {
            log.debug("Concurrent genesis stream initialization handled for {}:{}: {}", tenantId, streamId, e.getMessage());
        }

        return streamRepository.findWithLock(tenantId, streamId)
                .orElseThrow(() -> new IllegalStateException("Failed to acquire stream lock for " + tenantId + ":" + streamId));
    }
}
