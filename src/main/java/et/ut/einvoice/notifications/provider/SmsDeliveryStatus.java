package et.ut.einvoice.notifications.provider;

import java.time.Instant;

/**
 * Normalized carrier delivery status query result.
 */
public record SmsDeliveryStatus(
        String providerMessageId,
        boolean delivered,
        String statusCode,
        String statusDescription,
        Instant timestamp
) {}
