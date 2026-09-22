package et.ut.einvoice.platform.config.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * High-Security Cryptographic Secret Store Engine.
 * Enforces AES-256-GCM authenticated encryption with 96-bit unique nonces per encryption
 * and 128-bit GCM authentication tags.
 * Plaintext secrets are NEVER logged or returned across API boundaries.
 * Correlation and audit records utilize keyed HMAC-SHA-256 fingerprints.
 */
@Service
public class SecretEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(SecretEncryptionService.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits
    private static final int GCM_TAG_LENGTH_BITS = 128; // 16 bytes auth tag
    private static final String PAYLOAD_PREFIX = "AESGCM:v1:";

    private final byte[] aesKeyBytes;
    private final byte[] hmacKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(
            @Value("${platform.security.secret-store.encryption-key:}") String customKey,
            @Value("${platform.security.jwt.secret:default-dev-ut-einvoice-platform-jwt-secret-key-at-least-256-bits-long}") String jwtSecret
    ) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String keySource = (customKey != null && !customKey.isBlank()) ? customKey : (jwtSecret + "_UT_SECRET_VAULT_KEY");
            this.aesKeyBytes = sha256.digest(keySource.getBytes(StandardCharsets.UTF_8));

            sha256.reset();
            this.hmacKeyBytes = sha256.digest((keySource + "_AUDIT_HMAC_SALT").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize cryptographic secret store keys", e);
        }
    }

    /**
     * Encrypts a plaintext secret into an authenticated AES-256-GCM payload.
     */
    public String encryptSecret(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(iv);
            String ctB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(cipherText);

            return PAYLOAD_PREFIX + ivB64 + ":" + ctB64;
        } catch (Exception e) {
            log.error("Cryptographic encryption failure in secret store: {}", e.getClass().getSimpleName());
            throw new SecurityException("Failed to securely encrypt sensitive configuration secret", e);
        }
    }

    /**
     * Decrypts an authenticated AES-256-GCM payload back to plaintext.
     */
    public String decryptSecret(String encryptedPayload) {
        if (encryptedPayload == null || encryptedPayload.isBlank()) {
            return null;
        }

        if (!encryptedPayload.startsWith(PAYLOAD_PREFIX)) {
            throw new SecurityException("Invalid or untrusted secret payload format");
        }

        try {
            String stripped = encryptedPayload.substring(PAYLOAD_PREFIX.length());
            String[] parts = stripped.split(":");
            if (parts.length != 2) {
                throw new SecurityException("Corrupted secret payload components");
            }

            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getUrlDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Cryptographic decryption failure in secret store: {}", e.getClass().getSimpleName());
            throw new SecurityException("Failed to decrypt sensitive configuration secret", e);
        }
    }

    /**
     * Computes a deterministic HMAC-SHA256 fingerprint for secret verification without disclosing plaintext.
     */
    public String computeFingerprint(String secret) {
        if (secret == null || secret.isBlank()) {
            return "UNCONFIGURED";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] hmac = mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(10, hmac.length); i++) {
                hex.append(String.format("%02x", hmac[i]));
            }
            return "HMAC:" + hex;
        } catch (Exception e) {
            return "REDACTED";
        }
    }
}
