package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A periodic aggregate sales report submitted to the Tax Authority by a taxpayer
 * holding an Art. 20 exempt-sector authorization.
 *
 * Directive No. 1142/2026 Art. 20(3)(b)-(d):
 *   (b) Summary of sales information must be reported within the prescribed time limit
 *   (c) Summarized info includes: period from/to, service/goods type, quantity, unit price,
 *       total price, tax type, tax rate, tax amount, and grand total
 *   (d) Transmission period (DAILY/WEEKLY/MONTHLY) determined by Authority per business sector
 *
 * Art. 20(5): B2B invoices remain excluded from this summary — they require direct EIRS registration.
 * Art. 20(6): Invoices already registered via EIRS must NOT be double-counted in this summary.
 */
@Entity
@Table(name = "exempt_sector_summary_reports")
public class ExemptSectorSummaryReport {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "authorization_id", nullable = false)
    private UUID authorizationId;

    /** Human-readable period label — '2026-10-05' | '2026-W40' | '2026-10' */
    @Column(name = "report_period_label", length = 32, nullable = false)
    private String reportPeriodLabel;

    @Column(name = "reporting_frequency", length = 16, nullable = false)
    private String reportingFrequency;

    @Column(name = "period_from", nullable = false)
    private Instant periodFrom;

    @Column(name = "period_to", nullable = false)
    private Instant periodTo;

    @Column(name = "total_invoice_count", nullable = false)
    private Long totalInvoiceCount = 0L;

    @Column(name = "total_gross_amount", precision = 22, scale = 2, nullable = false)
    private BigDecimal totalGrossAmount = BigDecimal.ZERO;

    @Column(name = "total_tax_amount", precision = 22, scale = 2, nullable = false)
    private BigDecimal totalTaxAmount = BigDecimal.ZERO;

    @Column(name = "total_grand_total", precision = 22, scale = 2, nullable = false)
    private BigDecimal totalGrandTotal = BigDecimal.ZERO;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Transient
    private List<ExemptSectorReportLine> lines = new ArrayList<>();

    public ExemptSectorSummaryReport() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ExemptSectorSummaryReport(UUID id, UUID tenantId, UUID authorizationId,
                                     String reportPeriodLabel, String reportingFrequency,
                                     Instant periodFrom, Instant periodTo) {
        this.id = id;
        this.tenantId = tenantId;
        this.authorizationId = authorizationId;
        this.reportPeriodLabel = reportPeriodLabel;
        this.reportingFrequency = reportingFrequency;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() { this.updatedAt = Instant.now(); }

    /** Freeze the report for submission — immutable after submit */
    public void submit(String submittedBy, String sha256Checksum) {
        if (!"DRAFT".equals(this.status)) {
            throw new IllegalStateException("Only DRAFT reports can be submitted");
        }
        this.status = "SUBMITTED";
        this.submittedAt = Instant.now();
        this.submittedBy = submittedBy;
        this.sha256Checksum = sha256Checksum;
        this.updatedAt = Instant.now();
    }

    public void accept() {
        this.status = "ACCEPTED";
        this.acceptedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        this.status = "REJECTED";
        this.rejectionReason = reason;
        this.updatedAt = Instant.now();
    }

    public void recalculateTotals() {
        this.totalInvoiceCount = lines.stream().mapToLong(ExemptSectorReportLine::getInvoiceCount).sum();
        this.totalGrossAmount  = lines.stream().map(ExemptSectorReportLine::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalTaxAmount    = lines.stream().map(ExemptSectorReportLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalGrandTotal   = lines.stream().map(ExemptSectorReportLine::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAuthorizationId() { return authorizationId; }
    public String getReportPeriodLabel() { return reportPeriodLabel; }
    public String getReportingFrequency() { return reportingFrequency; }
    public Instant getPeriodFrom() { return periodFrom; }
    public Instant getPeriodTo() { return periodTo; }
    public Long getTotalInvoiceCount() { return totalInvoiceCount; }
    public BigDecimal getTotalGrossAmount() { return totalGrossAmount; }
    public BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
    public BigDecimal getTotalGrandTotal() { return totalGrandTotal; }
    public String getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getSubmittedBy() { return submittedBy; }
    public String getSha256Checksum() { return sha256Checksum; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ExemptSectorReportLine> getLines() { return lines; }
    public void setLines(List<ExemptSectorReportLine> lines) {
        if (this.lines == null) {
            this.lines = new ArrayList<>();
        } else {
            this.lines.clear();
        }
        if (lines != null) {
            this.lines.addAll(lines);
        }
    }
}
