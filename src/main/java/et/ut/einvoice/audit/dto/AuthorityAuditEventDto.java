package et.ut.einvoice.audit.dto;

import et.ut.einvoice.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Verifiable authority-facing audit metadata. Identity, IP/device telemetry,
 * resource identifiers, payloads, and tenant database identifiers stay server-side.
 */
public record AuthorityAuditEventDto(
        UUID id,
        String streamId,
        Long sequenceNumber,
        String action,
        String resourceType,
        String payloadHash,
        String previousEventHash,
        String eventHash,
        Instant timestamp
) {
    public static AuthorityAuditEventDto fromEntity(AuditEvent event) {
        return new AuthorityAuditEventDto(
                event.getId(), event.getStreamId(), event.getSequenceNumber(), event.getAction(),
                event.getResourceType(), event.getPayloadHash(), event.getPreviousEventHash(),
                event.getEventHash(), event.getTimestamp()
        );
    }
}
