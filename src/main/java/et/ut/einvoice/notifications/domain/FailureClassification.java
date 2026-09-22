package et.ut.einvoice.notifications.domain;

/**
 * Operational failure classifications partitioning SMS dispatch outcomes
 * to prevent infinite retry loops and provide targeted operational telemetry.
 */
public enum FailureClassification {
    /**
     * Terminal failure due to invalid phone format, landline, blocked recipient, or customer opt-out.
     * 0 retries.
     */
    MESSAGE_FAILURE,

    /**
     * Provider authentication or configuration failure (HTTP 401, 403, invalid token).
     * Trips circuit breaker; halts new dispatches; alerts operators; 0 retries on record.
     */
    PROVIDER_CONFIGURATION_FAILURE,

    /**
     * Gateway balance exhausted or tenant quota exceeded.
     * Pauses dispatch queue; preserves pending records; alerts billing.
     */
    PROVIDER_CAPACITY_FAILURE,

    /**
     * Transient network error (HTTP 429 rate limit, 500, 502, 503, connection dropped prior to send).
     * Scheduled for bounded exponential backoff.
     */
    PROVIDER_NETWORK_FAILURE,

    /**
     * Ambiguous outcome where timeout occurs after request payload transmission.
     * Must NOT be blindly retried; routes to status reconciliation.
     */
    PROVIDER_UNKNOWN_OUTCOME
}
