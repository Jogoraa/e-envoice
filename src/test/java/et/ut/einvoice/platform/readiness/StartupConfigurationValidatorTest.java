package et.ut.einvoice.platform.readiness;

import et.ut.einvoice.compliance.crypto.DigitalSignatureProvider;
import et.ut.einvoice.notifications.provider.EmailProvider;
import et.ut.einvoice.notifications.provider.SmsProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class StartupConfigurationValidatorTest {

    @Test
    @DisplayName("Startup Validator: Evaluates all subsystems and identifies readiness states without secret leakage")
    void test_StartupValidator_EvaluatesSubsystemsSafely() throws Exception {
        DataSource mockDataSource = Mockito.mock(DataSource.class);
        Connection mockConnection = Mockito.mock(Connection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(2)).thenReturn(true);

        JdbcTemplate mockJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(mockJdbcTemplate.queryForObject(Mockito.anyString(), Mockito.eq(Integer.class))).thenReturn(42);

        DigitalSignatureProvider mockSignatureProvider = Mockito.mock(DigitalSignatureProvider.class);
        when(mockSignatureProvider.getProviderName()).thenReturn("Software Test Provider");
        when(mockSignatureProvider.isHsmBacked()).thenReturn(false);

        SmsProvider mockSmsProvider = Mockito.mock(SmsProvider.class);
        when(mockSmsProvider.isConfigured()).thenReturn(false);

        EmailProvider mockEmailProvider = Mockito.mock(EmailProvider.class);
        when(mockEmailProvider.isConfigured()).thenReturn(false);

        Environment mockEnvironment = Mockito.mock(Environment.class);
        when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"test"});
        when(mockEnvironment.getProperty("spring.data.redis.host", "localhost")).thenReturn("localhost");
        when(mockEnvironment.getProperty("spring.data.redis.port", "6379")).thenReturn("6379");

        StartupConfigurationValidator validator = new StartupConfigurationValidator(
                mockDataSource, mockJdbcTemplate, mockSignatureProvider, mockSmsProvider, mockEmailProvider, mockEnvironment
        );

        Map<String, StartupConfigurationValidator.DependencyStatus> report = validator.evaluateDependencies();

        assertNotNull(report);
        assertTrue(report.containsKey("PostgreSQL"));
        assertTrue(report.containsKey("Redis"));
        assertTrue(report.containsKey("MoR_EIRS"));
        assertTrue(report.containsKey("Cryptographic_Engine"));
        assertTrue(report.containsKey("SMS_Gateway"));
        assertTrue(report.containsKey("SMTP_Gateway"));
        assertTrue(report.containsKey("JWT_Keys"));
        assertTrue(report.containsKey("Monitoring"));

        // PostgreSQL is reachable and ready, and marked startup-critical
        assertEquals(StartupConfigurationValidator.DependencyState.READY, report.get("PostgreSQL").state());
        assertTrue(report.get("PostgreSQL").reachable());
        assertTrue(report.get("PostgreSQL").startupCritical());

        // SMS & SMTP report DEGRADED in dev/test simulation, and are runtime-degradable (not startup-critical)
        assertEquals(StartupConfigurationValidator.DependencyState.DEGRADED, report.get("SMS_Gateway").state());
        assertFalse(report.get("SMS_Gateway").startupCritical());
        assertEquals(StartupConfigurationValidator.DependencyState.DEGRADED, report.get("SMTP_Gateway").state());
        assertFalse(report.get("SMTP_Gateway").startupCritical());

        // MoR EIRS is runtime-degradable (outbox pattern guarantees persistence)
        assertFalse(report.get("MoR_EIRS").startupCritical());

        // Cryptographic engine and JWT keys are startup-critical
        assertTrue(report.get("Cryptographic_Engine").startupCritical());
        assertTrue(report.get("JWT_Keys").startupCritical());

        // Validate no secret details exposed
        report.values().forEach(status -> {
            assertFalse(status.safeDetail().contains("password"));
            assertFalse(status.safeDetail().contains("BEGIN PRIVATE KEY"));
        });
    }
}
