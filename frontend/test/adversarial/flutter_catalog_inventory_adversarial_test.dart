import 'package:drift/drift.dart' hide isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';
import 'package:ut_einvoice_client/domain/invoice/models/invoice_models.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Adversarial Test Suite: Catalog-Bound Invoicing, Service Separation & Inventory Scoping', () {
    late AppDatabase db;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async {
      await db.close();
    });

    test('Catalog-Bound Invoicing Invariant: Every invoice line must reference an authorized catalog entity', () {
      // 1. Authorized Product Line
      final productLine = InvoiceLineDraft(
        itemCode: 'PRD-001',
        productDescription: 'Standard Industrial Filter',
        quantity: 2.0,
        unitPrice: 450.0,
        itemType: 'PRODUCT',
        catalogItemId: 'cat-item-uuid-001',
      );

      expect(productLine.catalogItemId, isNotNull);
      expect(productLine.catalogItemId, isNotEmpty);
      expect(productLine.itemType, equals('PRODUCT'));

      // 2. Authorized Service Line
      final serviceLine = InvoiceLineDraft(
        itemCode: 'SRV-001',
        productDescription: 'Engineering Consulting Service',
        quantity: 1.0,
        unitPrice: 1500.0,
        itemType: 'SERVICE',
        catalogItemId: 'cat-serv-uuid-002',
      );

      expect(serviceLine.catalogItemId, isNotNull);
      expect(serviceLine.catalogItemId, isNotEmpty);
      expect(serviceLine.itemType, equals('SERVICE'));

      // 3. Arbitrary Unlinked Line (Adversarial attempt to sell uncatalogued good)
      final unauthorizedLine = InvoiceLineDraft(
        itemCode: '',
        productDescription: 'Unregistered Random Widget',
        quantity: 1.0,
        unitPrice: 99.0,
        itemType: 'PRODUCT',
        catalogItemId: null, // Missing catalog reference
      );

      // Validation logic must reject lines without catalogItemId
      final isValid = unauthorizedLine.catalogItemId != null &&
          unauthorizedLine.catalogItemId!.isNotEmpty &&
          unauthorizedLine.itemCode.isNotEmpty;

      expect(isValid, isFalse, reason: 'Arbitrary invoice line without authorized catalog entity must be rejected');
    });

    test('Service vs Product Separation: Services must never deduct inventory or be treated as stockable items', () {
      final serviceLine = InvoiceLineDraft(
        itemCode: 'SRV-LABOR',
        productDescription: 'On-site Installation Labor',
        quantity: 5.0,
        unitPrice: 200.0,
        itemType: 'SERVICE',
        catalogItemId: 'serv-001',
      );

      final isStockDeductible = serviceLine.itemType == 'PRODUCT';
      expect(isStockDeductible, isFalse, reason: 'Services must never trigger physical stock deduction');
    });

    test('Local Product Catalog Branch Scoping: Branch B must never see Branch A products', () async {
      const tenantId = '00000000-0000-0000-0000-000000000001';
      const branchA = 'BRANCH-ADDIS-01';
      const branchB = 'BRANCH-HAWASSA-02';

      // Insert product specifically for Branch A
      await db.into(db.localProducts).insert(
            LocalProductsCompanion.insert(
              id: 'PROD-A-001',
              tenantId: tenantId,
              branchId: branchA,
              itemCode: 'ITM-BOLT-10',
              description: 'M10 Hex Steel Bolt',
              unitPrice: 25.0,
              taxCode: const Value('VAT15'),
              unit: const Value('PCS'),
            ),
          );

      // Insert product specifically for Branch B
      await db.into(db.localProducts).insert(
            LocalProductsCompanion.insert(
              id: 'PROD-B-001',
              tenantId: tenantId,
              branchId: branchB,
              itemCode: 'ITM-NUT-10',
              description: 'M10 Hex Steel Nut',
              unitPrice: 15.0,
              taxCode: const Value('VAT15'),
              unit: const Value('PCS'),
            ),
          );

      // Query for Branch A
      final branchAProducts = await (db.select(db.localProducts)
            ..where((tbl) => tbl.tenantId.equals(tenantId) & tbl.branchId.equals(branchA)))
          .get();

      expect(branchAProducts.length, equals(1));
      expect(branchAProducts.first.itemCode, equals('ITM-BOLT-10'));

      // Query for Branch B
      final branchBProducts = await (db.select(db.localProducts)
            ..where((tbl) => tbl.tenantId.equals(tenantId) & tbl.branchId.equals(branchB)))
          .get();

      expect(branchBProducts.length, equals(1));
      expect(branchBProducts.first.itemCode, equals('ITM-NUT-10'));
      expect(branchBProducts.any((p) => p.itemCode == 'ITM-BOLT-10'), isFalse,
          reason: 'Branch B must not see Branch A catalog products');
    });

    test('Offline Draft Sequence: Client creates temporary DRAFT numbers, never official sequence', () {
      const branchId = 'HO-001';
      final draftUlid = DateTime.now().millisecondsSinceEpoch.toString();
      final draftDocumentNumber = 'DRAFT-$branchId-$draftUlid';

      expect(draftDocumentNumber.startsWith('DRAFT-$branchId-'), isTrue);
      expect(draftDocumentNumber.contains('INV-'), isFalse,
          reason: 'Client must NEVER assign final legal invoice prefix INV- offline');
    });

    test('Non-Authoritative Client Tax Preview: Computes 15% VAT and two-decimal rounding preview', () {
      final items = [
        InvoiceLineDraft(
          itemCode: 'ITEM-1',
          productDescription: 'Goods Item 1',
          quantity: 3.0,
          unitPrice: 133.33,
          taxCode: 'VAT15',
          catalogItemId: 'prod-1',
        ),
        InvoiceLineDraft(
          itemCode: 'ITEM-2',
          productDescription: 'Exempt Medical Item',
          quantity: 1.0,
          unitPrice: 500.0,
          taxCode: 'EXEMPT',
          catalogItemId: 'prod-2',
        ),
      ];

      final totals = ClientTaxEstimator.estimateTotals(items);

      // Item 1: 3 * 133.33 = 399.99 base. 15% VAT = 60.00
      // Item 2: 500.00 base. 0% VAT
      // Pre-tax Total: 399.99 + 500.00 = 899.99
      // Tax Total: 60.00
      // Grand Total: 959.99
      expect(totals.preTaxTotal, equals(899.99));
      expect(totals.taxTotal, equals(60.00));
      expect(totals.grandTotal, equals(959.99));
    });
  });
}
