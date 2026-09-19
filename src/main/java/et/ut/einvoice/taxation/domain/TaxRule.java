package et.ut.einvoice.taxation.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tax_rules")
public class TaxRule {

    @Id
    private UUID id;

    @Column(name = "tax_code", nullable = false, length = 32)
    private String taxCode;

    @Column(name = "tax_type", nullable = false, length = 32)
    private String taxType;

    @Column(name = "rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal rate;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public TaxRule() {}

    public TaxRule(UUID id, String taxCode, String taxType, BigDecimal rate, Instant effectiveFrom, Instant effectiveTo, int version) {
        this.id = id;
        this.taxCode = taxCode;
        this.taxType = taxType;
        this.rate = rate;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.isActive = true;
        this.version = version;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTaxCode() { return taxCode; }
    public String getTaxType() { return taxType; }
    public BigDecimal getRate() { return rate; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public boolean isActive() { return isActive; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
