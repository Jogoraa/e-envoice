package et.ut.einvoice.notifications.provider;

/**
 * Immutable request payload dispatched to an SmsProvider.
 */
public record SmsSendRequest(
        String recipientPhone,
        String messageText,
        String senderId,
        String correlationId
) {
    public SmsSendRequest {
        if (recipientPhone == null || recipientPhone.isBlank()) {
            throw new IllegalArgumentException("recipientPhone cannot be null or blank");
        }
        if (messageText == null || messageText.isBlank()) {
            throw new IllegalArgumentException("messageText cannot be null or blank");
        }
    }
}
