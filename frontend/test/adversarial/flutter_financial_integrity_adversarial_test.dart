import 'package:drift/native.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/connectivity/connectivity_service.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';
import 'package:ut_einvoice_client/data/repositories/invoice_repository_impl.dart';
import 'package:ut_einvoice_client/domain/invoice/models/invoice_models.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Financial Integrity, Tax Engine & Idempotency', () {
    late AppDatabase db;
    late SecureStorageService storage;
    late ApiClient apiClient;
    late ConnectivityService connectivity;
    late InvoiceRepositoryImpl repository;

    const tenantId = '00000000-0000-0000-0000-000000000001';
    const branchId = '00000000-0000-0000-0000-000000000010';

    setUp(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('dev.fluttercommunity.plus/connectivity'),
        (MethodCall methodCall) async {
          if (methodCall.method == 'check') {
            return <String>['none'];
          }
          return null;
        },
      );
      FlutterSecureStorage.setMockInitialValues({});
      db = AppDatabase(NativeDatabase.memory());
      storage = SecureStorageService();
      apiClient = ApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: storage,
      );
      // Simulate offline mode to test local queueing and draft preservation
      connectivity = ConnectivityService(
        healthEndpoint: 'http://unreachable-host:9999/health',
      );
      repository = InvoiceRepositoryImpl(
        apiClient: apiClient,
        db: db,
        connectivity: connectivity,
      );
    });

    tearDown(() async {
      await db.close();
    });

    test('ClientTaxEstimator accurately estimates 15% VAT and two-decimal rounding without claiming final authority', () {
      final items = [
        InvoiceLineDraft(
          itemCode: 'ITEM-01',
          productDescription: 'Standard Item',
          quantity: 3,
          unitPrice: 33.33,
          discount: 0.0,
          taxCode: 'VAT15',
        ),
        InvoiceLineDraft(
          itemCode: 'ITEM-02',
          productDescription: 'Exempt Item',
          quantity: 2,
          unitPrice: 50.0,
          discount: 10.0,
          taxCode: 'EXEMPT',
        ),
      ];

      final estimate = ClientTaxEstimator.estimateTotals(items);

      // Item 1: 3 * 33.33 = 99.99. VAT 15% of 99.99 = 14.9985 -> rounded to 15.00
      // Item 2: (2 * 50) - 10 = 90.00. Tax = 0.00
      // PreTax Total = 99.99 + 90.00 = 189.99
      // Tax Total = 15.00
      // Grand Total = 204.99
      expect(estimate.preTaxTotal, equals(189.99));
      expect(estimate.taxTotal, equals(15.00));
      expect(estimate.grandTotal, equals(204.99));
    });

    test('Idempotency Key Preservation: Retried or queued invoice submission must strictly retain identical idempotencyKey', () async {
      const fixedIdempotencyKey = 'IDEMP-RETRY-TEST-UUID-12345';

      final items = [
        InvoiceLineDraft(
          itemCode: 'ITEM-01',
          productDescription: 'Testing Item',
          quantity: 1,
          unitPrice: 100.0,
          taxCode: 'VAT15',
        ),
      ];

      // Submit while offline
      final draftInvoice = await repository.submitOrQueueInvoice(
        tenantId: tenantId,
        branchId: branchId,
        transactionType: 'B2C',
        paymentMode: 'CASH',
        buyer: null,
        items: items,
        existingIdempotencyKey: fixedIdempotencyKey,
      );

      // Verify client temporary document number begins with DRAFT prefix
      expect(draftInvoice.documentNumber, startsWith('DRAFT-'));
      expect(draftInvoice.status, equals('offlineDraft'));

      // Verify outbox record preserved the exact idempotency key
      final pendingOutbox = await db.getPendingOutbox(tenantId: tenantId, branchId: branchId);
      expect(pendingOutbox.length, equals(1));
      expect(pendingOutbox.first.idempotencyKey, equals(fixedIdempotencyKey));
    });
  });
}
