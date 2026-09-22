package et.ut.einvoice.reports.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_jobs")
public class ReportJob {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "report_definition_id", length = 64)
    private String reportDefinitionId;

    @Column(name = "report_title", length = 255, nullable = false)
    private String reportTitle;

    @Column(name = "date_range", length = 128)
    private String dateRange;

    @Column(name = "format", length = 16, nullable = false)
    private String format = "PDF";

    @Column(name = "status", length = 32, nullable = false)
    private String status = "PROCESSING"; // 'PROCESSING', 'COMPLETED', 'FAILED'

    @Column(name = "record_count", nullable = false)
    private int recordCount = 0;

    @Column(name = "artifact_url", length = 512)
    private String artifactUrl;

    @Column(name = "report_content", columnDefinition = "TEXT")
    private String reportContent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public ReportJob() {}

    public ReportJob(UUID id, UUID tenantId, String reportDefinitionId, String reportTitle,
                     String dateRange, String format, String status, int recordCount,
                     String artifactUrl, String reportContent) {
        this.id = id;
        this.tenantId = tenantId;
        this.reportDefinitionId = reportDefinitionId;
        this.reportTitle = reportTitle;
        this.dateRange = dateRange;
        this.format = format;
        this.status = status;
        this.recordCount = recordCount;
        this.artifactUrl = artifactUrl;
        this.reportContent = reportContent;
        this.createdAt = Instant.now();
        if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            this.completedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getReportDefinitionId() { return reportDefinitionId; }
    public void setReportDefinitionId(String reportDefinitionId) { this.reportDefinitionId = reportDefinitionId; }

    public String getReportTitle() { return reportTitle; }
    public void setReportTitle(String reportTitle) { this.reportTitle = reportTitle; }

    public String getDateRange() { return dateRange; }
    public void setDateRange(String dateRange) { this.dateRange = dateRange; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }

    public String getArtifactUrl() { return artifactUrl; }
    public void setArtifactUrl(String artifactUrl) { this.artifactUrl = artifactUrl; }

    public String getReportContent() { return reportContent; }
    public void setReportContent(String reportContent) { this.reportContent = reportContent; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public void complete(int count, String url, String content) {
        this.status = "COMPLETED";
        this.recordCount = count;
        this.artifactUrl = url;
        this.reportContent = content;
        this.completedAt = Instant.now();
    }

    public void fail() {
        this.status = "FAILED";
        this.completedAt = Instant.now();
    }
}
