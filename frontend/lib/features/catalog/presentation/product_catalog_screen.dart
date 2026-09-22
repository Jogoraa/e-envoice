import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../domain/catalog/models/catalog_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class ProductCatalogScreen extends ConsumerStatefulWidget {
  const ProductCatalogScreen({super.key});

  @override
  ConsumerState<ProductCatalogScreen> createState() => _ProductCatalogScreenState();
}

class _ProductCatalogScreenState extends ConsumerState<ProductCatalogScreen> {
  final _searchController = TextEditingController();
  String? _selectedCategory;
  bool _isLoading = false;
  List<ProductModel> _products = [];
  List<CategoryModel> _categories = [];

  @override
  void initState() {
    super.initState();
    _loadCatalog();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadCatalog() async {
    setState(() => _isLoading = true);
    try {
      final tenant = ref.read(tenantContextProvider);
      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

      final repo = ref.read(catalogRepositoryProvider);
      final prods = await repo.getProducts(
        tenantId: tenantId,
        branchId: branchId,
        query: _searchController.text.trim(),
        category: _selectedCategory,
      );
      final cats = await repo.getCategories(tenantId: tenantId);

      setState(() {
        _products = prods;
        _categories = cats.where((c) => c.type == 'PRODUCT' || c.type == 'ALL').toList();
        if (_categories.isEmpty) {
          _categories = [
            CategoryModel(id: 'cat-01', tenantId: tenantId, code: 'BEV', name: 'Beverages (መጠጦች)', type: 'PRODUCT'),
            CategoryModel(id: 'cat-02', tenantId: tenantId, code: 'GRAIN', name: 'Grains & Cereals (እህሎች)', type: 'PRODUCT'),
          ];
        }
        _isLoading = false;
      });
    } catch (_) {
      setState(() => _isLoading = false);
    }
  }

  void _openRegisterProductDialog() {
    final formKey = GlobalKey<FormState>();
    final codeCtrl = TextEditingController();
    final skuCtrl = TextEditingController();
    final barcodeCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final priceCtrl = TextEditingController();

    final tenant = ref.read(tenantContextProvider);
    final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';

    if (_categories.isEmpty) {
      _categories = [
        CategoryModel(id: 'cat-01', tenantId: tenantId, code: 'BEV', name: 'Beverages (መጠጦች)', type: 'PRODUCT'),
        CategoryModel(id: 'cat-02', tenantId: tenantId, code: 'GRAIN', name: 'Grains & Cereals (እህሎች)', type: 'PRODUCT'),
      ];
    }
    String category = _categories.first.code;
    String unit = 'PCS';
    TaxClassification tax = TaxClassification.vat15;
    bool trackStock = true;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (dialogCtx, setDialogState) => AlertDialog(
          title: Text('REGISTER PRODUCT IN CATALOG', style: AppTypography.h2()),
          content: SizedBox(
            width: 520,
            child: Form(
              key: formKey,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextFormField(
                      controller: codeCtrl,
                      decoration: const InputDecoration(labelText: 'Item Code (Unique) *'),
                      validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: skuCtrl,
                            decoration: const InputDecoration(labelText: 'SKU *'),
                            validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: barcodeCtrl,
                            decoration: const InputDecoration(labelText: 'Barcode (EAN/UPC)'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: descCtrl,
                      decoration: const InputDecoration(labelText: 'Product Description / Name *'),
                      validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            value: category,
                            decoration: InputDecoration(
                              labelText: 'Category *',
                              suffixIcon: IconButton(
                                icon: const Icon(Icons.add_circle_outline, size: 20, color: AppColors.navy900),
                                tooltip: 'Create new category',
                                onPressed: () async {
                                  final newCodeCtrl = TextEditingController();
                                  final newNameCtrl = TextEditingController();
                                  await showDialog(
                                    context: context,
                                    builder: (innerCtx) => AlertDialog(
                                      title: Text('QUICK ADD CATEGORY', style: AppTypography.h3()),
                                      content: Column(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          TextField(
                                            controller: newCodeCtrl,
                                            decoration: const InputDecoration(labelText: 'Code (e.g. SNACK) *'),
                                            textCapitalization: TextCapitalization.characters,
                                          ),
                                          const SizedBox(height: 12),
                                          TextField(
                                            controller: newNameCtrl,
                                            decoration: const InputDecoration(labelText: 'Name (e.g. Snacks & Pastries) *'),
                                          ),
                                        ],
                                      ),
                                      actions: [
                                        TextButton(onPressed: () => Navigator.pop(innerCtx), child: const Text('Cancel')),
                                        ElevatedButton(
                                          onPressed: () async {
                                            if (newCodeCtrl.text.trim().isEmpty || newNameCtrl.text.trim().isEmpty) return;
                                            final repo = ref.read(catalogRepositoryProvider);
                                            final created = await repo.createCategory(
                                              tenantId: tenantId,
                                              code: newCodeCtrl.text.trim().toUpperCase(),
                                              name: newNameCtrl.text.trim(),
                                              type: 'PRODUCT',
                                            );
                                            if (innerCtx.mounted) {
                                              Navigator.pop(innerCtx);
                                            }
                                            setDialogState(() {
                                              if (!_categories.any((c) => c.code == created.code)) {
                                                _categories.add(created);
                                              }
                                              category = created.code;
                                            });
                                            setState(() {});
                                          },
                                          child: const Text('Add'),
                                        ),
                                      ],
                                    ),
                                  );
                                },
                              ),
                            ),
                            items: _categories
                                .map((c) => DropdownMenuItem(value: c.code, child: Text(c.name, overflow: TextOverflow.ellipsis)))
                                .toList(),
                            onChanged: (v) => setDialogState(() => category = v ?? category),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: unit,
                            decoration: const InputDecoration(labelText: 'Unit'),
                            items: const [
                              DropdownMenuItem(value: 'PCS', child: Text('PCS (ቁራጭ)')),
                              DropdownMenuItem(value: 'KG', child: Text('KG (ኪ.ግ)')),
                              DropdownMenuItem(value: 'BAG', child: Text('BAG (ጆንያ)')),
                              DropdownMenuItem(value: 'BOTTLE', child: Text('BOTTLE (ጠርሙስ)')),
                              DropdownMenuItem(value: 'LITER', child: Text('LITER (ሊትር)')),
                            ],
                            onChanged: (v) => setDialogState(() => unit = v ?? unit),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: priceCtrl,
                          decoration: const InputDecoration(labelText: 'Unit Price (ETB) *'),
                          keyboardType: TextInputType.number,
                          validator: (v) => v == null || double.tryParse(v) == null ? 'Valid number required' : null,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: DropdownButtonFormField<TaxClassification>(
                          initialValue: tax,
                          decoration: const InputDecoration(labelText: 'Tax Classification'),
                          items: TaxClassification.values
                              .map((t) => DropdownMenuItem(value: t, child: Text(t.code)))
                              .toList(),
                          onChanged: (v) => tax = v ?? tax,
                        ),
                      ),
                    ],
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

              final newProd = ProductModel(
                id: 'temp',
                tenantId: tenantId,
                branchId: branchId,
                itemCode: codeCtrl.text.trim(),
                sku: skuCtrl.text.trim(),
                barcode: barcodeCtrl.text.trim().isNotEmpty ? barcodeCtrl.text.trim() : null,
                description: descCtrl.text.trim(),
                category: category,
                unit: unit,
                unitPrice: double.parse(priceCtrl.text.trim()),
                taxClassification: tax,
                trackStock: trackStock,
                stockQuantity: 0.0,
              );

              final repo = ref.read(catalogRepositoryProvider);
              await repo.registerProduct(tenantId: tenantId, branchId: branchId, product: newProd);
              if (ctx.mounted) {
                Navigator.pop(ctx);
              }
              if (mounted) {
                _loadCatalog();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Product registered in catalog successfully')),
                );
              }
            },
            child: const Text('Register Product'),
          ),
        ],
      ),
    ),
  );
}

  @override
  Widget build(BuildContext context) {
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
                      Text('PRODUCT CATALOG & PRICING', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'Authorized sellable physical goods and statutory tax classifications',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  onPressed: _openRegisterProductDialog,
                  icon: const Icon(Icons.add_box_outlined, size: 16),
                  label: const Text('Register Product'),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Filter Bar
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule, width: 1),
              ),
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final isCompact = constraints.maxWidth < 650;
                  if (isCompact) {
                    return Column(
                      children: [
                        TextField(
                          controller: _searchController,
                          decoration: const InputDecoration(
                            hintText: 'Search by item code, description, SKU, or barcode...',
                            prefixIcon: Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                          ),
                          onChanged: (_) => _loadCatalog(),
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: DropdownButtonFormField<String?>(
                                isExpanded: true,
                                initialValue: _selectedCategory,
                                decoration: const InputDecoration(labelText: 'Category Filter'),
                                items: [
                                  const DropdownMenuItem(value: null, child: Text('All Categories')),
                                  ..._categories.map((c) => DropdownMenuItem(value: c.code, child: Text(c.name, overflow: TextOverflow.ellipsis))),
                                ],
                                onChanged: (v) {
                                  setState(() => _selectedCategory = v);
                                  _loadCatalog();
                                },
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              icon: const Icon(Icons.refresh),
                              tooltip: 'Refresh catalog',
                              onPressed: _loadCatalog,
                            ),
                          ],
                        ),
                      ],
                    );
                  }
                  return Row(
                    children: [
                      Expanded(
                        flex: 3,
                        child: TextField(
                          controller: _searchController,
                          decoration: const InputDecoration(
                            hintText: 'Search by item code, description, SKU, or barcode...',
                            prefixIcon: Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                          ),
                          onChanged: (_) => _loadCatalog(),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        flex: 2,
                        child: DropdownButtonFormField<String?>(
                          isExpanded: true,
                          initialValue: _selectedCategory,
                          decoration: const InputDecoration(labelText: 'Category Filter'),
                          items: [
                            const DropdownMenuItem(value: null, child: Text('All Categories')),
                            ..._categories.map((c) => DropdownMenuItem(value: c.code, child: Text(c.name, overflow: TextOverflow.ellipsis))),
                          ],
                          onChanged: (v) {
                            setState(() => _selectedCategory = v);
                            _loadCatalog();
                          },
                        ),
                      ),
                      const SizedBox(width: 16),
                      IconButton(
                        icon: const Icon(Icons.refresh),
                        tooltip: 'Refresh catalog',
                        onPressed: _loadCatalog,
                      ),
                    ],
                  );
                },
              ),
            ),
            const SizedBox(height: 16),

            // Products Data Table
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
                              DataColumn(label: Text('Item Code / SKU')),
                              DataColumn(label: Text('Description')),
                              DataColumn(label: Text('Category')),
                              DataColumn(label: Text('Unit Price (ETB)')),
                              DataColumn(label: Text('Tax Rate')),
                              DataColumn(label: Text('Stock Status')),
                              DataColumn(label: Text('Status')),
                            ],
                            rows: _products.map((p) {
                              Color stockColor = AppColors.green700;
                              String stockLabel = 'In Stock (${p.stockQuantity.toInt()} ${p.unit})';
                              if (p.isOutOfStock) {
                                stockColor = AppColors.red600;
                                stockLabel = 'Out of Stock (0 ${p.unit})';
                              } else if (p.isLowStock) {
                                stockColor = AppColors.amber600;
                                stockLabel = 'Low Stock (${p.stockQuantity.toInt()} ${p.unit})';
                              }

                              return DataRow(
                                cells: [
                                  DataCell(
                                    Column(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(p.itemCode, style: AppTypography.mono(weight: FontWeight.w600)),
                                        Text(p.sku, style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                      ],
                                    ),
                                  ),
                                  DataCell(
                                    Text(p.description, style: AppTypography.uiLabelBold()),
                                  ),
                                  DataCell(Text(p.category, style: AppTypography.bodySmall())),
                                  DataCell(
                                    Text(
                                      'ETB ${p.unitPrice.toStringAsFixed(2)}',
                                      style: AppTypography.mono(color: AppColors.ink, weight: FontWeight.w600),
                                    ),
                                  ),
                                  DataCell(
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: AppColors.navy900.withValues(alpha: 0.06),
                                        borderRadius: BorderRadius.circular(2),
                                      ),
                                      child: Text(
                                        p.taxClassification.code,
                                        style: AppTypography.monoSmall(color: AppColors.navy900, weight: FontWeight.w600),
                                      ),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      stockLabel,
                                      style: AppTypography.bodySmall(color: stockColor).copyWith(fontWeight: FontWeight.w500),
                                    ),
                                  ),
                                  DataCell(
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: p.isActive
                                            ? AppColors.green700.withValues(alpha: 0.08)
                                            : AppColors.inkMuted.withValues(alpha: 0.08),
                                        borderRadius: BorderRadius.circular(2),
                                        border: Border.all(
                                          color: p.isActive
                                              ? AppColors.green700.withValues(alpha: 0.3)
                                              : AppColors.inkMuted.withValues(alpha: 0.3),
                                        ),
                                      ),
                                      child: Text(
                                        p.isActive ? 'Active' : 'Inactive',
                                        style: AppTypography.uiLabel(
                                          color: p.isActive ? AppColors.green700 : AppColors.inkMuted,
                                        ),
                                      ),
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
