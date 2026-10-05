import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ut_einvoice_client/app/router/app_router.dart';
import 'package:ut_einvoice_client/apps/master_admin/master_admin_router.dart';
import 'package:ut_einvoice_client/apps/saas_management/saas_router.dart';
import 'package:ut_einvoice_client/core/authentication/multi_gateway_session.dart';
import 'package:ut_einvoice_client/core/routing/app_routes.dart';
import 'package:ut_einvoice_client/core/routing/app_surface.dart';
import 'package:ut_einvoice_client/core/routing/route_recovery_service.dart';
import 'package:ut_einvoice_client/core/routing/route_recovery_state.dart';
import 'package:ut_einvoice_client/domain/auth/models/auth_session.dart';

void main() {
  group('Phase 9 & 18: Master Admin Route Audit & Resolution', () {
    const masterAdminRoutes = [
      AppRoutes.adminDashboard,
      AppRoutes.adminAccountSettings,
      AppRoutes.adminUsers,
      AppRoutes.adminRoles,
      AppRoutes.adminRolesPermissions,
      AppRoutes.adminAccessReviews,
      AppRoutes.adminSessions,
      AppRoutes.adminTenants,
      AppRoutes.adminApiManagement,
      AppRoutes.adminReadiness,
      AppRoutes.adminGateway,
      AppRoutes.adminAudit,
      AppRoutes.adminConfig,
      AppRoutes.adminEnvironment,
      AppRoutes.adminEnvironmentAlias,
      AppRoutes.adminComplianceGovernance,
      AppRoutes.adminDirectiveCompliance,
      AppRoutes.adminComplianceEvidence,
      AppRoutes.adminAuthorityInvestigation,
      AppRoutes.adminProviderExit,
      AppRoutes.adminSystemIntegrity,
      AppRoutes.adminSecurity,
      AppRoutes.adminGovernmentIntegration,
      AppRoutes.adminReconciliation,
      AppRoutes.adminHsmHealth,
      AppRoutes.adminNotificationProviders,
      AppRoutes.adminSystemHealth,
    ];

    test('All Master Admin routes are registered and match in masterAdminRouterProvider', () {
      final container = ProviderContainer(
        overrides: [
          masterAdminSessionProvider.overrideWith(
            (ref) => MasterAdminSessionNotifier(
              null,
              const MasterAdminSession(
                adminId: 'MA-01',
                username: 'platform.admin',
                email: 'platform.admin@utsolutionsplc.com',
                roles: {'ROLE_PLATFORM_ADMIN'},
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final router = container.read(masterAdminRouterProvider);

      for (final route in masterAdminRoutes) {
        final matches = router.configuration.findMatch(Uri.parse(route));
        expect(
          matches.isEmpty,
          isFalse,
          reason: 'Master Admin route "$route" failed to match in masterAdminRouterProvider. Causes GoException: no routes for location: $route',
        );
      }
    });

    test('All Master Admin routes are also registered and match in appRouterProvider', () {
      final container = ProviderContainer(
        overrides: [
          masterAdminSessionProvider.overrideWith(
            (ref) => MasterAdminSessionNotifier(
              null,
              const MasterAdminSession(
                adminId: 'MA-01',
                username: 'platform.admin',
                email: 'platform.admin@utsolutionsplc.com',
                roles: {'ROLE_PLATFORM_ADMIN'},
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final router = container.read(appRouterProvider);

      for (final route in masterAdminRoutes) {
        final matches = router.configuration.findMatch(Uri.parse(route));
        expect(
          matches.isEmpty,
          isFalse,
          reason: 'Master Admin route "$route" failed to match in appRouterProvider',
        );
      }
    });
  });

  group('Phase 9 & 19: Commercial SaaS Route Audit & Resolution', () {
    const saasRoutes = [
      AppRoutes.saasDashboard,
      AppRoutes.saasOnboarding,
      AppRoutes.saasOnboardingWizard,
      AppRoutes.saasTenants,
      AppRoutes.saasSubscriptions,
      AppRoutes.saasUsage,
      AppRoutes.saasReports,
      AppRoutes.saasSupport,
      AppRoutes.saasLifecycleNotifications,
      AppRoutes.saasMarketplace,
      AppRoutes.saasTenantExitOversight,
      AppRoutes.saasMerchantsAlias,
      AppRoutes.saasExitOversightAlias,
      AppRoutes.saasProviderTiers,
    ];

    test('All SaaS Management routes are registered and match in saasRouterProvider', () {
      final container = ProviderContainer(
        overrides: [
          saasSessionProvider.overrideWith(
            (ref) => SaasManagementSessionNotifier(
              null,
              const SaasManagementSession(
                operatorId: 'SAAS-01',
                username: 'saas.commercial',
                email: 'operations@utsolutionsplc.com',
                roles: {'ROLE_SAAS_ADMIN'},
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final router = container.read(saasRouterProvider);

      for (final route in saasRoutes) {
        final matches = router.configuration.findMatch(Uri.parse(route));
        expect(
          matches.isEmpty,
          isFalse,
          reason: 'SaaS route "$route" failed to match in saasRouterProvider',
        );
      }
    });

    test('All SaaS Management routes are registered and match in appRouterProvider', () {
      final container = ProviderContainer(
        overrides: [
          saasSessionProvider.overrideWith(
            (ref) => SaasManagementSessionNotifier(
              null,
              const SaasManagementSession(
                operatorId: 'SAAS-01',
                username: 'saas.commercial',
                email: 'operations@utsolutionsplc.com',
                roles: {'ROLE_SAAS_ADMIN'},
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final router = container.read(appRouterProvider);

      for (final route in saasRoutes) {
        final matches = router.configuration.findMatch(Uri.parse(route));
        expect(
          matches.isEmpty,
          isFalse,
          reason: 'SaaS route "$route" failed to match in appRouterProvider',
        );
      }
    });
  });

  group('Phase 9 & 20: Tenant Client Route Audit & Resolution', () {
    const tenantRoutes = [
      AppRoutes.tenantDashboard,
      AppRoutes.invoices,
      AppRoutes.invoiceNew,
      AppRoutes.taxAdjustments,
      AppRoutes.productCatalog,
      AppRoutes.serviceCatalog,
      AppRoutes.categoryManagement,
      AppRoutes.branchInventory,
      AppRoutes.customerRegistry,
      AppRoutes.cashReceipts,
      AppRoutes.purchaseVouchers,
      AppRoutes.withholdingReceipts,
      AppRoutes.creditSettlements,
      AppRoutes.cancellations,
      AppRoutes.offlineOperations,
      AppRoutes.manualReconciliation,
      AppRoutes.deviceCompliance,
      AppRoutes.governmentCredentials,
      AppRoutes.signatureHealth,
      AppRoutes.tenantExit,
      AppRoutes.retentionSchedule,
      AppRoutes.exemptReporting,
      AppRoutes.offlineQueue,
      AppRoutes.governmentStatus,
      AppRoutes.reports,
      AppRoutes.audit,
      AppRoutes.settings,
      // Common Aliases
      AppRoutes.tenantCashReceiptsAlias,
      AppRoutes.tenantPurchaseVouchersAlias,
      AppRoutes.tenantWithholdingAlias,
      AppRoutes.tenantCreditSettlementAlias,
      AppRoutes.tenantCancellationsAlias,
      AppRoutes.tenantOfflineAlias,
      AppRoutes.tenantDevicesAlias,
      AppRoutes.tenantMposAlias,
      AppRoutes.tenantExitAlias,
      AppRoutes.tenantRetentionAlias,
    ];

    test('All Tenant Client routes and aliases are registered and match in appRouterProvider', () {
      final container = ProviderContainer(
        overrides: [
          authSessionProvider.overrideWith(
            (ref) => AuthSessionNotifier(
              null,
              AuthSession(
                userId: 'user-01',
                username: 'tenant.admin',
                tenantId: 'TENANT-001',
                roles: const {'ROLE_TENANT_ADMIN'},
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final router = container.read(appRouterProvider);

      for (final route in tenantRoutes) {
        final matches = router.configuration.findMatch(Uri.parse(route));
        expect(
          matches.isEmpty,
          isFalse,
          reason: 'Tenant route "$route" failed to match in appRouterProvider',
        );
      }
    });

    test('Dynamic route with parameters matches (/invoices/:id)', () {
      final container = ProviderContainer(
        overrides: [
          authSessionProvider.overrideWith(
            (ref) => AuthSessionNotifier(
              null,
              AuthSession(
                userId: 'user-01',
                username: 'tenant.admin',
                tenantId: 'TENANT-001',
                roles: const {'ROLE_TENANT_ADMIN'},
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final router = container.read(appRouterProvider);
      final matches = router.configuration.findMatch(Uri.parse('/invoices/inv-123456'));
      expect(matches.isEmpty, isFalse);
    });
  });

  group('Phase 10: App-Boundary Security & Realm Isolation', () {
    const recoveryService = RouteRecoveryService();

    test('Tenant attempting to access Master Admin routes is restricted (403)', () {
      final tenantSession = AuthSession(
        userId: 'taxpayer-1',
        username: 'taxpayer.operator',
        tenantId: 'TENANT-01',
        roles: const {'ROLE_TENANT_ADMIN'},
      );

      final state = recoveryService.resolveRecoveryState(
        rawAttemptedPath: '/admin/system-integrity',
        tenantSession: tenantSession,
      );

      expect(state.appSurface, equals(AppSurface.tenantClient));
      expect(state.isCrossAppViolation, isTrue);
      expect(state.failureType, equals(RouteFailureType.accessDenied));
      expect(state.shouldHideAttemptedPath, isTrue);
      expect(state.safeHomeRoute, equals('/dashboard'));
    });

    test('Tenant attempting to access SaaS Management routes is restricted (403)', () {
      final tenantSession = AuthSession(
        userId: 'taxpayer-1',
        username: 'taxpayer.operator',
        tenantId: 'TENANT-01',
        roles: const {'ROLE_TENANT_ADMIN'},
      );

      final state = recoveryService.resolveRecoveryState(
        rawAttemptedPath: '/saas/onboarding',
        tenantSession: tenantSession,
      );

      expect(state.appSurface, equals(AppSurface.tenantClient));
      expect(state.isCrossAppViolation, isTrue);
      expect(state.failureType, equals(RouteFailureType.accessDenied));
      expect(state.safeHomeRoute, equals('/dashboard'));
    });

    test('SaaS Operator attempting to access Master Admin routes is restricted (403)', () {
      const saasSession = SaasManagementSession(
        operatorId: 'SAAS-01',
        username: 'saas.commercial',
        email: 'commercial@ut-invoice.et',
        roles: {'ROLE_SAAS_ADMIN'},
      );

      final state = recoveryService.resolveRecoveryState(
        rawAttemptedPath: '/admin/readiness',
        saasSession: saasSession,
      );

      expect(state.appSurface, equals(AppSurface.saasManagement));
      expect(state.isCrossAppViolation, isTrue);
      expect(state.failureType, equals(RouteFailureType.accessDenied));
      expect(state.safeHomeRoute, equals('/saas/dashboard'));
    });
  });
}
