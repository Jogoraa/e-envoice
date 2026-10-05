package et.ut.einvoice.invoicing.domain;

/**
 * Registration states for manual paper/QR fallback invoices.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 22.
 */
public enum ManualFiscalState {
    PENDING,
    REGISTERED,
    FAILED,
    REJECTED
}
