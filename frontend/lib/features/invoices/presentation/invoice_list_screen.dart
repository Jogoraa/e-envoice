import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../domain/invoice/models/invoice_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../shared/widgets/status/status_pill.dart';
import '../../../core/di/providers.dart';

class InvoiceListScreen extends ConsumerStatefulWidget {
  const InvoiceListScreen({super.key});

  @override
  ConsumerState<InvoiceListScreen> createState() => _InvoiceListScreenState();
}

class _InvoiceListScreenState extends ConsumerState<InvoiceListScreen> {
  final _searchController = TextEditingController();
  String? _selectedStatus;
  bool _isLoading = false;
  List<InvoiceModel> _invoices = [];

  @override
  void initState() {
    super.initState();
    _loadInvoices();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadInvoices() async {
    setState(() => _isLoading = true);

    try {
      final tenantState = ref.read(tenantContextProvider);
      final tenantId = tenantState.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final branchId = tenantState.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

      final repo = ref.read(invoiceRepositoryProvider);
      final list = await repo.listInvoices(
        tenantId: tenantId,
        branchId: branchId,
        status: _selectedStatus,
      );

      if (mounted) {
        setState(() {
          _invoices = list;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final loc = AppLocalizations.of(context);

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
                      Text('ELECTRONIC INVOICE LEDGER', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'All registered and offline continuity fiscal transactions',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  onPressed: () => context.go('/invoices/new'),
                  icon: const Icon(Icons.add, size: 16),
                  label: Text(loc.get('new_invoice')),
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
                  final isCompact = constraints.maxWidth < 600;
                  if (isCompact) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        TextField(
                          controller: _searchController,
                          decoration: InputDecoration(
                            hintText: loc.get('search'),
                            prefixIcon: const Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                          ),
                          onChanged: (_) => setState(() {}),
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: DropdownButtonFormField<String?>(
                                isExpanded: true,
                                initialValue: _selectedStatus,
                                decoration: const InputDecoration(labelText: 'Status Filter'),
                                items: const [
                                  DropdownMenuItem(value: null, child: Text('All Statuses')),
                                  DropdownMenuItem(value: 'synced', child: Text('Synced')),
                                  DropdownMenuItem(value: 'offlineDraft', child: Text('Offline draft')),
                                  DropdownMenuItem(value: 'syncError', child: Text('Sync error')),
                                ],
                                onChanged: (v) {
                                  setState(() => _selectedStatus = v);
                                  _loadInvoices();
                                },
                              ),
                            ),
                            const SizedBox(width: 12),
                            IconButton(
                              icon: const Icon(Icons.refresh),
                              tooltip: 'Refresh list',
                              onPressed: _loadInvoices,
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
                          decoration: InputDecoration(
                            hintText: loc.get('search'),
                            prefixIcon: const Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                          ),
                          onChanged: (_) => setState(() {}),
                        ),
                      ),
                      const SizedBox(width: 16),
                      ConstrainedBox(
                        constraints: const BoxConstraints(minWidth: 180, maxWidth: 240),
                        child: DropdownButtonFormField<String?>(
                          isExpanded: true,
                          initialValue: _selectedStatus,
                          decoration: const InputDecoration(labelText: 'Status Filter'),
                          items: const [
                            DropdownMenuItem(value: null, child: Text('All Statuses')),
                            DropdownMenuItem(value: 'synced', child: Text('Synced')),
                            DropdownMenuItem(value: 'offlineDraft', child: Text('Offline draft')),
                            DropdownMenuItem(value: 'syncError', child: Text('Sync error')),
                          ],
                          onChanged: (v) {
                            setState(() => _selectedStatus = v);
                            _loadInvoices();
                          },
                        ),
                      ),
                      const SizedBox(width: 16),
                      IconButton(
                        icon: const Icon(Icons.refresh),
                        tooltip: 'Refresh list',
                        onPressed: _loadInvoices,
                      ),
                    ],
                  );
                },
              ),
            ),
            const SizedBox(height: 16),

            // Invoices Ledger Table
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: _isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : _invoices.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.receipt_long_outlined, size: 48, color: AppColors.inkMuted),
                                const SizedBox(height: 12),
                                Text(
                                  'No invoices found in database',
                                  style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Create a new invoice or adjust filters to view records.',
                                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
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
                            DataColumn(label: Text('Customer')),
                            DataColumn(label: Text('Reference / IRN')),
                            DataColumn(label: Text('Amount (ETB)')),
                            DataColumn(label: Text('Status')),
                            DataColumn(label: Text('Date')),
                            DataColumn(label: Text('Actions')),
                          ],
                          rows: _invoices.map((inv) {
                            InvoiceSyncStatus statusPillType = InvoiceSyncStatus.synced;
                            if (inv.status == 'offlineDraft') {
                              statusPillType = InvoiceSyncStatus.offlineDraft;
                            } else if (inv.status == 'syncError' || inv.status == 'REJECTED') {
                              statusPillType = InvoiceSyncStatus.syncError;
                            }

                            final displayRef = inv.irn ?? inv.documentNumber;

                            return DataRow(
                              cells: [
                                DataCell(
                                  Text(
                                    inv.buyer?.legalName ?? 'Cash Customer',
                                    style: AppTypography.bodySmall(color: AppColors.ink),
                                  ),
                                ),
                                DataCell(
                                  Text(
                                    displayRef,
                                    style: AppTypography.mono(
                                      color: AppColors.navy700,
                                      weight: FontWeight.w500,
                                    ),
                                  ),
                                ),
                                DataCell(
                                  Text(
                                    'ETB ${inv.grandTotal.toStringAsFixed(2)}',
                                    style: AppTypography.mono(
                                      color: AppColors.ink,
                                      weight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                                DataCell(
                                  StatusPill(
                                    status: statusPillType,
                                    errorMessage: statusPillType == InvoiceSyncStatus.syncError
                                        ? 'Gateway timeout — queued for automatic retry.'
                                        : null,
                                  ),
                                ),
                                DataCell(
                                  Text(
                                    inv.invoiceDate.toLocal().toString().substring(0, 16),
                                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                  ),
                                ),
                                DataCell(
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      IconButton(
                                        icon: const Icon(Icons.receipt_outlined, size: 18),
                                        tooltip: 'View invoice details',
                                        onPressed: () => context.go('/invoices/${inv.id}'),
                                      ),
                                    ],
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
