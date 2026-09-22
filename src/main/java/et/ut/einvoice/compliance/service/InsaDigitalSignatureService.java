package et.ut.einvoice.compliance.service;

import et.ut.einvoice.compliance.crypto.DigitalSignatureProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Digital Signature and Hashing Service implementing RSA-2048 with SHA-256 for Directive No. 1142/2018 Art. 4(6).
 * Backed by pluggable DigitalSignatureProvider architecture separating development software keys from production HSM.
 */
@Service
public class InsaDigitalSignatureService {

    private static final Logger log = LoggerFactory.getLogger(InsaDigitalSignatureService.class);

    private final DigitalSignatureProvider signatureProvider;

    public InsaDigitalSignatureService(DigitalSignatureProvider signatureProvider) {
        this.signatureProvider = signatureProvider;
        log.info("InsaDigitalSignatureService initialized with provider: {} (HSM Backed: {})",
                signatureProvider.getProviderName(), signatureProvider.isHsmBacked());
    }

    /**
     * Computes SHA-256 canonical hash of the invoice payload.
     */
    public String computeSha256Hash(String canonicalData) {
        return signatureProvider.computeSha256Hash(canonicalData);
    }

    /**
     * Signs data using private key with SHA256withRSA.
     */
    public String signData(byte[] data, PrivateKey privateKey) {
        return signatureProvider.signData(data, privateKey);
    }

    /**
     * Verifies signature using public key.
     */
    public boolean verifySignature(byte[] data, String base64Signature, PublicKey publicKey) {
        return signatureProvider.verifySignature(data, base64Signature, publicKey);
    }

    public DigitalSignatureProvider getProvider() {
        return signatureProvider;
    }
}
