import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../domain/catalog/models/catalog_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class ServiceCatalogScreen extends ConsumerStatefulWidget {
  const ServiceCatalogScreen({super.key});

  @override
  ConsumerState<ServiceCatalogScreen> createState() => _ServiceCatalogScreenState();
}

class _ServiceCatalogScreenState extends ConsumerState<ServiceCatalogScreen> {
  final _searchController = TextEditingController();
  bool _isLoading = false;
  List<ServiceModel> _services = [];
  List<CategoryModel> _categories = [];

  @override
  void initState() {
    super.initState();
    _loadServices();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadServices() async {
    setState(() => _isLoading = true);
    try {
      final tenant = ref.read(tenantContextProvider);
      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

      final repo = ref.read(catalogRepositoryProvider);
      final servs = await repo.getServices(
        tenantId: tenantId,
        branchId: branchId,
        query: _searchController.text.trim(),
      );
      final cats = await repo.getCategories(tenantId: tenantId);

      setState(() {
        _services = servs;
        _categories = cats.where((c) => c.type == 'SERVICE' || c.type == 'ALL').toList();
        if (_categories.isEmpty) {
          _categories = [
            CategoryModel(id: 'cat-03', tenantId: tenantId, code: 'IT_SERV', name: 'IT & Software Services', type: 'SERVICE'),
            CategoryModel(id: 'cat-04', tenantId: tenantId, code: 'CONSULT', name: 'Consulting & Advisory', type: 'SERVICE'),
          ];
        }
        _isLoading = false;
      });
    } catch (_) {
      setState(() => _isLoading = false);
    }
  }

  void _openRegisterServiceDialog() {
    final formKey = GlobalKey<FormState>();
    final codeCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final priceCtrl = TextEditingController();

    final tenant = ref.read(tenantContextProvider);
    final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';

    if (_categories.isEmpty) {
      _categories = [
        CategoryModel(id: 'cat-03', tenantId: tenantId, code: 'IT_SERV', name: 'IT & Software Services', type: 'SERVICE'),
        CategoryModel(id: 'cat-04', tenantId: tenantId, code: 'CONSULT', name: 'Consulting & Advisory', type: 'SERVICE'),
      ];
    }
    String category = _categories.first.code;
    String unit = 'SERVICE';
    TaxClassification tax = TaxClassification.vat15;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (dialogCtx, setDialogState) => AlertDialog(
          title: Text('REGISTER SERVICE IN CATALOG', style: AppTypography.h2()),
          content: SizedBox(
            width: 500,
            child: Form(
              key: formKey,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextFormField(
                      controller: codeCtrl,
                      decoration: const InputDecoration(labelText: 'Service Code (Unique) *'),
                      validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: nameCtrl,
                      decoration: const InputDecoration(labelText: 'Service Name *'),
                      validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(
                      value: category,
                      decoration: InputDecoration(
                        labelText: 'Service Category *',
                        suffixIcon: IconButton(
                          icon: const Icon(Icons.add_circle_outline, size: 20, color: AppColors.navy900),
                          tooltip: 'Add new service category',
                          onPressed: () async {
                            final newCodeCtrl = TextEditingController();
                            final newNameCtrl = TextEditingController();
                            await showDialog(
                              context: context,
                              builder: (innerCtx) => AlertDialog(
                                title: Text('QUICK ADD SERVICE CATEGORY', style: AppTypography.h3()),
                                content: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    TextField(
                                      controller: newCodeCtrl,
                                      decoration: const InputDecoration(labelText: 'Code (e.g. LEGAL) *'),
                                      textCapitalization: TextCapitalization.characters,
                                    ),
                                    const SizedBox(height: 12),
                                    TextField(
                                      controller: newNameCtrl,
                                      decoration: const InputDecoration(labelText: 'Name (e.g. Legal & Notary Services) *'),
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
                                        type: 'SERVICE',
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
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: descCtrl,
                      decoration: const InputDecoration(labelText: 'Scope / Description *'),
                      maxLines: 2,
                      validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: unit,
                            decoration: const InputDecoration(labelText: 'Billing Unit'),
                            items: const [
                              DropdownMenuItem(value: 'SERVICE', child: Text('Flat Service Fee')),
                              DropdownMenuItem(value: 'HOUR', child: Text('Hourly Rate (በሰዓት)')),
                              DropdownMenuItem(value: 'DAY', child: Text('Daily Rate (በቀን)')),
                              DropdownMenuItem(value: 'MONTH', child: Text('Monthly Retainer (በወር)')),
                            ],
                            onChanged: (v) => setDialogState(() => unit = v ?? unit),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: priceCtrl,
                            decoration: const InputDecoration(labelText: 'Unit Rate (ETB) *'),
                            keyboardType: TextInputType.number,
                            validator: (v) => v == null || double.tryParse(v) == null ? 'Valid number required' : null,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<TaxClassification>(
                      initialValue: tax,
                      decoration: const InputDecoration(labelText: 'Tax Classification'),
                      items: TaxClassification.values
                          .map((t) => DropdownMenuItem(value: t, child: Text(t.label)))
                          .toList(),
                      onChanged: (v) => setDialogState(() => tax = v ?? tax),
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
              final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-00000000001';
              final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

              final newServ = ServiceModel(
                id: 'temp',
                tenantId: tenantId,
                branchId: branchId,
                serviceCode: codeCtrl.text.trim(),
                name: nameCtrl.text.trim(),
                description: descCtrl.text.trim(),
                category: category,
                unit: unit,
                unitPrice: double.parse(priceCtrl.text.trim()),
                taxClassification: tax,
              );

              final repo = ref.read(catalogRepositoryProvider);
              await repo.registerService(tenantId: tenantId, branchId: branchId, service: newServ);
              if (ctx.mounted) {
                Navigator.pop(ctx);
              }
              if (mounted) {
                _loadServices();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Service registered in catalog successfully')),
                );
              }
            },
            child: const Text('Register Service'),
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
                      Text('SERVICE CATALOG', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'Authorized non-physical billable services (distinct from stock inventory)',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  onPressed: _openRegisterServiceDialog,
                  icon: const Icon(Icons.design_services_outlined, size: 16),
                  label: const Text('Register Service'),
                ),
              ],
            ),
            const SizedBox(height: 20),

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
                        hintText: 'Search services by code or title...',
                        prefixIcon: Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                      ),
                      onChanged: (_) => _loadServices(),
                    ),
                  ),
                  const SizedBox(width: 16),
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    tooltip: 'Refresh services',
                    onPressed: _loadServices,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Services Table
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
                              DataColumn(label: Text('Service Code')),
                              DataColumn(label: Text('Service Title')),
                              DataColumn(label: Text('Billing Unit')),
                              DataColumn(label: Text('Rate (ETB)')),
                              DataColumn(label: Text('Tax Classification')),
                              DataColumn(label: Text('Status')),
                            ],
                            rows: _services.map((s) {
                              return DataRow(
                                cells: [
                                  DataCell(Text(s.serviceCode, style: AppTypography.mono(weight: FontWeight.w600))),
                                  DataCell(
                                    Column(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(s.name, style: AppTypography.uiLabelBold()),
                                        Text(s.description, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                                      ],
                                    ),
                                  ),
                                  DataCell(Text(s.unit, style: AppTypography.bodySmall())),
                                  DataCell(
                                    Text(
                                      'ETB ${s.unitPrice.toStringAsFixed(2)}',
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
                                        s.taxClassification.code,
                                        style: AppTypography.monoSmall(color: AppColors.navy900, weight: FontWeight.w600),
                                      ),
                                    ),
                                  ),
                                  DataCell(
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: s.isActive
                                            ? AppColors.green700.withValues(alpha: 0.08)
                                            : AppColors.inkMuted.withValues(alpha: 0.08),
                                        borderRadius: BorderRadius.circular(2),
                                        border: Border.all(
                                          color: s.isActive
                                              ? AppColors.green700.withValues(alpha: 0.3)
                                              : AppColors.inkMuted.withValues(alpha: 0.3),
                                        ),
                                      ),
                                      child: Text(
                                        s.isActive ? 'Active' : 'Inactive',
                                        style: AppTypography.uiLabel(
                                          color: s.isActive ? AppColors.green700 : AppColors.inkMuted,
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
