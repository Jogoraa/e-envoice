import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/authentication/multi_gateway_session.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/networking/delegated_tenant_api_client.dart';
import 'package:ut_einvoice_client/core/networking/master_admin_api_client.dart';
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

  group('Adversarial Test Suite: True Session Isolation & Independent API Clients', () {
    late SecureStorageService storage;
    late CapturingHttpClientAdapter normalAdapter;
    late CapturingHttpClientAdapter delegatedAdapter;
    late CapturingHttpClientAdapter masterAdapter;
    late ApiClient normalTenantClient;
    late DelegatedTenantApiClient delegatedTenantClient;
    late MasterAdminApiClient masterAdminClient;

    setUp(() async {
      FlutterSecureStorage.setMockInitialValues({});
      storage = SecureStorageService();
      normalAdapter = CapturingHttpClientAdapter();
      delegatedAdapter = CapturingHttpClientAdapter();
      masterAdapter = CapturingHttpClientAdapter();

      normalTenantClient = ApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: storage,
        customDio: Dio(BaseOptions(baseUrl: 'http://localhost:8080'))..httpClientAdapter = normalAdapter,
      );

      delegatedTenantClient = DelegatedTenantApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: storage,
        customDio: Dio(BaseOptions(baseUrl: 'http://localhost:8080'))..httpClientAdapter = delegatedAdapter,
      );

      masterAdminClient = MasterAdminApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: storage,
        customDio: Dio(BaseOptions(baseUrl: 'http://localhost:8080'))..httpClientAdapter = masterAdapter,
      );
    });

    test('Concurrent Session Coexistence: 4 distinct JWT namespaces exist without cross-contamination', () async {
      const normalTenantJwt = 'jwt-normal-tenant-001';
      const saasAdminJwt = 'jwt-saas-admin-002';
      const masterAdminJwt = 'jwt-master-admin-003';
      const delegatedTenantJwt = 'jwt-delegated-tenant-004';

      await storage.saveToken(normalTenantJwt);
      await storage.saveSaasAdminToken(saasAdminJwt);
      await storage.saveMasterAdminToken(masterAdminJwt);
      await storage.saveDelegatedTenantToken(delegatedTenantJwt);

      expect(await storage.getToken(), equals(normalTenantJwt));
      expect(await storage.getSaasAdminToken(), equals(saasAdminJwt));
      expect(await storage.getMasterAdminToken(), equals(masterAdminJwt));
      expect(await storage.getDelegatedTenantToken(), equals(delegatedTenantJwt));
    });

    test('Strict Normal Client Independence: Normal ApiClient strictly uses Tenant Token, never hijacked by Delegated Token', () async {
      const normalTenantJwt = 'jwt-normal-tenant-001';
      const delegatedTenantJwt = 'jwt-delegated-tenant-004';

      await storage.saveToken(normalTenantJwt);
      await storage.saveDelegatedTenantToken(delegatedTenantJwt);

      await normalTenantClient.get('/api/v1/invoices');

      expect(normalAdapter.lastRequest, isNotNull);
      expect(
        normalAdapter.lastRequest!.headers['Authorization'],
        equals('Bearer $normalTenantJwt'),
        reason: 'Normal ApiClient must always send normal tenant token, never silently hijacked by delegated session',
      );
    });

    test('Strict Delegated Client Isolation: DelegatedTenantApiClient strictly uses Delegated Token and sets delegated headers', () async {
      const normalTenantJwt = 'jwt-normal-tenant-001';
      const delegatedTenantJwt = 'jwt-delegated-tenant-004';

      await storage.saveToken(normalTenantJwt);
      await storage.saveDelegatedTenantToken(delegatedTenantJwt);

      delegatedTenantClient.setDelegatedScope(targetTenantId: '00000000-0000-0000-0000-000000000001');
      await delegatedTenantClient.get('/api/v1/invoices');

      expect(delegatedAdapter.lastRequest, isNotNull);
      expect(
        delegatedAdapter.lastRequest!.headers['Authorization'],
        equals('Bearer $delegatedTenantJwt'),
        reason: 'DelegatedTenantApiClient must strictly send delegated token',
      );
      expect(
        delegatedAdapter.lastRequest!.headers['X-Delegated-Testing'],
        equals('true'),
      );
      expect(
        delegatedAdapter.lastRequest!.headers['X-Gateway-Surface'],
        equals('UT-DELEGATED-TESTING'),
      );
    });

    test('Strict Master Client Isolation: MasterAdminApiClient strictly uses Master Admin Token', () async {
      const masterAdminJwt = 'jwt-master-admin-003';
      const normalTenantJwt = 'jwt-normal-tenant-001';

      await storage.saveToken(normalTenantJwt);
      await storage.saveMasterAdminToken(masterAdminJwt);

      await masterAdminClient.get('/api/v1/saas/tenants');

      expect(masterAdapter.lastRequest, isNotNull);
      expect(
        masterAdapter.lastRequest!.headers['Authorization'],
        equals('Bearer $masterAdminJwt'),
        reason: 'MasterAdminApiClient must strictly send master admin token',
      );
      expect(
        masterAdapter.lastRequest!.headers['X-Gateway-Surface'],
        equals('UT-MASTER-ADMIN'),
      );
    });

    test('Delegated Session Teardown Non-Bleed: Terminating delegated session preserves all other sessions', () async {
      const normalTenantJwt = 'jwt-normal-tenant-001';
      const saasAdminJwt = 'jwt-saas-admin-002';
      const masterAdminJwt = 'jwt-master-admin-003';
      const delegatedTenantJwt = 'jwt-delegated-tenant-004';

      await storage.saveToken(normalTenantJwt);
      await storage.saveSaasAdminToken(saasAdminJwt);
      await storage.saveMasterAdminToken(masterAdminJwt);
      await storage.saveDelegatedTenantToken(delegatedTenantJwt);

      // Wipe only delegated session
      await storage.wipeDelegatedTenantSession();

      expect(await storage.getDelegatedTenantToken(), isNull);
      expect(await storage.getToken(), equals(normalTenantJwt), reason: 'Normal tenant token must remain intact');
      expect(await storage.getSaasAdminToken(), equals(saasAdminJwt), reason: 'SaaS admin token must remain intact');
      expect(await storage.getMasterAdminToken(), equals(masterAdminJwt), reason: 'Master admin token must remain intact');
    });

    test('Tenant Logout Isolation: Wiping Tenant Session does not affect Master, SaaS Operator, or Delegated credentials', () async {
      const normalTenantJwt = 'jwt-normal-tenant-001';
      const saasAdminJwt = 'jwt-saas-admin-002';
      const masterAdminJwt = 'jwt-master-admin-003';
      const delegatedTenantJwt = 'jwt-delegated-tenant-004';

      await storage.saveToken(normalTenantJwt);
      await storage.saveSaasAdminToken(saasAdminJwt);
      await storage.saveMasterAdminToken(masterAdminJwt);
      await storage.saveDelegatedTenantToken(delegatedTenantJwt);

      // Standard tenant logout
      await storage.wipeSession();

      expect(await storage.getToken(), isNull);
      expect(await storage.getSaasAdminToken(), equals(saasAdminJwt));
      expect(await storage.getMasterAdminToken(), equals(masterAdminJwt));
      expect(await storage.getDelegatedTenantToken(), equals(delegatedTenantJwt),
          reason: 'Tenant logout must not destroy active delegated support token');
    });

    test('DelegatedTenantSession Model: correctly represents dual identity, mode, and expiration', () {
      final now = DateTime.now();
      final expiry = now.add(const Duration(minutes: 30));

      final session = DelegatedTenantSession(
        accessToken: 'test-token',
        sessionId: 'ses-999',
        targetTenantId: 'TNT-001',
        targetTenantTin: '0012398471',
        targetTenantName: 'Abyssinia Trading Enterprises PLC',
        accessType: 'READ_ONLY_SUPPORT',
        initiatingMasterUserId: 'admin@utsolutions.et',
        reason: 'Verifying receipt layout',
        expiresAt: expiry,
      );

      expect(session.isExpired, isFalse);
      expect(session.accessType, equals('READ_ONLY_SUPPORT'));
      expect(session.targetTenantTin, equals('0012398471'));
      expect(session.delegatedBy, equals('admin@utsolutions.et'));
      expect(session.token, equals('test-token'));

      final expiredSession = DelegatedTenantSession(
        accessToken: 'test-token-2',
        sessionId: 'ses-1000',
        targetTenantId: 'TNT-001',
        targetTenantTin: '0012398471',
        targetTenantName: 'Abyssinia Trading Enterprises PLC',
        accessType: 'TESTING',
        initiatingMasterUserId: 'operator-1',
        reason: 'Regression test',
        expiresAt: now.subtract(const Duration(minutes: 5)),
      );

      expect(expiredSession.isExpired, isTrue);
      expect(expiredSession.token, equals('test-token-2'));
    });
  });
}
