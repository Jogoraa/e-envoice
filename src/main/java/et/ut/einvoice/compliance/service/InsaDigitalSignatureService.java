package et.ut.einvoice.compliance.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Digital Signature and Hashing Service implementing RSA-2048 with SHA-256 for Directive No. 1142/2018 Art. 4(6).
 * Directive Requirement: Digital-signature-secured communication and payload data exchange.
 * Technical Profile: RSA-2048 with SHA-256 (RSASSA-PKCS1-v1_5).
 * Implementation Status: Software key custody implemented; approved production key-custody mechanism (HSM/eID) pending specification/authority verification.
 */
@Service
public class InsaDigitalSignatureService {

    private static final Logger log = LoggerFactory.getLogger(InsaDigitalSignatureService.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Computes SHA-256 canonical hash of the invoice payload.
     */
    public String computeSha256Hash(String canonicalData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalData.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest algorithm not available", e);
        }
    }

    /**
     * Signs data using private key with SHA256withRSA.
     */
    public String signData(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(privateKey);
            signature.update(data);
            byte[] signedBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signedBytes);
        } catch (Exception e) {
            log.error("Failed to generate INSA digital signature", e);
            throw new RuntimeException("INSA Digital signature generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies signature using public key.
     */
    public boolean verifySignature(byte[] data, String base64Signature, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(publicKey);
            signature.update(data);
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to verify digital signature", e);
            return false;
        }
    }
}
