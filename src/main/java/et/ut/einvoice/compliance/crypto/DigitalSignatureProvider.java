package et.ut.einvoice.compliance.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Strict Cryptographic Digital Signature Provider contract fulfilling Directive No. 1142/2018 Art. 4(6).
 * Enforces strict boundary between software development providers and production HSM implementations.
 * Application layer consumes sign(documentHash, signingIdentity) without knowledge of underlying physical HSM.
 */
public interface DigitalSignatureProvider {

    /**
     * Computes the SHA-256 canonical hash of the fiscal invoice payload.
     */
    String computeSha256Hash(String canonicalData);

    /**
     * Primary signing contract: Digitally signs canonical document hash using configured signing identity.
     */
    String sign(byte[] documentHash, SigningIdentity signingIdentity);

    /**
     * Primary verification contract: Verifies signature against the signing identity.
     */
    boolean verify(byte[] documentHash, String base64Signature, SigningIdentity signingIdentity);

    /**
     * Legacy/convenience adapter: Digitally signs canonical data bytes using RSA-2048 with SHA-256.
     */
    default String signData(byte[] data, PrivateKey privateKey) {
        return sign(data, SigningIdentity.ofSoftwareKey("default", privateKey, null));
    }

    /**
     * Legacy/convenience adapter: Verifies digital signature against the signer's public key.
     */
    default boolean verifySignature(byte[] data, String base64Signature, PublicKey publicKey) {
        return verify(data, base64Signature, SigningIdentity.ofSoftwareKey("default", null, publicKey));
    }

    /**
     * Returns the name of the active cryptographic provider.
     */
    String getProviderName();

    /**
     * True if backed by a physical or cloud Hardware Security Module (HSM) under PKCS#11.
     */
    boolean isHsmBacked();

    /**
     * Returns the operational health status of the cryptographic provider.
     */
    CryptoHealthState getHealthStatus();
}
