package et.ut.einvoice.tenancy.config.domain;

/**
 * Categorization for tenant feature flags.
 * SECURITY_CONTROL and REGULATORY_FEATURE flags cannot be disabled by tenants.
 */
public enum FeatureFlagCategory {
    OPTIONAL_PRODUCT_FEATURE,
    INTEGRATION_FEATURE,
    UI_WORKFLOW_FEATURE,
    BETA_FEATURE,
    REGULATORY_FEATURE,
    SECURITY_CONTROL
}
