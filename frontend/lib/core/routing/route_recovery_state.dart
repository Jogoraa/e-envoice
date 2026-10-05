import 'app_surface.dart';

/// Category of route resolution failure.
enum RouteFailureType {
  /// 404: The requested path does not exist in the current application surface.
  notFound,

  /// 403: The requested path exists or belongs to another realm, but the current user
  /// does not possess required roles/permissions, or is crossing app security boundaries.
  accessDenied,

  /// 401: The requested path requires an authenticated session that is missing or expired.
  sessionRequired;

  String get statusCode {
    switch (this) {
      case RouteFailureType.notFound:
        return '404';
      case RouteFailureType.accessDenied:
        return '403';
      case RouteFailureType.sessionRequired:
        return '401';
    }
  }

  String get defaultTitle {
    switch (this) {
      case RouteFailureType.notFound:
        return 'Page unavailable';
      case RouteFailureType.accessDenied:
        return 'Access restricted';
      case RouteFailureType.sessionRequired:
        return 'Sign-in required';
    }
  }
}

/// Immutable typed model representing the route recovery context.
class RouteRecoveryState {
  final RouteFailureType failureType;
  final String attemptedPath;
  final AppSurface appSurface;
  final bool authenticated;
  final String safeHomeRoute;
  final String safeHomeLabel;
  final bool canGoBack;
  final String? userRole;
  final String? userIdentifier;
  final bool isCrossAppViolation;

  const RouteRecoveryState({
    required this.failureType,
    required this.attemptedPath,
    required this.appSurface,
    required this.authenticated,
    required this.safeHomeRoute,
    required this.safeHomeLabel,
    this.canGoBack = true,
    this.userRole,
    this.userIdentifier,
    this.isCrossAppViolation = false,
  });

  /// True if sensitive route path shouldn't be revealed to unprivileged users
  bool get shouldHideAttemptedPath {
    if (isCrossAppViolation) return true;
    if (failureType == RouteFailureType.accessDenied && !authenticated) return true;
    return false;
  }

  /// App-specific header title
  String get displayTitle {
    if (failureType == RouteFailureType.sessionRequired) {
      return 'Session required';
    }

    if (failureType == RouteFailureType.accessDenied || isCrossAppViolation) {
      return 'Access restricted';
    }

    switch (appSurface) {
      case AppSurface.tenantClient:
        return 'Page unavailable';
      case AppSurface.saasManagement:
        return 'Management page unavailable';
      case AppSurface.masterAdmin:
        return 'Admin page unavailable';
      case AppSurface.unknown:
        return 'Page unavailable';
    }
  }

  /// App-specific descriptive body text
  String get displayDescription {
    if (failureType == RouteFailureType.sessionRequired) {
      return 'Sign in to your authorized account to continue to UT Invoice.';
    }

    if (failureType == RouteFailureType.accessDenied || isCrossAppViolation) {
      return 'This destination is not accessible from your current application realm or account permissions.';
    }

    switch (appSurface) {
      case AppSurface.tenantClient:
        return 'This page isn\'t available in your business workspace. It may have been moved, renamed, or is currently unavailable.';
      case AppSurface.saasManagement:
        return 'This destination isn\'t available in the SaaS management workspace.';
      case AppSurface.masterAdmin:
        return 'This administrative destination could not be found or is no longer available on the platform.';
      case AppSurface.unknown:
        return 'The requested destination could not be found. Please check the address or return to the main workspace.';
    }
  }
}
