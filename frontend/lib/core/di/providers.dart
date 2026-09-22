import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/local/database/app_database.dart';
import '../../data/repositories/audit_repository_impl.dart';
import '../../data/repositories/catalog_repository.dart';
import '../../data/repositories/customer_repository.dart';
import '../../data/repositories/customer_repository_impl.dart';
import '../../data/repositories/inventory_repository.dart';
import '../../data/repositories/invoice_repository_impl.dart';
import '../connectivity/connectivity_service.dart';
import '../device/device_identity_service.dart';
import '../networking/api_client.dart';
import '../networking/delegated_tenant_api_client.dart';
import '../networking/master_admin_api_client.dart';
import '../networking/saas_management_api_client.dart';
import '../storage/secure_storage_service.dart';
import '../synchronization/sync_engine.dart';
import '../synchronization/sync_state_notifier.dart';

import '../networking/network_resilience_manager.dart';
import '../networking/gateway_config.dart';
import '../storage/receipt_cache_service.dart';

final networkResilienceManagerProvider = Provider<NetworkResilienceManager>((ref) {
  final manager = NetworkResilienceManager();
  ref.onDispose(() => manager.dispose());
  return manager;
});

final receiptCacheServiceProvider = Provider<ReceiptCacheService>((ref) {
  return ReceiptCacheService();
});

final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase();
  ref.onDispose(() => db.close());
  return db;
});

final secureStorageProvider = Provider<SecureStorageService>((ref) {
  return SecureStorageService();
});

final deviceIdentityServiceProvider = Provider<DeviceIdentityService>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return DeviceIdentityService(storage);
});

final tenantApiClientProvider = Provider<ApiClient>((ref) {
  final storage = ref.watch(secureStorageProvider);
  final resilience = ref.watch(networkResilienceManagerProvider);
  return ApiClient(
    baseUrl: GatewayConfig.activeBackendHost,
    secureStorage: storage,
    resilienceManager: resilience,
  );
});

final apiClientProvider = Provider<ApiClient>((ref) {
  return ref.watch(tenantApiClientProvider);
});

final delegatedTenantApiClientProvider = Provider<DelegatedTenantApiClient>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return DelegatedTenantApiClient(
    baseUrl: GatewayConfig.activeBackendHost,
    secureStorage: storage,
  );
});

final masterAdminApiClientProvider = Provider<MasterAdminApiClient>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return MasterAdminApiClient(
    secureStorage: storage,
  );
});

final saasManagementApiClientProvider = Provider<SaasManagementApiClient>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return SaasManagementApiClient(
    secureStorage: storage,
  );
});

final invoiceRepositoryProvider = Provider<InvoiceRepositoryImpl>((ref) {
  final client = ref.watch(apiClientProvider);
  final db = ref.watch(appDatabaseProvider);
  final conn = ref.watch(connectivityServiceProvider);
  final cache = ref.watch(receiptCacheServiceProvider);
  final resilience = ref.watch(networkResilienceManagerProvider);
  return InvoiceRepositoryImpl(
    apiClient: client,
    db: db,
    connectivity: conn,
    receiptCache: cache,
    resilienceManager: resilience,
  );
});


final auditRepositoryProvider = Provider<AuditRepositoryImpl>((ref) {
  final client = ref.watch(apiClientProvider);
  return AuditRepositoryImpl(client);
});

final catalogRepositoryProvider = Provider<CatalogRepositoryImpl>((ref) {
  final client = ref.watch(apiClientProvider);
  final db = ref.watch(appDatabaseProvider);
  return CatalogRepositoryImpl(apiClient: client, db: db);
});

final customerRepositoryProvider = Provider<CustomerRepository>((ref) {
  final client = ref.watch(apiClientProvider);
  final db = ref.watch(appDatabaseProvider);
  return CustomerRepositoryImpl(apiClient: client, db: db);
});

final inventoryRepositoryProvider = Provider<InventoryRepositoryImpl>((ref) {
  final client = ref.watch(apiClientProvider);
  final db = ref.watch(appDatabaseProvider);
  return InventoryRepositoryImpl(apiClient: client, db: db);
});

final syncEngineProvider = Provider<SyncEngine>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final client = ref.watch(apiClientProvider);
  final device = ref.watch(deviceIdentityServiceProvider);
  final conn = ref.watch(connectivityServiceProvider);
  final notifier = ref.watch(syncStateProvider.notifier);

  return SyncEngine(
    db: db,
    apiClient: client,
    deviceIdentity: device,
    connectivity: conn,
    syncNotifier: notifier,
  );
});
