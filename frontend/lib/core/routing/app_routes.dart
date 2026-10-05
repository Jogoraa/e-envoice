/// Standardized Route Constants for UT Electronic Invoicing.
/// Prevents hardcoded path string proliferation across UI widgets.
class AppRoutes {
  AppRoutes._();

  // Root / Public
  static const String root = '/';
  static const String workspace = '/workspace';
  static const String login = '/login';
  static const String tenantLogin = '/tenant/login';
  static const String masterLogin = '/master/login';
  static const String saasLogin = '/saas/login';
  static const String adminLogin = '/admin/login';
  static const String invitationAcceptance = '/auth/invitations/accept';
  static const String publicVerification = '/verify/:irn';

  // ---------------------------------------------------------------------------
  // Surface 1: Tenant Client Routes
  // ---------------------------------------------------------------------------
  static const String tenantDashboard = '/dashboard';
  static const String invoices = '/invoices';
  static const String invoiceNew = '/invoices/new';
  static const String invoiceDetail = '/invoices/:id';
  static const String taxAdjustments = '/adjustments';
  static const String productCatalog = '/catalog/products';
  static const String serviceCatalog = '/catalog/services';
  static const String categoryManagement = '/catalog/categories';
  static const String branchInventory = '/inventory';
  static const String customerRegistry = '/customers';
  static const String cashReceipts = '/receipts/cash';
  static const String purchaseVouchers = '/receipts/purchase-voucher';
  static const String withholdingReceipts = '/receipts/withholding';
  static const String creditSettlements = '/invoices/credit-settlement';
  static const String cancellations = '/invoices/cancellations';
  static const String offlineOperations = '/offline/operations';
  static const String manualReconciliation = '/offline/manual-reconciliation';
  static const String deviceCompliance = '/devices/compliance';
  static const String governmentCredentials = '/government/credentials';
  static const String signatureHealth = '/government/signature-health';
  static const String tenantExit = '/compliance/tenant-exit';
  static const String retentionSchedule = '/compliance/retention';
  static const String exemptReporting = '/compliance/exempt-reporting';
  static const String offlineQueue = '/offline/queue';
  static const String governmentStatus = '/government/status';
  static const String reports = '/reports';
  static const String audit = '/audit';
  static const String settings = '/settings';

  // ---------------------------------------------------------------------------
  // Surface 2: SaaS Management Routes
  // ---------------------------------------------------------------------------
  static const String saasDashboard = '/saas/dashboard';
  static const String saasOnboarding = '/saas/onboarding';
  static const String saasOnboardingWizard = '/saas/onboarding/wizard';
  static const String saasTenants = '/saas/tenants';
  static const String saasSubscriptions = '/saas/subscriptions';
  static const String saasUsage = '/saas/usage';
  static const String saasReports = '/saas/reports';
  static const String saasSupport = '/saas/support';
  static const String saasLifecycleNotifications = '/saas/lifecycle-notifications';
  static const String saasMarketplace = '/saas/marketplace';
  static const String saasTenantExitOversight = '/saas/tenant-exit-oversight';
  static const String saasProviderTiers = '/saas/provider-tiers';

  // ---------------------------------------------------------------------------
  // Surface 3: Master Admin Routes
  // ---------------------------------------------------------------------------
  static const String adminDashboard = '/admin/dashboard';
  static const String adminAccountSettings = '/admin/account/settings';
  static const String adminUsers = '/admin/users';
  static const String adminRoles = '/admin/roles';
  static const String adminRolesPermissions = '/admin/roles-permissions';
  static const String adminAccessReviews = '/admin/access-reviews';
  static const String adminSessions = '/admin/sessions';
  static const String adminTenants = '/admin/tenants';
  static const String adminApiManagement = '/admin/api-management';
  static const String adminReadiness = '/admin/readiness';
  static const String adminGateway = '/admin/gateway';
  static const String adminAudit = '/admin/audit';
  static const String adminConfig = '/admin/config';
  static const String adminEnvironment = '/admin/system/environment';
  static const String adminEnvironmentAlias = '/admin/environment';
  static const String adminComplianceGovernance = '/admin/compliance-governance';
  static const String adminDirectiveCompliance = '/admin/directive-compliance';
  static const String adminAuthorityInvestigation = '/admin/authority-investigation';
  static const String adminProviderExit = '/admin/provider-exit';
  static const String adminSystemIntegrity = '/admin/system-integrity';
  static const String adminSecurity = '/admin/security';
  static const String adminGovernmentIntegration = '/admin/government-integration';
  static const String adminReconciliation = '/admin/reconciliation';
  static const String adminHsmHealth = '/admin/hsm-health';
  static const String adminNotificationProviders = '/admin/notification-providers';
  static const String adminSystemHealth = '/admin/system-health';
  static const String adminComplianceEvidence = '/admin/compliance-evidence';

  // Tenant Aliases
  static const String tenantCashReceiptsAlias = '/cash-receipts';
  static const String tenantPurchaseVouchersAlias = '/purchase-vouchers';
  static const String tenantWithholdingAlias = '/withholding';
  static const String tenantCreditSettlementAlias = '/credit-settlement';
  static const String tenantCancellationsAlias = '/cancellations';
  static const String tenantOfflineAlias = '/offline';
  static const String tenantDevicesAlias = '/devices';
  static const String tenantMposAlias = '/mpos';
  static const String tenantExitAlias = '/tenant-exit';
  static const String tenantRetentionAlias = '/retention';

  // SaaS Aliases
  static const String saasMerchantsAlias = '/saas/merchants';
  static const String saasExitOversightAlias = '/saas/exit-oversight';

  /// Returns true if the path belongs to Master Admin realm
  static bool isAdminPath(String path) {
    final clean = path.split('?').first.toLowerCase();
    return clean.startsWith('/admin') || clean.startsWith('/master');
  }

  /// Returns true if the path belongs to SaaS Management realm
  static bool isSaasPath(String path) {
    final clean = path.split('?').first.toLowerCase();
    return clean.startsWith('/saas');
  }

  /// Returns true if the path is an unauthenticated public route
  static bool isPublicPath(String path) {
    final clean = path.split('?').first.toLowerCase();
    return clean == '/' ||
        clean == '/workspace' ||
        clean == '/login' ||
        clean == '/tenant/login' ||
        clean == '/saas/login' ||
        clean == '/admin/login' ||
        clean == '/master/login' ||
        clean.startsWith('/auth/invitations') ||
        clean.startsWith('/verify');
  }
}
