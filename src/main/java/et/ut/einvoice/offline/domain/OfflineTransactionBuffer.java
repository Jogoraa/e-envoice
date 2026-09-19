package et.ut.einvoice.offline.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offline_transaction_buffer")
public class OfflineTransactionBuffer {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "offline_session_id", length = 64)
    private String offlineSessionId;

    @Column(name = "client_transaction_id", length = 128)
    private String clientTransactionId;

    @Column(name = "offline_seq_no", nullable = false)
    private Long offlineSeqNo;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "device_signature", nullable = false, columnDefinition = "TEXT")
    private String deviceSignature;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState = "UPLOADED";

    @Column(name = "buffered_at", nullable = false)
    private Instant bufferedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "sync_status", nullable = false, length = 32)
    private String syncStatus = "QUEUED";

    @Column(name = "irn", length = 128)
    private String irn;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public OfflineTransactionBuffer() {}

    public OfflineTransactionBuffer(UUID id, UUID tenantId, UUID deviceId, Long offlineSeqNo,
                                    String payloadJson, String deviceSignature, Instant bufferedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.offlineSeqNo = offlineSeqNo;
        this.payloadJson = payloadJson;
        this.deviceSignature = deviceSignature;
        this.bufferedAt = bufferedAt != null ? bufferedAt : Instant.now();
        this.syncStatus = "QUEUED";
        this.lifecycleState = "UPLOADED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDeviceId() { return deviceId; }
    public String getOfflineSessionId() { return offlineSessionId; }
    public void setOfflineSessionId(String offlineSessionId) { this.offlineSessionId = offlineSessionId; }
    public String getClientTransactionId() { return clientTransactionId; }
    public void setClientTransactionId(String clientTransactionId) { this.clientTransactionId = clientTransactionId; }
    public Long getOfflineSeqNo() { return offlineSeqNo; }
    public String getPayloadJson() { return payloadJson; }
    public String getDeviceSignature() { return deviceSignature; }
    public String getLifecycleState() { return lifecycleState; }
    public Instant getBufferedAt() { return bufferedAt; }
    public Instant getSyncedAt() { return syncedAt; }
    public String getSyncStatus() { return syncStatus; }
    public String getIrn() { return irn; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    public void markSynced(String irn) {
        this.irn = irn;
        this.syncStatus = "SYNCED";
        this.lifecycleState = "REGISTERED";
        this.syncedAt = Instant.now();
    }

    public void markRejected(String state, String error) {
        this.syncStatus = "FAILED";
        this.lifecycleState = state;
        this.errorMessage = error;
    }
}
