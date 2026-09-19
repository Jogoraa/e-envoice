package et.ut.einvoice.audit.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Security telemetry component for immutable audit verification, tampering detection,
 * checkpointing, and outbox operations.
 * Strictly avoids logging sensitive payload contents.
 */
@Component
public class AuditSecurityTelemetry {

    private static final Logger log = LoggerFactory.getLogger(AuditSecurityTelemetry.class);

    private final Optional<MeterRegistry> meterRegistry;

    @Autowired
    public AuditSecurityTelemetry(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordChainVerificationSuccess(UUID tenantId, String streamId) {
        increment("audit_chain_verification_success", "tenant", String.valueOf(tenantId));
        log.info("[SECURITY_TELEMETRY] audit_chain_verification_success: tenant={}, stream={}", tenantId, streamId);
    }

    public void recordChainVerificationFailure(UUID tenantId, String streamId, String reason) {
        increment("audit_chain_verification_failure", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] audit_chain_verification_failure: tenant={}, stream={}, reason={}", tenantId, streamId, reason);
    }

    public void recordHashMismatch(UUID tenantId, String streamId, Long sequence) {
        increment("audit_hash_mismatch", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] audit_hash_mismatch: tenant={}, stream={}, sequence={}", tenantId, streamId, sequence);
    }

    public void recordSequenceGap(UUID tenantId, String streamId, Long expected, Long actual) {
        increment("audit_sequence_gap", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] audit_sequence_gap: tenant={}, stream={}, expected={}, actual={}", tenantId, streamId, expected, actual);
    }

    public void recordSequenceDuplicate(UUID tenantId, String streamId, Long sequence) {
        increment("audit_sequence_duplicate", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] audit_sequence_duplicate: tenant={}, stream={}, sequence={}", tenantId, streamId, sequence);
    }

    public void recordTamperDetected(UUID tenantId, String streamId, Long sequence, String details) {
        increment("audit_tamper_detected", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] audit_tamper_detected: tenant={}, stream={}, sequence={}, details={}", tenantId, streamId, sequence, details);
    }

    public void recordCheckpointCreated(UUID tenantId, String streamId, UUID checkpointId) {
        increment("checkpoint_created", "tenant", String.valueOf(tenantId));
        log.info("[SECURITY_TELEMETRY] checkpoint_created: tenant={}, stream={}, checkpointId={}", tenantId, streamId, checkpointId);
    }

    public void recordCheckpointVerificationFailure(UUID tenantId, String streamId, UUID checkpointId, String reason) {
        increment("checkpoint_verification_failure", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] checkpoint_verification_failure: tenant={}, stream={}, checkpointId={}, reason={}", tenantId, streamId, checkpointId, reason);
    }

    public void recordOutboxFailure(UUID tenantId, UUID outboxId, String error) {
        increment("audit_outbox_failure", "tenant", String.valueOf(tenantId));
        log.error("[SECURITY_TELEMETRY] audit_outbox_failure: tenant={}, outboxId={}, error={}", tenantId, outboxId, error);
    }

    public void recordOutboxLag(long lagCount) {
        log.warn("[SECURITY_TELEMETRY] audit_outbox_lag: pendingCount={}", lagCount);
    }

    public void recordExportGenerated(UUID tenantId, String streamId, UUID exportId) {
        increment("audit_export_generated", "tenant", String.valueOf(tenantId));
        log.info("[SECURITY_TELEMETRY] audit_export_generated: tenant={}, stream={}, exportId={}", tenantId, streamId, exportId);
    }

    private void increment(String name, String tagKey, String tagValue) {
        meterRegistry.ifPresent(registry -> {
            try {
                Counter.builder(name)
                        .tag(tagKey, tagValue)
                        .register(registry)
                        .increment();
            } catch (Exception e) {
                log.trace("Failed to increment micrometer metric {}: {}", name, e.getMessage());
            }
        });
    }
}
