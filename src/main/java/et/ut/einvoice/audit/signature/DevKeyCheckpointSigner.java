package et.ut.einvoice.audit.signature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Ephemeral in-memory development/test signer for audit checkpoints.
 * <p>
 * TRUST MODEL:
 * This signer generates an ephemeral, non-persisted RSA key pair in memory for
 * local development and testing only.
 * This provides INTERNAL CRYPTOGRAPHIC INTEGRITY EVIDENCE and tamper evidence.
 * It is NOT:
 * - an external trust anchor
 * - an independent witness
 * - a non-repudiation authority
 * - a regulatory signature
 * <p>
 * Production signing keys, secure key custody, independent immutable ledgers,
 * independent archives, and WORM/object-lock remain deployment-stage controls.
 * </p>
 */
@Component
public class DevKeyCheckpointSigner implements CheckpointSigner {

    private static final Logger log = LoggerFactory.getLogger(DevKeyCheckpointSigner.class);
    private static final String ALGORITHM = "SHA256withRSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    public DevKeyCheckpointSigner() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
            this.keyId = "dev-ephemeral-key-" + System.currentTimeMillis();
            log.info("Initialized DevKeyCheckpointSigner with ephemeral key {}", keyId);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    public DevKeyCheckpointSigner(KeyPair keyPair, String keyId) {
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.keyId = keyId;
    }

    @Override
    public String sign(String dataToSign) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign checkpoint data", e);
        }
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
