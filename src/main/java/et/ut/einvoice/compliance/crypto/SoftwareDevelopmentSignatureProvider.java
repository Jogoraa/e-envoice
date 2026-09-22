package et.ut.einvoice.compliance.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Development & Test Cryptographic Provider.
 * Uses in-memory software keys and Bouncy Castle RSA-2048.
 * Strictly disabled in production when HSM is active.
 */
@Component
@ConditionalOnProperty(name = "mor.crypto.provider", havingValue = "software", matchIfMissing = true)
public class SoftwareDevelopmentSignatureProvider implements DigitalSignatureProvider {

    private static final Logger log = LoggerFactory.getLogger(SoftwareDevelopmentSignatureProvider.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public SoftwareDevelopmentSignatureProvider() {
        log.info("[CRYPTO] Initialized SoftwareDevelopmentSignatureProvider (BouncyCastle RSA-2048 / SHA-256).");
    }

    @Override
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

    @Override
    public String signData(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(privateKey);
            signature.update(data);
            byte[] signedBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signedBytes);
        } catch (Exception e) {
            log.error("Failed to generate software digital signature", e);
            throw new RuntimeException("Software Digital signature generation failed: " + e.getMessage(), e);
        }
    }

    @Override
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

    @Override
    public String getProviderName() {
        return "BouncyCastle Software (DEVELOPMENT / TEST ONLY)";
    }

    @Override
    public boolean isHsmBacked() {
        return false;
    }
}
