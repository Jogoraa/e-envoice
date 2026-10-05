import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/auth/models/auth_session.dart';
import '../authentication/multi_gateway_session.dart';
import 'app_routes.dart';
import 'app_surface.dart';
import 'route_recovery_state.dart';

/// Central Route Recovery Service for UT Electronic Invoicing.
/// Responsibilities:
/// - Determine active application surface with zero leakage
/// - Sanitize URLs and query parameters (strips tokens/secrets)
/// - Classify route failure (404 Not Found vs 403 Access Denied vs 401 Session Required)
/// - Resolve safe app-specific landing routes
/// - Guard cross-surface security boundaries
class RouteRecoveryService {
  const RouteRecoveryService();

  /// Sensitive parameter keys that must NEVER be logged or displayed in the UI.
  static const Set<String> _sensitiveParamKeys = {
    'token',
    'invitationtoken',
    'invite',
    'secret',
    'code',
    'apikey',
    'api_key',
    'password',
    'jwt',
    'auth',
    'signature',
    'key',
    'session',
    'mfa',
  };

  /// Sanitizes an attempted route path:
  /// - Strips GoException prefix if GoRouter leaked it into message
  /// - Removes or strips sensitive query parameters
  /// - Normalizes path separators
  String sanitizePath(String? rawPath) {
    if (rawPath == null || rawPath.trim().isEmpty) {
      return '/';
    }

    var path = rawPath.trim();

    // Strip raw GoException wrapper if GoRouter stringified it
    if (path.contains('no routes for location:')) {
      final idx = path.indexOf('no routes for location:');
      path = path.substring(idx + 'no routes for location:'.length).trim();
    } else if (path.startsWith('GoException:')) {
      path = path.replaceFirst('GoException:', '').trim();
    }

    // Parse URI safely
    try {
      final uri = Uri.parse(path);
      final cleanPath = uri.path.isEmpty ? '/' : uri.path;

      // Filter query parameters to strip sensitive tokens
      if (uri.hasQuery) {
        final sanitizedParams = <String, String>{};
        uri.queryParameters.forEach((key, value) {
          final lowerKey = key.toLowerCase();
          final isSensitive = _sensitiveParamKeys.any(lowerKey.contains);
          if (isSensitive) {
            sanitizedParams[key] = '[REDACTED]';
          } else {
            sanitizedParams[key] = value;
          }
        });

        final queryStr = sanitizedParams.entries
            .map((e) => '${e.key}=${e.value}')
            .join('&');
        return queryStr.isEmpty ? cleanPath : '$cleanPath?$queryStr';
      }

      return cleanPath;
    } catch (_) {
      // Fallback: strip after question mark
      return path.split('?').first;
    }
  }

  /// Determines the active Application Surface based on strict priority:
  /// 1. Authenticated session / active application realm
  /// 2. Stored active app context (`--dart-define=APP_SURFACE`)
  /// 3. Role / permission claims mapping
  /// 4. Route prefix inference fallback
  /// 5. Unknown
  AppSurface resolveCurrentSurface({
    AuthSession? tenantSession,
    DelegatedTenantSession? delegatedSession,
    SaasManagementSession? saasSession,
    MasterAdminSession? masterAdminSession,
    String? attemptedPath,
    AppSurface? defaultSurfaceHint,
  }) {
    // 1. Authenticated Session / Realm Check
    if (masterAdminSession != null && !masterAdminSession.isExpired) {
      return AppSurface.masterAdmin;
    }
    if (saasSession != null && !saasSession.isExpired) {
      return AppSurface.saasManagement;
    }
    if (delegatedSession != null && !delegatedSession.isExpired) {
      return AppSurface.tenantClient;
    }
    if (tenantSession != null && !tenantSession.isExpired) {
      return AppSurface.tenantClient;
    }

    // 2. Build-time or explicit surface hint
    if (defaultSurfaceHint != null && defaultSurfaceHint != AppSurface.unknown) {
      return defaultSurfaceHint;
    }

    const appSurfaceEnv = String.fromEnvironment('APP_SURFACE', defaultValue: '');
    switch (appSurfaceEnv.toLowerCase()) {
      case 'admin':
        return AppSurface.masterAdmin;
      case 'saas':
        return AppSurface.saasManagement;
      case 'tenant':
        return AppSurface.tenantClient;
    }

    // 3. Route prefix inference fallback
    if (attemptedPath != null && attemptedPath.isNotEmpty) {
      final clean = attemptedPath.split('?').first.toLowerCase();
      if (clean.startsWith('/admin') || clean.startsWith('/master')) {
        return AppSurface.masterAdmin;
      }
      if (clean.startsWith('/saas')) {
        return AppSurface.saasManagement;
      }
      if (clean.startsWith('/dashboard') ||
          clean.startsWith('/invoices') ||
          clean.startsWith('/receipts') ||
          clean.startsWith('/offline') ||
          clean.startsWith('/devices') ||
          clean.startsWith('/government') ||
          clean.startsWith('/compliance') ||
          clean.startsWith('/customers') ||
          clean.startsWith('/inventory') ||
          clean.startsWith('/catalog') ||
          clean.startsWith('/reports') ||
          clean.startsWith('/settings') ||
          clean.startsWith('/audit')) {
        return AppSurface.tenantClient;
      }
    }

    return AppSurface.unknown;
  }

