package et.ut.einvoice.portability.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purge_audit_certificates")
public class PurgeAuditCertificate {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "exit_request_id", nullable = false)
    private UUID exitRequestId;

    @Column(name = "certificate_number", nullable = false, unique = true, length = 128)
    private String certificateNumber;

    @Column(name = "records_purged_count", nullable = false)
    private long recordsPurgedCount;

    @Column(name = "statutory_records_retained_count", nullable = false)
    private long statutoryRecordsRetainedCount;

    @Column(name = "purged_categories", nullable = false, columnDefinition = "TEXT")
    private String purgedCategories;

    @Column(name = "retention_justification", nullable = false, columnDefinition = "TEXT")
    private String retentionJustification;

    @Column(name = "authorized_by_tenant_admin", nullable = false, length = 128)
    private String authorizedByTenantAdmin;

    @Column(name = "authorized_by_platform_admin", nullable = false, length = 128)
    private String authorizedByPlatformAdmin;

    @Column(name = "certificate_hash", nullable = false, length = 128)
    private String certificateHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    public PurgeAuditCertificate() {}

    public PurgeAuditCertificate(
            UUID id,
            UUID tenantId,
            UUID exitRequestId,
            String certificateNumber,
            long recordsPurgedCount,
            long statutoryRecordsRetainedCount,
            String purgedCategories,
            String retentionJustification,
            String authorizedByTenantAdmin,
            String authorizedByPlatformAdmin,
            String certificateHash
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.exitRequestId = exitRequestId;
        this.certificateNumber = certificateNumber;
        this.recordsPurgedCount = recordsPurgedCount;
        this.statutoryRecordsRetainedCount = statutoryRecordsRetainedCount;
        this.purgedCategories = purgedCategories;
        this.retentionJustification = retentionJustification;
        this.authorizedByTenantAdmin = authorizedByTenantAdmin;
        this.authorizedByPlatformAdmin = authorizedByPlatformAdmin;
        this.certificateHash = certificateHash;
        this.issuedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getExitRequestId() { return exitRequestId; }
    public String getCertificateNumber() { return certificateNumber; }
    public long getRecordsPurgedCount() { return recordsPurgedCount; }
    public long getStatutoryRecordsRetainedCount() { return statutoryRecordsRetainedCount; }
    public String getPurgedCategories() { return purgedCategories; }
    public String getRetentionJustification() { return retentionJustification; }
    public String getAuthorizedByTenantAdmin() { return authorizedByTenantAdmin; }
    public String getAuthorizedByPlatformAdmin() { return authorizedByPlatformAdmin; }
    public String getCertificateHash() { return certificateHash; }
    public Instant getIssuedAt() { return issuedAt; }
}
