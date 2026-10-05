package et.ut.einvoice.portability.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_exit_requests")
public class TenantExitRequest {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "authorized_by", length = 128)
    private String authorizedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_status", nullable = false, length = 32)
    private TenantExitStatus exitStatus = TenantExitStatus.REQUESTED;

    @Column(name = "export_job_id")
    private UUID exportJobId;

    @Column(name = "archive_checksum", length = 128)
    private String archiveChecksum;

    @Column(name = "destination_provider")
    private String destinationProvider;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "export_generated_at")
    private Instant exportGeneratedAt;

    @Column(name = "archive_verified_at")
    private Instant archiveVerifiedAt;

    @Column(name = "tenant_confirmed_at")
    private Instant tenantConfirmedAt;

    @Column(name = "purge_executed_at")
    private Instant purgeExecutedAt;

    @Column(name = "purge_certificate_number", length = 128)
    private String purgeCertificateNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantExitRequest() {}

    public TenantExitRequest(UUID id, UUID tenantId, String requestedBy, String destinationProvider, String reason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.requestedBy = requestedBy;
        this.destinationProvider = destinationProvider;
        this.reason = reason;
        this.exitStatus = TenantExitStatus.REQUESTED;
        this.requestedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRequestedBy() { return requestedBy; }
    public String getAuthorizedBy() { return authorizedBy; }
    public TenantExitStatus getExitStatus() { return exitStatus; }
    public UUID getExportJobId() { return exportJobId; }
    public String getArchiveChecksum() { return archiveChecksum; }
    public String getDestinationProvider() { return destinationProvider; }
    public String getReason() { return reason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getFrozenAt() { return frozenAt; }
    public Instant getExportGeneratedAt() { return exportGeneratedAt; }
    public Instant getArchiveVerifiedAt() { return archiveVerifiedAt; }
    public Instant getTenantConfirmedAt() { return tenantConfirmedAt; }
    public Instant getPurgeExecutedAt() { return purgeExecutedAt; }
    public String getPurgeCertificateNumber() { return purgeCertificateNumber; }

    public void freezeTenant() {
        this.exitStatus = TenantExitStatus.FROZEN;
        this.frozenAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void linkExport(UUID exportJobId, String checksum) {
        this.exportJobId = exportJobId;
        this.archiveChecksum = checksum;
        this.exitStatus = TenantExitStatus.EXPORT_GENERATED;
        this.exportGeneratedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void verifyArchive() {
        this.exitStatus = TenantExitStatus.ARCHIVE_VERIFIED;
        this.archiveVerifiedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void confirmTenantReceipt() {
        this.exitStatus = TenantExitStatus.TENANT_CONFIRMED;
        this.tenantConfirmedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void completePurge(String certificateNumber, String dualAuthorizer) {
        this.authorizedBy = dualAuthorizer;
        this.purgeCertificateNumber = certificateNumber;
        this.exitStatus = TenantExitStatus.PURGED;
        this.purgeExecutedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markMigrated() {
        this.exitStatus = TenantExitStatus.MIGRATED;
        this.updatedAt = Instant.now();
    }
}
