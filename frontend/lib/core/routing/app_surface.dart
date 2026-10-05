import 'package:flutter/material.dart';
import '../../app/theme/app_colors.dart';

/// Application Surface Realm definition for UT Electronic Invoicing.
/// The system contains THREE distinct application surfaces in the same codebase.
enum AppSurface {
  tenantClient,
  saasManagement,
  masterAdmin,
  unknown;

  /// Human-readable display label for the application surface
  String get displayName {
    switch (this) {
      case AppSurface.tenantClient:
        return 'Business Client Workspace';
      case AppSurface.saasManagement:
        return 'SaaS Management Portal';
      case AppSurface.masterAdmin:
        return 'Master Admin Platform';
      case AppSurface.unknown:
        return 'UT Electronic Invoicing';
    }
  }

  /// Short badge label for UI chips/pills
  String get badgeLabel {
    switch (this) {
      case AppSurface.tenantClient:
        return 'Client Portal';
      case AppSurface.saasManagement:
        return 'SaaS Operations';
      case AppSurface.masterAdmin:
        return 'Master Admin';
      case AppSurface.unknown:
        return 'Platform';
    }
  }

  /// Canonical dashboard path for the surface
  String get defaultHomeRoute {
    switch (this) {
      case AppSurface.tenantClient:
        return '/dashboard';
      case AppSurface.saasManagement:
        return '/saas/dashboard';
      case AppSurface.masterAdmin:
        return '/admin/dashboard';
      case AppSurface.unknown:
        return '/';
    }
  }

  /// Canonical login path for the surface
  String get loginRoute {
    switch (this) {
      case AppSurface.tenantClient:
        return '/login';
      case AppSurface.saasManagement:
        return '/saas/login';
      case AppSurface.masterAdmin:
        return '/admin/login';
      case AppSurface.unknown:
        return '/login';
    }
  }

  /// Canonical action button text to return home
  String get returnActionLabel {
    switch (this) {
      case AppSurface.tenantClient:
        return 'Return to Client Dashboard';
      case AppSurface.saasManagement:
        return 'Return to SaaS Dashboard';
      case AppSurface.masterAdmin:
        return 'Return to Admin Dashboard';
      case AppSurface.unknown:
        return 'Return to Main Workspace';
    }
  }

  /// Primary theme tint for surface badge
  Color get badgeColor {
    switch (this) {
      case AppSurface.tenantClient:
        return AppColors.navy700;
      case AppSurface.saasManagement:
        return AppColors.primary;
      case AppSurface.masterAdmin:
        return AppColors.red600;
      case AppSurface.unknown:
        return AppColors.inkMuted;
    }
  }
}
