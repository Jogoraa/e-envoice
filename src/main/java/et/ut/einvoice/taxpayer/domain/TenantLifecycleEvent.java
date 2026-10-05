package et.ut.einvoice.taxpayer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_lifecycle_events")
public class TenantLifecycleEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private TenantLifecycleEventType eventType;

    @Column(name = "tin", nullable = false, length = 16)
    private String tin;

    @Column(name = "taxpayer_name", nullable = false)
    private String taxpayerName;

    @Column(name = "system_number", nullable = false, length = 128)
    private String systemNumber;

    @Column(name = "sector_code", length = 32)
    private String sectorCode;

    @Column(name = "effective_date", nullable = false)
    private Instant effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 32)
    private TenantNotificationStatus notificationStatus = TenantNotificationStatus.PENDING;

    @Column(name = "mor_acknowledgement_reference", length = 128)
    private String morAcknowledgementReference;

    @Column(name = "mor_response_payload", columnDefinition = "TEXT")
    private String morResponsePayload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantLifecycleEvent() {}

    public TenantLifecycleEvent(
            UUID id,
            UUID tenantId,
            TenantLifecycleEventType eventType,
            String tin,
            String taxpayerName,
            String systemNumber,
            String sectorCode,
            Instant effectiveDate
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.tin = tin;
        this.taxpayerName = taxpayerName;
        this.systemNumber = systemNumber;
        this.sectorCode = sectorCode;
        this.effectiveDate = effectiveDate != null ? effectiveDate : Instant.now();
        this.notificationStatus = TenantNotificationStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public TenantLifecycleEventType getEventType() { return eventType; }
    public String getTin() { return tin; }
    public String getTaxpayerName() { return taxpayerName; }
    public String getSystemNumber() { return systemNumber; }
    public String getSectorCode() { return sectorCode; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public TenantNotificationStatus getNotificationStatus() { return notificationStatus; }
    public String getMorAcknowledgementReference() { return morAcknowledgementReference; }
    public String getMorResponsePayload() { return morResponsePayload; }
    public int getRetryCount() { return retryCount; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markSubmitted() {
        this.notificationStatus = TenantNotificationStatus.SUBMITTED;
        this.lastAttemptAt = Instant.now();
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public void markAcknowledged(String ackRef, String payload) {
        this.notificationStatus = TenantNotificationStatus.ACKNOWLEDGED;
        this.morAcknowledgementReference = ackRef;
        this.morResponsePayload = payload;
        this.acknowledgedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.notificationStatus = TenantNotificationStatus.FAILED;
        this.morResponsePayload = error;
        this.lastAttemptAt = Instant.now();
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public void markUnknown(String message) {
        this.notificationStatus = TenantNotificationStatus.UNKNOWN;
        this.morResponsePayload = message;
        this.lastAttemptAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
