package et.ut.einvoice.compliance.signature;

import java.util.UUID;

/**
 * Statutory Fiscal Digital Signature Contract.
 * Implements FDRE MoR Directive No. 1142/2026 Art. 4(6) and Art. 19(5)(b).
 *
 * Guarantees:
 * - A SHA-256 digest is NOT considered a digital signature.
 * - Production implementations interface exclusively with the taxpayer's approved INSA credential / HSM boundary.
 * - In production mode, software fallback is strictly prohibited (fails closed as EXTERNAL_DEPENDENCY).
 * - Development provider operates solely under non-production profiles.
 */
public interface FiscalSignatureService {

    /**
     * Signs a canonical payload using the tenant's accredited INSA signing identity.
     */
    FiscalSignatureResponse signFiscalDocument(UUID tenantId, String canonicalPayload, String certificateRef);

    /**
     * Verifies the authenticity of a fiscal digital signature.
     */
    boolean verifyFiscalSignature(UUID tenantId, String canonicalPayload, String base64Signature, String certificateRef);

    /**
     * Returns whether the active provider is backed by physical/cloud HSM hardware.
     */
    boolean isHsmBacked();

    /**
     * Active cryptographic provider designation.
     */
    String getProviderName();
}
