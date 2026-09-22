package et.ut.einvoice.notifications.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Routes transactional SMS dispatches to the configured active provider.
 */
@Component
@Primary
public class SmsProviderRouter implements SmsProvider {

    private final MockGeezSmsProvider mockGeezSmsProvider;
    private final EthioTelecomSmsProvider ethioTelecomSmsProvider;
    private final String activeProvider;

    public SmsProviderRouter(
            MockGeezSmsProvider mockGeezSmsProvider,
            EthioTelecomSmsProvider ethioTelecomSmsProvider,
            @Value("${notifications.sms.active-provider:mock}") String activeProvider
    ) {
        this.mockGeezSmsProvider = mockGeezSmsProvider;
        this.ethioTelecomSmsProvider = ethioTelecomSmsProvider;
        this.activeProvider = activeProvider;
    }

    private SmsProvider getActiveDelegate() {
        if ("ethio-telecom".equalsIgnoreCase(activeProvider) || "ethio".equalsIgnoreCase(activeProvider)) {
            return ethioTelecomSmsProvider;
        }
        return mockGeezSmsProvider;
    }

    @Override
    public SmsSendResult sendTransactionalSms(SmsSendRequest request) {
        return getActiveDelegate().sendTransactionalSms(request);
    }

    @Override
    public Optional<SmsDeliveryStatus> getDeliveryStatus(String providerMessageId) {
        return getActiveDelegate().getDeliveryStatus(providerMessageId);
    }

    @Override
    public Optional<SmsBalanceResult> checkBalance() {
        return getActiveDelegate().checkBalance();
    }

    @Override
    public boolean sendSms(String recipientPhone, String messageText) {
        return getActiveDelegate().sendSms(recipientPhone, messageText);
    }

    @Override
    public String getProviderName() {
        return getActiveDelegate().getProviderName();
    }

    @Override
    public boolean isConfigured() {
        return getActiveDelegate().isConfigured();
    }
}
