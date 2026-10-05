package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks an individual taxpayer tenant's transition progress during provider exit under Art. 17.
 */
@Entity
@Table(name = "provider_tenant_transitions")
public class ProviderTenantTransition {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "exit_plan_id", nullable = false)
    private UUID exitPlanId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "tenant_tin", length = 32, nullable = false)
    private String tenantTin;

    @Column(name = "tenant_legal_name", length = 255, nullable = false)
    private String tenantLegalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TenantTransitionStatus status = TenantTransitionStatus.NOTIFIED;

    @Column(name = "notification_sent_at")
    private Instant notificationSentAt;

    @Column(name = "data_retrieval_completed_at")
    private Instant dataRetrievalCompletedAt;

    @Column(name = "destination_provider_name", length = 255)
    private String destinationProviderName;

    @Column(name = "destination_system_number", length = 128)
    private String destinationSystemNumber;

    @Column(name = "migration_confirmed_at")
    private Instant migrationConfirmedAt;

    @Column(name = "migration_evidence_hash", length = 128)
    private String migrationEvidenceHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ProviderTenantTransition() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ProviderTenantTransition(UUID id, UUID exitPlanId, UUID tenantId, String tenantTin, String tenantLegalName) {
        this.id = id;
        this.exitPlanId = exitPlanId;
        this.tenantId = tenantId;
        this.tenantTin = tenantTin;
        this.tenantLegalName = tenantLegalName;
        this.status = TenantTransitionStatus.NOTIFIED;
        this.notificationSentAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markDataRetrieved() {
        this.dataRetrievalCompletedAt = Instant.now();
        this.status = TenantTransitionStatus.DATA_RETRIEVED;
        this.updatedAt = Instant.now();
    }

    public void recordMigration(String destinationProviderName, String destinationSystemNumber, String evidenceHash) {
        this.destinationProviderName = destinationProviderName;
        this.destinationSystemNumber = destinationSystemNumber;
        this.migrationEvidenceHash = evidenceHash;
        this.migrationConfirmedAt = Instant.now();
        this.status = TenantTransitionStatus.MIGRATED;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExitPlanId() { return exitPlanId; }
    public void setExitPlanId(UUID exitPlanId) { this.exitPlanId = exitPlanId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTenantTin() { return tenantTin; }
    public void setTenantTin(String tenantTin) { this.tenantTin = tenantTin; }
    public String getTenantLegalName() { return tenantLegalName; }
    public void setTenantLegalName(String tenantLegalName) { this.tenantLegalName = tenantLegalName; }
    public TenantTransitionStatus getStatus() { return status; }
    public void setStatus(TenantTransitionStatus status) { this.status = status; }
    public Instant getNotificationSentAt() { return notificationSentAt; }
    public void setNotificationSentAt(Instant notificationSentAt) { this.notificationSentAt = notificationSentAt; }
    public Instant getDataRetrievalCompletedAt() { return dataRetrievalCompletedAt; }
    public void setDataRetrievalCompletedAt(Instant dataRetrievalCompletedAt) { this.dataRetrievalCompletedAt = dataRetrievalCompletedAt; }
    public String getDestinationProviderName() { return destinationProviderName; }
    public void setDestinationProviderName(String destinationProviderName) { this.destinationProviderName = destinationProviderName; }
    public String getDestinationSystemNumber() { return destinationSystemNumber; }
    public void setDestinationSystemNumber(String destinationSystemNumber) { this.destinationSystemNumber = destinationSystemNumber; }
    public Instant getMigrationConfirmedAt() { return migrationConfirmedAt; }
    public void setMigrationConfirmedAt(Instant migrationConfirmedAt) { this.migrationConfirmedAt = migrationConfirmedAt; }
    public String getMigrationEvidenceHash() { return migrationEvidenceHash; }
    public void setMigrationEvidenceHash(String migrationEvidenceHash) { this.migrationEvidenceHash = migrationEvidenceHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
