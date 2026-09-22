package et.ut.einvoice.compliance.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Cryptographic Digital Signature Provider abstraction fulfilling Directive No. 1142/2018 Art. 4(6).
 * Enforces strict decoupling between software development mocks and production Hardware Security Modules (HSM).
 */
public interface DigitalSignatureProvider {

    /**
     * Computes the SHA-256 canonical hash of the fiscal invoice payload.
     */
    String computeSha256Hash(String canonicalData);

    /**
     * Digitally signs canonical data bytes using RSA-2048 with SHA-256.
     */
    String signData(byte[] data, PrivateKey privateKey);

    /**
     * Verifies digital signature against the signer's public key.
     */
    boolean verifySignature(byte[] data, String base64Signature, PublicKey publicKey);

    /**
     * Returns the name of the active cryptographic provider.
     */
    String getProviderName();

    /**
     * True if backed by a physical/cloud Hardware Security Module (HSM) under PKCS#11.
     */
    boolean isHsmBacked();
}
