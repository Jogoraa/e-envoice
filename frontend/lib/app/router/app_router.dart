import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/authentication/multi_gateway_session.dart';
import '../../features/adjustments/presentation/tax_adjustment_screen.dart';
import '../../features/audit/presentation/audit_screen.dart';
import '../../features/authentication/presentation/login_screen.dart';
import '../../features/authentication/presentation/invitation_acceptance_screen.dart';
import '../../features/public_verification/presentation/public_verification_screen.dart';
import '../../features/catalog/presentation/product_catalog_screen.dart';
import '../../features/catalog/presentation/service_catalog_screen.dart';
import '../../features/catalog/presentation/category_management_screen.dart';
import '../../features/customers/presentation/customer_list_screen.dart';
import '../../features/dashboard/presentation/dashboard_screen.dart';
import '../../features/government/presentation/government_status_screen.dart';
import '../../features/inventory/presentation/inventory_screen.dart';
import '../../features/invoices/presentation/invoice_detail_screen.dart';
import '../../features/invoices/presentation/invoice_list_screen.dart';
import '../../features/invoicing/presentation/invoice_creation_wizard.dart';
import '../../features/master_admin/presentation/government_gateway_monitor_screen.dart';
import '../../features/master_admin/presentation/master_admin_dashboard_screen.dart';
import '../../features/master_admin/presentation/master_admin_login_screen.dart';
import '../../features/master_admin/presentation/master_admin_shell.dart';
import '../../features/master_admin/presentation/platform_config_screen.dart';
import '../../features/master_admin/presentation/security_audit_screen.dart';
import '../../features/master_admin/presentation/tenant_oversight_screen.dart';
import '../../features/master_admin/presentation/master_api_management_screen.dart';
import '../../features/master_admin/presentation/deployment_readiness_screen.dart';
import '../../features/master_admin/presentation/master_account_settings_screen.dart';
import '../../features/master_admin/presentation/master_users_directory_screen.dart';
import '../../features/master_admin/presentation/master_roles_permissions_screen.dart';
import '../../features/master_admin/presentation/master_access_reviews_screen.dart';
import '../../features/master_admin/presentation/master_sessions_devices_screen.dart';
import '../../features/master_admin/presentation/master_environment_secrets_screen.dart';
import '../../features/offline/presentation/offline_queue_screen.dart';
import '../../features/reports/presentation/reports_screen.dart';
import '../../features/saas_management/presentation/saas_dashboard_screen.dart';
import '../../features/saas_management/presentation/saas_login_screen.dart';
import '../../features/saas_management/presentation/saas_management_shell.dart';
import '../../features/saas_management/presentation/saas_report_definitions_screen.dart';
import '../../features/saas_management/presentation/subscriptions_screen.dart';
import '../../features/saas_management/presentation/support_diagnostics_screen.dart';
import '../../features/saas_management/presentation/tenant_lifecycle_screen.dart';
import '../../features/saas_management/presentation/tenant_onboarding_wizard.dart';
import '../../features/saas_management/presentation/usage_metering_screen.dart';
import '../../features/settings/presentation/settings_screen.dart';
import '../../features/shell/presentation/enterprise_shell.dart';
import '../../features/workspace/presentation/workspace_selection_screen.dart';
import '../../features/receipts/presentation/cash_receipt_screen.dart';
import '../../features/receipts/presentation/purchase_voucher_screen.dart';
import '../../features/receipts/presentation/withholding_receipt_screen.dart';
import '../../features/invoices/presentation/credit_settlement_screen.dart';
import '../../features/invoices/presentation/cancellation_management_screen.dart';
import '../../features/offline/presentation/offline_operations_screen.dart';
import '../../features/offline/presentation/manual_invoice_reconciliation_screen.dart';
import '../../features/mpos/presentation/device_compliance_screen.dart';
import '../../features/government/presentation/government_credentials_screen.dart';
import '../../features/government/presentation/signature_health_screen.dart';
import '../../features/compliance/presentation/tenant_exit_screen.dart';
import '../../features/compliance/presentation/retention_schedule_screen.dart';
import '../../features/compliance/presentation/exempt_sector_reporting_screen.dart';
import '../../features/saas_management/presentation/tenant_lifecycle_notifications_screen.dart';
import '../../features/saas_management/presentation/marketplace_management_screen.dart';
import '../../features/saas_management/presentation/saas_tenant_exit_oversight_screen.dart';
import '../../features/saas_management/presentation/provider_tier_dashboard_screen.dart';
import '../../features/master_admin/presentation/authority_investigation_portal_screen.dart';
import '../../features/master_admin/presentation/provider_exit_governance_screen.dart';
import '../../features/master_admin/presentation/system_integrity_screen.dart';
import '../../features/master_admin/presentation/directive_compliance_governance_screen.dart';
import '../../core/routing/app_surface.dart';
import '../../core/routing/presentation/route_recovery_screen.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  final tenantSession = ref.watch(authSessionProvider);
  final saasSession = ref.watch(saasSessionProvider);
  final masterAdminSession = ref.watch(masterAdminSessionProvider);
  final delegatedSession = ref.watch(delegatedTenantSessionProvider);

  String determineInitialLocation() {
    const appSurface = String.fromEnvironment(
      'APP_SURFACE',
      defaultValue: 'desktop',
    );
    if (delegatedSession != null) return '/dashboard';
    if (tenantSession != null) return '/dashboard';
    if (appSurface == 'tenant') return '/login';
    if (saasSession != null) return '/saas/dashboard';
    if (masterAdminSession != null) return '/admin/dashboard';
    if (appSurface == 'saas') return '/saas/login';
    if (appSurface == 'admin') return '/admin/login';
    return '/';
  }

  return GoRouter(
    initialLocation: determineInitialLocation(),
    routes: [
      // 1. Workspace Selection (Root Entrypoint)
      GoRoute(
        path: '/',
        builder: (context, state) {
          const appSurface = String.fromEnvironment(
            'APP_SURFACE',
            defaultValue: 'desktop',
          );
          if (appSurface == 'tenant') {
            return tenantSession != null || delegatedSession != null
                ? const DashboardScreen()
                : const LoginScreen();
          }
          return const WorkspaceSelectionScreen();
        },
      ),
      GoRoute(
        path: '/workspace',
        builder: (context, state) => const WorkspaceSelectionScreen(),
      ),
      GoRoute(
        path: '/auth/invitations/accept',
        builder: (context, state) => InvitationAcceptanceScreen(
          token: state.uri.queryParameters['token'],
        ),
      ),

      // 2. Authentication Entrypoints (Deep linkable & Switchable)
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      GoRoute(
        path: '/tenant/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/master/login',
        builder: (context, state) => const SaasLoginScreen(),
      ),
      GoRoute(
        path: '/saas/login',
        builder: (context, state) => const SaasLoginScreen(),
      ),
      GoRoute(
        path: '/admin/login',
        builder: (context, state) => const MasterAdminLoginScreen(),
      ),

      // Public Unauthenticated QR / IRN Verification Portal (Directive Art. 20(6) & Art. 4(1)(d))
      GoRoute(
        path: '/verify/:irn',
        builder: (context, state) {
          final irn = state.pathParameters['irn'] ?? '';
          return PublicVerificationScreen(irn: irn);
        },
      ),

      // 3. Tenant Operations Shell
      ShellRoute(
        builder: (context, state, child) => EnterpriseShell(child: child),
        routes: [
          GoRoute(
            path: '/dashboard',
            builder: (context, state) => const DashboardScreen(),
          ),
          GoRoute(
            path: '/invoices',
            builder: (context, state) => const InvoiceListScreen(),
          ),
          GoRoute(
            path: '/adjustments',
            builder: (context, state) => const TaxAdjustmentScreen(),
          ),
          GoRoute(
            path: '/invoices/new',
            builder: (context, state) => const InvoiceCreationWizard(),
          ),
          GoRoute(
            path: '/invoices/:id',
            builder: (context, state) {
              final id = state.pathParameters['id'] ?? '';
              return InvoiceDetailScreen(invoiceId: id);
            },
          ),
          GoRoute(
            path: '/catalog/products',
            builder: (context, state) => const ProductCatalogScreen(),
          ),
          GoRoute(
            path: '/catalog/services',
            builder: (context, state) => const ServiceCatalogScreen(),
          ),
          GoRoute(
            path: '/catalog/categories',
            builder: (context, state) => const CategoryManagementScreen(),
          ),
          GoRoute(
            path: '/inventory',
            builder: (context, state) => const InventoryScreen(),
          ),
          GoRoute(
            path: '/customers',
            builder: (context, state) => const CustomerListScreen(),
          ),
          GoRoute(
            path: '/offline/queue',
            builder: (context, state) => const OfflineQueueScreen(),
          ),
          GoRoute(
            path: '/government/status',
            builder: (context, state) => const GovernmentStatusScreen(),
          ),
          GoRoute(
            path: '/reports',
            builder: (context, state) => const ReportsScreen(),
          ),
          GoRoute(
            path: '/audit',
            builder: (context, state) => const AuditScreen(),
          ),
          GoRoute(
            path: '/settings',
            builder: (context, state) => const SettingsScreen(),
          ),
          GoRoute(
            path: '/receipts/cash',
            builder: (context, state) => const CashReceiptScreen(),
          ),
          GoRoute(
            path: '/receipts/purchase-voucher',
            builder: (context, state) => const PurchaseVoucherScreen(),
          ),
          GoRoute(
            path: '/receipts/withholding',
            builder: (context, state) => const WithholdingReceiptScreen(),
          ),
          GoRoute(
            path: '/invoices/credit-settlement',
            builder: (context, state) => const CreditSettlementScreen(),
          ),
          GoRoute(
            path: '/invoices/cancellations',
            builder: (context, state) => const CancellationManagementScreen(),
          ),
          GoRoute(
            path: '/offline/operations',
            builder: (context, state) => const OfflineOperationsScreen(),
          ),
          GoRoute(
            path: '/offline/manual-reconciliation',
            builder: (context, state) => const ManualInvoiceReconciliationScreen(),
          ),
          GoRoute(
            path: '/devices/compliance',
            builder: (context, state) => const DeviceComplianceScreen(),
          ),
          GoRoute(
            path: '/government/credentials',
            builder: (context, state) => const GovernmentCredentialsScreen(),
          ),
          GoRoute(
            path: '/government/signature-health',
            builder: (context, state) => const SignatureHealthScreen(),
          ),
          GoRoute(
            path: '/compliance/tenant-exit',
            builder: (context, state) => const TenantExitScreen(),
          ),
          GoRoute(
            path: '/compliance/retention',
            builder: (context, state) => const RetentionScheduleScreen(),
          ),
          GoRoute(
            path: '/compliance/exempt-reporting',
            builder: (context, state) => const ExemptSectorReportingScreen(),
          ),
          GoRoute(
            path: '/cash-receipts',
            builder: (context, state) => const CashReceiptScreen(),
          ),
          GoRoute(
            path: '/purchase-vouchers',
            builder: (context, state) => const PurchaseVoucherScreen(),
          ),
          GoRoute(
            path: '/withholding',
            builder: (context, state) => const WithholdingReceiptScreen(),
          ),
          GoRoute(
            path: '/credit-settlement',
            builder: (context, state) => const CreditSettlementScreen(),
          ),
          GoRoute(
            path: '/cancellations',
            builder: (context, state) => const CancellationManagementScreen(),
          ),
          GoRoute(
            path: '/offline',
            builder: (context, state) => const OfflineOperationsScreen(),
          ),
          GoRoute(
            path: '/devices',
            builder: (context, state) => const DeviceComplianceScreen(),
          ),
          GoRoute(
            path: '/mpos',
            builder: (context, state) => const DeviceComplianceScreen(),
          ),
          GoRoute(
            path: '/tenant-exit',
            builder: (context, state) => const TenantExitScreen(),
          ),
          GoRoute(
            path: '/retention',
            builder: (context, state) => const RetentionScheduleScreen(),
          ),
        ],
      ),

      // 4. SaaS Master Management Shell
      ShellRoute(
        builder: (context, state, child) => SaasManagementShell(child: child),
        routes: [
          GoRoute(
            path: '/saas/dashboard',
            builder: (context, state) => const SaasDashboardScreen(),
          ),
          GoRoute(
            path: '/saas/onboarding',
            builder: (context, state) => const TenantOnboardingWizard(),
          ),
          GoRoute(
            path: '/saas/tenants',
            builder: (context, state) => const TenantLifecycleScreen(),
          ),
          GoRoute(
            path: '/saas/subscriptions',
            builder: (context, state) => const SubscriptionsScreen(),
          ),
          GoRoute(
            path: '/saas/usage',
            builder: (context, state) => const UsageMeteringScreen(),
          ),
          GoRoute(
            path: '/saas/reports',
            builder: (context, state) => const SaasReportDefinitionsScreen(),
          ),
          GoRoute(
            path: '/saas/support',
            builder: (context, state) => const SupportDiagnosticsScreen(),
          ),
          GoRoute(
            path: '/saas/lifecycle-notifications',
            builder: (context, state) => const TenantLifecycleNotificationsScreen(),
          ),
          GoRoute(
            path: '/saas/marketplace',
            builder: (context, state) => const MarketplaceManagementScreen(),
          ),
          GoRoute(
            path: '/saas/tenant-exit-oversight',
            builder: (context, state) => const SaasTenantExitOversightScreen(),
          ),
          GoRoute(
            path: '/saas/merchants',
            builder: (context, state) => const MarketplaceManagementScreen(),
          ),
          GoRoute(
            path: '/saas/exit-oversight',
            builder: (context, state) => const SaasTenantExitOversightScreen(),
          ),
          GoRoute(
            path: '/saas/provider-tiers',
            builder: (context, state) => const ProviderTierDashboardScreen(),
          ),
          GoRoute(
            path: '/saas/onboarding/wizard',
            builder: (context, state) => const TenantOnboardingWizard(),
          ),
        ],
      ),

      // 5. Master Platform Operations Shell
      ShellRoute(
        builder: (context, state, child) => MasterAdminShell(child: child),
        routes: [
          GoRoute(
            path: '/admin/dashboard',
            builder: (context, state) => const MasterAdminDashboardScreen(),
          ),
          GoRoute(
            path: '/admin/account/settings',
            builder: (context, state) => const MasterAccountSettingsScreen(),
          ),
          GoRoute(
            path: '/admin/users',
            builder: (context, state) => const MasterUsersDirectoryScreen(),
          ),
          GoRoute(
            path: '/admin/roles',
            builder: (context, state) => const MasterRolesPermissionsScreen(),
          ),
          GoRoute(
            path: '/admin/roles-permissions',
            builder: (context, state) => const MasterRolesPermissionsScreen(),
          ),
          GoRoute(
            path: '/admin/access-reviews',
            builder: (context, state) => const MasterAccessReviewsScreen(),
          ),
          GoRoute(
            path: '/admin/sessions',
            builder: (context, state) => const MasterSessionsDevicesScreen(),
          ),
          GoRoute(
            path: '/admin/tenants',
            builder: (context, state) => const TenantOversightScreen(),
          ),
          GoRoute(
            path: '/admin/api-management',
            builder: (context, state) => const MasterApiManagementScreen(),
          ),
          GoRoute(
            path: '/admin/readiness',
            builder: (context, state) => const DeploymentReadinessScreen(),
          ),
          GoRoute(
            path: '/admin/gateway',
            builder: (context, state) => const GovernmentGatewayMonitorScreen(),
          ),
          GoRoute(
            path: '/admin/audit',
            builder: (context, state) => const SecurityAuditScreen(),
          ),
          GoRoute(
            path: '/admin/config',
            builder: (context, state) => const PlatformConfigScreen(),
          ),
          GoRoute(
            path: '/admin/system/environment',
            builder: (context, state) => const MasterEnvironmentSecretsScreen(),
          ),
          GoRoute(
            path: '/admin/environment',
            builder: (context, state) => const MasterEnvironmentSecretsScreen(),
          ),
          GoRoute(
            path: '/admin/compliance-governance',
            builder: (context, state) => const DirectiveComplianceGovernanceScreen(),
          ),
          GoRoute(
            path: '/admin/directive-compliance',
            builder: (context, state) => const DirectiveComplianceGovernanceScreen(),
          ),
          GoRoute(
            path: '/admin/compliance-evidence',
            builder: (context, state) => const DirectiveComplianceGovernanceScreen(),
          ),
          GoRoute(
            path: '/admin/authority-investigation',
            builder: (context, state) => const AuthorityInvestigationPortalScreen(),
          ),
          GoRoute(
            path: '/admin/provider-exit',
            builder: (context, state) => const ProviderExitGovernanceScreen(),
          ),
          GoRoute(
            path: '/admin/system-integrity',
            builder: (context, state) => const SystemIntegrityScreen(),
          ),
          GoRoute(
            path: '/admin/security',
            builder: (context, state) => const PlatformConfigScreen(),
          ),
          GoRoute(
            path: '/admin/government-integration',
            builder: (context, state) => const GovernmentGatewayMonitorScreen(),
          ),
          GoRoute(
            path: '/admin/reconciliation',
            builder: (context, state) => const DeploymentReadinessScreen(),
          ),
          GoRoute(
            path: '/admin/hsm-health',
            builder: (context, state) => const DeploymentReadinessScreen(),
          ),
          GoRoute(
            path: '/admin/notification-providers',
            builder: (context, state) => const MasterApiManagementScreen(),
          ),
          GoRoute(
            path: '/admin/system-health',
            builder: (context, state) => const DeploymentReadinessScreen(),
          ),
        ],
      ),
    ],
    errorBuilder: (context, state) => RouteRecoveryScreen(
      state: state,
      defaultSurface: AppSurface.tenantClient,
    ),
  );
});