  /// Maps roles to the expected Application Surface.
  AppSurface mapRoleToSurface(Set<String> roles) {
    if (roles.any((r) =>
        r == 'ROLE_PLATFORM_ADMIN' ||
        r == 'ROLE_AUTHORITY_AUDITOR' ||
        r == 'PLATFORM_SUPER_ADMIN' ||
        r == 'PLATFORM_COMPLIANCE_ADMIN' ||
        r == 'PLATFORM_SECURITY_ADMIN')) {
      return AppSurface.masterAdmin;
    }

    if (roles.any((r) =>
        r == 'ROLE_SAAS_ADMIN' ||
        r == 'ROLE_SAAS_OPERATOR' ||
        r == 'SAAS_ADMIN' ||
        r == 'SAAS_SUPPORT' ||
        r == 'SAAS_COMPLIANCE')) {
      return AppSurface.saasManagement;
    }

    if (roles.any((r) =>
        r == 'ROLE_TENANT_ADMIN' ||
        r == 'ROLE_CASHIER' ||
        r == 'ROLE_ACCOUNTANT' ||
        r == 'ROLE_COMPLIANCE_OFFICER' ||
        r == 'TENANT_ADMIN' ||
        r == 'ACCOUNTANT' ||
        r == 'CASHIER' ||
        r == 'COMPLIANCE_OFFICER' ||
        r == 'SALES_USER')) {
      return AppSurface.tenantClient;
    }

    return AppSurface.unknown;
  }

  /// Resolves the safe home landing route for a surface and auth state.
  String resolveSafeHomeRoute(AppSurface surface, {required bool isAuthenticated}) {
    if (!isAuthenticated) {
      return surface.loginRoute;
    }
    return surface.defaultHomeRoute;
  }

  /// Constructs the complete typed RouteRecoveryState for a route failure.
  RouteRecoveryState resolveRecoveryState({
    required String? rawAttemptedPath,
    AuthSession? tenantSession,
    DelegatedTenantSession? delegatedSession,
    SaasManagementSession? saasSession,
    MasterAdminSession? masterAdminSession,
    AppSurface? surfaceHint,
    bool canGoBack = true,
  }) {
    final sanitized = sanitizePath(rawAttemptedPath);
    final isPublic = AppRoutes.isPublicPath(sanitized);

    // Identify user authentication & identity
    final isAuthenticated = (tenantSession != null && !tenantSession.isExpired) ||
        (delegatedSession != null && !delegatedSession.isExpired) ||
        (saasSession != null && !saasSession.isExpired) ||
        (masterAdminSession != null && !masterAdminSession.isExpired);

    String? userRole;
    String? userIdentifier;

    if (masterAdminSession != null && !masterAdminSession.isExpired) {
      userRole = masterAdminSession.roles.firstOrNull ?? 'PLATFORM_ADMIN';
      userIdentifier = masterAdminSession.username;
    } else if (saasSession != null && !saasSession.isExpired) {
      userRole = saasSession.roles.firstOrNull ?? 'SAAS_OPERATOR';
      userIdentifier = saasSession.username;
    } else if (delegatedSession != null && !delegatedSession.isExpired) {
      userRole = 'DELEGATED_OPERATOR';
      userIdentifier = delegatedSession.initiatingMasterUserId;
    } else if (tenantSession != null && !tenantSession.isExpired) {
      userRole = tenantSession.roles.firstOrNull ?? 'TENANT_USER';
      userIdentifier = tenantSession.username;
    }

    // Determine current surface
    final activeSurface = resolveCurrentSurface(
      tenantSession: tenantSession,
      delegatedSession: delegatedSession,
      saasSession: saasSession,
      masterAdminSession: masterAdminSession,
      attemptedPath: sanitized,
      defaultSurfaceHint: surfaceHint,
    );

    // Check for Cross-App Security Boundary Violations:
    // e.g., Tenant user entering /admin/* or /saas/*
    final isTargetAdmin = AppRoutes.isAdminPath(sanitized);
    final isTargetSaas = AppRoutes.isSaasPath(sanitized);

    bool isCrossAppViolation = false;
    if (isAuthenticated) {
      if (activeSurface == AppSurface.tenantClient && (isTargetAdmin || isTargetSaas)) {
        isCrossAppViolation = true;
      } else if (activeSurface == AppSurface.saasManagement && isTargetAdmin) {
        isCrossAppViolation = true;
      }
    }

    // Determine failure classification
    RouteFailureType failureType;
    if (isCrossAppViolation) {
      failureType = RouteFailureType.accessDenied;
    } else if (!isAuthenticated && !isPublic) {
      failureType = RouteFailureType.sessionRequired;
    } else {
      failureType = RouteFailureType.notFound;
    }

    final safeHome = resolveSafeHomeRoute(activeSurface, isAuthenticated: isAuthenticated);
    final safeLabel = isAuthenticated
        ? activeSurface.returnActionLabel
        : 'Sign In to UT Invoice';

    final state = RouteRecoveryState(
      failureType: failureType,
      attemptedPath: sanitized,
      appSurface: activeSurface,
      authenticated: isAuthenticated,
      safeHomeRoute: safeHome,
      safeHomeLabel: safeLabel,
      canGoBack: canGoBack,
      userRole: userRole,
      userIdentifier: userIdentifier,
      isCrossAppViolation: isCrossAppViolation,
    );

    logFailureTelemetry(state);
    return state;
  }

  /// Structured audit and diagnostics logger without exposing sensitive tokens.
  void logFailureTelemetry(RouteRecoveryState state, {Object? error}) {
    if (kDebugMode) {
      debugPrint('[RouteRecovery] Failure: '
          'status=${state.failureType.statusCode} '
          'type=${state.failureType.name} '
          'surface=${state.appSurface.name} '
          'path="${state.attemptedPath}" '
          'auth=${state.authenticated} '
          'safeHome=${state.safeHomeRoute} '
          'crossApp=${state.isCrossAppViolation}');
    }
  }
}

/// Provider for RouteRecoveryService
final routeRecoveryServiceProvider = Provider<RouteRecoveryService>((ref) {
  return const RouteRecoveryService();
});
