package et.ut.einvoice.cancellation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable evidence attachment supporting cancellation under Directive No. 1142/2026 Art. 26.
 */
@Entity
@Table(name = "cancellation_evidence_attachments")
public class CancellationEvidenceAttachment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cancellation_request_id", nullable = false)
    private UUID cancellationRequestId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256_checksum", nullable = false, length = 128)
    private String sha256Checksum;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "uploaded_by", nullable = false, length = 64)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CancellationEvidenceAttachment() {}

    public CancellationEvidenceAttachment(UUID id, UUID tenantId, UUID cancellationRequestId,
                                          String fileName, String contentType, long fileSize,
                                          String sha256Checksum, String storagePath,
                                          String description, String uploadedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.cancellationRequestId = cancellationRequestId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256Checksum = sha256Checksum;
        this.storagePath = storagePath;
        this.description = description;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCancellationRequestId() { return cancellationRequestId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getSha256Checksum() { return sha256Checksum; }
    public String getStoragePath() { return storagePath; }
    public String getDescription() { return description; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
