package et.ut.einvoice.compliance.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.UUID;

/**
 * Key Management Provider SPI (Directive No. 1142/2026 Art. 4(6) & Requirement 17).
 * Decouples cryptographic signing from key storage (Vault/KMS/HSM/Encrypted Store).
 */
public interface SigningKeyProvider {

    PrivateKey getTenantPrivateKey(UUID tenantId);

    PublicKey getTenantPublicKey(UUID tenantId);

    String getCertificateSerialNumber(UUID tenantId);
}
