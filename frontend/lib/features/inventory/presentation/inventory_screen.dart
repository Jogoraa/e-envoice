import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../domain/inventory/models/inventory_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class InventoryScreen extends ConsumerStatefulWidget {
  const InventoryScreen({super.key});

  @override
  ConsumerState<InventoryScreen> createState() => _InventoryScreenState();
}

class _InventoryScreenState extends ConsumerState<InventoryScreen> {
  final _searchController = TextEditingController();
  bool _lowStockFilter = false;
  bool _isLoading = false;
  List<InventorySnapshotModel> _snapshots = [];

  @override
  void initState() {
    super.initState();
    _loadInventory();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadInventory() async {
    setState(() => _isLoading = true);
    try {
      final tenant = ref.read(tenantContextProvider);
      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

      final repo = ref.read(inventoryRepositoryProvider);
      final list = await repo.getInventorySnapshots(
        tenantId: tenantId,
        branchId: branchId,
        query: _searchController.text.trim(),
        lowStockOnly: _lowStockFilter ? true : null,
      );

      setState(() {
        _snapshots = list;
        _isLoading = false;
      });
    } catch (_) {
      setState(() => _isLoading = false);
    }
  }

  void _openMovementDialog() {
    final formKey = GlobalKey<FormState>();
    final qtyCtrl = TextEditingController();
    final refCtrl = TextEditingController();
    final reasonCtrl = TextEditingController();
    StockMovementType selectedType = StockMovementType.receive;
    InventorySnapshotModel? selectedProduct = _snapshots.isNotEmpty ? _snapshots.first : null;

    if (selectedProduct == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No products available in this branch to record movement.')),
      );
      return;
    }

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text('RECORD STOCK MOVEMENT', style: AppTypography.h2()),
          content: SizedBox(
            width: 500,
            child: Form(
              key: formKey,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    DropdownButtonFormField<StockMovementType>(
                      initialValue: selectedType,
                      decoration: const InputDecoration(labelText: 'Movement Type *'),
                      items: StockMovementType.values
                          .map((t) => DropdownMenuItem(value: t, child: Text(t.label)))
                          .toList(),
                      onChanged: (v) {
                        if (v != null) setDialogState(() => selectedType = v);
                      },
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<InventorySnapshotModel>(
                      initialValue: selectedProduct,
                      decoration: const InputDecoration(labelText: 'Product *'),
                      items: _snapshots
                          .map((s) => DropdownMenuItem(value: s, child: Text('${s.itemCode} — ${s.productName}')))
                          .toList(),
                      onChanged: (v) {
                        if (v != null) setDialogState(() => selectedProduct = v);
                      },
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: qtyCtrl,
                            decoration: InputDecoration(
                              labelText: 'Quantity (${selectedProduct?.unit ?? 'Units'}) *',
                            ),
                            keyboardType: TextInputType.number,
                            validator: (v) => v == null || double.tryParse(v) == null || double.parse(v) <= 0
                                ? 'Enter positive quantity'
                                : null,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: refCtrl,
                            decoration: const InputDecoration(labelText: 'Reference # (PO/Transfer) *'),
                            validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: reasonCtrl,
                      decoration: const InputDecoration(labelText: 'Audit Reason / Notes *'),
                      maxLines: 2,
                      validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            OutlinedButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
            ElevatedButton(
              onPressed: () async {
                if (!formKey.currentState!.validate()) return;
                final tenant = ref.read(tenantContextProvider);
                final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
                final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

                final repo = ref.read(inventoryRepositoryProvider);
                await repo.recordStockMovement(
                  tenantId: tenantId,
                  branchId: branchId,
                  productId: selectedProduct!.productId,
                  itemCode: selectedProduct!.itemCode,
                  productName: selectedProduct!.productName,
                  movementType: selectedType,
                  quantity: double.parse(qtyCtrl.text.trim()),
                  referenceNumber: refCtrl.text.trim(),
                  reason: reasonCtrl.text.trim(),
                );

                if (ctx.mounted) {
                  Navigator.pop(ctx);
                }
                if (mounted) {
                  _loadInventory();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Stock movement recorded and queued for backend sync')),
                  );
                }
              },
              child: const Text('Record Movement'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final lowStockCount = _snapshots.where((s) => s.isLowStock || s.isOutOfStock).length;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('INVENTORY & STOCK SNAPSHOTS', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'Branch-scoped warehouse balances. Authoritative stock is reconciled by the server.',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  onPressed: _openMovementDialog,
                  icon: const Icon(Icons.sync_alt, size: 16),
                  label: const Text('Record Movement'),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Low Stock Alert Banner if any
            if (lowStockCount > 0) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: AppColors.amber600.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.amber600.withValues(alpha: 0.4)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.warning_amber_outlined, size: 20, color: AppColors.amber600),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        '$lowStockCount product(s) are below safety reorder thresholds in this branch.',
                        style: AppTypography.uiLabelBold(color: AppColors.ink),
                      ),
                    ),
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _lowStockFilter = !_lowStockFilter;
                          _loadInventory();
                        });
                      },
                      child: Text(_lowStockFilter ? 'Show All Products' : 'Filter Low Stock'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Search Bar
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule, width: 1),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _searchController,
                      decoration: const InputDecoration(
                        hintText: 'Search stock by item code or product name...',
                        prefixIcon: Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                      ),
                      onChanged: (_) => _loadInventory(),
                    ),
                  ),
                  const SizedBox(width: 16),
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    tooltip: 'Refresh stock snapshots',
                    onPressed: _loadInventory,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Inventory Ledger Table
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: _isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : SingleChildScrollView(
                        scrollDirection: Axis.horizontal,
                        child: SingleChildScrollView(
                          scrollDirection: Axis.vertical,
                          child: DataTable(
                            columns: const [
                              DataColumn(label: Text('Item Code')),
                              DataColumn(label: Text('Product Description')),
                              DataColumn(label: Text('Available Quantity')),
                              DataColumn(label: Text('Reserved')),
                              DataColumn(label: Text('Reorder Point')),
                              DataColumn(label: Text('Stock Health')),
                              DataColumn(label: Text('Last Reconciled')),
                            ],
                            rows: _snapshots.map((s) {
                              Color healthColor = AppColors.green700;
                              String healthText = 'Optimal';
                              if (s.isOutOfStock) {
                                healthColor = AppColors.red600;
                                healthText = 'Out of Stock';
                              } else if (s.isLowStock) {
                                healthColor = AppColors.amber600;
                                healthText = 'Low Stock';
                              }

                              return DataRow(
                                cells: [
                                  DataCell(Text(s.itemCode, style: AppTypography.mono(weight: FontWeight.w600))),
                                  DataCell(Text(s.productName, style: AppTypography.uiLabelBold())),
                                  DataCell(
                                    Text(
                                      '${s.availableQuantity.toStringAsFixed(1)} ${s.unit}',
                                      style: AppTypography.mono(weight: FontWeight.w700),
                                    ),
                                  ),
                                  DataCell(
                                    Text('${s.reservedQuantity.toStringAsFixed(1)} ${s.unit}', style: AppTypography.monoSmall()),
                                  ),
                                  DataCell(
                                    Text('${s.reorderPoint.toStringAsFixed(1)} ${s.unit}', style: AppTypography.monoSmall()),
                                  ),
                                  DataCell(
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: healthColor.withValues(alpha: 0.08),
                                        borderRadius: BorderRadius.circular(2),
                                        border: Border.all(color: healthColor.withValues(alpha: 0.3)),
                                      ),
                                      child: Text(
                                        healthText,
                                        style: AppTypography.uiLabel(color: healthColor),
                                      ),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      s.lastReconciledAt.toLocal().toString().substring(0, 16),
                                      style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                    ),
                                  ),
                                ],
                              );
                            }).toList(),
                          ),
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
