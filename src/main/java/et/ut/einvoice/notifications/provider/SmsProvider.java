package et.ut.einvoice.notifications.provider;

/**
 * Pluggable SMS Provider abstraction fulfilling Directive No. 1142/2018 Art. 4(1)(i).
 * Decouples transaction processing from external telecom SMS infrastructure.
 */
public interface SmsProvider {

    /**
     * Dispatches an SMS message to the recipient's phone number.
     *
     * @param recipientPhone Destination phone number in E.164 or national format (e.g. +251911XXXXXX).
     * @param messageText    Statutory fiscal message content.
     * @return true if successfully accepted by the SMS gateway, false otherwise.
     */
    boolean sendSms(String recipientPhone, String messageText);

    /**
     * Name/identifier of the active SMS gateway provider.
     */
    String getProviderName();

    /**
     * Returns true if production gateway credentials and endpoints are configured.
     */
    boolean isConfigured();
}
