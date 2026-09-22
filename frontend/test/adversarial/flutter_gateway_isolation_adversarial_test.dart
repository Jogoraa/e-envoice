import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/networking/gateway_config.dart';
import 'package:ut_einvoice_client/core/networking/master_admin_api_client.dart';
import 'package:ut_einvoice_client/core/networking/saas_management_api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';

class CapturingHttpClientAdapter implements HttpClientAdapter {
  RequestOptions? lastRequest;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    lastRequest = options;
    return ResponseBody.fromString(
      jsonEncode({'status': 'ok', 'surface': options.headers['X-Gateway-Surface'] ?? 'TENANT'}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Multi-Gateway Isolation & Credential Non-Bleed', () {
    late SecureStorageService storage;

    setUp(() async {
      FlutterSecureStorage.setMockInitialValues({});
      storage = SecureStorageService();
    });

    test('Gateway Configuration: Tenant, Master Admin, and SaaS Management base URLs must be strictly distinct', () {
      expect(GatewayConfig.tenantApiBaseUrl, isNotEmpty);
      expect(GatewayConfig.masterAdminApiBaseUrl, isNotEmpty);
      expect(GatewayConfig.saasManagementApiBaseUrl, isNotEmpty);

      expect(GatewayConfig.tenantApiBaseUrl, isNot(equals(GatewayConfig.masterAdminApiBaseUrl)),
          reason: 'Tenant API and Master Admin API must have isolated gateways');
      expect(GatewayConfig.tenantApiBaseUrl, isNot(equals(GatewayConfig.saasManagementApiBaseUrl)),
          reason: 'Tenant API and SaaS Management API must have isolated gateways');
      expect(GatewayConfig.masterAdminApiBaseUrl, isNot(equals(GatewayConfig.saasManagementApiBaseUrl)),
          reason: 'Master Admin API and SaaS Management API must have isolated gateways');
    });

    test('Storage Isolation: Storage keys and tokens for Tenant, SaaS Admin, and Master Admin must be strictly separate', () async {
      const tenantToken = 'jwt-tenant-token-alpha';
      const saasToken = 'jwt-saas-token-beta';
      const masterAdminToken = 'jwt-master-admin-gamma';

      await storage.saveToken(tenantToken);
      await storage.saveSaasAdminToken(saasToken);
      await storage.saveMasterAdminToken(masterAdminToken);

      expect(await storage.getToken(), equals(tenantToken));
      expect(await storage.getSaasAdminToken(), equals(saasToken));
      expect(await storage.getMasterAdminToken(), equals(masterAdminToken));
    });

    test('Session Wipe Non-Bleed: Logging out of Tenant Client MUST NOT wipe Master Admin or SaaS Admin sessions', () async {
      const tenantToken = 'jwt-tenant-token-alpha';
      const saasToken = 'jwt-saas-token-beta';
      const masterAdminToken = 'jwt-master-admin-gamma';

      await storage.saveToken(tenantToken);
      await storage.saveRefreshToken('refresh-tenant-123');
      await storage.saveActiveTenantId('tenant-uuid-1');
      await storage.saveActiveBranchId('branch-uuid-1');
      await storage.saveSaasAdminToken(saasToken);
      await storage.saveMasterAdminToken(masterAdminToken);

      // Wipe Tenant Session
      await storage.wipeSession();

      // Verify Tenant credentials are wiped
      expect(await storage.getToken(), isNull);
      expect(await storage.getRefreshToken(), isNull);
      expect(await storage.getActiveTenantId(), isNull);
      expect(await storage.getActiveBranchId(), isNull);

      // Verify Master Admin and SaaS Admin tokens remain completely intact
      expect(await storage.getMasterAdminToken(), equals(masterAdminToken),
          reason: 'Tenant logout must not destroy Master Admin session');
      expect(await storage.getSaasAdminToken(), equals(saasToken),
          reason: 'Tenant logout must not destroy SaaS Management session');
    });

    test('Session Wipe Non-Bleed: Logging out of Master Admin MUST NOT wipe Tenant or SaaS Admin sessions', () async {
      const tenantToken = 'jwt-tenant-token-alpha';
      const saasToken = 'jwt-saas-token-beta';
      const masterAdminToken = 'jwt-master-admin-gamma';

      await storage.saveToken(tenantToken);
      await storage.saveSaasAdminToken(saasToken);
      await storage.saveMasterAdminToken(masterAdminToken);

      // Wipe Master Admin Session
      await storage.wipeMasterAdminSession();

      expect(await storage.getMasterAdminToken(), isNull);
      expect(await storage.getToken(), equals(tenantToken),
          reason: 'Master Admin logout must not affect Tenant session');
      expect(await storage.getSaasAdminToken(), equals(saasToken),
          reason: 'Master Admin logout must not affect SaaS session');
    });

    test('Session Wipe Non-Bleed: Logging out of SaaS Admin MUST NOT wipe Tenant or Master Admin sessions', () async {
      const tenantToken = 'jwt-tenant-token-alpha';
      const saasToken = 'jwt-saas-token-beta';
      const masterAdminToken = 'jwt-master-admin-gamma';

      await storage.saveToken(tenantToken);
      await storage.saveSaasAdminToken(saasToken);
      await storage.saveMasterAdminToken(masterAdminToken);

      // Wipe SaaS Admin Session
      await storage.wipeSaasAdminSession();

      expect(await storage.getSaasAdminToken(), isNull);
      expect(await storage.getToken(), equals(tenantToken),
          reason: 'SaaS Admin logout must not affect Tenant session');
      expect(await storage.getMasterAdminToken(), equals(masterAdminToken),
          reason: 'SaaS Admin logout must not affect Master Admin session');
    });

    test('API Client Header & Token Isolation: MasterAdminApiClient attaches Master Admin token and NEVER leaks Tenant token', () async {
      const tenantToken = 'jwt-tenant-token-secret';
      const masterAdminToken = 'jwt-master-admin-privileged';

      await storage.saveToken(tenantToken);
      await storage.saveMasterAdminToken(masterAdminToken);

      final adapter = CapturingHttpClientAdapter();
      final dio = Dio(BaseOptions(baseUrl: GatewayConfig.masterAdminApiBaseUrl));
      dio.httpClientAdapter = adapter;

      final adminClient = MasterAdminApiClient(
        secureStorage: storage,
        customDio: dio,
      );

      await adminClient.get<Map<String, dynamic>>('/platform/health');

      final captured = adapter.lastRequest;
      expect(captured, isNotNull);
      expect(captured!.headers['X-Gateway-Surface'], equals('UT-MASTER-ADMIN'));
      expect(captured.headers['Authorization'], equals('Bearer $masterAdminToken'));
      expect(captured.headers['Authorization'], isNot(contains(tenantToken)),
          reason: 'Tenant token must NEVER bleed into Master Admin API calls');
      expect(captured.headers.containsKey('X-Tenant-ID'), isFalse,
          reason: 'Master Admin client must not inject arbitrary tenant headers');
      expect(captured.headers.containsKey('X-Branch-ID'), isFalse);
    });

    test('API Client Header & Token Isolation: SaasManagementApiClient attaches SaaS token and NEVER leaks Master or Tenant token', () async {
      const tenantToken = 'jwt-tenant-token-secret';
      const saasToken = 'jwt-saas-token-commercial';
      const masterAdminToken = 'jwt-master-admin-privileged';

      await storage.saveToken(tenantToken);
      await storage.saveSaasAdminToken(saasToken);
      await storage.saveMasterAdminToken(masterAdminToken);

      final adapter = CapturingHttpClientAdapter();
      final dio = Dio(BaseOptions(baseUrl: GatewayConfig.saasManagementApiBaseUrl));
      dio.httpClientAdapter = adapter;

      final saasClient = SaasManagementApiClient(
        secureStorage: storage,
        customDio: dio,
      );

      await saasClient.get<Map<String, dynamic>>('/tenants');

      final captured = adapter.lastRequest;
      expect(captured, isNotNull);
      expect(captured!.headers['X-Gateway-Surface'], equals('UT-SAAS-MANAGEMENT'));
      expect(captured.headers['Authorization'], equals('Bearer $saasToken'));
      expect(captured.headers['Authorization'], isNot(contains(tenantToken)));
      expect(captured.headers['Authorization'], isNot(contains(masterAdminToken)));
    });

    test('Token Bleed Guard: ApiClient NEVER falls back to Master Admin or SaaS token when Tenant Token is missing', () async {
      // Setup: Only Master Admin and SaaS tokens exist in storage, NO Tenant token
      await storage.saveMasterAdminToken('jwt-master-admin-privileged');
      await storage.saveSaasAdminToken('jwt-saas-token-commercial');

      final adapter = CapturingHttpClientAdapter();
      final dio = Dio(BaseOptions(baseUrl: GatewayConfig.tenantApiBaseUrl));
      dio.httpClientAdapter = adapter;

      final tenantClient = ApiClient(
        baseUrl: GatewayConfig.tenantApiBaseUrl,
        secureStorage: storage,
        customDio: dio,
      );

      await tenantClient.get<Map<String, dynamic>>('/invoices');

      final captured = adapter.lastRequest;
      expect(captured, isNotNull);
      // The Authorization header must NOT contain master admin or saas admin tokens
      final authHeader = captured!.headers['Authorization'];
      if (authHeader != null) {
        expect(authHeader, isNot(contains('jwt-master-admin-privileged')));
        expect(authHeader, isNot(contains('jwt-saas-token-commercial')));
      }
      expect(captured.headers['X-Gateway-Surface'], isNull);
    });
  });
}
