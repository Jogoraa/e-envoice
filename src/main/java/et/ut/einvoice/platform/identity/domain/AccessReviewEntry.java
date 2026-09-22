package et.ut.einvoice.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_review_entries")
public class AccessReviewEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "current_roles")
    private String currentRoles;

    @Column(name = "effective_permissions_summary")
    private String effectivePermissionsSummary;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision; // 'PENDING', 'APPROVED', 'MODIFIED', 'REVOKED', 'SUSPENDED'

    @Column(name = "notes")
    private String notes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public AccessReviewEntry() {
    }

    public AccessReviewEntry(UUID id, UUID campaignId, UUID userId, String currentRoles, String effectivePermissionsSummary) {
        this.id = id != null ? id : UUID.randomUUID();
        this.campaignId = campaignId;
        this.userId = userId;
        this.currentRoles = currentRoles;
        this.effectivePermissionsSummary = effectivePermissionsSummary;
        this.decision = "PENDING";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(UUID campaignId) {
        this.campaignId = campaignId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getCurrentRoles() {
        return currentRoles;
    }

    public void setCurrentRoles(String currentRoles) {
        this.currentRoles = currentRoles;
    }

    public String getEffectivePermissionsSummary() {
        return effectivePermissionsSummary;
    }

    public void setEffectivePermissionsSummary(String effectivePermissionsSummary) {
        this.effectivePermissionsSummary = effectivePermissionsSummary;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
