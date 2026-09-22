import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'apps/master_admin/master_admin_app.dart';
import 'apps/saas_management/saas_app.dart';
import 'apps/tenant_client/tenant_app.dart';
import 'core/authentication/multi_gateway_session.dart';
import 'core/di/providers.dart';
import 'core/storage/secure_storage_service.dart';
import 'domain/tenant/models/tenant_context.dart';

/// Multi-Surface Enterprise Entrypoint
/// Supports runtime selection via --dart-define=APP_SURFACE=tenant|saas|admin
/// Defaults to 'tenant' (Application A — UT Invoice Client).
void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Filter out benign Windows desktop OS Alt-Tab RawKeyboard assertions and hot-restart asset loading glitches
  FlutterError.onError = (FlutterErrorDetails details) {
    final str = details.exception.toString();
    final bool isIgnorable = (details.exception is AssertionError &&
        (str.contains('RawKeyDownEvent') ||
         str.contains('RawKeyboard') ||
         str.contains('keysPressed.isNotEmpty'))) ||
        str.contains('AssetManifest.bin') ||
        str.contains('google_fonts was unable to load font');
    if (isIgnorable) {
      return;
    }
    FlutterError.presentError(details);
  };

  PlatformDispatcher.instance.onError = (error, stack) {
    final str = error.toString();
    if ((error is AssertionError &&
        (str.contains('RawKeyDownEvent') ||
         str.contains('RawKeyboard') ||
         str.contains('keysPressed.isNotEmpty'))) ||
        str.contains('AssetManifest.bin') ||
        str.contains('google_fonts was unable to load font')) {
      return true;
    }
    return false;
  };

  final secureStorage = SecureStorageService();

  // Rehydrate sessions from hardware-backed secure storage across hot restarts and page refreshes
  final initialTenantSession = await secureStorage.getTenantAuthSession();
  final initialTenantContext = await secureStorage.getTenantContext();
  final initialSaasSession = await secureStorage.getSaasSession();
  final initialMasterAdminSession = await secureStorage.getMasterAdminSession();
  final initialDelegatedSession = await secureStorage.getDelegatedTenantSession();

  const appSurface = String.fromEnvironment('APP_SURFACE', defaultValue: 'tenant');

  Widget rootApp;
  switch (appSurface) {
    case 'saas':
      rootApp = const UtSaasManagementApp();
      break;
    case 'admin':
      rootApp = const UtMasterAdminApp();
      break;
    case 'tenant':
    default:
      rootApp = const UtTenantClientApp();
      break;
  }

  runApp(
    ProviderScope(
      overrides: [
        secureStorageProvider.overrideWithValue(secureStorage),
        authSessionProvider.overrideWith((ref) => AuthSessionNotifier(secureStorage, initialTenantSession)),
        tenantContextProvider.overrideWith((ref) => TenantContextNotifier(secureStorage, initialTenantContext ?? const TenantContextState())),
        saasSessionProvider.overrideWith((ref) => SaasManagementSessionNotifier(secureStorage, initialSaasSession)),
        masterAdminSessionProvider.overrideWith((ref) => MasterAdminSessionNotifier(secureStorage, initialMasterAdminSession)),
        delegatedTenantSessionProvider.overrideWith((ref) => DelegatedTenantSessionNotifier(secureStorage, initialDelegatedSession)),
      ],
      child: rootApp,
    ),
  );
}
