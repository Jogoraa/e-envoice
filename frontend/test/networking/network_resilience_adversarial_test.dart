import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/connectivity/connectivity_service.dart';
import 'package:ut_einvoice_client/core/errors/app_error.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/networking/network_resilience_manager.dart';
import 'package:ut_einvoice_client/core/networking/poor_network_simulator.dart';
import 'package:ut_einvoice_client/core/networking/token_refresh_interceptor.dart';
import 'package:ut_einvoice_client/core/storage/receipt_cache_service.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';
import 'package:ut_einvoice_client/data/repositories/invoice_repository_impl.dart';

class _MockPoorNetworkAuthAdapter implements HttpClientAdapter {
  int refreshCalls = 0;
  int resourceCalls = 0;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.contains('/api/v1/auth/refresh')) {
      refreshCalls++;
      if (refreshCalls == 1) {
        throw DioException(
          requestOptions: options,
          type: DioExceptionType.connectionTimeout,
          message: 'Connection timeout contacting auth server',
        );
      }
      return ResponseBody.fromString(
        jsonEncode({
          'accessToken': 'new_valid_token',
          'refreshToken': 'new_refresh_token',
        }),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }

    resourceCalls++;
    final authHeader = options.headers['Authorization'];
    if (authHeader == 'Bearer new_valid_token') {
      return ResponseBody.fromString(
        jsonEncode({'success': true, 'path': options.path}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }

    return ResponseBody.fromString(
      jsonEncode({'error': 'Unauthorized', 'message': 'Token expired'}),
      401,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}


void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Network Resilience & Poor Network Adversarial Test Suite', () {
    late AppDatabase db;
    late SecureStorageService secureStorage;
    late ReceiptCacheService receiptCache;
    late NetworkResilienceManager resilienceManager;

    setUp(() async {
      TestWidgetsFlutterBinding.ensureInitialized();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('dev.fluttercommunity.plus/connectivity'),
        (MethodCall methodCall) async {
          if (methodCall.method == 'check') {
            return <String>['wifi'];
          }
          return null;
        },
      );
      FlutterSecureStorage.setMockInitialValues({});
      PoorNetworkSimulator.reset();
      db = AppDatabase(NativeDatabase.memory());
      secureStorage = SecureStorageService();
      await secureStorage.wipeSession();
      receiptCache = ReceiptCacheService();
      await receiptCache.clearAll();
      resilienceManager = NetworkResilienceManager(
        sleep: (_) async {}, // instant sleep for test speed
      );
    });


    tearDown(() async {
      PoorNetworkSimulator.reset();
      await db.close();
      await receiptCache.clearAll();
    });

    test('REGRESSION TEST (Screenshot Scenario): Slow network with initial timeout must retry and succeed without NETWORK_UNAVAILABLE false offline error', () async {
      var requestCount = 0;

      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          requestCount++;
          if (requestCount == 1) {
            // Attempt 1 fails due to slow server / receive timeout
            return handler.reject(DioException(
              requestOptions: options,
              type: DioExceptionType.receiveTimeout,
              message: 'The server took too long to render the receipt.',
            ));
          }
          // Attempt 2 succeeds with authoritative receipt HTML
          return handler.resolve(Response(
            requestOptions: options,
            statusCode: 200,
            data: '<html><body><h1>OFFICIAL MOR TAX INVOICE - INV-001</h1></body></html>',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );
      apiClient.setScope(tenantId: 'TENANT-ALPHA', branchId: 'BRANCH-01');

      final connectivity = ConnectivityService(probeDio: dio);

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: connectivity,
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      final progressEvents = <String>[];
      final html = await repo.getInvoiceReceiptHtml(
        'INV-001',
        tenantId: 'TENANT-ALPHA',
        branchId: 'BRANCH-01',
        onProgress: (p) => progressEvents.add(p.message),
      );

      // Verify request retried and succeeded
      expect(requestCount, equals(2));
      expect(html, contains('OFFICIAL MOR TAX INVOICE - INV-001'));
      expect(resilienceManager.snapshot.state, equals(NetworkState.online));

      // Verify receipt was cached locally
      final isCached = await receiptCache.hasCachedReceipt(
        tenantId: 'TENANT-ALPHA',
        branchId: 'BRANCH-01',
        invoiceId: 'INV-001',
      );
      expect(isCached, isTrue);
    });

    test('TEST A — Excellent Network: Immediate receipt retrieval and local caching', () async {
      var receiptRequestCount = 0;
      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          if (options.path.contains('/document')) {
            receiptRequestCount++;
          }
          return handler.resolve(Response(
            requestOptions: options,
            statusCode: 200,
            data: '<html><body>Tax Invoice A</body></html>',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );
      apiClient.setScope(tenantId: 'TENANT-A', branchId: 'BRANCH-A');

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      final html = await repo.getInvoiceReceiptHtml(
        'INV-A',
        tenantId: 'TENANT-A',
        branchId: 'BRANCH-A',
      );

      expect(receiptRequestCount, equals(1));
      expect(html, equals('<html><body>Tax Invoice A</body></html>'));
      expect(await receiptCache.hasCachedReceipt(tenantId: 'TENANT-A', branchId: 'BRANCH-A', invoiceId: 'INV-A'), isTrue);
    });

    test('TEST B — Slow Network: Transient DNS & connection resets retried automatically', () async {
      var requestCount = 0;
      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          if (!options.path.contains('/document')) {
            return handler.resolve(Response(requestOptions: options, statusCode: 200));
          }
          requestCount++;
          if (requestCount == 1) {
            return handler.reject(DioException(
              requestOptions: options,
              type: DioExceptionType.connectionError,
              message: 'Failed host lookup: api.ut.gov.et',
            ));
          } else if (requestCount == 2) {
            return handler.reject(DioException(
              requestOptions: options,
              type: DioExceptionType.connectionError,
              message: 'Connection reset by peer',
            ));
          }
          return handler.resolve(Response(
            requestOptions: options,
            statusCode: 200,
            data: '<html><body>Recovered After Slow Network</body></html>',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      final html = await repo.getInvoiceReceiptHtml(
        'INV-SLOW',
        tenantId: 'TENANT-A',
        branchId: 'BRANCH-A',
      );

      expect(requestCount, equals(3));
      expect(html, contains('Recovered After Slow Network'));
      expect(resilienceManager.snapshot.state, equals(NetworkState.online));
    });

    test('TEST C — Intermittent Network: Link transition triggers recovery stream', () async {
      final recoveryStates = <NetworkState>[];
      final sub = resilienceManager.recoveryStream.listen(recoveryStates.add);

      // Report link down
      resilienceManager.reportLinkStatus(hasLink: false);
      expect(resilienceManager.snapshot.isOffline, isTrue);

      // Report link back up
      resilienceManager.reportLinkStatus(hasLink: true);
      expect(resilienceManager.snapshot.state, equals(NetworkState.unknown));

      // Network request proves online
      resilienceManager.recordSuccess();
      expect(resilienceManager.snapshot.state, equals(NetworkState.online));

      // Flush microtasks for broadcast stream event delivery
      await Future<void>.delayed(Duration.zero);
      expect(recoveryStates, contains(NetworkState.online));

      await sub.cancel();
    });

    test('TEST D — Offline with Cached Receipt: Opens cached official receipt immediately', () async {
      // Pre-populate cache with authoritative receipt
      await receiptCache.saveReceiptHtml(
        tenantId: 'TENANT-OFFLINE',
        branchId: 'BRANCH-01',
        invoiceId: 'INV-OFFLINE-01',
        htmlContent: '<html><body>Authoritative Cached Receipt</body></html>',
        documentNumber: 'MOR/2026/00099',
        irn: 'IRN-9999-OFFLINE',
      );

      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          // Network completely unreachable
          return handler.reject(DioException(
            requestOptions: options,
            type: DioExceptionType.connectionError,
            message: 'Network is unreachable',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );
      apiClient.setScope(tenantId: 'TENANT-OFFLINE', branchId: 'BRANCH-01');

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      // Should return cached receipt immediately without throwing
      final html = await repo.getInvoiceReceiptHtml(
        'INV-OFFLINE-01',
        tenantId: 'TENANT-OFFLINE',
        branchId: 'BRANCH-01',
      );

      expect(html, equals('<html><body>Authoritative Cached Receipt</body></html>'));
    });

    test('TEST E — Offline without Cached Receipt: Accurate error message, never fabricates official receipt', () async {
      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          return handler.reject(DioException(
            requestOptions: options,
            type: DioExceptionType.connectionError,
            message: 'Network is unreachable',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      expect(
        () async => await repo.getInvoiceReceiptHtml(
          'INV-UNCACHED',
          tenantId: 'TENANT-X',
          branchId: 'BRANCH-X',
        ),
        throwsA(isA<AppError>()),
      );

      // Verify no fake receipt was created in cache
      final hasCached = await receiptCache.hasCachedReceipt(
        tenantId: 'TENANT-X',
        branchId: 'BRANCH-X',
        invoiceId: 'INV-UNCACHED',
      );
      expect(hasCached, isFalse);
    });

    test('TEST F — Server Temporary Failure (503): Exponential backoff retries and recovers', () async {
      var requestCount = 0;
      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          requestCount++;
          if (requestCount == 1) {
            return handler.reject(DioException(
              requestOptions: options,
              type: DioExceptionType.badResponse,
              response: Response(
                requestOptions: options,
                statusCode: 503,
                data: {'code': 'SERVICE_UNAVAILABLE', 'message': 'MoR Gateway Busy'},
              ),
            ));
          }
          return handler.resolve(Response(
            requestOptions: options,
            statusCode: 200,
            data: '<html><body>503 Recovered Receipt</body></html>',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      final html = await repo.getInvoiceReceiptHtml(
        'INV-503',
        tenantId: 'TENANT-A',
        branchId: 'BRANCH-01',
      );

      expect(requestCount, equals(2));
      expect(html, contains('503 Recovered Receipt'));
    });

    test('TEST G — Request Deduplication: Multiple concurrent clicks result in exactly ONE network request', () async {
      var receiptCallCount = 0;
      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) async {
          if (options.path.contains('/document')) {
            receiptCallCount++;
          }
          await Future<void>.delayed(const Duration(milliseconds: 50));
          return handler.resolve(Response(
            requestOptions: options,
            statusCode: 200,
            data: '<html><body>Deduplicated Receipt</body></html>',
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      // Launch 5 concurrent calls
      final results = await Future.wait([
        repo.getInvoiceReceiptHtml('INV-DEDUPE', tenantId: 'T1', branchId: 'B1'),
        repo.getInvoiceReceiptHtml('INV-DEDUPE', tenantId: 'T1', branchId: 'B1'),
        repo.getInvoiceReceiptHtml('INV-DEDUPE', tenantId: 'T1', branchId: 'B1'),
        repo.getInvoiceReceiptHtml('INV-DEDUPE', tenantId: 'T1', branchId: 'B1'),
        repo.getInvoiceReceiptHtml('INV-DEDUPE', tenantId: 'T1', branchId: 'B1'),
      ]);

      // All 5 returned the exact same content
      for (final res in results) {
        expect(res, contains('Deduplicated Receipt'));
      }

      // Exactly ONE network request was sent
      expect(receiptCallCount, equals(1));
    });

    test('TEST H — PDF Download Resilience: Retries transient failure and returns complete bytes', () async {
      var requestCount = 0;
      final expectedBytes = [0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34]; // %PDF-1.4

      final dio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          requestCount++;
          if (requestCount == 1) {
            return handler.reject(DioException(
              requestOptions: options,
              type: DioExceptionType.receiveTimeout,
              message: 'PDF streaming timeout',
            ));
          }
          return handler.resolve(Response(
            requestOptions: options,
            statusCode: 200,
            data: Uint8List.fromList(expectedBytes),
          ));
        },
      ));

      final apiClient = ApiClient(
        baseUrl: 'http://test-server.local',
        secureStorage: secureStorage,
        resilienceManager: resilienceManager,
        customDio: dio,
      );

      final repo = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: ConnectivityService(probeDio: dio),
        receiptCache: receiptCache,
        resilienceManager: resilienceManager,
      );

      final pdfBytes = await repo.downloadInvoicePdf(
        'INV-PDF',
        tenantId: 'T1',
        branchId: 'B1',
      );

      expect(requestCount, equals(2));
      expect(pdfBytes, equals(expectedBytes));
    });

    test('TEST I — Single-Flight Token Refresh Under Poor Network: Does not wipe session on transient error', () async {
      await secureStorage.saveToken('expired_token');
      await secureStorage.saveRefreshToken('valid_refresh_token');

      final adapter = _MockPoorNetworkAuthAdapter();
      final mainDio = Dio(BaseOptions(baseUrl: 'http://test-server.local'));
      mainDio.httpClientAdapter = adapter;

      mainDio.interceptors.add(TokenRefreshInterceptor(
        dio: mainDio,
        secureStorage: secureStorage,
        refreshClient: mainDio,
      ));

      final response = await mainDio.get(
        '/api/v1/resource',
        options: Options(headers: {'Authorization': 'Bearer expired_token'}),
      );
      expect(response.statusCode, equals(200));
      expect(response.data['success'], isTrue);

      // Session was preserved and refreshed
      final savedToken = await secureStorage.getToken();
      expect(savedToken, equals('new_valid_token'));
      expect(adapter.refreshCalls, equals(2)); // 1st failed with timeout, 2nd succeeded
    });

    test('TEST J — Strict Tenant Isolation: Tenant B cannot access Tenant A cached receipt', () async {
      // Save receipt for Tenant A
      await receiptCache.saveReceiptHtml(
        tenantId: 'TENANT-A',
        branchId: 'BRANCH-01',
        invoiceId: 'INV-SHARED-ID',
        htmlContent: '<html><body>Tenant A Secret Invoice</body></html>',
      );

      // Query from Tenant B for the exact same invoice ID
      final tenantBReceipt = await receiptCache.getCachedReceiptHtml(
        tenantId: 'TENANT-B',
        branchId: 'BRANCH-01',
        invoiceId: 'INV-SHARED-ID',
      );

      // Must be null — absolute tenant isolation
      expect(tenantBReceipt, isNull);

      final hasTenantBReceipt = await receiptCache.hasCachedReceipt(
        tenantId: 'TENANT-B',
        branchId: 'BRANCH-01',
        invoiceId: 'INV-SHARED-ID',
      );
      expect(hasTenantBReceipt, isFalse);
    });
  });
}
