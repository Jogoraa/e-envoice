package et.ut.einvoice.compliance.signature;

import java.time.Instant;

/**
 * Output of a formal statutory fiscal signature operation (Directive No. 1142/2026 Art. 4(6), Art. 19(5)(b)).
 */
public record FiscalSignatureResponse(
        String signingAlgorithm,
        String keyCertificateReference,
        String signature,
        String documentDigest,
        CertificateMetadata certificateMetadata,
        Instant signedTimestamp
) {}
