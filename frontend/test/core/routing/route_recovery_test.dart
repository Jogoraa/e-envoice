import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:ut_einvoice_client/core/authentication/multi_gateway_session.dart';
import 'package:ut_einvoice_client/core/routing/app_surface.dart';
import 'package:ut_einvoice_client/core/routing/app_routes.dart';
import 'package:ut_einvoice_client/core/routing/route_recovery_state.dart';
import 'package:ut_einvoice_client/core/routing/route_recovery_service.dart';
import 'package:ut_einvoice_client/core/routing/presentation/route_recovery_screen.dart';
import 'package:ut_einvoice_client/domain/auth/models/auth_session.dart';

void main() {
  const service = RouteRecoveryService();

  group('RouteRecoveryService Unit Tests', () {
    test('13. Query parameter sanitization strips tokens and secrets', () {
      final sanitized = service.sanitizePath('/admin/verify?token=SECRET_AUTH_TOKEN&user=admin1&apiKey=KEY_999');
      expect(sanitized.contains('SECRET_AUTH_TOKEN'), isFalse);
      expect(sanitized.contains('KEY_999'), isFalse);
      expect(sanitized, contains('[REDACTED]'));
      expect(sanitized, contains('user=admin1'));
    });

    test('Raw GoException wrapper is cleanly stripped from path', () {
      const raw = 'GoException: no routes for location: /admin/system-integrity';
      final sanitized = service.sanitizePath(raw);
      expect(sanitized, equals('/admin/system-integrity'));
    });

    test('1. Authenticated tenant -> unknown tenant route resolves safe tenant dashboard', () {
      final tenantSession = AuthSession(
        userId: 'taxpayer-1',
        username: 'Abebe Bikila',
        tenantId: 'TENANT-ET-01',
        roles: const {'ROLE_TENANT_ADMIN'},
      );

      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/unknown-tenant-page',
        tenantSession: tenantSession,
      );

      expect(state.appSurface, equals(AppSurface.tenantClient));
      expect(state.authenticated, isTrue);
      expect(state.failureType, equals(RouteFailureType.notFound));
      expect(state.safeHomeRoute, equals('/dashboard'));
      expect(state.safeHomeLabel, equals('Return to Client Dashboard'));
      expect(state.displayTitle, equals('Page unavailable'));
      expect(state.isCrossAppViolation, isFalse);
    });

    test('2. Authenticated tenant -> unknown admin route blocks cross-app exposure', () {
      final tenantSession = AuthSession(
        userId: 'taxpayer-1',
        username: 'Abebe Bikila',
        tenantId: 'TENANT-ET-01',
        roles: const {'ROLE_TENANT_ADMIN'},
      );

      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/admin/system-integrity',
        tenantSession: tenantSession,
      );

      expect(state.appSurface, equals(AppSurface.tenantClient));
      expect(state.isCrossAppViolation, isTrue);
      expect(state.failureType, equals(RouteFailureType.accessDenied));
      expect(state.shouldHideAttemptedPath, isTrue);
      expect(state.safeHomeRoute, equals('/dashboard'));
      expect(state.safeHomeLabel, equals('Return to Client Dashboard'));
      expect(state.displayTitle, equals('Access restricted'));
    });

    test('3. Authenticated SaaS admin -> unknown SaaS route resolves SaaS dashboard', () {
      const saasSession = SaasManagementSession(
        operatorId: 'OP-01',
        username: 'saas.operator',
        email: 'operator@ut-invoice.et',
        roles: {'ROLE_SAAS_ADMIN'},
      );

      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/saas/unknown-billing-feature',
        saasSession: saasSession,
      );

      expect(state.appSurface, equals(AppSurface.saasManagement));
      expect(state.authenticated, isTrue);
      expect(state.failureType, equals(RouteFailureType.notFound));
      expect(state.safeHomeRoute, equals('/saas/dashboard'));
      expect(state.safeHomeLabel, equals('Return to SaaS Dashboard'));
      expect(state.displayTitle, equals('Management page unavailable'));
    });

    test('4. Authenticated SaaS admin -> unknown master route enforces realm boundary', () {
      const saasSession = SaasManagementSession(
        operatorId: 'OP-01',
        username: 'saas.operator',
        email: 'operator@ut-invoice.et',
        roles: {'ROLE_SAAS_OPERATOR'},
      );

      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/admin/environment/keys',
        saasSession: saasSession,
      );

      expect(state.appSurface, equals(AppSurface.saasManagement));
      expect(state.isCrossAppViolation, isTrue);
      expect(state.failureType, equals(RouteFailureType.accessDenied));
      expect(state.safeHomeRoute, equals('/saas/dashboard'));
      expect(state.displayTitle, equals('Access restricted'));
    });

    test('5. Authenticated Master Admin -> unknown admin route resolves Admin dashboard', () {
      const adminSession = MasterAdminSession(
        adminId: 'ADM-01',
        username: 'root.admin',
        email: 'admin@ut-invoice.et',
        roles: {'ROLE_PLATFORM_ADMIN'},
      );

      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/admin/nonexistent-feature',
        masterAdminSession: adminSession,
      );

      expect(state.appSurface, equals(AppSurface.masterAdmin));
      expect(state.authenticated, isTrue);
      expect(state.failureType, equals(RouteFailureType.notFound));
      expect(state.safeHomeRoute, equals('/admin/dashboard'));
      expect(state.safeHomeLabel, equals('Return to Admin Dashboard'));
      expect(state.displayTitle, equals('Admin page unavailable'));
    });

    test('6. Unauthenticated -> unknown route resolves login fallback', () {
      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/invoices/secret-view',
      );

      expect(state.authenticated, isFalse);
      expect(state.failureType, equals(RouteFailureType.sessionRequired));
      expect(state.safeHomeRoute, equals('/login'));
      expect(state.safeHomeLabel, equals('Sign In to UT Invoice'));
    });

    test('7. Expired session is treated as unauthenticated sessionRequired', () {
      final expiredSession = AuthSession(
        userId: 'taxpayer-1',
        username: 'Expired User',
        tenantId: 'TENANT-ET-01',
        roles: const {'ROLE_TENANT_ADMIN'},
        expiresAt: DateTime.now().subtract(const Duration(hours: 1)),
      );

      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/invoices/12345',
        tenantSession: expiredSession,
      );

      expect(state.authenticated, isFalse);
      expect(state.failureType, equals(RouteFailureType.sessionRequired));
      expect(state.safeHomeRoute, equals('/login'));
    });

    test('8. Role to surface mapping correctly resolves each platform realm', () {
      expect(service.mapRoleToSurface({'ROLE_PLATFORM_ADMIN'}), equals(AppSurface.masterAdmin));
      expect(service.mapRoleToSurface({'ROLE_AUTHORITY_AUDITOR'}), equals(AppSurface.masterAdmin));
      expect(service.mapRoleToSurface({'ROLE_SAAS_ADMIN'}), equals(AppSurface.saasManagement));
      expect(service.mapRoleToSurface({'ROLE_TENANT_ADMIN'}), equals(AppSurface.tenantClient));
      expect(service.mapRoleToSurface({'ROLE_CASHIER'}), equals(AppSurface.tenantClient));
      expect(service.mapRoleToSurface({'ROLE_UNKNOWN_EXTERNAL'}), equals(AppSurface.unknown));
    });

    test('9 & 10. Deep link invalid route preserves surface hint when specified', () {
      final state = service.resolveRecoveryState(
        rawAttemptedPath: '/admin/missing-audit-link',
        surfaceHint: AppSurface.masterAdmin,
      );

      expect(state.appSurface, equals(AppSurface.masterAdmin));
      expect(state.safeHomeRoute, equals('/admin/login'));
    });

    test('11 & 12. AppRoutes constants consistency check', () {
      expect(AppRoutes.isAdminPath('/admin/dashboard'), isTrue);
      expect(AppRoutes.isAdminPath('/master/audit'), isTrue);
      expect(AppRoutes.isAdminPath('/invoices'), isFalse);

      expect(AppRoutes.isSaasPath('/saas/subscriptions'), isTrue);
      expect(AppRoutes.isSaasPath('/dashboard'), isFalse);

      expect(AppRoutes.isPublicPath('/login'), isTrue);
      expect(AppRoutes.isPublicPath('/admin/dashboard'), isFalse);
    });
  });

  group('RouteRecoveryScreen Widget Tests', () {
    testWidgets('Renders premium recovery screen without raw GoException', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));

      const testSession = MasterAdminSession(
        adminId: 'ADM-01',
        username: 'Platform Inspector',
        email: 'inspector@ut.et',
        roles: {'ROLE_PLATFORM_ADMIN'},
      );

      final testRouter = GoRouter(
        initialLocation: '/admin/nonexistent-feature',
        routes: [
          GoRoute(
            path: '/admin/dashboard',
            builder: (context, state) => const Text('Admin Dashboard Target'),
          ),
        ],
        errorBuilder: (context, state) => RouteRecoveryScreen(
          state: state,
          defaultSurface: AppSurface.masterAdmin,
        ),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            masterAdminSessionProvider.overrideWith((ref) => MasterAdminSessionNotifier(null, testSession)),
          ],
          child: MaterialApp.router(
            routerConfig: testRouter,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Verify no raw GoException is shown
      expect(find.textContaining('GoException'), findsNothing);
      expect(find.textContaining('StackTrace'), findsNothing);

      // Verify premium enterprise content is rendered
      expect(find.text('Admin page unavailable'), findsOneWidget);
      expect(find.text('Master Admin'), findsOneWidget);
      expect(find.text('Return to Admin Dashboard'), findsOneWidget);
      expect(find.textContaining('UT Electronic Invoicing'), findsOneWidget);

      // Test tapping Return to Admin Dashboard
      await tester.tap(find.text('Return to Admin Dashboard'));
      await tester.pumpAndSettle();
      expect(find.text('Admin Dashboard Target'), findsOneWidget);
    });
  });
}
