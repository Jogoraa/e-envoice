package et.ut.einvoice.platform.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "privileged_configuration_sessions")
public class PrivilegedConfigurationSession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "mfa_verified_at", nullable = false)
    private Instant mfaVerifiedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revocation_reason", length = 128)
    private String revocationReason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PrivilegedConfigurationSession() {}

    public PrivilegedConfigurationSession(
            UUID id,
            UUID userId,
            String username,
            Instant mfaVerifiedAt,
            Instant expiresAt,
            String ipAddress,
            String userAgent
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.username = username;
        this.mfaVerifiedAt = mfaVerifiedAt != null ? mfaVerifiedAt : Instant.now();
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getMfaVerifiedAt() {
        return mfaVerifiedAt;
    }

    public void setMfaVerifiedAt(Instant mfaVerifiedAt) {
        this.mfaVerifiedAt = mfaVerifiedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isValid() {
        return !revoked && expiresAt != null && Instant.now().isBefore(expiresAt);
    }
}
