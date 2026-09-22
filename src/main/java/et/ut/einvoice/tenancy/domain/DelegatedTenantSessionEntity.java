package et.ut.einvoice.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Cluster-consistent, database-backed delegated tenant session entity.
 * Guarantees that support sessions and testing delegations are revocable across
 * all distributed backend application nodes (Instance A, B, C).
 */
@Entity
@Table(name = "delegated_tenant_sessions")
public class DelegatedTenantSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "master_user_id", nullable = false, length = 128)
    private String masterUserId;

    @Column(name = "target_tenant_id", nullable = false)
    private UUID targetTenantId;

    @Column(name = "target_branch_id")
    private UUID targetBranchId;

    @Column(name = "access_type", nullable = false, length = 32)
    private String accessType; // 'TESTING' or 'READ_ONLY_SUPPORT'

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private boolean isRevoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 128)
    private String revokedBy;

    public DelegatedTenantSessionEntity() {}

    public DelegatedTenantSessionEntity(
            UUID sessionId,
            String masterUserId,
            UUID targetTenantId,
            UUID targetBranchId,
            String accessType,
            String reason,
            Instant issuedAt,
            Instant expiresAt
    ) {
        this.sessionId = sessionId;
        this.masterUserId = masterUserId;
        this.targetTenantId = targetTenantId;
        this.targetBranchId = targetBranchId;
        this.accessType = accessType != null ? accessType : "TESTING";
        this.reason = reason;
        this.issuedAt = issuedAt != null ? issuedAt : Instant.now();
        this.expiresAt = expiresAt;
        this.isRevoked = false;
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getMasterUserId() { return masterUserId; }
    public void setMasterUserId(String masterUserId) { this.masterUserId = masterUserId; }

    public UUID getTargetTenantId() { return targetTenantId; }
    public void setTargetTenantId(UUID targetTenantId) { this.targetTenantId = targetTenantId; }

    public UUID getTargetBranchId() { return targetBranchId; }
    public void setTargetBranchId(UUID targetBranchId) { this.targetBranchId = targetBranchId; }

    public String getAccessType() { return accessType; }
    public void setAccessType(String accessType) { this.accessType = accessType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevoked() { return isRevoked; }
    public void setRevoked(boolean revoked) { isRevoked = revoked; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isRevoked && !isExpired();
    }
}
