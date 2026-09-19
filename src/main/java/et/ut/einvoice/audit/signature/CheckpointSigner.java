package et.ut.einvoice.audit.signature;

/**
 * Port boundary for signing cryptographic audit checkpoints.
 * <p>
 * Decouples checkpoint signing from specific key storage or hardware security modules (HSM).
 * Production private signing keys are strictly external and must never be stored in source code.
 * </p>
 */
public interface CheckpointSigner {

    /**
     * Signs the given canonical checkpoint data.
     *
     * @param dataToSign canonical representation of the checkpoint to sign
     * @return Base64-encoded signature
     */
    String sign(String dataToSign);

    /**
     * Unique identifier for the signing key.
     */
    String getKeyId();

    /**
     * The signature algorithm used (e.g. SHA256withRSA, Ed25519).
     */
    String getAlgorithm();
}
