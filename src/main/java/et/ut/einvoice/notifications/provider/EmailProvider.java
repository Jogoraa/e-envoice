package et.ut.einvoice.notifications.provider;

/**
 * Pluggable Email Provider abstraction fulfilling Directive No. 1142/2018 Art. 4(1)(i).
 * Decouples transaction processing from external email dispatch transport.
 */
public interface EmailProvider {

    /**
     * Dispatches an electronic tax invoice notification email to the buyer.
     *
     * @param recipientEmail Valid destination email address.
     * @param subject        Notification subject line.
     * @param bodyText       Certified tax invoice message body (plain text or HTML).
     * @return true if accepted for delivery by the email transport, false otherwise.
     */
    boolean sendEmail(String recipientEmail, String subject, String bodyText);

    /**
     * Name/identifier of the active email provider.
     */
    String getProviderName();

    /**
     * Returns true if production SMTP or API credentials are configured.
     */
    boolean isConfigured();
}
