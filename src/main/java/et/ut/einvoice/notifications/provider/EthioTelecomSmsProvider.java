package et.ut.einvoice.notifications.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Production-ready Ethio Telecom / MoR SMS Gateway Provider Adapter.
 * Connects via secure HTTPS REST endpoint with API key authentication, timeouts, and structured error handling.
 */
@Component
public class EthioTelecomSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(EthioTelecomSmsProvider.class);

    private final WebClient webClient;
    private final String endpointUrl;
    private final String apiKey;
    private final String senderId;
    private final boolean isConfigured;
    private final String activeProfile;

    public EthioTelecomSmsProvider(
            WebClient.Builder webClientBuilder,
            @Value("${notifications.sms.ethio-telecom.endpoint:${ETHIO_TELECOM_SMS_URL:}}") String endpointUrl,
            @Value("${notifications.sms.ethio-telecom.api-key:${ETHIO_TELECOM_SMS_KEY:}}") String apiKey,
            @Value("${notifications.sms.ethio-telecom.sender-id:${ETHIO_TELECOM_SMS_SENDER_ID:MoR-EIRS}}") String senderId,
            @Value("${spring.profiles.active:dev}") String activeProfile
    ) {
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.activeProfile = activeProfile;
        this.isConfigured = endpointUrl != null && !endpointUrl.isBlank() && apiKey != null && !apiKey.isBlank();

        if (this.isConfigured) {
            this.webClient = webClientBuilder
                    .baseUrl(endpointUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("EthioTelecomSmsProvider initialized with active endpoint: {}", endpointUrl);
        } else {
            this.webClient = null;
            log.info("EthioTelecomSmsProvider unconfigured (ETHIO_TELECOM_SMS_URL/KEY not present). Operating in development simulation mode.");
        }
    }

    @Override
    public boolean sendSms(String recipientPhone, String messageText) {
        if (!isConfigured || webClient == null) {
            if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
                log.error("SMS dispatch rejected: Ethio Telecom SMS gateway credentials are not configured in production.");
                return false;
            }
            log.info("[SMS-DEV-SIMULATION -> {}] [Sender: {}]: {}", recipientPhone, senderId, messageText);
            return true;
        }

        try {
            var payload = Map.of(
                    "sender", senderId,
                    "recipient", recipientPhone,
                    "message", messageText,
                    "priority", "HIGH"
            );

            String response = webClient.post()
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            log.info("SMS delivered successfully to {} via Ethio Telecom: response={}", recipientPhone, response);
            return true;
        } catch (Exception ex) {
            log.error("Failed to dispatch SMS to {} via Ethio Telecom gateway: {}", recipientPhone, ex.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Ethio Telecom SMS Gateway";
    }

    @Override
    public boolean isConfigured() {
        return isConfigured;
    }
}
