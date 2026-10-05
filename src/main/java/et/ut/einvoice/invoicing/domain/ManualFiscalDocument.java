package et.ut.einvoice.invoicing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted manual fallback fiscal document under outage conditions.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 22.
 */
@Entity
@Table(name = "manual_fiscal_documents")
public class ManualFiscalDocument {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "manual_document_number", nullable = false, length = 64)
    private String manualDocumentNumber;

    @Column(name = "manual_book", nullable = false, length = 64)
    private String manualBook;

    @Column(name = "original_issue_time", nullable = false)
    private Instant originalIssueTime;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt = Instant.now();

    @Column(name = "customer_tin", length = 16)
    private String customerTin;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "outage_reference", nullable = false, length = 128)
    private String outageReference;

    @Column(name = "reconciliation_deadline", nullable = false)
    private Instant reconciliationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "eirs_registration_state", nullable = false, length = 32)
    private ManualFiscalState eirsRegistrationState = ManualFiscalState.PENDING;

    @Column(name = "irn", length = 128)
    private String irn;

    @Column(name = "duplicate_reprint_count", nullable = false)
    private int duplicateReprintCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ManualFiscalDocument() {}

    public ManualFiscalDocument(UUID id, UUID tenantId, UUID branchId,
                                String manualDocumentNumber, String manualBook,
                                Instant originalIssueTime, Instant enteredAt,
                                String customerTin, String customerName,
                                BigDecimal totalAmount, BigDecimal subtotal, BigDecimal taxAmount,
                                String itemsJson, String operatorId, String outageReference,
                                Instant reconciliationDeadline) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.manualDocumentNumber = manualDocumentNumber;
        this.manualBook = manualBook;
        this.originalIssueTime = originalIssueTime;
        this.enteredAt = enteredAt != null ? enteredAt : Instant.now();
        this.customerTin = customerTin;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.itemsJson = itemsJson;
        this.operatorId = operatorId;
        this.outageReference = outageReference;
        this.reconciliationDeadline = reconciliationDeadline;
        this.eirsRegistrationState = ManualFiscalState.PENDING;
        this.duplicateReprintCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public String getManualDocumentNumber() {
        return manualDocumentNumber;
    }

    public void setManualDocumentNumber(String manualDocumentNumber) {
        this.manualDocumentNumber = manualDocumentNumber;
    }

    public String getManualBook() {
        return manualBook;
    }

    public void setManualBook(String manualBook) {
        this.manualBook = manualBook;
    }

    public Instant getOriginalIssueTime() {
        return originalIssueTime;
    }

    public void setOriginalIssueTime(Instant originalIssueTime) {
        this.originalIssueTime = originalIssueTime;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Instant enteredAt) {
        this.enteredAt = enteredAt;
    }

    public String getCustomerTin() {
        return customerTin;
    }

    public void setCustomerTin(String customerTin) {
        this.customerTin = customerTin;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOutageReference() {
        return outageReference;
    }

    public void setOutageReference(String outageReference) {
        this.outageReference = outageReference;
    }

    public Instant getReconciliationDeadline() {
        return reconciliationDeadline;
    }

    public void setReconciliationDeadline(Instant reconciliationDeadline) {
        this.reconciliationDeadline = reconciliationDeadline;
    }

    public ManualFiscalState getEirsRegistrationState() {
        return eirsRegistrationState;
    }

    public void setEirsRegistrationState(ManualFiscalState eirsRegistrationState) {
        this.eirsRegistrationState = eirsRegistrationState;
        this.updatedAt = Instant.now();
    }

    public String getIrn() {
        return irn;
    }

    public void setIrn(String irn) {
        this.irn = irn;
        this.updatedAt = Instant.now();
    }

    public int getDuplicateReprintCount() {
        return duplicateReprintCount;
    }

    public void incrementReprintCount() {
        this.duplicateReprintCount++;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
