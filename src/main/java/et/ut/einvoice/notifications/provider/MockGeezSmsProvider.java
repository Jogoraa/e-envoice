package et.ut.einvoice.notifications.provider;

import et.ut.einvoice.notifications.domain.FailureClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic programmable Mock SMS Provider supporting all 15 compliance test scenarios
 * and configurable provider capability permutations.
 * Enables full end-to-end verification without live network dependencies or speculative vendor contracts.
 */
@Component
public class MockGeezSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockGeezSmsProvider.class);

    public enum MockScenario {
        SUCCESS,
        TIMEOUT_BEFORE_SEND,
        TIMEOUT_AFTER_SEND,
        HTTP_400,
        HTTP_401,
        HTTP_403,
        HTTP_429,
        HTTP_500,
        INVALID_PHONE,
        INSUFFICIENT_BALANCE,
        DUPLICATE_CALLBACK,
        DELIVERY_SUCCESS,
        DELIVERY_FAILURE,
        NO_DLR,
        STATUS_UNKNOWN
    }

    private MockScenario defaultScenario = MockScenario.SUCCESS;
    private final ConcurrentHashMap<String, MockScenario> phoneScenarios = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SmsDeliveryStatus> deliveryReports = new ConcurrentHashMap<>();
    private final AtomicInteger invocationCount = new AtomicInteger(0);

    // Configurable capability toggles for testing provider adaptation
    private boolean supportsDeliveryReceipts = true;
    private boolean supportsStatusLookup = true;
    private boolean supportsProviderIdempotency = true;
    private boolean supportsSenderId = true;
    private boolean supportsBalanceQuery = true;

    public void setScenario(MockScenario scenario) {
        this.defaultScenario = scenario;
    }

    public void setScenarioForPhone(String phone, MockScenario scenario) {
        this.phoneScenarios.put(phone, scenario);
    }

    public void setSupportsDeliveryReceipts(boolean value) {
        this.supportsDeliveryReceipts = value;
    }

    public void setSupportsStatusLookup(boolean value) {
        this.supportsStatusLookup = value;
    }

    public void setSupportsProviderIdempotency(boolean value) {
        this.supportsProviderIdempotency = value;
    }

    public void setSupportsSenderId(boolean value) {
        this.supportsSenderId = value;
    }

    public void setSupportsBalanceQuery(boolean value) {
        this.supportsBalanceQuery = value;
    }

    public void recordDeliveryReport(String providerMessageId, SmsDeliveryStatus status) {
        this.deliveryReports.put(providerMessageId, status);
    }

    public void reset() {
        this.defaultScenario = MockScenario.SUCCESS;
        this.phoneScenarios.clear();
        this.deliveryReports.clear();
        this.invocationCount.set(0);
        this.supportsDeliveryReceipts = true;
        this.supportsStatusLookup = true;
        this.supportsProviderIdempotency = true;
        this.supportsSenderId = true;
        this.supportsBalanceQuery = true;
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    @Override
    public SmsSendResult sendTransactionalSms(SmsSendRequest request) {
        invocationCount.incrementAndGet();
        MockScenario scenario = phoneScenarios.getOrDefault(request.recipientPhone(), defaultScenario);

        log.info("[MOCK-SMS-PROVIDER] Executing scenario {} for recipient {}", scenario, request.recipientPhone());

        switch (scenario) {
            case TIMEOUT_BEFORE_SEND -> {
                return SmsSendResult.failed(
                        "TIMEOUT_CONNECT",
                        "Connection timed out before request dispatch",
                        FailureClassification.PROVIDER_NETWORK_FAILURE
                );
            }
            case TIMEOUT_AFTER_SEND -> {
                return SmsSendResult.unknown(
                        "READ_TIMEOUT",
                        "Read timed out waiting for provider response"
                );
            }
            case HTTP_400 -> {
                return SmsSendResult.failed(
                        "INVALID_REQUEST",
                        "Provider rejected payload: HTTP 400 Bad Request",
                        FailureClassification.MESSAGE_FAILURE
                );
            }
            case HTTP_401 -> {
                return SmsSendResult.failed(
                        "AUTH_FAILED",
                        "Provider rejected token: HTTP 401 Unauthorized",
                        FailureClassification.PROVIDER_CONFIGURATION_FAILURE
                );
            }
            case HTTP_403 -> {
                return SmsSendResult.failed(
                        "FORBIDDEN",
                        "Account suspended or IP not allowlisted: HTTP 403 Forbidden",
                        FailureClassification.PROVIDER_CONFIGURATION_FAILURE
                );
            }
            case HTTP_429 -> {
                return SmsSendResult.failed(
                        "RATE_LIMITED",
                        "Gateway rate limit reached: HTTP 429",
                        FailureClassification.PROVIDER_CAPACITY_FAILURE
                );
            }
            case HTTP_500 -> {
                return SmsSendResult.failed(
                        "SERVER_ERROR",
                        "Provider internal error: HTTP 500",
                        FailureClassification.PROVIDER_NETWORK_FAILURE
                );
            }
            case INVALID_PHONE -> {
                return SmsSendResult.failed(
                        "INVALID_PHONE",
                        "Destination MSISDN unreachable or unallocated",
                        FailureClassification.MESSAGE_FAILURE
                );
            }
            case INSUFFICIENT_BALANCE -> {
                return SmsSendResult.failed(
                        "INSUFFICIENT_BALANCE",
                        "Tenant/platform SMS credit exhausted",
                        FailureClassification.PROVIDER_CAPACITY_FAILURE
                );
            }
            case DUPLICATE_CALLBACK -> {
                String msgId = "MOCK-GEEZ-DUP-" + UUID.randomUUID();
                deliveryReports.put(msgId, new SmsDeliveryStatus(msgId, true, "DELIVRD", "DELIVRD", Instant.now()));
                return SmsSendResult.accepted(msgId);
            }
            case DELIVERY_SUCCESS -> {
                String msgId = "MOCK-GEEZ-SUCC-" + UUID.randomUUID();
                deliveryReports.put(msgId, new SmsDeliveryStatus(msgId, true, "DELIVRD", "DELIVRD", Instant.now()));
                return SmsSendResult.accepted(msgId);
            }
            case DELIVERY_FAILURE -> {
                String msgId = "MOCK-GEEZ-FAIL-" + UUID.randomUUID();
                deliveryReports.put(msgId, new SmsDeliveryStatus(msgId, false, "UNDELIV", "ABSENT_SUBSCRIBER", null));
                return SmsSendResult.accepted(msgId);
            }
            case NO_DLR -> {
                String msgId = "MOCK-GEEZ-NODLR-" + UUID.randomUUID();
                return SmsSendResult.accepted(msgId);
            }
            case STATUS_UNKNOWN -> {
                String msgId = "MOCK-GEEZ-UNK-" + UUID.randomUUID();
                return SmsSendResult.accepted(msgId);
            }
            default -> {
                String msgId = "MOCK-GEEZ-" + UUID.randomUUID();
                deliveryReports.put(msgId, new SmsDeliveryStatus(msgId, true, "DELIVRD", "DELIVRD", Instant.now()));
                return SmsSendResult.accepted(msgId);
            }
        }
    }

    @Override
    public Optional<SmsDeliveryStatus> getDeliveryStatus(String providerMessageId) {
        if (!supportsStatusLookup) {
            return Optional.empty();
        }
        return Optional.ofNullable(deliveryReports.get(providerMessageId));
    }

    @Override
    public Optional<SmsBalanceResult> checkBalance() {
        if (!supportsBalanceQuery) {
            return Optional.empty();
        }
        if (defaultScenario == MockScenario.INSUFFICIENT_BALANCE) {
            return Optional.of(new SmsBalanceResult(BigDecimal.ZERO, "ETB", 0));
        }
        return Optional.of(new SmsBalanceResult(new BigDecimal("5000.00"), "ETB", 25000));
    }

    @Override
    public String getProviderName() {
        return "Mock GeezSMS Provider";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean supportsDeliveryReceipts() {
        return supportsDeliveryReceipts;
    }

    @Override
    public boolean supportsStatusLookup() {
        return supportsStatusLookup;
    }

    @Override
    public boolean supportsProviderIdempotency() {
        return supportsProviderIdempotency;
    }

    @Override
    public boolean supportsSenderId() {
        return supportsSenderId;
    }

    @Override
    public boolean supportsBalanceQuery() {
        return supportsBalanceQuery;
    }
}
