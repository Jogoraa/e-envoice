package et.ut.einvoice.tenancy.config.domain;

/**
 * Safety classification for platform and tenant configuration properties.
 * In accordance with Directive No. 1142/2026 and Multi-Tenant Security Standards:
 * - IMMUTABLE_REGULATORY controls CANNOT be disabled or overridden by any tenant.
 * - SYSTEM_ONLY controls are platform-level and cannot be modified via tenant APIs.
 * - TENANT_ADMIN_ONLY requires elevated tenant administrative privileges.
 * - TENANT_OVERRIDABLE / TENANT_RUNTIME can be safely configured per tenant.
 */
public enum ConfigSafetyClassification {
    SYSTEM_ONLY,
    TENANT_OVERRIDABLE,
    TENANT_RUNTIME,
    TENANT_ADMIN_ONLY,
    IMMUTABLE_REGULATORY
}
