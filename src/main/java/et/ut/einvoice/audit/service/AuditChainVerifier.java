package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.telemetry.AuditSecurityTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Independent Audit Chain Verifier detecting any form of tampering,
 * truncation, insertion, reordering, or sequence alteration.
 */
@Service
public class AuditChainVerifier {

    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifier.class);

    private final AuditHashService hashService;
    private final AuditSecurityTelemetry securityTelemetry;

    public AuditChainVerifier(AuditHashService hashService, AuditSecurityTelemetry securityTelemetry) {
        this.hashService = hashService;
        this.securityTelemetry = securityTelemetry;
    }

    public record ChainVerificationResult(
            boolean valid,
            String errorMessage,
            Long errorSequence,
            long verifiedEventCount
    ) {
        public static ChainVerificationResult success(long count) {
            return new ChainVerificationResult(true, null, null, count);
        }

        public static ChainVerificationResult failure(String error, Long seq) {
            return new ChainVerificationResult(false, error, seq, 0);
        }
    }

    public enum VerificationStatus {
        VALID,
        TAMPERED_EVENT_HASH,
        BROKEN_PREVIOUS_HASH,
        SEQUENCE_GAP,
        SEQUENCE_DUPLICATE,
        CROSS_TENANT_CONTAMINATION,
        CROSS_STREAM_CONTAMINATION,
        EMPTY_STREAM
    }

    public record StreamVerificationResult(
            VerificationStatus status,
            boolean isValid,
            long verifiedEventCount,
            Long failedSequenceNumber,
            UUID failedEventId,
            String errorMessage
    ) {
        public static StreamVerificationResult success(long count) {
            return new StreamVerificationResult(VerificationStatus.VALID, true, count, null, null, "Audit chain is cryptographically intact and unbroken.");
        }

        public static StreamVerificationResult failure(VerificationStatus status, Long seq, UUID id, String error) {
            return new StreamVerificationResult(status, false, 0, seq, id, error);
        }
    }

    /**
     * Verifies an individual event's internal cryptographic hash consistency.
     */
    public boolean verifySingleEvent(AuditEvent event) {
        if (event == null || event.getEventHash() == null) {
            return false;
        }
        String calculated = hashService.computeEventHash(event);
        return Objects.equals(event.getEventHash(), calculated);
    }

    /**
     * Verifies the entire sequence and hash chain for a stream of audit events.
     */
    public StreamVerificationResult verifyStreamChain(UUID expectedTenantId, String expectedStreamId, List<AuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return new StreamVerificationResult(VerificationStatus.EMPTY_STREAM, true, 0, null, null, "Stream is empty.");
        }

        String expectedGenesisHash = hashService.computeGenesisHash(expectedTenantId, expectedStreamId);
        String previousHash = expectedGenesisHash;
        long expectedSequence = 1L;

        for (int i = 0; i < events.size(); i++) {
            AuditEvent event = events.get(i);

            // 1. Tenant boundary validation
            if (!Objects.equals(event.getTenantId(), expectedTenantId)) {
                log.error("TAMPER DETECTED: Cross-tenant event {} found in tenant {} stream", event.getId(), expectedTenantId);
                securityTelemetry.recordTamperDetected(expectedTenantId, expectedStreamId, event.getSequenceNumber(), "Cross-tenant event detected");
                securityTelemetry.recordChainVerificationFailure(expectedTenantId, expectedStreamId, "Cross-tenant contamination");
                return StreamVerificationResult.failure(
                        VerificationStatus.CROSS_TENANT_CONTAMINATION,
                        event.getSequenceNumber(),
                        event.getId(),
                        "Event tenant " + event.getTenantId() + " does not match stream tenant " + expectedTenantId
                );
            }

            // 2. Stream boundary validation
            if (!Objects.equals(event.getStreamId(), expectedStreamId)) {
                log.error("TAMPER DETECTED: Cross-stream event {} found in stream {}", event.getId(), expectedStreamId);
                securityTelemetry.recordTamperDetected(expectedTenantId, expectedStreamId, event.getSequenceNumber(), "Cross-stream event detected");
                securityTelemetry.recordChainVerificationFailure(expectedTenantId, expectedStreamId, "Cross-stream contamination");
                return StreamVerificationResult.failure(
                        VerificationStatus.CROSS_STREAM_CONTAMINATION,
                        event.getSequenceNumber(),
                        event.getId(),
                        "Event stream " + event.getStreamId() + " does not match expected stream " + expectedStreamId
                );
            }

            // 3. Monotonic sequence continuity check
            if (i == 0) {
                expectedSequence = event.getSequenceNumber();
            } else {
                long currentSeq = event.getSequenceNumber();
                if (currentSeq == events.get(i - 1).getSequenceNumber()) {
                    log.error("TAMPER DETECTED: Duplicate sequence number {} on event {}", currentSeq, event.getId());
                    securityTelemetry.recordSequenceDuplicate(expectedTenantId, expectedStreamId, currentSeq);
                    securityTelemetry.recordChainVerificationFailure(expectedTenantId, expectedStreamId, "Sequence duplicate");
                    return StreamVerificationResult.failure(
                            VerificationStatus.SEQUENCE_DUPLICATE,
                            currentSeq,
                            event.getId(),
                            "Duplicate sequence number detected at sequence " + currentSeq
                    );
                }
                if (currentSeq != expectedSequence) {
                    log.error("TAMPER DETECTED: Sequence gap on event {}. Expected {}, found {}", event.getId(), expectedSequence, currentSeq);
                    securityTelemetry.recordSequenceGap(expectedTenantId, expectedStreamId, expectedSequence, currentSeq);
                    securityTelemetry.recordChainVerificationFailure(expectedTenantId, expectedStreamId, "Sequence gap");
                    return StreamVerificationResult.failure(
                            VerificationStatus.SEQUENCE_GAP,
                            currentSeq,
                            event.getId(),
                            "Sequence gap detected. Expected sequence " + expectedSequence + " but found " + currentSeq
                    );
                }
            }

            // 4. Previous hash chain continuity check
            if (i > 0) {
                if (!Objects.equals(event.getPreviousEventHash(), previousHash)) {
                    log.error("TAMPER DETECTED: Broken previous hash link on event {}. Expected {}, found {}",
                            event.getId(), previousHash, event.getPreviousEventHash());
                    securityTelemetry.recordTamperDetected(expectedTenantId, expectedStreamId, event.getSequenceNumber(), "Broken previous hash");
                    securityTelemetry.recordChainVerificationFailure(expectedTenantId, expectedStreamId, "Broken previous hash link");
                    return StreamVerificationResult.failure(
                            VerificationStatus.BROKEN_PREVIOUS_HASH,
                            event.getSequenceNumber(),
                            event.getId(),
                            "Broken previous hash link at sequence " + event.getSequenceNumber()
                    );
                }
            }

            // 5. Event hash re-computation and verification
            String calculatedHash = hashService.computeEventHash(event);
            if (!Objects.equals(event.getEventHash(), calculatedHash)) {
                log.error("TAMPER DETECTED: Event hash mismatch on event {}. Expected {}, calculated {}",
                        event.getId(), event.getEventHash(), calculatedHash);
                securityTelemetry.recordHashMismatch(expectedTenantId, expectedStreamId, event.getSequenceNumber());
                securityTelemetry.recordChainVerificationFailure(expectedTenantId, expectedStreamId, "Event hash mismatch");
                return StreamVerificationResult.failure(
                        VerificationStatus.TAMPERED_EVENT_HASH,
                        event.getSequenceNumber(),
                        event.getId(),
                        "Cryptographic event hash mismatch at sequence " + event.getSequenceNumber()
                );
            }

            previousHash = event.getEventHash();
            expectedSequence++;
        }

        securityTelemetry.recordChainVerificationSuccess(expectedTenantId, expectedStreamId);
        return StreamVerificationResult.success(events.size());
    }

    /**
     * Verifies the chain of events agnostic to tenant/stream lookup, inferring expected IDs from the first event.
     */
    public ChainVerificationResult verifyChain(List<AuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return ChainVerificationResult.success(0);
        }
        AuditEvent first = events.get(0);
        StreamVerificationResult result = verifyStreamChain(first.getTenantId(), first.getStreamId(), events);
        if (result.isValid()) {
            return ChainVerificationResult.success(result.verifiedEventCount());
        } else {
            return ChainVerificationResult.failure(result.errorMessage(), result.failedSequenceNumber());
        }
    }
}
