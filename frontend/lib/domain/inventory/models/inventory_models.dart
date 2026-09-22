/// Inventory Domain Models
/// Explicitly represents inventory as non-authoritative local snapshots and audited stock movements.
library;

enum StockMovementType {
  receive('RECEIVE', 'Stock Receiving (ግዢ ማስገባት)'),
  transfer('TRANSFER', 'Branch Transfer (ወደ ቅርንጫፍ ማስተላለፍ)'),
  adjustment('ADJUSTMENT', 'Physical Count Adjustment (የቆጠራ ማስተካከያ)'),
  customerReturn('RETURN', 'Customer Sales Return (የደንበኛ መልስ)'),
  damage('DAMAGE', 'Damaged / Expired Write-Off (የተበላሸ/ያለፈበት)');

  final String code;
  final String label;

  const StockMovementType(this.code, this.label);

  static StockMovementType fromCode(String? code) {
    if (code == null) return StockMovementType.adjustment;
    for (final val in StockMovementType.values) {
      if (val.code == code) return val;
    }
    return StockMovementType.adjustment;
  }
}

class InventorySnapshotModel {
  final String productId;
  final String tenantId;
  final String branchId;
  final String itemCode;
  final String productName;
  final String unit;
  final double availableQuantity;
  final double reservedQuantity;
  final double reorderPoint;
  final DateTime lastReconciledAt;

  const InventorySnapshotModel({
    required this.productId,
    required this.tenantId,
    required this.branchId,
    required this.itemCode,
    required this.productName,
    this.unit = 'PCS',
    required this.availableQuantity,
    this.reservedQuantity = 0.0,
    this.reorderPoint = 5.0,
    required this.lastReconciledAt,
  });

  bool get isLowStock => availableQuantity <= reorderPoint && availableQuantity > 0;
  bool get isOutOfStock => availableQuantity <= 0;
}

class StockMovementModel {
  final String id;
  final String idempotencyKey;
  final String tenantId;
  final String branchId;
  final String productId;
  final String itemCode;
  final String productName;
  final StockMovementType movementType;
  final double quantity;
  final String referenceNumber;
  final String reason;
  final DateTime createdAt;
  final bool isSynced;

  const StockMovementModel({
    required this.id,
    required this.idempotencyKey,
    required this.tenantId,
    required this.branchId,
    required this.productId,
    required this.itemCode,
    required this.productName,
    required this.movementType,
    required this.quantity,
    required this.referenceNumber,
    required this.reason,
    required this.createdAt,
    this.isSynced = false,
  });
}
