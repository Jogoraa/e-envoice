package et.ut.einvoice.invoicing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_invoice_sequences")
public class TenantInvoiceSequence {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "current_counter", nullable = false)
    private Long currentCounter = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantInvoiceSequence() {}

    public TenantInvoiceSequence(UUID tenantId, Long currentCounter) {
        this.tenantId = tenantId;
        this.currentCounter = currentCounter != null ? currentCounter : 0L;
        this.updatedAt = Instant.now();
    }

    public UUID getTenantId() { return tenantId; }
    public Long getCurrentCounter() { return currentCounter; }
    public void setCurrentCounter(Long currentCounter) {
        this.currentCounter = currentCounter;
        this.updatedAt = Instant.now();
    }
    public Instant getUpdatedAt() { return updatedAt; }
}
