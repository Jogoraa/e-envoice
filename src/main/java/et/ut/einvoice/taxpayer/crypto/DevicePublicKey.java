package et.ut.einvoice.taxpayer.crypto;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Registered POS Device Public Key Entity (Directive No. 1142/2026 Art. 4(6)).
 */
@Entity
@Table(name = "device_public_keys")
public class DevicePublicKey {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(name = "algorithm", nullable = false, length = 32)
    private String algorithm = "RSA-2048";

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeviceKeyStatus status = DeviceKeyStatus.ACTIVE;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public DevicePublicKey() {}

    public DevicePublicKey(
            UUID id,
            UUID tenantId,
            UUID deviceId,
            int keyVersion,
            String algorithm,
            String publicKeyPem
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.keyVersion = keyVersion;
        this.algorithm = algorithm != null ? algorithm : "RSA-2048";
        this.publicKeyPem = publicKeyPem;
        this.status = DeviceKeyStatus.ACTIVE;
        this.registeredAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(86400 * 365);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDeviceId() { return deviceId; }
    public int getKeyVersion() { return keyVersion; }
    public String getAlgorithm() { return algorithm; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public DeviceKeyStatus getStatus() { return status; }
    public void setStatus(DeviceKeyStatus status) { this.status = status; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
