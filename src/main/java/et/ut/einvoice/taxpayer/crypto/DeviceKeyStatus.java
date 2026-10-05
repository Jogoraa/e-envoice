package et.ut.einvoice.taxpayer.crypto;

/**
 * Lifecycle status of POS Device Cryptographic Public Key (Directive No. 1142/2026 Art. 4(6)).
 */
public enum DeviceKeyStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
