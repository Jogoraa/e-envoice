package et.ut.einvoice.documents.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_documents")
public class StoredDocument {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType; // 'HTML_RECEIPT', 'PDF_TAX_INVOICE', 'EXPORT_ARCHIVE'

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public StoredDocument() {}

    public StoredDocument(UUID id, UUID tenantId, UUID invoiceId, String documentType,
                          String storagePath, String contentHash, String contentType, long byteSize) {
        this.id = id;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.documentType = documentType;
        this.storagePath = storagePath;
        this.contentHash = contentHash;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getDocumentType() { return documentType; }
    public String getStoragePath() { return storagePath; }
    public String getContentHash() { return contentHash; }
    public String getContentType() { return contentType; }
    public long getByteSize() { return byteSize; }
    public Instant getCreatedAt() { return createdAt; }
}
