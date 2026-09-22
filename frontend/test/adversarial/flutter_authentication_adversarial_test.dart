import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/networking/token_refresh_interceptor.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';

class MockAuthAdapter implements HttpClientAdapter {
  int refreshCallCount = 0;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.contains('/api/v1/auth/refresh')) {
      refreshCallCount++;
      return ResponseBody.fromString(
        jsonEncode({
          'accessToken': 'newly-refreshed-jwt-token',
          'refreshToken': 'newly-refreshed-refresh-token',
        }),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }

    final authHeader = options.headers['Authorization'];
    if (authHeader == 'Bearer expired-access-token' || authHeader == null) {
      return ResponseBody.fromString(
        jsonEncode({'error': 'Unauthorized', 'message': 'Token expired'}),
        401,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }

    if (authHeader == 'Bearer newly-refreshed-jwt-token') {
      return ResponseBody.fromString(
        jsonEncode({'result': 'success', 'path': options.path}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }

    return ResponseBody.fromString(
      jsonEncode({'error': 'Not Found'}),
      404,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Authentication Security & Single-Flight Token Refresh', () {
    late SecureStorageService storage;
    late Dio testDio;
    late MockAuthAdapter adapter;

    setUp(() async {
      FlutterSecureStorage.setMockInitialValues({});
      storage = SecureStorageService();
      await storage.saveToken('expired-access-token');
      await storage.saveRefreshToken('valid-refresh-token');

      adapter = MockAuthAdapter();
      testDio = Dio(BaseOptions(baseUrl: 'http://localhost:8080'));
      testDio.httpClientAdapter = adapter;

      // Attach TokenRefreshInterceptor
      testDio.interceptors.add(
        TokenRefreshInterceptor(
          dio: testDio,
          secureStorage: storage,
          refreshUrl: '/api/v1/auth/refresh',
          refreshClient: testDio,
        ),
      );
    });

    test('Single-Flight Token Refresh: 5 concurrent 401 requests must trigger EXACTLY ONE refresh call (No thundering herd)', () async {
      // Simulate 5 simultaneous asynchronous queries hitting the backend
      final futures = List.generate(5, (index) {
        return testDio.get(
          '/api/v1/resource-$index',
          options: Options(headers: {'Authorization': 'Bearer expired-access-token'}),
        );
      });

      final responses = await Future.wait(futures);

      // Verify all 5 requests completed successfully
      expect(responses.length, equals(5));
      for (final resp in responses) {
        expect(resp.statusCode, equals(200));
        expect(resp.data['result'], equals('success'));
      }

      // Adversarial Assertion: Refresh must be executed exactly ONCE
      expect(
        adapter.refreshCallCount,
        equals(1),
        reason: 'Single-flight mutex must coalesce concurrent 401s and disallow thundering herd',
      );

      // Verify new token persisted in secure storage
      final storedToken = await storage.getToken();
      expect(storedToken, equals('newly-refreshed-jwt-token'));
    });

    test('Session Wipe: Logout must purge all credentials, tokens, and encryption metadata completely', () async {
      await storage.saveToken('jwt-to-wipe');
      await storage.saveRefreshToken('refresh-to-wipe');
      await storage.saveM2MCredentials(apiKey: 'api-key-to-wipe', clientSecret: 'secret-to-wipe');
      await storage.saveActiveTenantId('tenant-to-wipe');

      // Execute complete session wipe
      await storage.wipeSession();

      // Assert all secrets and session identifiers are purged
      expect(await storage.getToken(), isNull);
      expect(await storage.getRefreshToken(), isNull);
      expect(await storage.getApiKey(), isNull);
      expect(await storage.getClientSecret(), isNull);
      expect(await storage.getActiveTenantId(), isNull);
    });
  });
}
