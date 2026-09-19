package et.ut.einvoice.platform.idempotency.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_client_idempotency", columnNames = {"tenant_id", "client_id", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId = "DEFAULT_CLIENT";

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IdempotencyStatus status;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public IdempotencyRecord() {}

    public IdempotencyRecord(UUID id, UUID tenantId, String clientId, String idempotencyKey,
                             String requestHash, IdempotencyStatus status, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = (clientId != null && !clientId.isBlank()) ? clientId : "DEFAULT_CLIENT";
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getClientId() { return clientId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getResourceId() { return resourceId; }
    public IdempotencyStatus getStatus() { return status; }
    public String getResponsePayload() { return responsePayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void markCompleted(String resourceId, String responsePayload) {
        this.resourceId = resourceId;
        this.responsePayload = responsePayload;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = IdempotencyStatus.FAILED;
    }
}
