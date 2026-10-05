package et.ut.einvoice.government.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_government_credentials")
public class TenantGovernmentCredential {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "mor_system_number", nullable = false, length = 128)
    private String morSystemNumber;

    @Column(name = "seller_tin", nullable = false, length = 16)
    private String sellerTin;

    @Column(name = "taxpayer_registration_identity", length = 128)
    private String taxpayerRegistrationIdentity;

    @Column(name = "encrypted_client_id", nullable = false, columnDefinition = "TEXT")
    private String encryptedClientId;

    @Column(name = "encrypted_client_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedClientSecret;

    @Column(name = "encrypted_api_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(name = "key_vault_version", nullable = false)
    private int keyVaultVersion = 1;

    @Column(name = "insa_certificate_reference", length = 256)
    private String insaCertificateReference;

    @Column(name = "certificate_serial", length = 128)
    private String certificateSerial;

    @Column(name = "certificate_expiry")
    private Instant certificateExpiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_status", nullable = false, length = 32)
    private CredentialStatus credentialStatus = CredentialStatus.ACTIVE;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantGovernmentCredential() {}

    public TenantGovernmentCredential(
            UUID id,
            UUID tenantId,
            String morSystemNumber,
            String sellerTin,
            String taxpayerRegistrationIdentity,
            String encryptedClientId,
            String encryptedClientSecret,
            String encryptedApiKey,
            int keyVaultVersion,
            String insaCertificateReference,
            String certificateSerial,
            Instant certificateExpiry
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.morSystemNumber = morSystemNumber;
        this.sellerTin = sellerTin;
        this.taxpayerRegistrationIdentity = taxpayerRegistrationIdentity;
        this.encryptedClientId = encryptedClientId;
        this.encryptedClientSecret = encryptedClientSecret;
        this.encryptedApiKey = encryptedApiKey;
        this.keyVaultVersion = keyVaultVersion;
        this.insaCertificateReference = insaCertificateReference;
        this.certificateSerial = certificateSerial;
        this.certificateExpiry = certificateExpiry;
        this.credentialStatus = CredentialStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getMorSystemNumber() { return morSystemNumber; }
    public void setMorSystemNumber(String morSystemNumber) { this.morSystemNumber = morSystemNumber; }
    public String getSellerTin() { return sellerTin; }
    public void setSellerTin(String sellerTin) { this.sellerTin = sellerTin; }
    public String getTaxpayerRegistrationIdentity() { return taxpayerRegistrationIdentity; }
    public void setTaxpayerRegistrationIdentity(String taxpayerRegistrationIdentity) { this.taxpayerRegistrationIdentity = taxpayerRegistrationIdentity; }
    public String getEncryptedClientId() { return encryptedClientId; }
    public void setEncryptedClientId(String encryptedClientId) { this.encryptedClientId = encryptedClientId; }
    public String getEncryptedClientSecret() { return encryptedClientSecret; }
    public void setEncryptedClientSecret(String encryptedClientSecret) { this.encryptedClientSecret = encryptedClientSecret; }
    public String getEncryptedApiKey() { return encryptedApiKey; }
    public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }
    public int getKeyVaultVersion() { return keyVaultVersion; }
    public void setKeyVaultVersion(int keyVaultVersion) { this.keyVaultVersion = keyVaultVersion; }
    public String getInsaCertificateReference() { return insaCertificateReference; }
    public void setInsaCertificateReference(String insaCertificateReference) { this.insaCertificateReference = insaCertificateReference; }
    public String getCertificateSerial() { return certificateSerial; }
    public void setCertificateSerial(String certificateSerial) { this.certificateSerial = certificateSerial; }
    public Instant getCertificateExpiry() { return certificateExpiry; }
    public void setCertificateExpiry(Instant certificateExpiry) { this.certificateExpiry = certificateExpiry; }
    public CredentialStatus getCredentialStatus() { return credentialStatus; }
    public void setCredentialStatus(CredentialStatus credentialStatus) { this.credentialStatus = credentialStatus; }
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(Instant lastValidatedAt) { this.lastValidatedAt = lastValidatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
