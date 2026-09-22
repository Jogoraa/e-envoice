package et.ut.einvoice.notifications.provider;

import java.math.BigDecimal;

/**
 * Provider account balance inquiry result.
 */
public record SmsBalanceResult(
        BigDecimal balance,
        String currency,
        long remainingUnits
) {}
