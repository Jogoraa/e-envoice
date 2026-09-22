import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/authentication/multi_gateway_session.dart';
import '../../features/saas_management/presentation/saas_dashboard_screen.dart';
import '../../features/saas_management/presentation/saas_login_screen.dart';
import '../../features/saas_management/presentation/saas_management_shell.dart';
import '../../features/saas_management/presentation/saas_report_definitions_screen.dart';
import '../../features/saas_management/presentation/subscriptions_screen.dart';
import '../../features/saas_management/presentation/support_diagnostics_screen.dart';
import '../../features/saas_management/presentation/tenant_lifecycle_screen.dart';
import '../../features/saas_management/presentation/tenant_onboarding_wizard.dart';
import '../../features/saas_management/presentation/usage_metering_screen.dart';

final saasRouterProvider = Provider<GoRouter>((ref) {
  final session = ref.watch(saasSessionProvider);

  return GoRouter(
    initialLocation: session != null ? '/saas/dashboard' : '/saas/login',
    routes: [
      GoRoute(
        path: '/saas/login',
        builder: (context, state) => const SaasLoginScreen(),
      ),
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
        ],
      ),
    ],
  );
});
