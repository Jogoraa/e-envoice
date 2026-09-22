/// Multi-Gateway Configuration for UT Electronic Invoicing SaaS
/// Enforces strict architectural separation across the three operational trust domains.
///
/// IMPORTANT: Dio concatenates baseUrl + path rather than using RFC 3986 URI resolution.
/// All base URLs must end at the host root (no path prefix) because every caller already
/// supplies the full path (e.g. /api/v1/saas/telemetry). Adding a path prefix here causes
/// double-prefixing: /api/v1/tenant + /api/v1/saas/... = /api/v1/tenant/api/v1/saas/...
class GatewayConfig {
  /// Base URL for the Tenant Client API (Taxpayer operations)
  static const String tenantApiBaseUrl = String.fromEnvironment(
    'TENANT_API_BASE_URL',
    defaultValue: 'http://localhost:8081/api/v1/tenant',
  );

  static const String activeBackendHost = 'http://localhost:8081';

  /// Base URL for the UT Master Admin API (Platform, regulatory, security oversight)
  static const String masterAdminApiBaseUrl = String.fromEnvironment(
    'MASTER_ADMIN_API_BASE_URL',
    defaultValue: 'http://localhost:8081/api/v1/master',
  );

  /// Base URL for the UT SaaS Management API (Commercial lifecycle, onboarding, billing)
  static const String saasManagementApiBaseUrl = String.fromEnvironment(
    'SAAS_MANAGEMENT_API_BASE_URL',
    defaultValue: 'http://localhost:8081/api/v1/saas',
  );

  static const Duration connectTimeout = Duration(seconds: 10);
  static const Duration receiveTimeout = Duration(seconds: 15);
}
