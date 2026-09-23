package et.ut.einvoice.notifications.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Routes transactional SMS dispatches to the configured active provider.
 *
 * Supported active-provider values (notifications.sms.active-provider / SMS_PROVIDER env):
 *   geezsms        → Live GeezSMS HTTP provider (production)
 *   ethio-telecom  → Ethio Telecom gateway
 *   mock (default) → Deterministic mock for dev/test
 */
@Component
@Primary
public class SmsProviderRouter implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsProviderRouter.class);

    private final MockGeezSmsProvider mockGeezSmsProvider;
    private final EthioTelecomSmsProvider ethioTelecomSmsProvider;
    private final GeezSmsProvider geezSmsProvider;
    private final String activeProvider;

    public SmsProviderRouter(
            MockGeezSmsProvider mockGeezSmsProvider,
            EthioTelecomSmsProvider ethioTelecomSmsProvider,
            GeezSmsProvider geezSmsProvider,
            @Value("${notifications.sms.active-provider:mock}") String activeProvider
    ) {
        this.mockGeezSmsProvider = mockGeezSmsProvider;
        this.ethioTelecomSmsProvider = ethioTelecomSmsProvider;
        this.geezSmsProvider = geezSmsProvider;
        this.activeProvider = activeProvider;
        log.info("SmsProviderRouter initialized. Active provider: '{}'. Live delegate: {}",
                activeProvider, resolveDelegate(activeProvider).getProviderName());
    }

    private SmsProvider getActiveDelegate() {
        return resolveDelegate(activeProvider);
    }

    private SmsProvider resolveDelegate(String provider) {
        if ("geezsms".equalsIgnoreCase(provider)) {
            return geezSmsProvider;
        }
        if ("ethio-telecom".equalsIgnoreCase(provider) || "ethio".equalsIgnoreCase(provider)) {
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
