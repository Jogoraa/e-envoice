import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Branch Isolation & Scoping Verification', () {
    late AppDatabase db;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async {
      await db.close();
    });

    test('Branches under the same tenant must remain strictly isolated in local queries and outbox', () async {
      const tenantId = '00000000-0000-0000-0000-000000000001';
      const branchMain = '00000000-0000-0000-0000-000000000010'; // HO-001
      const branchBole = '00000000-0000-0000-0000-000000000011'; // BR-002

      // 1. Seed Main Branch Invoice
      await db.into(db.localInvoices).insert(
            LocalInvoicesCompanion.insert(
              localId: 'INV-MAIN-001',
              tenantId: tenantId,
              branchId: branchMain,
              documentNumber: 'DRAFT-HO-001',
              invoiceDate: DateTime.now(),
              transactionType: 'B2C',
              paymentMode: 'CASH',
              preTaxTotal: 500.0,
              taxTotal: 75.0,
              grandTotal: 575.0,
              syncStatus: 'offlineDraft',
              rawPayload: '{"branch": "HO-001"}',
              createdAt: DateTime.now(),
            ),
          );

      // 2. Seed Bole Branch Invoice
      await db.into(db.localInvoices).insert(
            LocalInvoicesCompanion.insert(
              localId: 'INV-BOLE-001',
              tenantId: tenantId,
              branchId: branchBole,
              documentNumber: 'DRAFT-BR-002',
              invoiceDate: DateTime.now(),
              transactionType: 'B2B',
              paymentMode: 'TELEBIRR',
              preTaxTotal: 1000.0,
              taxTotal: 150.0,
              grandTotal: 1150.0,
              syncStatus: 'offlineDraft',
              rawPayload: '{"branch": "BR-002"}',
              createdAt: DateTime.now(),
            ),
          );

      // 3. Seed outbox operation specifically for Bole branch
      await db.into(db.outboxOperations).insert(
            OutboxOperationsCompanion.insert(
              operationId: 'OP-BOLE-001',
              idempotencyKey: 'IDEMP-BOLE-001',
              tenantId: tenantId,
              branchId: branchBole,
              operationType: 'CREATE_INVOICE',
              endpoint: '/api/v1/offline/sync',
              payloadJson: '{"branch": "BOLE"}',
              syncState: 'pending',
              attemptCount: const Value(0),
              bufferedAt: DateTime.now(),
              createdAt: DateTime.now(),
            ),
          );

      // 4. Query Main Branch context
      final mainInvoices = await db.getScopedInvoices(tenantId: tenantId, branchId: branchMain);
      final mainOutbox = await db.getPendingOutbox(tenantId: tenantId, branchId: branchMain);

      expect(mainInvoices.length, equals(1));
      expect(mainInvoices.first.localId, equals('INV-MAIN-001'));
      expect(mainOutbox, isEmpty, reason: 'Main Branch must not pick up Bole Branch outbox operations');

      // 5. Query Bole Branch context
      final boleInvoices = await db.getScopedInvoices(tenantId: tenantId, branchId: branchBole);
      final boleOutbox = await db.getPendingOutbox(tenantId: tenantId, branchId: branchBole);

      expect(boleInvoices.length, equals(1));
      expect(boleInvoices.first.localId, equals('INV-BOLE-001'));
      expect(boleOutbox.length, equals(1));
      expect(boleOutbox.first.operationId, equals('OP-BOLE-001'));
    });
  });
}
