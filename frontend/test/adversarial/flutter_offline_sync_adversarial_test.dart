import 'package:dio/dio.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/connectivity/connectivity_service.dart';
import 'package:ut_einvoice_client/core/device/device_identity_service.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/core/synchronization/sync_engine.dart';
import 'package:ut_einvoice_client/core/synchronization/sync_state_notifier.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Offline Synchronization & Compliance Protocols', () {
    late AppDatabase db;
    late SecureStorageService storage;
    late DeviceIdentityService deviceIdentity;
    late ApiClient apiClient;
    late ConnectivityService connectivity;
    late SyncStateNotifier syncNotifier;
    late SyncEngine syncEngine;

    const tenantId = '00000000-0000-0000-0000-000000000001';
    const branchId = '00000000-0000-0000-0000-000000000010';

    setUp(() {
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
      db = AppDatabase(NativeDatabase.memory());
      storage = SecureStorageService();
      deviceIdentity = DeviceIdentityService(storage);
      apiClient = ApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: storage,
      );
      connectivity = ConnectivityService();
      syncNotifier = SyncStateNotifier();
      syncEngine = SyncEngine(
        db: db,
        apiClient: apiClient,
        deviceIdentity: deviceIdentity,
        connectivity: connectivity,
        syncNotifier: syncNotifier,
      );
    });

    tearDown(() async {
      await db.close();
    });

    test('Directive No. 1142/2026 Art. 4(4): Transactions buffered > 72 hours must be marked OFFLINE_BATCH_EXPIRED and blocked from auto-sync', () async {
      // 1. Insert an expired transaction (73 hours ago)
      final expiredTime = DateTime.now().subtract(const Duration(hours: 73));
      await db.into(db.outboxOperations).insert(
            OutboxOperationsCompanion.insert(
              operationId: 'OP-EXPIRED-73H',
              idempotencyKey: 'IDEMP-EXP-1',
              tenantId: tenantId,
              branchId: branchId,
              operationType: 'CREATE_INVOICE',
              endpoint: '/api/v1/offline/sync',
              payloadJson: '{"test": "expired"}',
              offlineSeqNo: Value(BigInt.from(101)),
              syncState: 'pending',
              attemptCount: const Value(0),
              bufferedAt: expiredTime,
              createdAt: expiredTime,
            ),
          );

      // 2. Trigger sync
      await syncEngine.syncOutbox(tenantId: tenantId, branchId: branchId);

      // 3. Verify operation was marked permanentFailure with OFFLINE_BATCH_EXPIRED
      final allOps = await db.select(db.outboxOperations).get();
      expect(allOps.length, equals(1));
      final op = allOps.first;
      expect(op.syncState, equals('permanentFailure'));
      expect(op.lastError, contains('OFFLINE_BATCH_EXPIRED'));
    });

    test('409 Conflict Handling: Backend reporting DUPLICATE_OFFLINE_SEQUENCE must reconcile cleanly without data drop or infinite loop', () async {
      // Intercept Dio to simulate HTTP 409 Conflict from backend
      apiClient.rawDio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            if (options.path.contains('/offline/sync')) {
              return handler.reject(
                DioException(
                  requestOptions: options,
                  response: Response(
                    requestOptions: options,
                    statusCode: 409,
                    data: {
                      'status': 409,
                      'code': 'DUPLICATE_OFFLINE_SEQUENCE',
                      'message': 'Transaction sequence was already accepted and reconciled by server.',
                    },
                  ),
                  type: DioExceptionType.badResponse,
                ),
              );
            }
            return handler.next(options);
          },
        ),
      );

      // 1. Insert an offline invoice in local db
      await db.into(db.localInvoices).insert(
            LocalInvoicesCompanion.insert(
              localId: 'INV-409-TEST',
              tenantId: tenantId,
              branchId: branchId,
              documentNumber: 'DRAFT-HO-001-999',
              invoiceDate: DateTime.now(),
              transactionType: 'B2C',
              paymentMode: 'CASH',
              preTaxTotal: 100.0,
              taxTotal: 15.0,
              grandTotal: 115.0,
              syncStatus: 'offlineDraft',
              rawPayload: '{}',
              createdAt: DateTime.now(),
            ),
          );

      // 2. Insert corresponding outbox operation (buffered 2 hours ago - within 72h limit)
      await db.into(db.outboxOperations).insert(
            OutboxOperationsCompanion.insert(
              operationId: 'INV-409-TEST',
              idempotencyKey: 'IDEMP-409-TEST',
              tenantId: tenantId,
              branchId: branchId,
              operationType: 'CREATE_INVOICE',
              endpoint: '/api/v1/offline/sync',
              payloadJson: '{"invoiceId": "INV-409-TEST"}',
              offlineSeqNo: Value(BigInt.from(102)),
              syncState: 'pending',
              attemptCount: const Value(0),
              bufferedAt: DateTime.now().subtract(const Duration(hours: 2)),
              createdAt: DateTime.now().subtract(const Duration(hours: 2)),
            ),
          );

      // 3. Trigger sync
      await syncEngine.syncOutbox(tenantId: tenantId, branchId: branchId);

      // 4. Verify the outbox operation was successfully deleted / reconciled
      final pendingOps = await db.getPendingOutbox(tenantId: tenantId, branchId: branchId);
      expect(pendingOps, isEmpty, reason: '409 Conflict must reconcile outbox operation as resolved');

      // 5. Verify local invoice status updated to 'synced'
      final invoices = await db.getScopedInvoices(tenantId: tenantId, branchId: branchId);
      expect(invoices.first.syncStatus, equals('synced'));
    });
  });
}
