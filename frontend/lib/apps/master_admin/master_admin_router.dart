import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/authentication/multi_gateway_session.dart';
import '../../features/master_admin/presentation/government_gateway_monitor_screen.dart';
import '../../features/master_admin/presentation/master_admin_dashboard_screen.dart';
import '../../features/master_admin/presentation/master_admin_login_screen.dart';
import '../../features/master_admin/presentation/master_admin_shell.dart';
import '../../features/master_admin/presentation/platform_config_screen.dart';
import '../../features/master_admin/presentation/security_audit_screen.dart';
import '../../features/master_admin/presentation/tenant_oversight_screen.dart';
import '../../features/master_admin/presentation/master_api_management_screen.dart';
import '../../features/master_admin/presentation/deployment_readiness_screen.dart';

final masterAdminRouterProvider = Provider<GoRouter>((ref) {
  final session = ref.watch(masterAdminSessionProvider);

  return GoRouter(
    initialLocation: session != null ? '/admin/dashboard' : '/admin/login',
    routes: [
      GoRoute(
        path: '/admin/login',
        builder: (context, state) => const MasterAdminLoginScreen(),
      ),
      ShellRoute(
        builder: (context, state, child) => MasterAdminShell(child: child),
        routes: [
          GoRoute(
            path: '/admin/dashboard',
            builder: (context, state) => const MasterAdminDashboardScreen(),
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
        ],
      ),
    ],
  );
});
