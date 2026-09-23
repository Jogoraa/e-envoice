package et.ut.einvoice.notifications.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.notifications.domain.FailureClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * Production GeezSMS HTTP provider adapter.
 *
 * Wire protocol (from official GeezSMS API documentation):
 *   POST https://api.geezsms.com/api/v1/sms/send
 *   Content-Type: multipart/form-data
 *   Fields:
 *     token       — API token (required, NOT a Bearer header)
 *     phone       — recipient MSISDN starting with 2519 (required)
 *     msg         — message body, max 335 chars, supports Unicode (required)
 *     shortcode_id — approved sender ID (optional; omit = GeezSMS default)
 *     callback    — webhook URL for delivery receipts (optional)
 *
 * Success response (HTTP 200):
 *   { "message_status": "success", "log": "async <UUID>",
 *     "phone": "2519XXXXXXXX", "message": "...", "api_log_id": <int> }
 *
 * Authentication: The API token is sent as a form field named "token".
 * Do NOT send it as a Bearer Authorization header.
 *
 * Security:
 *   - Token is never logged, never returned in API responses.
 *   - TLS is always enforced (HTTPS endpoint).
 *   - SSRF is prevented by using a fixed approved base URL, not an operator-controlled URL.
 */
@Component
public class GeezSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(GeezSmsProvider.class);

    static final String DEFAULT_API_BASE = "https://api.geezsms.com";
    static final String SEND_PATH = "/api/v1/sms/send";
    private static final String PROVIDER_NAME = "GeezSMS";

    private static final int CONNECT_TIMEOUT_SECONDS = 8;
    private static final int READ_TIMEOUT_SECONDS = 12;

    private final WebClient webClient;
    private final String token;
    private final String senderId;
    private final boolean isConfigured;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeezSmsProvider(
            WebClient.Builder webClientBuilder,
            @Value("${notifications.sms.geezsms.token:${GEEZSMS_TOKEN:}}") String token,
            @Value("${notifications.sms.geezsms.api-url:${GEEZSMS_API_URL:https://api.geezsms.com}}") String apiBaseUrl,
            @Value("${notifications.sms.geezsms.sender-id:${GEEZSMS_SENDER_ID:}}") String senderId
    ) {
        this.token = token;
        this.senderId = (senderId != null) ? senderId.trim() : "";
        this.isConfigured = token != null && !token.isBlank();

        // Defend against arbitrary URL injection (SSRF): only allow the approved GeezSMS base URL.
        String safeBase = validateAndSanitizeBaseUrl(apiBaseUrl);

        this.webClient = webClientBuilder
                .baseUrl(safeBase)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();

        if (this.isConfigured) {
            log.info("GeezSmsProvider initialized. Token: CONFIGURED (length={}), Base URL: {}, SenderID: {}",
                    token.length(), safeBase, senderId.isBlank() ? "(GeezSMS default)" : senderId);
        } else {
            log.info("GeezSmsProvider: GEEZSMS_TOKEN not configured. Provider inactive.");
        }
    }

    @Override
    public SmsSendResult sendTransactionalSms(SmsSendRequest request) {
        if (!isConfigured) {
            log.warn("[GEEZSMS] Dispatch rejected: token not configured. Outbox item will remain unprocessed.");
            return SmsSendResult.failed(
                    "TOKEN_NOT_CONFIGURED",
                    "GeezSMS token is not configured. Set GEEZSMS_TOKEN.",
                    FailureClassification.PROVIDER_CONFIGURATION_FAILURE
            );
        }

        String normalizedPhone = normalizePhone(request.recipientPhone());
        if (normalizedPhone == null) {
            log.warn("[GEEZSMS] Rejected dispatch: phone '{}' cannot be normalized to 2519XXXXXXXX format.",
                    maskPhone(request.recipientPhone()));
            return SmsSendResult.failed(
                    "INVALID_PHONE_FORMAT",
                    "Phone number cannot be normalized to required 2519XXXXXXXX format",
                    FailureClassification.MESSAGE_FAILURE
            );
        }

        log.info("[GEEZSMS] Dispatching SMS to {}. Sender: {}. Message length: {} chars.",
                maskPhone(normalizedPhone),
                senderId.isBlank() ? "(default)" : senderId,
                request.messageText().length());

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("token", token);
        bodyBuilder.part("phone", normalizedPhone);
        bodyBuilder.part("msg", request.messageText());
        if (!senderId.isBlank()) {
            bodyBuilder.part("shortcode_id", senderId);
        }

        try {
            String rawResponse = webClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                    .block();

            return parseSuccessResponse(rawResponse, normalizedPhone);

        } catch (WebClientResponseException ex) {
            return classifyHttpError(ex, normalizedPhone);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            boolean isTimeout = msg != null && (msg.contains("timeout") || msg.contains("TimeoutException")
                    || msg.contains("ReadTimeoutException") || msg.contains("PoolAcquirePendingLimitException"));
            if (isTimeout) {
                // Timeout after request was sent — outcome is genuinely unknown. Do NOT retry blindly.
                log.error("[GEEZSMS] Read timeout dispatching SMS to {}. Outcome unknown; routing to SUBMISSION_UNKNOWN.",
                        maskPhone(normalizedPhone));
                return SmsSendResult.unknown("READ_TIMEOUT", "Provider response timeout; delivery outcome unknown");
            }
            log.error("[GEEZSMS] Network/dispatch exception for {}: {}", maskPhone(normalizedPhone), msg);
            return SmsSendResult.failed("DISPATCH_EXCEPTION", msg, FailureClassification.PROVIDER_NETWORK_FAILURE);
        }
    }

    private SmsSendResult parseSuccessResponse(String rawResponse, String normalizedPhone) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[GEEZSMS] Empty response body from provider for {}.", maskPhone(normalizedPhone));
            return SmsSendResult.unknown("EMPTY_RESPONSE", "Provider returned empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            boolean isError = root.has("error") && root.path("error").asBoolean(false);
            String messageStatus = root.path("message_status").asText("");
            String msg = root.path("msg").asText("");

            boolean isSuccess = (!isError && (root.has("error") || "success".equalsIgnoreCase(messageStatus) || msg.toLowerCase().contains("success")));

            if (isSuccess) {
                // api_log_id is the provider's durable message identifier (may be in data or top-level)
                String providerMsgId = null;
                if (root.has("data") && root.path("data").has("api_log_id") && !root.path("data").path("api_log_id").isNull()) {
                    providerMsgId = root.path("data").path("api_log_id").asText();
                } else if (root.has("api_log_id") && !root.path("api_log_id").isNull()) {
                    providerMsgId = root.path("api_log_id").asText();
                } else if (root.has("log")) {
                    providerMsgId = root.path("log").asText();
                } else {
                    providerMsgId = "GEEZSMS-" + System.currentTimeMillis();
                }

                log.info("[GEEZSMS] SMS accepted. Recipient: {}. Provider msg ID: {}.",
                        maskPhone(normalizedPhone), providerMsgId);
                return SmsSendResult.accepted(providerMsgId);
            } else {
                String errorReason = !msg.isBlank() ? msg : messageStatus;
                log.warn("[GEEZSMS] Provider returned non-success response for {}. Body: {}",
                        maskPhone(normalizedPhone), truncate(rawResponse));
                return SmsSendResult.failed("PROVIDER_REJECTED", "Provider rejected: " + errorReason,
                        FailureClassification.MESSAGE_FAILURE);
            }
        } catch (Exception parseEx) {
            log.warn("[GEEZSMS] Failed to parse provider response for {}: {}", maskPhone(normalizedPhone), parseEx.getMessage());
            return SmsSendResult.unknown("PARSE_FAILURE", "Could not parse provider response");
        }
    }

    private SmsSendResult classifyHttpError(WebClientResponseException ex, String normalizedPhone) {
        int statusCode = ex.getStatusCode().value();
        log.error("[GEEZSMS] HTTP {} from provider for {}. Body: {}",
                statusCode, maskPhone(normalizedPhone), truncate(ex.getResponseBodyAsString()));
        return switch (statusCode) {
            case 401 -> SmsSendResult.failed("AUTH_FAILED",
                    "GeezSMS token rejected (HTTP 401). Verify GEEZSMS_TOKEN.",
                    FailureClassification.PROVIDER_CONFIGURATION_FAILURE);
            case 403 -> SmsSendResult.failed("FORBIDDEN",
                    "GeezSMS account forbidden (HTTP 403). Account may be suspended.",
                    FailureClassification.PROVIDER_CONFIGURATION_FAILURE);
            case 400 -> SmsSendResult.failed("BAD_REQUEST",
                    "GeezSMS rejected the request payload (HTTP 400). Check phone format and message.",
                    FailureClassification.MESSAGE_FAILURE);
            case 429 -> SmsSendResult.failed("RATE_LIMITED",
                    "GeezSMS rate limit reached (HTTP 429). Backoff and retry.",
                    FailureClassification.PROVIDER_CAPACITY_FAILURE);
            default -> {
                if (statusCode >= 500) {
                    yield SmsSendResult.failed("SERVER_ERROR_" + statusCode,
                            "GeezSMS server error (HTTP " + statusCode + ").",
                            FailureClassification.PROVIDER_NETWORK_FAILURE);
                }
                yield SmsSendResult.failed("HTTP_" + statusCode,
                        "Unexpected HTTP status from GeezSMS.",
                        FailureClassification.PROVIDER_NETWORK_FAILURE);
            }
        };
    }

    /**
     * Normalizes phone to GeezSMS required format: 2519XXXXXXXX (no leading +).
     * Accepts: 09XXXXXXXX, 9XXXXXXXX, 2519XXXXXXXX, +2519XXXXXXXX.
     * Returns null if format is unrecognizable.
     */
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String p = phone.trim().replaceAll("[\\s\\-()]", "");
        if (p.startsWith("+251")) {
            p = p.substring(1); // strip leading +
        }
        if (p.startsWith("251") && p.length() == 12) {
            return p; // already correct
        }
        if (p.startsWith("09") && p.length() == 10) {
            return "251" + p.substring(1); // 09XXXXXXXX → 2519XXXXXXXX
        }
        if (p.startsWith("9") && p.length() == 9) {
            return "251" + p; // 9XXXXXXXX → 2519XXXXXXXX
        }
        return null; // unrecognizable format
    }

    @Override
    public Optional<SmsBalanceResult> checkBalance() {
        if (!isConfigured) return Optional.empty();
        try {
            String rawResponse = webClient.get()
                    .uri("/api/v1/balance")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            if (rawResponse != null) {
                JsonNode root = objectMapper.readTree(rawResponse);
                if (!root.path("error").asBoolean(true)) {
                    JsonNode data = root.path("data");
                    String amountStr = data.path("total_amount").asText("0").replaceAll("[^0-9.]", "").trim();
                    java.math.BigDecimal amount = amountStr.isEmpty() ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(amountStr);
                    long totalSms = data.path("total_sms").asLong(-1);
                    return Optional.of(new SmsBalanceResult(amount, "ETB", totalSms));
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("[GEEZSMS] Balance check failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isConfigured() {
        return isConfigured;
    }

    @Override
    public boolean supportsSenderId() {
        return true; // shortcode_id is supported
    }

    @Override
    public boolean supportsBalanceQuery() {
        return true; // balance endpoint exists
    }

    @Override
    public boolean supportsStatusLookup() {
        return false; // GeezSMS uses async log-based delivery; no active poll endpoint in docs
    }

    @Override
    public boolean supportsDeliveryReceipts() {
        return true; // callback/webhook URL supported per API doc
    }

    // =========================================================================
    // Internal helpers — never expose token
    // =========================================================================

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
    }

    private static String truncate(String s) {
        if (s == null) return "(null)";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static String validateAndSanitizeBaseUrl(String configuredUrl) {
        // SSRF protection: only allow the approved GeezSMS production hostname.
        // The base URL comes from server-side configuration, not user input, but we
        // apply defense-in-depth by rejecting anything that isn't the expected domain.
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return DEFAULT_API_BASE;
        }
        String url = configuredUrl.trim();
        if (url.startsWith("https://api.geezsms.com")) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        log.warn("[GEEZSMS] Configured GEEZSMS_API_URL '{}' is not the approved GeezSMS endpoint. " +
                "Overriding with approved default: {}", url, DEFAULT_API_BASE);
        return DEFAULT_API_BASE;
    }
}
