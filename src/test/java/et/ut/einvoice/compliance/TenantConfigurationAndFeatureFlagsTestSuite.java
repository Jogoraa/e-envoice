package et.ut.einvoice.compliance;

import et.ut.einvoice.audit.domain.AuditEvent;
import et.ut.einvoice.audit.repository.AuditEventRepository;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.tenancy.config.domain.TenantConfigurationOverride;
import et.ut.einvoice.tenancy.config.domain.TenantFeatureFlag;
import et.ut.einvoice.tenancy.config.repository.TenantConfigurationOverrideRepository;
import et.ut.einvoice.tenancy.config.repository.TenantFeatureFlagRepository;
import et.ut.einvoice.tenancy.config.service.TenantConfigurationService;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TenantConfigurationAndFeatureFlagsTestSuite {

    @Autowired
    private TenantConfigurationService configurationService;

    @Autowired
    private TenantConfigurationOverrideRepository configRepository;

    @Autowired
    private TenantFeatureFlagRepository featureFlagRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID suspendedTenant;
    private UUID deactivatedTenant;

    @BeforeEach
    void setUp() {
        configRepository.deleteAll();
        featureFlagRepository.deleteAll();

        String tinA = "11" + UUID.randomUUID().toString().replaceAll("[^0-9]", "1").substring(0, 8);
        String tinB = "22" + UUID.randomUUID().toString().replaceAll("[^0-9]", "2").substring(0, 8);
        String tinSusp = "33" + UUID.randomUUID().toString().replaceAll("[^0-9]", "3").substring(0, 8);
        String tinDeact = "44" + UUID.randomUUID().toString().replaceAll("[^0-9]", "4").substring(0, 8);

        tenantA = UUID.randomUUID();
        Tenant tA = new Tenant(tenantA, "ORG-A", "Tenant Alpha PLC", "Alpha", tinA, "SME");
        tA.activate();
        tenantRepository.save(tA);

        tenantB = UUID.randomUUID();
        Tenant tB = new Tenant(tenantB, "ORG-B", "Tenant Beta PLC", "Beta", tinB, "SME");
        tB.activate();
        tenantRepository.save(tB);

        suspendedTenant = UUID.randomUUID();
        Tenant tSusp = new Tenant(suspendedTenant, "ORG-SUSP", "Suspended PLC", "Susp", tinSusp, "SME");
        tSusp.suspend();
        tenantRepository.save(tSusp);

        deactivatedTenant = UUID.randomUUID();
        Tenant tDeact = new Tenant(deactivatedTenant, "ORG-DEACT", "Deactivated PLC", "Deact", tinDeact, "SME");
        tDeact.deactivate();
        tenantRepository.save(tDeact);

        configurationService.invalidateCache(tenantA);
        configurationService.invalidateCache(tenantB);
    }

    @Test
    @DisplayName("Config 1: Global default resolution when no tenant override exists")
    void test_GlobalDefaultResolution() {
        Map<String, String> config = configurationService.getEffectiveConfiguration(tenantA);
        assertEquals("ETB", config.get("invoice.default.currency"));
        assertEquals("STANDARD_A4", config.get("invoice.pdf.template"));
        assertEquals("true", config.get("audit.enabled"));
        assertEquals("true", config.get("invoice.serverTaxCalculation.enabled"));
    }

    @Test
    @DisplayName("Config 2 & 3: Tenant override takes precedence over global default")
    void test_TenantOverride_TakesPrecedence() {
        configurationService.setTenantOverride(tenantA, "invoice.default.currency", "USD", null, "admin-a");

        String effectiveCurrencyA = configurationService.getEffectiveValue(tenantA, "invoice.default.currency");
        assertEquals("USD", effectiveCurrencyA, "Tenant A override must take precedence");

        String effectiveCurrencyB = configurationService.getEffectiveValue(tenantB, "invoice.default.currency");
        assertEquals("ETB", effectiveCurrencyB, "Tenant B must remain on global default ETB");
    }

    @Test
    @DisplayName("Config 4: Fallback to global default when override is deleted")
    void test_FallbackToGlobalDefault() {
        TenantConfigurationOverride override = configurationService.setTenantOverride(tenantA, "invoice.default.currency", "EUR", null, "admin-a");
        assertEquals("EUR", configurationService.getEffectiveValue(tenantA, "invoice.default.currency"));

        configRepository.delete(override);
        configurationService.invalidateCache(tenantA);

        assertEquals("ETB", configurationService.getEffectiveValue(tenantA, "invoice.default.currency"));
    }

    @Test
    @DisplayName("Config 5: IMMUTABLE_REGULATORY controls cannot be modified or disabled by tenants")
    void test_ProtectedRegulatoryControls_CannotBeOverridden() {
        List<String> protectedKeys = List.of(
                "audit.enabled",
                "tenantIsolation.enabled",
                "invoice.serverTaxCalculation.enabled",
                "invoice.immutability.enabled",
                "offline.signatureVerification.enabled",
                "idempotency.enabled",
                "governmentSubmission.validation.enabled",
                "invoice.rounding.mode"
        );

        for (String key : protectedKeys) {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    configurationService.setTenantOverride(tenantA, key, "false", null, "malicious-admin")
            );
            assertEquals("PROTECTED_REGULATORY_CONTROL", ex.getCode(), "Modifying " + key + " must throw PROTECTED_REGULATORY_CONTROL");
        }
    }

    @Test
    @DisplayName("Config 6: Unknown configuration keys are rejected")
    void test_UnknownConfigKey_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                configurationService.setTenantOverride(tenantA, "invalid.unknown.key", "value", null, "admin")
        );
        assertEquals("UNKNOWN_CONFIGURATION_KEY", ex.getCode());
    }

    @Test
    @DisplayName("Feature 1: Security controls and mandatory regulatory features can NEVER be disabled")
    void test_SecurityControls_CannotBeDisabled() {
        // FEATURE_AUDIT_TRAIL is a SECURITY_CONTROL
        assertTrue(configurationService.isFeatureEnabled(tenantA, "FEATURE_AUDIT_TRAIL"));
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                configurationService.setFeatureFlag(tenantA, "FEATURE_AUDIT_TRAIL", false, null, "admin")
        );
        assertEquals("PROTECTED_REGULATORY_FEATURE", ex1.getCode());

        // FEATURE_OFFLINE_SYNC is a REGULATORY_FEATURE
        assertTrue(configurationService.isFeatureEnabled(tenantA, "FEATURE_OFFLINE_SYNC"));
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                configurationService.setFeatureFlag(tenantA, "FEATURE_OFFLINE_SYNC", false, null, "admin")
        );
        assertEquals("PROTECTED_REGULATORY_FEATURE", ex2.getCode());
    }

    @Test
    @DisplayName("Feature 2: Optional product features can be toggled per tenant")
    void test_OptionalFeatures_CanBeToggledPerTenant() {
        // Global default for FEATURE_CUSTOM_PDF_LAYOUT is false
        assertFalse(configurationService.isFeatureEnabled(tenantA, "FEATURE_CUSTOM_PDF_LAYOUT"));

        configurationService.setFeatureFlag(tenantA, "FEATURE_CUSTOM_PDF_LAYOUT", true, null, "admin-a");
        assertTrue(configurationService.isFeatureEnabled(tenantA, "FEATURE_CUSTOM_PDF_LAYOUT"));

        // Tenant B remains false
        assertFalse(configurationService.isFeatureEnabled(tenantB, "FEATURE_CUSTOM_PDF_LAYOUT"));
    }

    @Test
    @DisplayName("Config Concurrency: Version conflict is detected on concurrent configuration updates")
    void test_Configuration_OptimisticLocking_VersionConflict() {
        TenantConfigurationOverride initial = configurationService.setTenantOverride(
                tenantA, "invoice.payment.terms.days", "45", null, "admin-1"
        );
        Long currentVersion = initial.getVersion();

        // Admin A updates with valid expected version
        configurationService.setTenantOverride(
                tenantA, "invoice.payment.terms.days", "60", currentVersion, "admin-a"
        );

        // Admin B tries to update using stale version
        BusinessException ex = assertThrows(BusinessException.class, () ->
                configurationService.setTenantOverride(tenantA, "invoice.payment.terms.days", "90", currentVersion, "admin-b")
        );
        assertEquals("CONFIGURATION_VERSION_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Audit: Privileged configuration and feature changes generate audit events")
    void test_ConfigAndFeatureChanges_ProduceAuditEvents() {
        long auditCountBefore = auditEventRepository.count();

        configurationService.setTenantOverride(tenantA, "invoice.notification.sms.enabled", "true", null, "auditor-tester");
        configurationService.setFeatureFlag(tenantA, "FEATURE_NOTIFICATIONS", false, null, "auditor-tester");

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertTrue(auditEvents.size() >= auditCountBefore + 2);

        boolean foundConfigAudit = auditEvents.stream().anyMatch(e -> "UPDATE_TENANT_CONFIG".equals(e.getAction()));
        boolean foundFeatureAudit = auditEvents.stream().anyMatch(e -> "UPDATE_FEATURE_FLAG".equals(e.getAction()));

        assertTrue(foundConfigAudit, "Audit trail must record UPDATE_TENANT_CONFIG");
        assertTrue(foundFeatureAudit, "Audit trail must record UPDATE_FEATURE_FLAG");
    }

    @Test
    @DisplayName("Tenant Status: Suspended and deactivated tenants cannot access or modify configuration")
    void test_InactiveTenants_CannotAccessConfiguration() {
        BusinessException exSusp = assertThrows(BusinessException.class, () ->
                configurationService.getEffectiveConfiguration(suspendedTenant)
        );
        assertEquals("TENANT_SUSPENDED", exSusp.getCode());

        BusinessException exDeact = assertThrows(BusinessException.class, () ->
                configurationService.getEffectiveConfiguration(deactivatedTenant)
        );
        assertEquals("TENANT_DEACTIVATED", exDeact.getCode());
    }
}
