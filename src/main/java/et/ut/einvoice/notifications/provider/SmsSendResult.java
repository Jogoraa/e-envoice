package et.ut.einvoice.notifications.provider;

import et.ut.einvoice.notifications.domain.FailureClassification;

/**
 * Result returned by an SmsProvider upon attempting message dispatch.
 */
public record SmsSendResult(
        boolean success,
        String providerMessageId,
        String errorCode,
        String errorMessage,
        FailureClassification failureClassification
) {
    public static SmsSendResult accepted(String providerMessageId) {
        return new SmsSendResult(true, providerMessageId, null, null, null);
    }

    public static SmsSendResult failed(String errorCode, String errorMessage, FailureClassification classification) {
        return new SmsSendResult(false, null, errorCode, errorMessage, classification);
    }

    public static SmsSendResult unknown(String errorCode, String errorMessage) {
        return new SmsSendResult(false, null, errorCode, errorMessage, FailureClassification.PROVIDER_UNKNOWN_OUTCOME);
    }
}
