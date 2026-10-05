package et.ut.einvoice.compliance.signature;

import java.time.Instant;

/**
 * Metadata snapshot of the INSA-accredited cryptographic certificate (Directive No. 1142/2026 Art. 4(6)).
 */
public record CertificateMetadata(
        String serialNumber,
        String issuerDn,
        String subjectDn,
        Instant validFrom,
        Instant validTo
) {}
