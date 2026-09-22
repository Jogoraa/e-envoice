import 'package:uuid/uuid.dart';
import '../../core/networking/api_client.dart';
import '../../domain/inventory/models/inventory_models.dart';
import '../local/database/app_database.dart';

abstract class InventoryRepository {
  Future<List<InventorySnapshotModel>> getInventorySnapshots({
    required String tenantId,
    required String branchId,
    String? query,
    bool? lowStockOnly,
  });

  Future<StockMovementModel> recordStockMovement({
    required String tenantId,
    required String branchId,
    required String productId,
    required String itemCode,
    required String productName,
    required StockMovementType movementType,
    required double quantity,
    required String referenceNumber,
    required String reason,
    String? idempotencyKey,
  });

  Future<List<StockMovementModel>> getMovementHistory({
    required String tenantId,
    required String branchId,
    String? productId,
  });
}

class InventoryRepositoryImpl implements InventoryRepository {
  final ApiClient apiClient;
  final AppDatabase db;
  static const Uuid _uuid = Uuid();

  final List<InventorySnapshotModel> _snapshots = [];
  final List<StockMovementModel> _movements = [];

  InventoryRepositoryImpl({
    required this.apiClient,
    required this.db,
  }) {
    _initSeedData();
  }

  void _initSeedData() {
    const tenantId = '00000000-0000-0000-0000-000000000001';
    const branchHeadOffice = '00000000-0000-0000-0000-000000000010';
    final now = DateTime.now();

    _snapshots.addAll([
      InventorySnapshotModel(
        productId: 'prod-001',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-TEFF-01',
        productName: 'Magna White Teff 50kg (የማኛ ነጭ ጤፍ)',
        unit: 'BAG',
        availableQuantity: 120.0,
        reservedQuantity: 10.0,
        reorderPoint: 20.0,
        lastReconciledAt: now.subtract(const Duration(hours: 1)),
      ),
      InventorySnapshotModel(
        productId: 'prod-002',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-OIL-01',
        productName: 'Pure Sunflower Cooking Oil 5L (የሱፍ ዘይት)',
        unit: 'BOTTLE',
        availableQuantity: 45.0,
        reservedQuantity: 5.0,
        reorderPoint: 10.0,
        lastReconciledAt: now.subtract(const Duration(hours: 2)),
      ),
      InventorySnapshotModel(
        productId: 'prod-003',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-COFFEE-01',
        productName: 'Yirgacheffe Roasted Coffee Beans 1kg (ይርጋጨፌ ቡና)',
        unit: 'KG',
        availableQuantity: 4.0, // Low stock
        reservedQuantity: 0.0,
        reorderPoint: 10.0,
        lastReconciledAt: now.subtract(const Duration(minutes: 30)),
      ),
    ]);

    _movements.add(
      StockMovementModel(
        id: 'mov-001',
        idempotencyKey: 'IDEMP-STOCK-001',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        productId: 'prod-001',
        itemCode: 'ITM-TEFF-01',
        productName: 'Magna White Teff 50kg',
        movementType: StockMovementType.receive,
        quantity: 50.0,
        referenceNumber: 'PO-2026-0881',
        reason: 'Vendor delivery receipt',
        createdAt: now.subtract(const Duration(days: 1)),
        isSynced: true,
      ),
    );
  }

  @override
  Future<List<InventorySnapshotModel>> getInventorySnapshots({
    required String tenantId,
    required String branchId,
    String? query,
    bool? lowStockOnly,
  }) async {
    return _snapshots.where((s) {
      if (s.tenantId != tenantId) return false;
      if (s.branchId != branchId) return false;
      if (lowStockOnly == true && !s.isLowStock && !s.isOutOfStock) return false;
      if (query != null && query.isNotEmpty) {
        final q = query.toLowerCase();
        return s.itemCode.toLowerCase().contains(q) || s.productName.toLowerCase().contains(q);
      }
      return true;
    }).toList();
  }

  @override
  Future<StockMovementModel> recordStockMovement({
    required String tenantId,
    required String branchId,
    required String productId,
    required String itemCode,
    required String productName,
    required StockMovementType movementType,
    required double quantity,
    required String referenceNumber,
    required String reason,
    String? idempotencyKey,
  }) async {
    final stableKey = idempotencyKey ?? _uuid.v4();
    final movement = StockMovementModel(
      id: 'mov-${_uuid.v4().substring(0, 8)}',
      idempotencyKey: stableKey,
      tenantId: tenantId,
      branchId: branchId,
      productId: productId,
      itemCode: itemCode,
      productName: productName,
      movementType: movementType,
      quantity: quantity,
      referenceNumber: referenceNumber,
      reason: reason,
      createdAt: DateTime.now(),
      isSynced: false,
    );

    _movements.insert(0, movement);

    // Update local snapshot cache (non-authoritative preview)
    final index = _snapshots.indexWhere((s) => s.productId == productId && s.tenantId == tenantId && s.branchId == branchId);
    if (index >= 0) {
      final current = _snapshots[index];
      double updatedQty = current.availableQuantity;
      if (movementType == StockMovementType.receive || movementType == StockMovementType.customerReturn) {
        updatedQty += quantity;
      } else {
        updatedQty -= quantity;
      }
      _snapshots[index] = InventorySnapshotModel(
        productId: current.productId,
        tenantId: current.tenantId,
        branchId: current.branchId,
        itemCode: current.itemCode,
        productName: current.productName,
        unit: current.unit,
        availableQuantity: updatedQty,
        reservedQuantity: current.reservedQuantity,
        reorderPoint: current.reorderPoint,
        lastReconciledAt: DateTime.now(),
      );
    }

    return movement;
  }

  @override
  Future<List<StockMovementModel>> getMovementHistory({
    required String tenantId,
    required String branchId,
    String? productId,
  }) async {
    return _movements.where((m) {
      if (m.tenantId != tenantId) return false;
      if (m.branchId != branchId) return false;
      if (productId != null && m.productId != productId) return false;
      return true;
    }).toList();
  }
}
