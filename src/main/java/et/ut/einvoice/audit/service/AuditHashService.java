package et.ut.einvoice.audit.service;

import et.ut.einvoice.audit.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Audit Hash Service providing cryptographic hashing for audit payloads and canonical event records.
 */
@Service
public class AuditHashService {

    private final AuditCanonicalizer canonicalizer;

    public AuditHashService(AuditCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public String computePayloadHash(String sanitizedPayload) {
        return sha256Hex(sanitizedPayload != null ? sanitizedPayload : "{}");
    }

    public String computeEventHash(AuditEvent event) {
        byte[] canonicalBytes = canonicalizer.canonicalize(event);
        return sha256Hex(canonicalBytes);
    }

    public String computeGenesisHash(UUID tenantId, String streamId) {
        String base = (tenantId != null ? tenantId.toString().replace("-", "") : "00000000000000000000000000000000") +
                ":" + (streamId != null ? streamId : "MAIN");
        return "GENESIS-" + sha256Hex(base).substring(0, 32).toUpperCase();
    }

    public String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
