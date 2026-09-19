package et.ut.einvoice.portability.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
public class ExportJob {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "REQUESTED"; // 'REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED'

    @Column(name = "artifact_url", length = 512)
    private String artifactUrl;

    @Column(name = "artifact_checksum", length = 64)
    private String artifactChecksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public ExportJob() {}

    public ExportJob(UUID id, UUID tenantId) {
        this.id = id;
        this.tenantId = tenantId;
        this.status = "REQUESTED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public String getArtifactUrl() { return artifactUrl; }
    public String getArtifactChecksum() { return artifactChecksum; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void complete(String url, String checksum) {
        this.status = "COMPLETED";
        this.artifactUrl = url;
        this.artifactChecksum = checksum;
        this.completedAt = Instant.now();
    }

    public void fail() {
        this.status = "FAILED";
        this.completedAt = Instant.now();
    }
}
