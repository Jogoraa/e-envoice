package et.ut.einvoice.creditsales.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_settlements")
public class CreditSettlement {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "cash_receipt_id", nullable = false)
    private UUID cashReceiptId;

    @Column(name = "settlement_number", nullable = false, length = 64)
    private String settlementNumber;

    @Column(name = "settlement_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "balance_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod = "CASH";

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CreditSettlement() {}

    public CreditSettlement(UUID id, UUID tenantId, UUID invoiceId, UUID cashReceiptId,
                            String settlementNumber, BigDecimal settlementAmount,
                            BigDecimal balanceBefore, BigDecimal balanceAfter,
                            String paymentMethod, String paymentReference, Instant settledAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.cashReceiptId = cashReceiptId;
        this.settlementNumber = settlementNumber;
        this.settlementAmount = settlementAmount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "CASH";
        this.paymentReference = paymentReference;
        this.settledAt = settledAt != null ? settledAt : Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getCashReceiptId() { return cashReceiptId; }
    public String getSettlementNumber() { return settlementNumber; }
    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getCreatedAt() { return createdAt; }
}
