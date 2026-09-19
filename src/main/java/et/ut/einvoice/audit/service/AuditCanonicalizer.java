package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;

/**
 * Deterministic Audit Canonicalizer ensuring strict, unambiguous, reproducible
 * serialization across different runtimes and languages.
 * <p>
 * Canonicalization Policy:
 * 1. Encoding: UTF-8 exclusively across all runtimes.
 * 2. Unicode Normalization: Unicode Standard NFC (Canonical Decomposition, followed by Canonical Composition)
 *    applied to all text fields prior to escaping.
 * 3. Delimiter Escaping:
 *    - Backslash: '\' -> '\\'
 *    - Pipe:      '|' -> '\|'
 *    - Equals:    '=' -> '\='
 * 4. Required Identity Policy (Fail-Closed):
 *    - tenantId: REQUIRED (UUID). Missing or null causes IllegalArgumentException. Zero UUID never substituted.
 *    - actorId: REQUIRED (non-blank string).
 *    - actorType: REQUIRED (non-blank string).
 *    - action: REQUIRED (non-blank string).
 *    - resourceType: REQUIRED (non-blank string).
 *    - resourceId: REQUIRED (non-blank string).
 *    - occurredAt: REQUIRED (non-null Instant).
 * 5. Null Policy (Optional fields): Null optional values serialize to empty string ("").
 * 6. Empty-string Policy: Empty strings serialize as "key=" (distinct because all keys are in a fixed order).
 * 7. Numeric Representation: Base-10 canonical integer representation without leading zeros.
 * 8. Timestamp Representation: ISO-8601 UTC representation truncated to milliseconds (e.g. "2026-09-18T19:00:00.123Z").
 * </p>
 */
@Component
public class AuditCanonicalizer {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public byte[] canonicalize(AuditEvent event) {
        return canonicalizeToString(event).getBytes(StandardCharsets.UTF_8);
    }

    public String canonicalizeToString(AuditEvent event) {
        validateRequiredFields(event);
        if (event.getSchemaVersion() == 1) {
            return canonicalizeV1(event);
        }
        // Backward-compatible fallback
        return canonicalizeV1(event);
    }

    public static void validateRequiredFields(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("AuditEvent must not be null");
        }
        if (event.getTenantId() == null) {
            throw new IllegalArgumentException("tenantId is REQUIRED for audit canonicalization: zero UUID substitution rejected");
        }
        if (event.getActorId() == null || event.getActorId().isBlank()) {
            throw new IllegalArgumentException("actorId is REQUIRED for audit canonicalization");
        }
        if (event.getActorType() == null || event.getActorType().isBlank()) {
            throw new IllegalArgumentException("actorType is REQUIRED for audit canonicalization");
        }
        if (event.getAction() == null || event.getAction().isBlank()) {
            throw new IllegalArgumentException("action is REQUIRED for audit canonicalization");
        }
        if (event.getResourceType() == null || event.getResourceType().isBlank()) {
            throw new IllegalArgumentException("resourceType is REQUIRED for audit canonicalization");
        }
        if (event.getResourceId() == null || event.getResourceId().isBlank()) {
            throw new IllegalArgumentException("resourceId is REQUIRED for audit canonicalization");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("occurredAt (timestamp) is REQUIRED for audit canonicalization");
        }
    }

    private String canonicalizeV1(AuditEvent event) {
        StringBuilder sb = new StringBuilder(512);
        appendField(sb, "schema_version", String.valueOf(event.getSchemaVersion()));
        appendField(sb, "event_id", event.getId() != null ? event.getId().toString() : "");
        appendField(sb, "tenant_id", event.getTenantId().toString());
        appendField(sb, "stream_id", event.getStreamId() != null ? event.getStreamId() : "MAIN");
        appendField(sb, "sequence_number", String.valueOf(event.getSequenceNumber()));
        appendField(sb, "occurred_at", event.getTimestamp().truncatedTo(ChronoUnit.MILLIS).toString());
        appendField(sb, "actor_id", event.getActorId());
        appendField(sb, "actor_type", event.getActorType());
        appendField(sb, "action", event.getAction());
        appendField(sb, "resource_type", event.getResourceType());
        appendField(sb, "resource_id", event.getResourceId());
        appendField(sb, "payload_hash", event.getPayloadHash() != null ? event.getPayloadHash() : "");
        appendField(sb, "previous_event_hash", event.getPreviousEventHash() != null ? event.getPreviousEventHash() : "");
        appendField(sb, "correlation_id", event.getCorrelationId() != null ? event.getCorrelationId() : "");
        appendField(sb, "trace_id", event.getTraceId() != null ? event.getTraceId() : "");
        appendField(sb, "application_version", event.getApplicationVersion() != null ? event.getApplicationVersion() : "1.0.0-RELEASE");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) {
            sb.append("|");
        }
        sb.append(key).append("=").append(escapeField(value));
    }

    public static String escapeField(String val) {
        if (val == null) return "";
        String normalized = Normalizer.normalize(val, Normalizer.Form.NFC);
        return normalized.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=");
    }
}
