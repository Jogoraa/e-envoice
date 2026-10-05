package et.ut.einvoice.compliance.domain;

/**
 * Tracking states for provider tier compliance and threshold alerting.
 * Mandated by FDRE MoR Directive No. 1142/2026 Art. 14(6).
 */
public enum ProviderTierStatus {
    CURRENT_LEVEL,
    THRESHOLD_APPROACHING,
    LEVEL_CHANGE_PENDING_NOTIFICATION,
    LEVEL_CHANGE_NOTIFIED,
    CONFIRMED
}
