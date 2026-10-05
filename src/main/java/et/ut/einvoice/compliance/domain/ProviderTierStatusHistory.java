package et.ut.einvoice.compliance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Historical snapshot of provider regulatory tier status and threshold proximity evaluations.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 14(6).
 */
@Entity
@Table(name = "provider_tier_status_history")
public class ProviderTierStatusHistory {

    @Id
    private UUID id;

    @Column(name = "assessment_time", nullable = false)
    private Instant assessmentTime = Instant.now();

    @Column(name = "active_taxpayer_count", nullable = false)
    private int activeTaxpayerCount;

    @Column(name = "annual_sales_volume", nullable = false, precision = 18, scale = 2)
    private BigDecimal annualSalesVolume;

    @Column(name = "current_level", nullable = false)
    private int currentLevel;

    @Column(name = "projected_level", nullable = false)
    private int projectedLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private ProviderTierStatus status;

    @Column(name = "proximity_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal proximityPercentage;

    @Column(name = "alert_message")
    private String alertMessage;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ProviderTierStatusHistory() {}

    public ProviderTierStatusHistory(UUID id, Instant assessmentTime, int activeTaxpayerCount,
                                     BigDecimal annualSalesVolume, int currentLevel, int projectedLevel,
                                     ProviderTierStatus status, BigDecimal proximityPercentage,
                                     String alertMessage, Instant notifiedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.assessmentTime = assessmentTime != null ? assessmentTime : Instant.now();
        this.activeTaxpayerCount = activeTaxpayerCount;
        this.annualSalesVolume = annualSalesVolume;
        this.currentLevel = currentLevel;
        this.projectedLevel = projectedLevel;
        this.status = status;
        this.proximityPercentage = proximityPercentage;
        this.alertMessage = alertMessage;
        this.notifiedAt = notifiedAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getAssessmentTime() { return assessmentTime; }
    public void setAssessmentTime(Instant assessmentTime) { this.assessmentTime = assessmentTime; }

    public int getActiveTaxpayerCount() { return activeTaxpayerCount; }
    public void setActiveTaxpayerCount(int activeTaxpayerCount) { this.activeTaxpayerCount = activeTaxpayerCount; }

    public BigDecimal getAnnualSalesVolume() { return annualSalesVolume; }
    public void setAnnualSalesVolume(BigDecimal annualSalesVolume) { this.annualSalesVolume = annualSalesVolume; }

    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }

    public int getProjectedLevel() { return projectedLevel; }
    public void setProjectedLevel(int projectedLevel) { this.projectedLevel = projectedLevel; }

    public ProviderTierStatus getStatus() { return status; }
    public void setStatus(ProviderTierStatus status) { this.status = status; }

    public BigDecimal getProximityPercentage() { return proximityPercentage; }
    public void setProximityPercentage(BigDecimal proximityPercentage) { this.proximityPercentage = proximityPercentage; }

    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }

    public Instant getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(Instant notifiedAt) { this.notifiedAt = notifiedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
