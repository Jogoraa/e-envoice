import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class SaasReportDefinitionModel {
  final String id;
  final String title;
  final String amharicTitle;
  final String description;
  final String iconName;
  final String category;
  final List<String> exportFormats;
  final bool isActive;
  final int displayOrder;

  const SaasReportDefinitionModel({
    required this.id,
    required this.title,
    required this.amharicTitle,
    required this.description,
    required this.iconName,
    required this.category,
    required this.exportFormats,
    required this.isActive,
    required this.displayOrder,
  });

  factory SaasReportDefinitionModel.fromJson(Map<String, dynamic> json) {
    List<String> formats = ['PDF', 'EXCEL', 'CSV'];
    if (json['exportFormats'] != null) {
      final val = json['exportFormats'];
      if (val is List) {
        formats = val.map((e) => e.toString()).toList();
      } else if (val is String) {
        formats = val.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
      }
    }

    return SaasReportDefinitionModel(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      amharicTitle: json['amharicTitle']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      iconName: json['iconName']?.toString() ?? 'assessment',
      category: json['category']?.toString() ?? 'COMPLIANCE',
      exportFormats: formats,
      isActive: json['isActive'] == true || json['active'] == true,
      displayOrder: (json['displayOrder'] as num?)?.toInt() ?? 0,
    );
  }

  IconData get iconData => iconNameToData(iconName);

  static IconData iconNameToData(String name) {
    switch (name.toLowerCase()) {
      case 'receipt_long':
        return Icons.receipt_long;
      case 'point_of_sale':
        return Icons.point_of_sale;
      case 'trending_up':
        return Icons.trending_up;
      case 'cloud_sync':
        return Icons.cloud_sync;
      case 'account_balance':
        return Icons.account_balance;
      case 'pie_chart':
        return Icons.pie_chart;
      case 'table_chart':
        return Icons.table_chart;
      case 'summarize':
        return Icons.summarize;
      case 'description':
        return Icons.description;
      case 'bar_chart':
        return Icons.bar_chart;
      case 'menu_book':
        return Icons.menu_book;
      case 'assessment':
      default:
        return Icons.assessment;
    }
  }
}

class SaasReportDefinitionsScreen extends ConsumerStatefulWidget {
  const SaasReportDefinitionsScreen({super.key});

  @override
  ConsumerState<SaasReportDefinitionsScreen> createState() => _SaasReportDefinitionsScreenState();
}

class _SaasReportDefinitionsScreenState extends ConsumerState<SaasReportDefinitionsScreen> {
  final List<SaasReportDefinitionModel> _definitions = [];
  bool _isLoading = true;
  String? _error;
  String _searchQuery = '';
  String _selectedCategory = 'ALL';

  @override
  void initState() {
    super.initState();
    _loadDefinitions();
  }

