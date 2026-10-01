package et.ut.einvoice.compliance.crypto;

/**
 * Health and operational state for cryptographic signing engines.
 */
public enum CryptoHealthState {
    READY,
    UNAVAILABLE,
    HARDWARE_MISSING_EXTERNAL_DEPENDENCY,
    MISCONFIGURED
}
