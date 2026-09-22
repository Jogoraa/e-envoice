package et.ut.einvoice.notifications.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpEmailProviderTest {

    @Test
    @DisplayName("1. Unconfigured provider in dev profile operates in simulation mode with accepted status")
    void testDevSimulationMode() {
        SmtpEmailProvider provider = new SmtpEmailProvider("", 587, "", "", "no-reply@ut-invoice.et", "dev");

        assertThat(provider.isConfigured()).isFalse();
        var result = provider.sendEmailWithResult("buyer@example.com", "Test Subject", "Test Body");

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.providerMessageId()).startsWith("DEV-SIM-");
    }

    @Test
    @DisplayName("2. Unconfigured provider in prod profile fails closed (NOT_CONFIGURED)")
    void testProdFailClosedWhenUnconfigured() {
        SmtpEmailProvider provider = new SmtpEmailProvider("", 587, "", "", "no-reply@ut-invoice.et", "prod");

        assertThat(provider.isConfigured()).isFalse();
        var result = provider.sendEmailWithResult("buyer@example.com", "Test Subject", "Test Body");

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.message()).contains("not configured in production");
    }

    @Test
    @DisplayName("3. Configured provider initializes with host, port, fromAddress and masked username")
    void testConfiguredMetadataAndMasking() {
        SmtpEmailProvider provider = new SmtpEmailProvider(
                "mail.utsolutionsplc.com", 587, "smtp.admin@utsolutionsplc.com", "SecretPass123", "no-reply@ut-invoice.et", "prod"
        );

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.getHost()).isEqualTo("mail.utsolutionsplc.com");
        assertThat(provider.getPort()).isEqualTo(587);
        assertThat(provider.getFromEmail()).isEqualTo("no-reply@ut-invoice.et");
        assertThat(provider.getMaskedUsername()).isEqualTo("sm***@utsolutionsplc.com");
    }

    @Test
    @DisplayName("4. Connection check on unconfigured provider returns NOT_CONFIGURED without connecting")
    void testConnectionCheckUnconfigured() {
        SmtpEmailProvider provider = new SmtpEmailProvider("", 587, "", "", "no-reply@ut-invoice.et", "dev");

        var check = provider.checkConnection();
        assertThat(check.success()).isFalse();
        assertThat(check.status()).isEqualTo("NOT_CONFIGURED");
    }
}
