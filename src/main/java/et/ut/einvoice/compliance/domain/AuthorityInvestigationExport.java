package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of an asynchronous, encrypted tax authority investigation export job.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 15(5).
 */
@Entity
@Table(name = "authority_investigation_exports")
public class AuthorityInvestigationExport {

    @Id
    private UUID id;

    @Column(name = "case_reference", nullable = false, length = 128)
    private String caseReference;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "customer_tin", length = 32)
    private String customerTin;

    @Column(name = "date_from")
    private Instant dateFrom;

    @Column(name = "date_to")
    private Instant dateTo;

    @Column(name = "invoice_range_start", length = 64)
    private String invoiceRangeStart;

    @Column(name = "invoice_range_end", length = 64)
    private String invoiceRangeEnd;

    @Column(name = "transaction_type", length = 64)
    private String transactionType;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'

    @Column(name = "record_count", nullable = false)
    private int recordCount = 0;

    @Column(name = "payload_encrypted_base64", columnDefinition = "TEXT")
    private String payloadEncryptedBase64;

    @Column(name = "encryption_algorithm", nullable = false, length = 64)
    private String encryptionAlgorithm = "AES/GCM/NoPadding";

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AuthorityInvestigationExport() {}

    public AuthorityInvestigationExport(UUID id, String caseReference, String reason, String requestedBy,
                                        UUID tenantId, String customerTin, Instant dateFrom, Instant dateTo,
                                        String invoiceRangeStart, String invoiceRangeEnd, String transactionType) {
        this.id = id != null ? id : UUID.randomUUID();
        this.caseReference = caseReference;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.tenantId = tenantId;
        this.customerTin = customerTin;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.invoiceRangeStart = invoiceRangeStart;
        this.invoiceRangeEnd = invoiceRangeEnd;
        this.transactionType = transactionType;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCaseReference() { return caseReference; }
    public void setCaseReference(String caseReference) { this.caseReference = caseReference; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCustomerTin() { return customerTin; }
    public void setCustomerTin(String customerTin) { this.customerTin = customerTin; }

    public Instant getDateFrom() { return dateFrom; }
    public void setDateFrom(Instant dateFrom) { this.dateFrom = dateFrom; }

    public Instant getDateTo() { return dateTo; }
    public void setDateTo(Instant dateTo) { this.dateTo = dateTo; }

    public String getInvoiceRangeStart() { return invoiceRangeStart; }
    public void setInvoiceRangeStart(String invoiceRangeStart) { this.invoiceRangeStart = invoiceRangeStart; }

    public String getInvoiceRangeEnd() { return invoiceRangeEnd; }
    public void setInvoiceRangeEnd(String invoiceRangeEnd) { this.invoiceRangeEnd = invoiceRangeEnd; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }

    public String getPayloadEncryptedBase64() { return payloadEncryptedBase64; }
    public void setPayloadEncryptedBase64(String payloadEncryptedBase64) { this.payloadEncryptedBase64 = payloadEncryptedBase64; }

    public String getEncryptionAlgorithm() { return encryptionAlgorithm; }
    public void setEncryptionAlgorithm(String encryptionAlgorithm) { this.encryptionAlgorithm = encryptionAlgorithm; }

    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public void markCompleted(int recordCount, String encryptedPayload, String checksum) {
        this.status = "COMPLETED";
        this.recordCount = recordCount;
        this.payloadEncryptedBase64 = encryptedPayload;
        this.sha256Checksum = checksum;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = "FAILED";
        this.completedAt = Instant.now();
    }
}
