import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../domain/catalog/models/catalog_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class CategoryManagementScreen extends ConsumerStatefulWidget {
  const CategoryManagementScreen({super.key});

  @override
  ConsumerState<CategoryManagementScreen> createState() => _CategoryManagementScreenState();
}

class _CategoryManagementScreenState extends ConsumerState<CategoryManagementScreen> {
  final _searchController = TextEditingController();
  String _selectedTypeFilter = 'ALL';
  bool _isLoading = false;
  List<CategoryModel> _categories = [];

  @override
  void initState() {
    super.initState();
    _loadCategories();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadCategories() async {
    setState(() => _isLoading = true);
    try {
      final tenant = ref.read(tenantContextProvider);
      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final repo = ref.read(catalogRepositoryProvider);
      final cats = await repo.getCategories(tenantId: tenantId);

      setState(() {
        _categories = cats;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
    }
  }

  void _openCreateCategoryDialog({VoidCallback? onCreated}) {
    final formKey = GlobalKey<FormState>();
    final codeCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    String categoryType = 'PRODUCT';

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('CREATE MASTER DATA CATEGORY', style: AppTypography.h2()),
          content: SizedBox(
            width: 460,
            child: Form(
              key: formKey,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Categories structure your statutory goods and services, ensuring consistent tax handling across POS and ERP invoices.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: codeCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Category Code *',
                        hintText: 'e.g., PHARMA, BEV, ELEC',
                      ),
                      textCapitalization: TextCapitalization.characters,
                      validator: (v) => (v == null || v.trim().isEmpty) ? 'Category code is required' : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: nameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Category Name *',
                        hintText: 'e.g., Pharmaceuticals & Medicines (መድኃኒቶች)',
                      ),
                      validator: (v) => (v == null || v.trim().isEmpty) ? 'Category name is required' : null,
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(
                      initialValue: categoryType,
                      decoration: const InputDecoration(labelText: 'Category Classification *'),
                      items: const [
                        DropdownMenuItem(value: 'PRODUCT', child: Text('Physical Goods / Products (ሸቀጦች)')),
                        DropdownMenuItem(value: 'SERVICE', child: Text('Services / Consulting (አገልግሎቶች)')),
                        DropdownMenuItem(value: 'ALL', child: Text('Universal (Both Goods & Services)')),
                      ],
                      onChanged: (v) {
                        if (v != null) {
                          setDialogState(() => categoryType = v);
                        }
                      },
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: descCtrl,
                      maxLines: 2,
                      decoration: const InputDecoration(
                        labelText: 'Description / Scope',
                        hintText: 'Optional notes regarding VAT status, item scope, or regulatory codes',
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            OutlinedButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                if (!formKey.currentState!.validate()) return;
                final tenant = ref.read(tenantContextProvider);
                final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
                final repo = ref.read(catalogRepositoryProvider);

                final messenger = ScaffoldMessenger.of(context);
                try {
                  await repo.createCategory(
                    tenantId: tenantId,
                    code: codeCtrl.text.trim().toUpperCase(),
                    name: nameCtrl.text.trim(),
                    type: categoryType,
                    description: descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim(),
                  );
                  if (ctx.mounted) {
                    Navigator.pop(ctx);
                  }
                  await _loadCategories();
                  onCreated?.call();
                  messenger.showSnackBar(
                    SnackBar(content: Text('Category "${codeCtrl.text.trim().toUpperCase()}" created successfully')),
                  );
                } catch (err) {
                  messenger.showSnackBar(
                    SnackBar(content: Text('Failed to create category: $err'), backgroundColor: AppColors.red600),
                  );
                }
              },
              child: const Text('Create Category'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final query = _searchController.text.trim().toLowerCase();
    final filteredCategories = _categories.where((c) {
      if (_selectedTypeFilter != 'ALL' && c.type != _selectedTypeFilter && c.type != 'ALL') {
        return false;
      }
      if (query.isNotEmpty) {
        final matchesCode = c.code.toLowerCase().contains(query);
        final matchesName = c.name.toLowerCase().contains(query);
        final matchesDesc = c.description?.toLowerCase().contains(query) ?? false;
        return matchesCode || matchesName || matchesDesc;
      }
      return true;
    }).toList();

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Page Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('ITEM & SERVICE CATEGORIES', style: AppTypography.h1()),
                    const SizedBox(height: 4),
                    Text(
                      'Statutory Master Data Management • Product and Service Groups',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: () => _openCreateCategoryDialog(),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('ADD CATEGORY'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Controls & Filters
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: AppColors.rule),
              ),
              child: Row(
                children: [
                  Expanded(
                    flex: 3,
                    child: TextField(
                      controller: _searchController,
                      decoration: const InputDecoration(
                        hintText: 'Search by category code, name, or description...',
                        prefixIcon: Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                      ),
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    flex: 2,
                    child: DropdownButtonFormField<String>(
                      initialValue: _selectedTypeFilter,
                      decoration: const InputDecoration(labelText: 'Classification Filter'),
                      items: const [
                        DropdownMenuItem(value: 'ALL', child: Text('All Classifications')),
                        DropdownMenuItem(value: 'PRODUCT', child: Text('Product / Goods Categories')),
                        DropdownMenuItem(value: 'SERVICE', child: Text('Service Categories')),
                      ],
                      onChanged: (v) {
                        if (v != null) {
                          setState(() => _selectedTypeFilter = v);
                        }
                      },
                    ),
                  ),
                  const SizedBox(width: 16),
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    tooltip: 'Reload categories',
                    onPressed: _loadCategories,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Data Table
            Expanded(
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppColors.rule),
                ),
                child: _isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : filteredCategories.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.category_outlined, size: 48, color: AppColors.inkMuted),
                                const SizedBox(height: 12),
                                Text('No Categories Found', style: AppTypography.h3()),
                                const SizedBox(height: 4),
                                Text(
                                  'Create categories to organize products and services in your tenant catalog.',
                                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                ),
                                const SizedBox(height: 16),
                                ElevatedButton.icon(
                                  onPressed: () => _openCreateCategoryDialog(),
                                  icon: const Icon(Icons.add),
                                  label: const Text('Create First Category'),
                                ),
                              ],
                            ),
                          )
                        : SingleChildScrollView(
                            scrollDirection: Axis.horizontal,
                            child: SingleChildScrollView(
                              scrollDirection: Axis.vertical,
                              child: DataTable(
                                columns: const [
                                  DataColumn(label: Text('Code')),
                                  DataColumn(label: Text('Category Name')),
                                  DataColumn(label: Text('Type')),
                                  DataColumn(label: Text('Description')),
                                  DataColumn(label: Text('Status')),
                                ],
                                rows: filteredCategories.map((cat) {
                                  return DataRow(
                                    cells: [
                                      DataCell(
                                        Text(
                                          cat.code,
                                          style: AppTypography.mono(weight: FontWeight.w600, color: AppColors.navy900),
                                        ),
                                      ),
                                      DataCell(
                                        Text(cat.name, style: AppTypography.uiLabelBold()),
                                      ),
                                      DataCell(
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                          decoration: BoxDecoration(
                                            color: cat.type == 'PRODUCT'
                                                ? AppColors.navy900.withValues(alpha: 0.08)
                                                : cat.type == 'SERVICE'
                                                    ? AppColors.amber600.withValues(alpha: 0.12)
                                                    : AppColors.green700.withValues(alpha: 0.08),
                                            borderRadius: BorderRadius.circular(2),
                                          ),
                                          child: Text(
                                            cat.type,
                                            style: AppTypography.monoSmall(
                                              weight: FontWeight.w600,
                                              color: cat.type == 'PRODUCT'
                                                  ? AppColors.navy900
                                                  : cat.type == 'SERVICE'
                                                      ? AppColors.amber600
                                                      : AppColors.green700,
                                            ),
                                          ),
                                        ),
                                      ),
                                      DataCell(
                                        Text(
                                          cat.description != null && cat.description!.isNotEmpty
                                              ? cat.description!
                                              : '—',
                                          style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                        ),
                                      ),
                                      DataCell(
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                          decoration: BoxDecoration(
                                            color: AppColors.green700.withValues(alpha: 0.08),
                                            borderRadius: BorderRadius.circular(2),
                                            border: Border.all(color: AppColors.green700.withValues(alpha: 0.3)),
                                          ),
                                          child: Text(
                                            cat.status,
                                            style: AppTypography.uiLabel(color: AppColors.green700),
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
