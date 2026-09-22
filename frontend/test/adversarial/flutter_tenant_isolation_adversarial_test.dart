import 'package:dio/dio.dart';
import 'package:drift/drift.dart' hide isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Tenant Isolation & Non-Bleed Verification', () {
    late AppDatabase db;
    late SecureStorageService storage;
    late ApiClient apiClient;

    setUp(() {
      FlutterSecureStorage.setMockInitialValues({});
      db = AppDatabase(NativeDatabase.memory());
      storage = SecureStorageService();
      apiClient = ApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: storage,
      );
    });

    tearDown(() async {
      await db.close();
    });

    test('Local DB queries for Tenant B must never return Tenant A invoices or outbox items', () async {
      const tenantA = '00000000-0000-0000-0000-000000000001';
      const tenantB = '00000000-0000-0000-0000-000000000002';
      const branchA = 'BR-A1';
      const branchB = 'BR-B1';

      // 1. Insert invoice for Tenant A
      await db.into(db.localInvoices).insert(
            LocalInvoicesCompanion.insert(
              localId: 'INV-A-1',
              tenantId: tenantA,
              branchId: branchA,
              documentNumber: 'DRAFT-A-1',
              invoiceDate: DateTime.now(),
              transactionType: 'B2C',
              paymentMode: 'CASH',
              preTaxTotal: 100.0,
              taxTotal: 15.0,
              grandTotal: 115.0,
              syncStatus: 'offlineDraft',
              rawPayload: '{"taxpayer": "Tenant A"}',
              createdAt: DateTime.now(),
            ),
          );

      // 2. Insert outbox operation for Tenant A
      await db.into(db.outboxOperations).insert(
            OutboxOperationsCompanion.insert(
              operationId: 'OP-A-1',
              idempotencyKey: 'IDEMP-A-1',
              tenantId: tenantA,
              branchId: branchA,
              operationType: 'CREATE_INVOICE',
              endpoint: '/api/v1/invoices',
              payloadJson: '{"tenant": "A"}',
              syncState: 'pending',
              attemptCount: const Value(0),
              bufferedAt: DateTime.now(),
              createdAt: DateTime.now(),
            ),
          );

      // 3. Query as Tenant B
      final tenantBInvoices = await db.getScopedInvoices(
        tenantId: tenantB,
        branchId: branchB,
      );
      final tenantBOutbox = await db.getPendingOutbox(
        tenantId: tenantB,
        branchId: branchB,
      );

      // Adversarial Assertion: Absolute non-bleed
      expect(tenantBInvoices, isEmpty, reason: 'Tenant B must see 0 invoices belonging to Tenant A');
      expect(tenantBOutbox, isEmpty, reason: 'Tenant B must see 0 outbox operations belonging to Tenant A');

      // 4. Query as Tenant A should return exactly Tenant A records
      final tenantAInvoices = await db.getScopedInvoices(
        tenantId: tenantA,
        branchId: branchA,
      );
      expect(tenantAInvoices.length, equals(1));
      expect(tenantAInvoices.first.localId, equals('INV-A-1'));
    });

    test('ApiClient dynamically injects X-Tenant-ID and prevents header bleed upon tenant switch', () async {
      const tenantA = 'tenant-uuid-1111';
      const tenantB = 'tenant-uuid-2222';
      const branchA = 'branch-uuid-aaaa';
      const branchB = 'branch-uuid-bbbb';

      // Interceptor to capture outgoing headers
      RequestOptions? lastCapturedRequest;
      apiClient.rawDio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            lastCapturedRequest = options;
            // Short-circuit to avoid real network call
            return handler.resolve(
              Response(
                requestOptions: options,
                data: {'status': 'ok'},
                statusCode: 200,
              ),
            );
          },
        ),
      );

      // 1. Set scope to Tenant A and dispatch request
      apiClient.setScope(tenantId: tenantA, branchId: branchA);
      await apiClient.get('/api/v1/test');

      expect(lastCapturedRequest, isNotNull);
      expect(lastCapturedRequest!.headers['X-Tenant-ID'], equals(tenantA));
      expect(lastCapturedRequest!.headers['X-Branch-ID'], equals(branchA));
      expect(lastCapturedRequest!.headers['X-Correlation-ID'], isNotNull);

      // 2. Switch scope to Tenant B and dispatch request
      apiClient.setScope(tenantId: tenantB, branchId: branchB);
      await apiClient.get('/api/v1/test');

      expect(lastCapturedRequest, isNotNull);
      expect(lastCapturedRequest!.headers['X-Tenant-ID'], equals(tenantB));
      expect(lastCapturedRequest!.headers['X-Branch-ID'], equals(branchB));
      expect(lastCapturedRequest!.headers['X-Tenant-ID'], isNot(equals(tenantA)));
    });
  });
}
