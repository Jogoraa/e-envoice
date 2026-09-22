package et.ut.einvoice.notifications.provider;

import java.util.Optional;

/**
 * Pluggable provider-neutral SMS Provider Service Provider Interface (SPI)
 * fulfilling FDRE Ministry of Revenues Directive No. 1142/2026 Art. 4(1)(i)
 * and VAT Proclamation/Regulation No. 570/2024.
 *
 * <p><b>Reliability and Delivery Semantics:</b>
 * <ul>
 *   <li><b>Internal Notification Intent: EXACTLY-ONCE</b> — Enforced atomically via database transaction
 *       and deterministic unique idempotency constraint on (tenant_id, idempotency_key).</li>
 *   <li><b>Provider Gateway Submission: AT-LEAST-ONCE / AMBIGUOUS UNDER NETWORK FAILURE</b> — In the event
 *       of transport timeout or socket read disconnect, outcome transitions to SUBMISSION_UNKNOWN and
 *       is reconciled out-of-band. Blind retries are strictly prohibited.</li>
 *   <li><b>Physical Handset Delivery: NOT GUARANTEED EXACTLY-ONCE</b> — Telecom carrier networks (SMPP/SS7)
 *       operate asynchronously across cellular base stations. Handset reception may experience network
 *       retries or duplicates beyond platform boundary control.</li>
 * </ul>
 */
public interface SmsProvider {

    /**
     * Dispatches an SMS message with structured request data.
     */
    SmsSendResult sendTransactionalSms(SmsSendRequest request);

    /**
     * Queries carrier delivery status from provider if supported.
     */
    default Optional<SmsDeliveryStatus> getDeliveryStatus(String providerMessageId) {
        return Optional.empty();
    }

    /**
     * Checks provider account balance/units if supported.
     */
    default Optional<SmsBalanceResult> checkBalance() {
        return Optional.empty();
    }

    /**
     * Backwards-compatible legacy dispatch method.
     */
    default boolean sendSms(String recipientPhone, String messageText) {
        var result = sendTransactionalSms(new SmsSendRequest(recipientPhone, messageText, null, null));
        return result.success();
    }

    /**
     * Name/identifier of the active SMS gateway provider.
     */
    String getProviderName();

    /**
     * Returns true if production gateway credentials and endpoints are configured.
     */
    boolean isConfigured();

    // =========================================================================
    // PROVIDER CAPABILITY DETECTION
    // =========================================================================

    /**
     * Indicates whether the provider supports carrier Delivery Receipts (DLR) via webhook.
     */
    default boolean supportsDeliveryReceipts() {
        return false;
    }

    /**
     * Indicates whether the provider supports active polling for message delivery status.
     */
    default boolean supportsStatusLookup() {
        return false;
    }

    /**
     * Indicates whether the provider supports native request idempotency keys.
     */
    default boolean supportsProviderIdempotency() {
        return false;
    }

    /**
     * Indicates whether the provider supports custom Alphanumeric Sender IDs (e.g., 'MoR-EIRS').
     */
    default boolean supportsSenderId() {
        return false;
    }

    /**
     * Indicates whether the provider supports querying remaining account credits/balance.
     */
    default boolean supportsBalanceQuery() {
        return false;
    }
}
