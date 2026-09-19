package et.ut.einvoice.audit.signature;

import et.ut.einvoice.audit.domain.AuditCheckpoint;

/**
 * Value object representing an audit checkpoint coupled with its cryptographic signature.
 */
public record SignedCheckpoint(
        AuditCheckpoint checkpoint,
        String signature,
        String keyId,
        String algorithm
) {
}
