package et.ut.einvoice.offline.domain;

/**
 * Lifecycle states for pre-allocated temporary document ranges.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 4(4) & Art. 22.
 */
public enum OfflineAllocationStatus {
    ALLOCATED,
    ACTIVE,
    EXHAUSTED,
    EXPIRED,
    REVOKED,
    RECONCILED
}
