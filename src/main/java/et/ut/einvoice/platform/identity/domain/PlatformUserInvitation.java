package et.ut.einvoice.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_user_invitations")
public class PlatformUserInvitation {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(name = "initial_role_code", nullable = false, length = 64)
    private String initialRoleCode;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // 'PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PlatformUserInvitation() {
    }

    public PlatformUserInvitation(UUID id, String email, String phone, String fullName, String initialRoleCode, UUID tenantId, String tokenHash, Instant expiresAt, String createdBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.email = email;
        this.phone = phone;
        this.fullName = fullName;
        this.initialRoleCode = initialRoleCode;
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getInitialRoleCode() {
        return initialRoleCode;
    }

    public void setInitialRoleCode(String initialRoleCode) {
        this.initialRoleCode = initialRoleCode;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
