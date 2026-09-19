package et.ut.einvoice.audit.signature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Verifier for cryptographic signatures on audit checkpoints.
 */
@Component
public class CheckpointSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(CheckpointSignatureVerifier.class);

    public boolean verify(String data, String signatureBase64, PublicKey publicKey, String algorithm) {
        if (data == null || signatureBase64 == null || publicKey == null || algorithm == null) {
            log.error("Signature verification failed: null argument provided");
            return false;
        }

        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
            boolean valid = sig.verify(sigBytes);
            if (!valid) {
                log.warn("Checkpoint signature verification returned FALSE");
            }
            return valid;
        } catch (Exception e) {
            log.error("Signature verification failed due to exception: {}", e.getMessage());
            return false;
        }
    }
}
