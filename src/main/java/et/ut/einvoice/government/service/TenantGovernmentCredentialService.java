package et.ut.einvoice.government.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.government.domain.CredentialStatus;
import et.ut.einvoice.government.domain.TenantGovernmentCredential;
import et.ut.einvoice.government.dto.ProvisionTenantCredentialRequest;
import et.ut.einvoice.government.dto.TenantGovernmentCredentialMetadataResponse;
import et.ut.einvoice.government.repository.TenantGovernmentCredentialRepository;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantGovernmentCredentialService {

    private static final Logger log = LoggerFactory.getLogger(TenantGovernmentCredentialService.class);
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final TenantGovernmentCredentialRepository credentialRepository;
    private final AuditService auditService;
    private final byte[] vaultMasterKey;

    public TenantGovernmentCredentialService(
            TenantGovernmentCredentialRepository credentialRepository,
            AuditService auditService,
            @Value("${ut.security.vault.master-key:UT-INVOICE-GOVERNMENT-CREDENTIALS-VAULT-2026-KEY}") String masterKeySource
    ) {
        this.credentialRepository = credentialRepository;
        this.auditService = auditService;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            this.vaultMasterKey = sha.digest(masterKeySource.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize vault master key", e);
        }
    }

    public record DecryptedGovernmentCredentials(
            UUID tenantId,
            String morSystemNumber,
            String sellerTin,
            String taxpayerRegistrationIdentity,
            String clientId,
            String clientSecret,
            String apiKey,
            String insaCertificateReference,
            String certificateSerial,
            Instant certificateExpiry,
            CredentialStatus status
    ) {}

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_TENANT_ADMIN') or hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TenantGovernmentCredentialMetadataResponse provisionCredentials(ProvisionTenantCredentialRequest request) {
        UUID currentTenant = TenantContextHolder.getRequiredContext().tenantId();
        if (request.sellerTin() == null || request.sellerTin().isBlank()) {
            throw new BusinessException("INVALID_TIN", "Seller TIN is mandatory for government credentials");
        }
        if (request.morSystemNumber() == null || request.morSystemNumber().isBlank()) {
            throw new BusinessException("INVALID_SYSTEM_NUMBER", "MoR system number is mandatory");
        }
        if (request.clientId() == null || request.clientId().isBlank() ||
                request.clientSecret() == null || request.clientSecret().isBlank() ||
                request.apiKey() == null || request.apiKey().isBlank()) {
            throw new BusinessException("INVALID_CREDENTIALS", "Client ID, Client Secret, and API Key are mandatory");
        }

        // Encrypt secrets using AES-256-GCM
        String encClientId = encrypt(request.clientId());
        String encClientSecret = encrypt(request.clientSecret());
        String encApiKey = encrypt(request.apiKey());

        TenantGovernmentCredential credential = credentialRepository.findByTenantId(currentTenant)
                .orElse(new TenantGovernmentCredential(
                        UUID.randomUUID(),
                        currentTenant,
                        request.morSystemNumber(),
                        request.sellerTin(),
                        request.taxpayerRegistrationIdentity(),
                        encClientId,
                        encClientSecret,
                        encApiKey,
                        1,
                        request.insaCertificateReference(),
                        request.certificateSerial(),
                        request.certificateExpiry()
                ));

        credential.setMorSystemNumber(request.morSystemNumber());
        credential.setSellerTin(request.sellerTin());
        credential.setTaxpayerRegistrationIdentity(request.taxpayerRegistrationIdentity());
        credential.setEncryptedClientId(encClientId);
        credential.setEncryptedClientSecret(encClientSecret);
        credential.setEncryptedApiKey(encApiKey);
        credential.setInsaCertificateReference(request.insaCertificateReference());
        credential.setCertificateSerial(request.certificateSerial());
        credential.setCertificateExpiry(request.certificateExpiry());
        credential.setCredentialStatus(CredentialStatus.ACTIVE);
        credential.setUpdatedAt(Instant.now());

        TenantGovernmentCredential saved = credentialRepository.save(credential);

        // Audit without exposing plaintext secrets
        auditService.recordEvent(
                currentTenant,
                "SYSTEM",
                "ADMIN",
                "PROVISION",
                "TENANT_GOV_CREDENTIALS",
                saved.getId().toString(),
                "SELLER_TIN=" + saved.getSellerTin() + ", MOR_SYS=" + saved.getMorSystemNumber() + ", VAULT_VER=" + saved.getKeyVaultVersion(),
                "127.0.0.1"
        );

        log.info("Provisioned secure government credentials vault for tenant {}", currentTenant);
        return toMetadataResponse(saved);
    }

    @Transactional(readOnly = true)
    public DecryptedGovernmentCredentials getDecryptedCredentialsForTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException("TENANT_REQUIRED", "Tenant context required for government credentials");
        }
        TenantGovernmentCredential cred = credentialRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("GOVERNMENT_CREDENTIALS_NOT_FOUND",
                        "Taxpayer tenant does not have provisioned MoR/EIRS credentials"));

        if (cred.getCredentialStatus() != CredentialStatus.ACTIVE) {
            throw new BusinessException("CREDENTIAL_NOT_ACTIVE", "Taxpayer government credentials are " + cred.getCredentialStatus());
        }

        String clientId = decrypt(cred.getEncryptedClientId());
        String clientSecret = decrypt(cred.getEncryptedClientSecret());
        String apiKey = decrypt(cred.getEncryptedApiKey());

        return new DecryptedGovernmentCredentials(
                cred.getTenantId(),
                cred.getMorSystemNumber(),
                cred.getSellerTin(),
                cred.getTaxpayerRegistrationIdentity(),
                clientId,
                clientSecret,
                apiKey,
                cred.getInsaCertificateReference(),
                cred.getCertificateSerial(),
                cred.getCertificateExpiry(),
                cred.getCredentialStatus()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_TENANT_ADMIN') or hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TenantGovernmentCredentialMetadataResponse getMetadataForCurrentTenant() {
        UUID currentTenant = TenantContextHolder.getRequiredContext().tenantId();
        TenantGovernmentCredential cred = credentialRepository.findByTenantId(currentTenant)
                .orElseThrow(() -> new BusinessException("GOVERNMENT_CREDENTIALS_NOT_FOUND",
                        "No government credentials found for tenant"));
        return toMetadataResponse(cred);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_TENANT_ADMIN') or hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TenantGovernmentCredentialMetadataResponse rotateApiKey(String newApiKey) {
        UUID currentTenant = TenantContextHolder.getRequiredContext().tenantId();
        if (newApiKey == null || newApiKey.isBlank()) {
            throw new BusinessException("INVALID_API_KEY", "New API Key is mandatory for rotation");
        }
        TenantGovernmentCredential cred = credentialRepository.findByTenantId(currentTenant)
                .orElseThrow(() -> new BusinessException("GOVERNMENT_CREDENTIALS_NOT_FOUND",
                        "No credentials found to rotate"));

        cred.setEncryptedApiKey(encrypt(newApiKey));
        cred.setKeyVaultVersion(cred.getKeyVaultVersion() + 1);
        cred.setUpdatedAt(Instant.now());
        TenantGovernmentCredential saved = credentialRepository.save(cred);

        auditService.recordEvent(
                currentTenant,
                "SYSTEM",
                "ADMIN",
                "ROTATE_API_KEY",
                "TENANT_GOV_CREDENTIALS",
                saved.getId().toString(),
                "NEW_VAULT_VER=" + saved.getKeyVaultVersion(),
                "127.0.0.1"
        );

        return toMetadataResponse(saved);
    }

    @Transactional
    public void recordValidationSuccess(UUID tenantId) {
        credentialRepository.findByTenantId(tenantId).ifPresent(c -> {
            c.setLastValidatedAt(Instant.now());
            credentialRepository.save(c);
        });
    }

    private TenantGovernmentCredentialMetadataResponse toMetadataResponse(TenantGovernmentCredential c) {
        return new TenantGovernmentCredentialMetadataResponse(
                c.getTenantId(),
                c.getMorSystemNumber(),
                c.getSellerTin(),
                c.getTaxpayerRegistrationIdentity(),
                c.getInsaCertificateReference(),
                c.getCertificateSerial(),
                c.getCertificateExpiry(),
                c.getCredentialStatus(),
                c.getLastValidatedAt(),
                c.getKeyVaultVersion(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            SecretKey secretKey = new SecretKeySpec(vaultMasterKey, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Vault encryption failure", e);
        }
    }

    private String decrypt(String base64Encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Encrypted);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            SecretKey secretKey = new SecretKeySpec(vaultMasterKey, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Vault decryption failure", e);
        }
    }
}
