package et.ut.einvoice.audit.domain;

import et.ut.einvoice.platform.exception.BusinessException;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Audit Event entity for forensic and regulatory audit trails.
 * Enforces database and application-level immutability pursuant to
 * Federal Tax Administration Proclamation No. 983/2016 Art. 17 and
 * Directive No. 1142/2018 EC (2026 GC) Art. 4(2)(b), Art. 4(2)(c), Art. 4(2)(d), Art. 27(2).
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_events_tenant_stream_seq", columnList = "tenant_id, stream_id, sequence_number"),
        @Index(name = "idx_audit_events_tenant_time", columnList = "tenant_id, timestamp"),
        @Index(name = "idx_audit_events_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_audit_events_hash", columnList = "event_hash")
})
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "stream_id", nullable = false, length = 64)
    private String streamId = "MAIN";

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber = 1L;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType = "USER";

    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "previous_event_hash", nullable = false, length = 64)
    private String previousEventHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "application_version", length = 32)
    private String applicationVersion = "1.0.0-RELEASE";

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    public AuditEvent() {}

    public AuditEvent(UUID id, UUID tenantId, String streamId, Long sequenceNumber, String actorId, String actorType,
                      String clientId, String deviceId, String action, String resourceType, String resourceId,
                      String clientIp, String userAgent, String payloadHash, String previousEventHash, String eventHash,
                      String correlationId, String traceId, String applicationVersion, int schemaVersion, Instant timestamp) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.streamId = (streamId != null && !streamId.isBlank()) ? streamId : "MAIN";
        this.sequenceNumber = sequenceNumber != null ? sequenceNumber : 1L;
        this.actorId = actorId;
        this.actorType = actorType;
        this.clientId = clientId;
        this.deviceId = deviceId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.payloadHash = payloadHash;
        this.previousEventHash = previousEventHash;
        this.eventHash = eventHash;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.applicationVersion = applicationVersion != null ? applicationVersion : "1.0.0-RELEASE";
        this.schemaVersion = schemaVersion > 0 ? schemaVersion : 1;
        this.timestamp = timestamp != null ? timestamp.truncatedTo(java.time.temporal.ChronoUnit.MILLIS) : null;
    }

    // Backwards-compatible constructor
    public AuditEvent(UUID id, UUID tenantId, String streamId, String actorId, String action, String resourceType, String resourceId,
                      String clientIp, String payloadHash, String previousEventHash, String eventHash) {
        this(id, tenantId, streamId, 1L, actorId, "USER", null, null, action, resourceType, resourceId,
                clientIp, null, payloadHash, previousEventHash, eventHash, null, null, "1.0.0-RELEASE", 1, Instant.now());
    }

    @PreUpdate
    public void onPreUpdate() {
        throw new BusinessException(
                "AUDIT_TRAIL_IMMUTABLE",
                "Historical audit events cannot be modified once persisted. Update rejected.",
                "የተመዘገቡ የኦዲት መረጃዎችን ማሻሻል በሕግ በጥብቅ የተከለከለ ነው።",
                HttpStatus.FORBIDDEN
        );
    }

    @PreRemove
    public void onPreRemove() {
        throw new BusinessException(
                "AUDIT_TRAIL_IMMUTABLE",
                "Historical audit events cannot be deleted once persisted. Deletion rejected.",
                "የተመዘገቡ የኦዲት መረጃዎችን መሰረዝ በሕግ በጥብቅ የተከለከለ ነው።",
                HttpStatus.FORBIDDEN
        );
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getStreamId() { return streamId; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public String getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getClientId() { return clientId; }
    public String getDeviceId() { return deviceId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getPayloadHash() { return payloadHash; }
    public String getPayloadJson() { return payloadJson; }
    public String getPreviousEventHash() { return previousEventHash; }
    public String getEventHash() { return eventHash; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public String getApplicationVersion() { return applicationVersion; }
    public int getSchemaVersion() { return schemaVersion; }
    public Instant getTimestamp() { return timestamp; }

    // Setters strictly for initialization before JPA persist (attempting to update loaded entity will fail via @PreUpdate)
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public void setPreviousEventHash(String previousEventHash) { this.previousEventHash = previousEventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setAction(String action) { this.action = action; }
}
