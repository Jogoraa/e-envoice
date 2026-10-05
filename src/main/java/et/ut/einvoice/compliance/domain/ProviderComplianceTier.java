package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Statutory SaaS Provider Compliance Tier metadata under Directive No. 1142/2026 Art. 14(6).
 */
@Entity
@Table(name = "provider_compliance_tiers")
public class ProviderComplianceTier {

    @Id
    private UUID id;

    @Column(name = "tier_level", nullable = false, unique = true)
    private int tierLevel;

    @Column(name = "min_active_taxpayers", nullable = false)
    private int minActiveTaxpayers;

    @Column(name = "max_active_taxpayers", nullable = false)
    private int maxActiveTaxpayers;

    @Column(name = "min_annual_sales_volume", nullable = false, precision = 18, scale = 2)
    private BigDecimal minAnnualSalesVolume;

    @Column(name = "max_annual_sales_volume", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxAnnualSalesVolume;

    @Column(name = "required_guarantee_amount_usd", nullable = false, precision = 18, scale = 2)
    private BigDecimal requiredGuaranteeAmountUsd;

    @Column(name = "required_technical_staffing", nullable = false)
    private int requiredTechnicalStaffing;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "source_article", nullable = false, length = 64)
    private String sourceArticle = "Directive 1142/2026 Art. 14(6)";

    @Column(name = "version", nullable = false, length = 32)
    private String version = "2026.1";

    public ProviderComplianceTier() {}

    public ProviderComplianceTier(UUID id, int tierLevel, int minActiveTaxpayers, int maxActiveTaxpayers,
                                  BigDecimal minAnnualSalesVolume, BigDecimal maxAnnualSalesVolume,
                                  BigDecimal requiredGuaranteeAmountUsd, int requiredTechnicalStaffing,
                                  LocalDate effectiveFrom, String sourceArticle, String version) {
        this.id = id;
        this.tierLevel = tierLevel;
        this.minActiveTaxpayers = minActiveTaxpayers;
        this.maxActiveTaxpayers = maxActiveTaxpayers;
        this.minAnnualSalesVolume = minAnnualSalesVolume;
        this.maxAnnualSalesVolume = maxAnnualSalesVolume;
        this.requiredGuaranteeAmountUsd = requiredGuaranteeAmountUsd;
        this.requiredTechnicalStaffing = requiredTechnicalStaffing;
        this.effectiveFrom = effectiveFrom;
        this.sourceArticle = sourceArticle;
        this.version = version;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public int getTierLevel() { return tierLevel; }
    public void setTierLevel(int tierLevel) { this.tierLevel = tierLevel; }

    public int getMinActiveTaxpayers() { return minActiveTaxpayers; }
    public void setMinActiveTaxpayers(int minActiveTaxpayers) { this.minActiveTaxpayers = minActiveTaxpayers; }

    public int getMaxActiveTaxpayers() { return maxActiveTaxpayers; }
    public void setMaxActiveTaxpayers(int maxActiveTaxpayers) { this.maxActiveTaxpayers = maxActiveTaxpayers; }

    public BigDecimal getMinAnnualSalesVolume() { return minAnnualSalesVolume; }
    public void setMinAnnualSalesVolume(BigDecimal minAnnualSalesVolume) { this.minAnnualSalesVolume = minAnnualSalesVolume; }

    public BigDecimal getMaxAnnualSalesVolume() { return maxAnnualSalesVolume; }
    public void setMaxAnnualSalesVolume(BigDecimal maxAnnualSalesVolume) { this.maxAnnualSalesVolume = maxAnnualSalesVolume; }

    public BigDecimal getRequiredGuaranteeAmountUsd() { return requiredGuaranteeAmountUsd; }
    public void setRequiredGuaranteeAmountUsd(BigDecimal requiredGuaranteeAmountUsd) { this.requiredGuaranteeAmountUsd = requiredGuaranteeAmountUsd; }

    public int getRequiredTechnicalStaffing() { return requiredTechnicalStaffing; }
    public void setRequiredTechnicalStaffing(int requiredTechnicalStaffing) { this.requiredTechnicalStaffing = requiredTechnicalStaffing; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public String getSourceArticle() { return sourceArticle; }
    public void setSourceArticle(String sourceArticle) { this.sourceArticle = sourceArticle; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
