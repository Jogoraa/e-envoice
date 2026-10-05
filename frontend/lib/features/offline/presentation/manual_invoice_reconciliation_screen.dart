import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/offline_operations_service.dart';

class ManualInvoiceReconciliationScreen extends ConsumerStatefulWidget {
  const ManualInvoiceReconciliationScreen({super.key});

  @override
  ConsumerState<ManualInvoiceReconciliationScreen> createState() =>
      _ManualInvoiceReconciliationScreenState();
}

class _ManualInvoiceReconciliationScreenState
    extends ConsumerState<ManualInvoiceReconciliationScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = true;
  String? _errorMessage;
  List<ManualFiscalDocumentDto> _allDocuments = [];

  final _currencyFormat = NumberFormat.currency(symbol: 'ETB ');
  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _loadDocuments();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadDocuments() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(offlineOperationsServiceProvider);
      final docs = await service.listManualInvoices();
      if (mounted) {
        setState(() {
          _allDocuments = docs;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  List<ManualFiscalDocumentDto> _filterByStatus(String status) {
    if (status == 'ALL') return _allDocuments;
    return _allDocuments.where((d) => d.status.toUpperCase() == status).toList();
  }

  void _showNewManualInvoiceDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _ManualInvoiceEntryDialog(
        onSuccess: () {
          _loadDocuments();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Manual invoice batch submitted for server reconciliation.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  void _showReprintDialog(ManualFiscalDocumentDto doc) async {
    try {
      final service = ref.read(offlineOperationsServiceProvider);
      await service.reprintManualInvoice(doc.id);

      if (!mounted) return;

      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Row(
            children: [
              const Icon(Icons.print, color: AppColors.warning),
              const SizedBox(width: 8),
              const Text('Fiscal Reprint (Manual Fallback)'),
            ],
          ),
          content: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                // Prominent mandatory DUPLICATE watermark
                Container(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  decoration: BoxDecoration(
                    color: AppColors.error.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.error, width: 2),
                  ),
                  child: Center(
                    child: Text(
                      '*** DUPLICATE ***\nReprint Count: ${(doc.reprintCount + 1)}',
                      textAlign: TextAlign.center,
                      style: AppTypography.titleLarge.copyWith(
                        color: AppColors.error,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                _reprintRow('Manual Book Number:', doc.manualBookNumber),
                _reprintRow('Manual Doc Number:', doc.manualDocumentNumber),
                _reprintRow('Customer:', doc.customerName),
                if (doc.customerTin != null)
                  _reprintRow('Customer TIN:', doc.customerTin!),
                _reprintRow('Original Issue Date:', _dateFormat.format(doc.originalIssueDate)),
                _reprintRow('Outage Reference:', doc.outageReference),
                _reprintRow('Reconciliation Status:', doc.status),
                if (doc.serverIrn != null)
                  _reprintRow('Authoritative Server IRN:', doc.serverIrn!),
                const Divider(height: 24),
                _reprintRow('Tax Amount:', _currencyFormat.format(doc.taxAmount)),
                _reprintRow('Total Fiscal Amount:', _currencyFormat.format(doc.totalAmount), isBold: true),
                const SizedBox(height: 12),
                Text(
                  'Reprinted strictly under Directive No. 1142/2026 Art. 22 audit rules. '
                  'The DUPLICATE mark is permanently stamped.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Close'),
            ),
            ElevatedButton.icon(
              onPressed: () {
                Navigator.pop(ctx);
                _loadDocuments();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Reprint sent to thermal fiscal printer.')),
                );
              },
              icon: const Icon(Icons.print),
              label: const Text('Send to Printer'),
              style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
            ),
          ],
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Reprint failed: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  Widget _reprintRow(String label, String value, {bool isBold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary)),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: AppTypography.bodyMedium.copyWith(
                fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Manual Invoice Recovery & Fallback'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadDocuments,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: [
            Tab(text: 'Pending (${_filterByStatus('PENDING_RECONCILIATION').length})'),
            Tab(text: 'Reconciled (${_filterByStatus('RECONCILED').length})'),
            Tab(text: 'Failed (${_filterByStatus('FAILED').length})'),
            Tab(text: 'All Documents (${_allDocuments.length})'),
          ],
        ),
      ),
      body: Column(
        children: [
          _buildOutageNoticeBanner(),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.error_outline, size: 48, color: AppColors.error),
                            const SizedBox(height: 16),
                            Text(_errorMessage!, style: AppTypography.bodyMedium),
                            const SizedBox(height: 16),
                            ElevatedButton(
                              onPressed: _loadDocuments,
                              child: const Text('Retry'),
                            ),
                          ],
                        ),
                      )
                    : TabBarView(
                        controller: _tabController,
                        children: [
                          _buildDocumentList(_filterByStatus('PENDING_RECONCILIATION')),
                          _buildDocumentList(_filterByStatus('RECONCILED')),
                          _buildDocumentList(_filterByStatus('FAILED')),
                          _buildDocumentList(_allDocuments),
                        ],
                      ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showNewManualInvoiceDialog,
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.post_add),
        label: const Text('Record Manual Batch'),
      ),
    );
  }

  Widget _buildOutageNoticeBanner() {
    return Container(
      padding: const EdgeInsets.all(16),
      color: AppColors.warning.withValues(alpha: 0.1),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: AppColors.warning, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 22 — Manual Paper / QR Fallback',
                  style: AppTypography.titleSmall.copyWith(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Invoices issued manually during verified network or power outages must be entered into '
                  'the system and reconciled with the Ministry of Revenues within the statutory recovery deadline (72h). '
                  'All reprints must be permanently stamped with DUPLICATE.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDocumentList(List<ManualFiscalDocumentDto> docs) {
    if (docs.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.assignment_turned_in, size: 64, color: AppColors.textTertiary),
            const SizedBox(height: 16),
            Text('No manual documents in this category', style: AppTypography.titleMedium),
            const SizedBox(height: 8),
            Text(
              'Use "Record Manual Batch" to log paper invoices issued during an outage.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: docs.length,
      itemBuilder: (ctx, index) {
        final doc = docs[index];

        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Book #${doc.manualBookNumber} / Doc #${doc.manualDocumentNumber}',
                      style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.bold),
                    ),
                    _buildStatusChip(doc.status),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Icon(Icons.person_outline, size: 16, color: AppColors.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      doc.customerName,
                      style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.w500),
                    ),
                    if (doc.customerTin != null) ...[
                      const SizedBox(width: 8),
                      Text('TIN: ${doc.customerTin}', style: AppTypography.caption),
                    ],
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    const Icon(Icons.event_outlined, size: 16, color: AppColors.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      'Issued: ${_dateFormat.format(doc.originalIssueDate)}',
                      style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                    ),
                    const SizedBox(width: 16),
                    const Icon(Icons.warning_amber_outlined, size: 16, color: AppColors.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      'Outage Ref: ${doc.outageReference}',
                      style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                    ),
                  ],
                ),
                const Divider(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Total Amount', style: AppTypography.caption),
                        Text(
                          _currencyFormat.format(doc.totalAmount),
                          style: AppTypography.titleMedium.copyWith(
                            color: AppColors.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        if (doc.serverIrn != null)
                          Tooltip(
                            message: 'Server IRN: ${doc.serverIrn}',
                            child: Chip(
                              backgroundColor: AppColors.success.withValues(alpha: 0.1),
                              avatar: const Icon(Icons.verified, size: 16, color: AppColors.success),
                              label: Text(
                                'IRN: ${doc.serverIrn!.substring(0, doc.serverIrn!.length > 10 ? 10 : doc.serverIrn!.length)}...',
                                style: const TextStyle(fontSize: 11, color: AppColors.success),
                              ),
                            ),
                          ),
                        const SizedBox(width: 8),
                        OutlinedButton.icon(
                          onPressed: () => _showReprintDialog(doc),
                          icon: const Icon(Icons.print, size: 16),
                          label: const Text('Reprint (DUPLICATE)'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: AppColors.textPrimary,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatusChip(String status) {
    Color bg;
    Color fg;
    String label = status;

    switch (status.toUpperCase()) {
      case 'RECONCILED':
        bg = AppColors.success.withValues(alpha: 0.15);
        fg = AppColors.success;
        label = 'RECONCILED';
        break;
      case 'FAILED':
        bg = AppColors.error.withValues(alpha: 0.15);
        fg = AppColors.error;
        label = 'FAILED';
        break;
      case 'PENDING_RECONCILIATION':
      default:
        bg = AppColors.warning.withValues(alpha: 0.15);
        fg = AppColors.warning;
        label = 'PENDING RECONCILIATION';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        label,
        style: TextStyle(color: fg, fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }
}

class _ManualInvoiceEntryDialog extends ConsumerStatefulWidget {
  final VoidCallback onSuccess;

  const _ManualInvoiceEntryDialog({required this.onSuccess});

  @override
  ConsumerState<_ManualInvoiceEntryDialog> createState() =>
      _ManualInvoiceEntryDialogState();
}

class _ManualInvoiceEntryDialogState extends ConsumerState<_ManualInvoiceEntryDialog> {
  final _formKey = GlobalKey<FormState>();
  final _outageRefController = TextEditingController(text: 'OUTAGE-${DateTime.now().year}-');
  final _manualBookController = TextEditingController();
  final _manualDocController = TextEditingController();
  final _customerNameController = TextEditingController();
  final _customerTinController = TextEditingController();
  final _totalAmountController = TextEditingController();
  final _taxAmountController = TextEditingController();
  final _itemDescController = TextEditingController(text: 'Goods/Services sold during power outage');

  final DateTime _issueDate = DateTime.now();
  bool _isSubmitting = false;

  @override
  void dispose() {
    _outageRefController.dispose();
    _manualBookController.dispose();
    _manualDocController.dispose();
    _customerNameController.dispose();
    _customerTinController.dispose();
    _totalAmountController.dispose();
    _taxAmountController.dispose();
    _itemDescController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final total = double.parse(_totalAmountController.text.trim());
      final tax = double.parse(_taxAmountController.text.trim());

      final lineItem = ManualDocumentLineItemDto(
        itemDescription: _itemDescController.text.trim(),
        quantity: 1.0,
        unitPrice: total - tax,
        taxAmount: tax,
        totalAmount: total,
      );

      final doc = {
        'manualBookNumber': _manualBookController.text.trim(),
        'manualDocumentNumber': _manualDocController.text.trim(),
        'originalIssueDate': _issueDate.toIso8601String(),
        'customerName': _customerNameController.text.trim(),
        if (_customerTinController.text.trim().isNotEmpty)
          'customerTin': _customerTinController.text.trim(),
        'totalAmount': total,
        'taxAmount': tax,
        'lineItems': [lineItem.toJson()],
      };

      final service = ref.read(offlineOperationsServiceProvider);
      await service.reconcileManualBatch(
        outageReference: _outageRefController.text.trim(),
        documents: [doc],
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to submit manual batch: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Record Paper Fallback Invoices (Art. 22)'),
      content: SizedBox(
        width: 500,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Enter details from the pre-printed paper book or fallback invoice issued during the outage.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _outageRefController,
                  decoration: const InputDecoration(
                    labelText: 'Outage Incident Reference *',
                    hintText: 'e.g. OUTAGE-2026-GRID-FAIL-01',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _manualBookController,
                        decoration: const InputDecoration(
                          labelText: 'Manual Book # *',
                          border: OutlineInputBorder(),
                        ),
                        validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextFormField(
                        controller: _manualDocController,
                        decoration: const InputDecoration(
                          labelText: 'Document # *',
                          border: OutlineInputBorder(),
                        ),
                        validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _customerNameController,
                  decoration: const InputDecoration(
                    labelText: 'Customer Name *',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _customerTinController,
                  decoration: const InputDecoration(
                    labelText: 'Customer TIN (Optional)',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _totalAmountController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(
                          labelText: 'Total Amount (ETB) *',
                          border: OutlineInputBorder(),
                        ),
                        validator: (v) {
                          if (v == null || v.isEmpty) return 'Required';
                          if (double.tryParse(v) == null) return 'Invalid amount';
                          return null;
                        },
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextFormField(
                        controller: _taxAmountController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(
                          labelText: 'Tax Amount (VAT) *',
                          border: OutlineInputBorder(),
                        ),
                        validator: (v) {
                          if (v == null || v.isEmpty) return 'Required';
                          if (double.tryParse(v) == null) return 'Invalid tax';
                          return null;
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _itemDescController,
                  decoration: const InputDecoration(
                    labelText: 'Item / Service Summary *',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Submit for Server Reconciliation'),
        ),
      ],
    );
  }
}
