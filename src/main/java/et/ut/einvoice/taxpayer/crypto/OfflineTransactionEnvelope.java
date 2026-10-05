package et.ut.einvoice.taxpayer.crypto;

import java.time.Instant;
import java.util.UUID;

/**
 * Statutory offline transaction envelope signed by the client device (Directive No. 1142/2026 Art. 4(6)).
 */
public record OfflineTransactionEnvelope(
        UUID tenantId,
        UUID deviceId,
        String clientOperationId,
        Instant localTimestamp,
        String payloadDigest,
        String offlineAllocationRef,
        Long sequenceCounter,
        String nonce,
        Integer keyVersion,
        String signature
) {
    /**
     * Canonical string for deterministic cryptographic signature verification.
     */
    public String toCanonicalString() {
        return "TENANT:" + tenantId +
                "|DEV:" + deviceId +
                "|OP:" + (clientOperationId != null ? clientOperationId : "") +
                "|TIME:" + (localTimestamp != null ? localTimestamp.toString() : "") +
                "|DIGEST:" + (payloadDigest != null ? payloadDigest : "") +
                "|ALLOC:" + (offlineAllocationRef != null ? offlineAllocationRef : "") +
                "|SEQ:" + sequenceCounter +
                "|NONCE:" + (nonce != null ? nonce : "") +
                "|VER:" + (keyVersion != null ? keyVersion : 1);
    }
}
