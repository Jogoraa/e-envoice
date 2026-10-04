package et.ut.einvoice.audit.dto;

import et.ut.einvoice.audit.domain.AuditCheckpoint;
import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.export.AuditExportPackage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Explicit wire schema for offline audit-evidence verification. Persistence
 * entities are intentionally never deserialized from a client request.
 */
public record AuditExportVerificationRequest(
        @NotNull UUID exportId,
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 64) String streamId,
        @Positive long eventCount,
        @NotNull @Positive Long firstSequence,
        @NotNull @Positive Long lastSequence,
        @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String firstHash,
        @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String lastHash,
        @Valid CheckpointEvidence checkpoint,
        @NotEmpty @Size(max = 10_000) List<@Valid AuditEventEvidence> events,
        @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String packageContentHash,
        @Min(1) @Max(1) int schemaVersion,
        @NotBlank @Size(max = 32) String applicationVersion,
        @NotNull Instant exportedAt
) {
    private static final String HASH_PATTERN = "^[A-Fa-f0-9]{64}$";
    private static final String PREVIOUS_HASH_PATTERN = "^(GENESIS-[A-Fa-f0-9]{32}|[A-Fa-f0-9]{64})$";

    public record CheckpointEvidence(
            @NotNull UUID checkpointId,
            @NotBlank @Size(max = 64) String streamId,
            @NotNull @Positive Long firstEventSequence,
            @NotNull @Positive Long lastEventSequence,
            @Positive long eventCount,
            @NotBlank @Pattern(regexp = HASH_PATTERN) String firstEventHash,
            @NotBlank @Pattern(regexp = HASH_PATTERN) String lastEventHash,
            @NotBlank @Pattern(regexp = HASH_PATTERN) String chainStateHash,
            @NotBlank @Pattern(regexp = HASH_PATTERN) String previousCheckpointHash,
            @Min(1) @Max(1) int schemaVersion,
            @NotNull Instant createdAt
    ) {
        AuditCheckpoint toDomain(UUID tenantId) {
            return new AuditCheckpoint(
                    checkpointId, tenantId, streamId, firstEventSequence, lastEventSequence, eventCount,
                    firstEventHash, lastEventHash, chainStateHash, previousCheckpointHash, schemaVersion, createdAt
            );
        }
    }

    public record AuditEventEvidence(
            @NotNull UUID id,
            @NotNull UUID tenantId,
            @NotBlank @Size(max = 64) String streamId,
            @NotNull @Positive Long sequenceNumber,
            @NotBlank @Size(max = 64) String actorId,
            @NotBlank @Size(max = 32) String actorType,
            @NotBlank @Size(max = 64) String action,
            @NotBlank @Size(max = 64) String resourceType,
            @NotBlank @Size(max = 128) String resourceId,
            @NotBlank @Pattern(regexp = HASH_PATTERN) String payloadHash,
            @NotBlank @Pattern(regexp = PREVIOUS_HASH_PATTERN) String previousEventHash,
            @NotBlank @Pattern(regexp = HASH_PATTERN) String eventHash,
            @Size(max = 64) String correlationId,
            @Size(max = 64) String traceId,
            @NotBlank @Size(max = 32) String applicationVersion,
            @Min(1) @Max(1) int schemaVersion,
            @NotNull Instant timestamp
    ) {
        AuditEvent toDomain() {
            return new AuditEvent(
                    id, tenantId, streamId, sequenceNumber, actorId, actorType,
                    null, null, action, resourceType, resourceId,
                    null, null, payloadHash, previousEventHash, eventHash,
                    correlationId, traceId, applicationVersion, schemaVersion, timestamp
            );
        }
    }

    public AuditExportPackage toPackage() {
        List<AuditEvent> auditEvents = events.stream().map(AuditEventEvidence::toDomain).toList();
        return new AuditExportPackage(
                exportId, tenantId, streamId, eventCount, firstSequence, lastSequence, firstHash, lastHash,
                checkpoint == null ? null : checkpoint.toDomain(tenantId), auditEvents,
                packageContentHash, schemaVersion, applicationVersion, exportedAt
        );
    }
}