  Future<void> _loadDefinitions() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(saasManagementApiClientProvider);
      final response = await client.get('/api/v1/saas/reports/definitions');
      if (response.data is List) {
        final List list = response.data;
        _definitions.clear();
        for (final item in list) {
          if (item is Map) {
            _definitions.add(SaasReportDefinitionModel.fromJson(Map<String, dynamic>.from(item)));
          }
        }
      }
    } catch (e) {
      _error = 'Failed to load report definitions from database: $e';
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _deleteDefinition(String id) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Delete Report Template?', style: AppTypography.h2()),
        content: Text(
          'Are you sure you want to permanently delete template "$id"? Tenants will no longer be able to generate this report.',
          style: AppTypography.bodySmall(),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.red600),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      final client = ref.read(saasManagementApiClientProvider);
      await client.delete('/api/v1/saas/reports/definitions/$id');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Report definition $id deleted from database.'), backgroundColor: AppColors.green700),
        );
        _loadDefinitions();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to delete: $e'), backgroundColor: AppColors.red600),
        );
      }
    }
  }

  Future<void> _toggleActive(String id) async {
    try {
      final client = ref.read(saasManagementApiClientProvider);
      await client.put('/api/v1/saas/reports/definitions/$id/toggle');
      if (mounted) {
        _loadDefinitions();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to toggle status: $e'), backgroundColor: AppColors.red600),
        );
      }
    }
  }

  void _showAddEditDialog([SaasReportDefinitionModel? existing]) {
    final isEditing = existing != null;
    final idController = TextEditingController(text: existing?.id ?? '');
    final titleController = TextEditingController(text: existing?.title ?? '');
    final amharicController = TextEditingController(text: existing?.amharicTitle ?? '');
    final descController = TextEditingController(text: existing?.description ?? '');
    final orderController = TextEditingController(text: (existing?.displayOrder ?? 0).toString());

    String selectedCategory = existing?.category ?? 'COMPLIANCE';
    String selectedIcon = existing?.iconName ?? 'assessment';
    bool isActive = existing?.isActive ?? true;

    final formats = <String>{
      ...?existing?.exportFormats,
      if (existing == null) ...['PDF', 'EXCEL', 'CSV'],
    };

    final availableIcons = [
      'assessment',
      'receipt_long',
      'point_of_sale',
      'trending_up',
      'cloud_sync',
      'account_balance',
      'pie_chart',
      'table_chart',
      'summarize',
      'description',
      'bar_chart',
      'menu_book',
    ];

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (dialogContext, setDialogState) {
            return AlertDialog(
              title: Row(
                children: [
                  Icon(
                    isEditing ? Icons.edit_note : Icons.add_chart,
                    color: AppColors.navy900,
                  ),
                  const SizedBox(width: 10),
                  Text(
                    isEditing ? 'Edit Report Template' : 'Add New Report Definition',
                    style: AppTypography.h2(),
                  ),
                ],
              ),
              content: SizedBox(
                width: 650,
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        'Report Identifier & Metadata',
                        style: AppTypography.uiLabelBold(color: AppColors.navy900),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: idController,
                              enabled: !isEditing,
                              decoration: const InputDecoration(
                                labelText: 'Report ID / Code (e.g. vat_sales_ledger)',
                                hintText: 'unique_snake_case_id',
                                border: OutlineInputBorder(),
                              ),
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: DropdownButtonFormField<String>(
                              value: selectedCategory,
                              decoration: const InputDecoration(
                                labelText: 'Regulatory Category',
                                border: OutlineInputBorder(),
                              ),
                              items: const [
                                DropdownMenuItem(value: 'COMPLIANCE', child: Text('COMPLIANCE (Statutory)')),
                                DropdownMenuItem(value: 'OPERATIONAL', child: Text('OPERATIONAL (Daily/Cashier)')),
                                DropdownMenuItem(value: 'COMMERCIAL', child: Text('COMMERCIAL (Sales/Product)')),
                                DropdownMenuItem(value: 'AUDIT', child: Text('AUDIT (Trace & Sync)')),
                                DropdownMenuItem(value: 'FINANCIAL', child: Text('FINANCIAL (Tax & Accounts)')),
                              ],
                              onChanged: (val) {
                                if (val != null) setDialogState(() => selectedCategory = val);
                              },
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: titleController,
                        decoration: const InputDecoration(
                          labelText: 'Report English Title',
                          hintText: 'e.g. Monthly VAT Sales Ledger',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: amharicController,
                        decoration: const InputDecoration(
                          labelText: 'Amharic Title (የአማርኛ ስም)',
                          hintText: 'e.g. ወርሃዊ የተጨማሪ እሴት ታክስ የሽያጭ መዝገብ',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: descController,
                        maxLines: 2,
                        decoration: const InputDecoration(
                          labelText: 'Directive Compliance Description',
                          hintText: 'Detailed description of ledger columns, calculation basis, or directive references.',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          Expanded(
                            child: DropdownButtonFormField<String>(
                              value: selectedIcon,
                              decoration: const InputDecoration(
                                labelText: 'Visual Icon',
                                border: OutlineInputBorder(),
                              ),
                              items: availableIcons.map((ic) {
                                return DropdownMenuItem(
                                  value: ic,
                                  child: Row(
                                    children: [
                                      Icon(SaasReportDefinitionModel.iconNameToData(ic), size: 18),
                                      const SizedBox(width: 8),
                                      Text(ic),
                                    ],
                                  ),
                                );
                              }).toList(),
                              onChanged: (val) {
                                if (val != null) setDialogState(() => selectedIcon = val);
                              },
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: TextField(
                              controller: orderController,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: 'Display Sequence Order',
                                border: OutlineInputBorder(),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Text('Allowed Export Formats:', style: AppTypography.uiLabelBold()),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 12,
                        children: ['PDF', 'EXCEL', 'CSV', 'JSON'].map((fmt) {
                          final isSelected = formats.contains(fmt);
                          return FilterChip(
                            label: Text(fmt),
                            selected: isSelected,
                            onSelected: (selected) {
                              setDialogState(() {
                                if (selected) {
                                  formats.add(fmt);
                                } else {
                                  if (formats.length > 1) formats.remove(fmt);
                                }
                              });
                            },
                          );
                        }).toList(),
                      ),
                      const SizedBox(height: 12),
                      SwitchListTile(
                        title: const Text('Template Active in Tenant Portal'),
                        subtitle: const Text('When enabled, all licensed tenants will see and can generate this report.'),
                        value: isActive,
                        onChanged: (val) => setDialogState(() => isActive = val),
                        contentPadding: EdgeInsets.zero,
                      ),
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('Cancel'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    final id = idController.text.trim();
                    final title = titleController.text.trim();
                    if (id.isEmpty || title.isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('ID and Title are mandatory.'), backgroundColor: AppColors.red600),
                      );
                      return;
                    }

                    final payload = {
                      'id': id,
                      'title': title,
                      'amharicTitle': amharicController.text.trim(),
                      'description': descController.text.trim(),
                      'category': selectedCategory,
                      'iconName': selectedIcon,
                      'exportFormats': formats.join(','),
                      'isActive': isActive,
                      'displayOrder': int.tryParse(orderController.text.trim()) ?? 0,
                    };

                    try {
                      final client = ref.read(saasManagementApiClientProvider);
                      if (isEditing) {
                        await client.put('/api/v1/saas/reports/definitions/$id', data: payload);
                      } else {
                        await client.post('/api/v1/saas/reports/definitions', data: payload);
                      }
                      if (ctx.mounted) {
                        Navigator.of(ctx).pop();
                      }
                      if (mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('Report definition "$title" saved to database.'),
                            backgroundColor: AppColors.green700,
                          ),
                        );
                        _loadDefinitions();
                      }
                    } catch (e) {
                      if (mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text('Failed to save definition: $e'), backgroundColor: AppColors.red600),
                        );
                      }
                    }
                  },
                  child: Text(isEditing ? 'Save Changes' : 'Create Template'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _definitions.where((d) {
      final matchesSearch = _searchQuery.isEmpty ||
          d.title.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          d.amharicTitle.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          d.id.toLowerCase().contains(_searchQuery.toLowerCase());
      final matchesCategory = _selectedCategory == 'ALL' || d.category == _selectedCategory;
      return matchesSearch && matchesCategory;
    }).toList();

    final totalCount = _definitions.length;
    final activeCount = _definitions.where((d) => d.isActive).length;
    final inactiveCount = totalCount - activeCount;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadDefinitions,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('REPORT TEMPLATES & STATUTORY DEFINITIONS', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Database-driven compliance export catalog and report templates (Directive No. 1142/2026)',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  ElevatedButton.icon(
                    onPressed: () => _showAddEditDialog(),
                    icon: const Icon(Icons.add, size: 16),
                    label: const Text('Add Report Definition'),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Metric Summary Cards
              LayoutBuilder(
                builder: (context, constraints) {
                  final isWide = constraints.maxWidth >= 800;
                  final cards = [
                    _buildMetricCard('TOTAL TEMPLATES', totalCount.toString(), Icons.folder_copy_outlined, AppColors.navy900),
                    _buildMetricCard('ACTIVE IN PORTAL', activeCount.toString(), Icons.check_circle_outline, AppColors.green700),
                    _buildMetricCard('INACTIVE / DRAFT', inactiveCount.toString(), Icons.pause_circle_outline, AppColors.inkMuted),
                    _buildMetricCard('EXPORT ENGINES', 'PDF, XLS, CSV, JSON', Icons.file_download_outlined, AppColors.navy700),
                  ];

                  if (isWide) {
                    return Row(
                      children: cards.map((c) => Expanded(child: Padding(padding: const EdgeInsets.symmetric(horizontal: 6), child: c))).toList(),
                    );
                  }
                  return GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: 2.0,
                    children: cards,
                  );
                },
              ),
              const SizedBox(height: 24),

              // Filter & Search Bar
              Material(
                color: AppColors.paperRaised,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(3),
                  side: const BorderSide(color: AppColors.rule),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          decoration: const InputDecoration(
                            hintText: 'Search templates by ID, title, or Amharic translation...',
                            prefixIcon: Icon(Icons.search, size: 20),
                            border: InputBorder.none,
                            isDense: true,
                          ),
                          onChanged: (val) => setState(() => _searchQuery = val),
                        ),
                      ),
                      const SizedBox(width: 16),
                      DropdownButton<String>(
                        value: _selectedCategory,
                        underline: const SizedBox(),
                        items: const [
                          DropdownMenuItem(value: 'ALL', child: Text('All Categories')),
                          DropdownMenuItem(value: 'COMPLIANCE', child: Text('Compliance')),
                          DropdownMenuItem(value: 'OPERATIONAL', child: Text('Operational')),
                          DropdownMenuItem(value: 'COMMERCIAL', child: Text('Commercial')),
                          DropdownMenuItem(value: 'AUDIT', child: Text('Audit')),
                          DropdownMenuItem(value: 'FINANCIAL', child: Text('Financial')),
                        ],
                        onChanged: (val) {
                          if (val != null) setState(() => _selectedCategory = val);
                        },
                      ),
                      const SizedBox(width: 12),
                      IconButton(
                        icon: const Icon(Icons.refresh, size: 20),
                        tooltip: 'Reload from database',
                        onPressed: _loadDefinitions,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Table / Content
              if (_isLoading)
                const Center(
                  child: Padding(
                    padding: EdgeInsets.all(48),
                    child: CircularProgressIndicator(),
                  ),
                )
              else if (_error != null)
                Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      children: [
                        const Icon(Icons.error_outline, size: 36, color: AppColors.red600),
                        const SizedBox(height: 12),
                        Text(_error!, style: AppTypography.bodySmall(color: AppColors.red600)),
                        const SizedBox(height: 16),
                        OutlinedButton(onPressed: _loadDefinitions, child: const Text('Retry Database Query')),
                      ],
                    ),
                  ),
                )
              else if (filtered.isEmpty)
                Material(
                  color: AppColors.paperRaised,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(3),
                    side: const BorderSide(color: AppColors.rule),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(48),
                    child: Center(
                      child: Column(
                        children: [
                          const Icon(Icons.library_books_outlined, size: 48, color: AppColors.inkMuted),
                          const SizedBox(height: 12),
                          Text('No report definitions match your query.', style: AppTypography.h2(color: AppColors.inkMuted)),
                          const SizedBox(height: 4),
                          Text('Click "Add Report Definition" to publish a new template to the platform database.', style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                        ],
                      ),
                    ),
                  ),
                )
              else
                Material(
                  color: AppColors.paperRaised,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(3),
                    side: const BorderSide(color: AppColors.rule),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: filtered.length,
                    separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                    itemBuilder: (context, index) {
                      final item = filtered[index];
                      return ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                        leading: CircleAvatar(
                          backgroundColor: item.isActive ? AppColors.navy700.withValues(alpha: 0.1) : AppColors.inkMuted.withValues(alpha: 0.1),
                          child: Icon(item.iconData, color: item.isActive ? AppColors.navy900 : AppColors.inkMuted),
                        ),
                        title: Row(
                          children: [
                            Text(item.title, style: AppTypography.uiLabelBold()),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: AppColors.paper,
                                borderRadius: BorderRadius.circular(3),
                                border: Border.all(color: AppColors.rule),
                              ),
                              child: Text(item.category, style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                            ),
                            if (!item.isActive) ...[
                              const SizedBox(width: 8),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppColors.red600.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: Text('INACTIVE', style: AppTypography.monoSmall(color: AppColors.red600)),
                              ),
                            ],
                          ],
                        ),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const SizedBox(height: 2),
                            Text(item.amharicTitle, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                            const SizedBox(height: 4),
                            Text(item.description, style: AppTypography.bodySmall(), maxLines: 2, overflow: TextOverflow.ellipsis),
                            const SizedBox(height: 6),
                            Row(
                              children: [
                                Text('ID: ${item.id} | Seq: ${item.displayOrder} | Formats: ', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                ...item.exportFormats.map((f) => Padding(
                                  padding: const EdgeInsets.only(right: 4),
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                                    decoration: BoxDecoration(
                                      color: AppColors.paper,
                                      border: Border.all(color: AppColors.rule),
                                      borderRadius: BorderRadius.circular(2),
                                    ),
                                    child: Text(f, style: AppTypography.monoSmall(color: AppColors.navy700)),
                                  ),
                                )),
                              ],
                            ),
                          ],
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: Icon(item.isActive ? Icons.visibility : Icons.visibility_off, size: 20),
                              tooltip: item.isActive ? 'Deactivate template' : 'Activate template',
                              onPressed: () => _toggleActive(item.id),
                            ),
                            IconButton(
                              icon: const Icon(Icons.edit_outlined, size: 20),
                              tooltip: 'Edit template',
                              onPressed: () => _showAddEditDialog(item),
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline, size: 20, color: AppColors.red600),
                              tooltip: 'Delete template',
                              onPressed: () => _deleteDefinition(item.id),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMetricCard(String label, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(label, style: AppTypography.monoSmall(color: AppColors.inkMuted)),
              Icon(icon, size: 18, color: color),
            ],
          ),
          const SizedBox(height: 8),
          Text(value, style: AppTypography.h2(color: color)),
        ],
      ),
    );
  }
}
