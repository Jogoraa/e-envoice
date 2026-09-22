package et.ut.einvoice.notifications.provider;

import et.ut.einvoice.notifications.domain.FailureClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockGeezSmsProviderTest {

    private MockGeezSmsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockGeezSmsProvider();
        provider.reset();
    }

    @Test
    @DisplayName("Executes SUCCESS scenario returning accepted provider message ID")
    void testSuccessScenario() {
        provider.setScenario(MockGeezSmsProvider.MockScenario.SUCCESS);
        var result = provider.sendTransactionalSms(new SmsSendRequest("251911234567", "Test message", "MoR", "corr-1"));

        assertTrue(result.success());
        assertNotNull(result.providerMessageId());
        assertTrue(result.providerMessageId().startsWith("MOCK-GEEZ-"));

        var status = provider.getDeliveryStatus(result.providerMessageId());
        assertTrue(status.isPresent());
        assertTrue(status.get().delivered());
    }

    @Test
    @DisplayName("Executes TIMEOUT_AFTER_SEND scenario returning PROVIDER_UNKNOWN_OUTCOME")
    void testTimeoutAfterSend() {
        provider.setScenario(MockGeezSmsProvider.MockScenario.TIMEOUT_AFTER_SEND);
        var result = provider.sendTransactionalSms(new SmsSendRequest("251911234567", "Test message", "MoR", "corr-2"));

        assertFalse(result.success());
        assertEquals(FailureClassification.PROVIDER_UNKNOWN_OUTCOME, result.failureClassification());
        assertEquals("READ_TIMEOUT", result.errorCode());
    }

    @Test
    @DisplayName("Executes HTTP_401 unauthorized scenario returning PROVIDER_CONFIGURATION_FAILURE")
    void testHttp401Scenario() {
        provider.setScenario(MockGeezSmsProvider.MockScenario.HTTP_401);
        var result = provider.sendTransactionalSms(new SmsSendRequest("251911234567", "Test message", "MoR", "corr-3"));

        assertFalse(result.success());
        assertEquals(FailureClassification.PROVIDER_CONFIGURATION_FAILURE, result.failureClassification());
    }

    @Test
    @DisplayName("Executes INSUFFICIENT_BALANCE scenario returning PROVIDER_CAPACITY_FAILURE")
    void testInsufficientBalanceScenario() {
        provider.setScenario(MockGeezSmsProvider.MockScenario.INSUFFICIENT_BALANCE);
        var result = provider.sendTransactionalSms(new SmsSendRequest("251911234567", "Test message", "MoR", "corr-4"));

        assertFalse(result.success());
        assertEquals(FailureClassification.PROVIDER_CAPACITY_FAILURE, result.failureClassification());
        assertEquals("INSUFFICIENT_BALANCE", result.errorCode());

        var balance = provider.checkBalance();
        assertTrue(balance.isPresent());
        assertEquals(0, balance.get().remainingUnits());
    }

    @Test
    @DisplayName("Executes INVALID_PHONE scenario returning MESSAGE_FAILURE")
    void testInvalidPhoneScenario() {
        provider.setScenario(MockGeezSmsProvider.MockScenario.INVALID_PHONE);
        var result = provider.sendTransactionalSms(new SmsSendRequest("251911234567", "Test message", "MoR", "corr-5"));

        assertFalse(result.success());
        assertEquals(FailureClassification.MESSAGE_FAILURE, result.failureClassification());
    }
}
