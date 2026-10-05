import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/utils/tin_validator.dart';
import '../../../data/services/compliance/statutory_documents_service.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class WithholdingReceiptScreen extends ConsumerStatefulWidget {
  const WithholdingReceiptScreen({super.key});

  @override
  ConsumerState<WithholdingReceiptScreen> createState() => _WithholdingReceiptScreenState();
}

class _WithholdingReceiptScreenState extends ConsumerState<WithholdingReceiptScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  List<WithholdingReceiptDto> _receipts = [];
  WithholdingType? _selectedTypeFilter;
  final _searchController = TextEditingController();

  final _currencyFormat = NumberFormat('#,##0.00', 'en_US');
  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadReceipts();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadReceipts() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(statutoryDocumentsServiceProvider);
      final list = await service.listWithholdingReceipts(type: _selectedTypeFilter);
      if (mounted) {
        setState(() {
          _receipts = list;
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

  void _openCreateDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _CreateWithholdingReceiptDialog(
        onSuccess: (receipt) {
          _loadReceipts();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Withholding Receipt ${receipt.receiptNumber} issued successfully.'),
              backgroundColor: AppColors.green700,
            ),
          );
        },
      ),
    );
  }

  void _showPrintPreview(WithholdingReceiptDto receipt) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Text(
                  'WITHHOLDING RECEIPT / የግብር ቅነሳ ደረሰኝ',
                  style: AppTypography.h3(),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
                Text(
                  'Statutory Tax Withholding (Directive No. 1142/2026 Art. 2(17, 19))',
                  style: AppTypography.caption(color: AppColors.inkMuted),
                  textAlign: TextAlign.center,
                ),
                const Divider(height: 24, thickness: 1, color: AppColors.rule),
                _buildRow('Receipt Number', receipt.receiptNumber),
                _buildRow('Issue Date', _dateFormat.format(receipt.issueDate)),
                _buildRow('Withholding Type', receipt.withholdingType.label),
                if (receipt.relatedInvoiceIrn != null)
                  _buildRow('Related Invoice IRN', receipt.relatedInvoiceIrn!),
                const Divider(height: 16, thickness: 1, color: AppColors.rule),
                _buildRow('Withholding Agent', receipt.withholdingAgentName),
                _buildRow('Agent TIN', receipt.withholdingAgentTin),
                _buildRow('Taxpayer / Beneficiary', receipt.taxpayerName),
                _buildRow('Taxpayer TIN', receipt.taxpayerTin),
                const Divider(height: 16, thickness: 1, color: AppColors.rule),
                _buildRow('Tax Base Gross Amount', '${_currencyFormat.format(receipt.taxBaseAmount)} ETB'),
                _buildRow('Statutory Rate', '${(receipt.withheldTaxRate * 100).toStringAsFixed(1)}%'),
                const Divider(height: 16, thickness: 1, color: AppColors.rule),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('TOTAL TAX WITHHELD:', style: AppTypography.uiLabelBold(size: 15)),
                    Text(
                      '${_currencyFormat.format(receipt.withheldAmount)} ETB',
                      style: AppTypography.h3(color: AppColors.navy900),
                    ),
                  ],
                ),
                if (receipt.irn != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      'IRN: ${receipt.irn}',
                      style: AppTypography.monospace(size: 11),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    OutlinedButton(
                      onPressed: () => Navigator.of(ctx).pop(),
                      child: const Text('Close'),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.navy900,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: () {
                        Navigator.of(ctx).pop();
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Dispatched to thermal printer.')),
                        );
                      },
                      icon: const Icon(Icons.print, size: 16),
                      label: const Text('Thermal Print (80mm)'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
          const SizedBox(width: 12),
          Flexible(
            child: Text(
              value,
              style: AppTypography.bodySmall(color: AppColors.ink).copyWith(fontWeight: FontWeight.w600),
              textAlign: TextAlign.right,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final query = _searchController.text.trim().toLowerCase();
    final filtered = _receipts.where((r) {
      if (query.isEmpty) return true;
      return r.receiptNumber.toLowerCase().contains(query) ||
          r.taxpayerName.toLowerCase().contains(query) ||
          r.taxpayerTin.contains(query);
    }).toList();

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            decoration: const BoxDecoration(
              color: AppColors.paperRaised,
              border: Border(bottom: BorderSide(color: AppColors.rule)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.account_balance_wallet_outlined, color: AppColors.navy900, size: 28),
                        const SizedBox(width: 10),
                        Text('Withholding Receipts / የግብር ቅነሳ ደረሰኞች', style: AppTypography.h2()),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Income Tax (2%) and VAT (50%/100%) statutory withholding receipts under Directive No. 1142/2026 Art. 2(17, 19)',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy900,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                  ),
                  onPressed: _openCreateDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Issue Withholding Receipt'),
                ),
              ],
            ),
          ),

          // Filters
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(bottom: BorderSide(color: AppColors.rule)),
            ),
            child: Row(
              children: [
                Expanded(
                  flex: 3,
                  child: TextField(
                    controller: _searchController,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      hintText: 'Search by receipt number, taxpayer name, or TIN...',
                      prefixIcon: const Icon(Icons.search, size: 20),
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  flex: 2,
                  child: DropdownButtonFormField<WithholdingType?>(
                    value: _selectedTypeFilter,
                    decoration: InputDecoration(
                      labelText: 'Filter by Withholding Type',
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('All Withholding Types')),
                      ...WithholdingType.values.map(
                        (t) => DropdownMenuItem(value: t, child: Text(t.label, maxLines: 1, overflow: TextOverflow.ellipsis)),
                      ),
                    ],
                    onChanged: (val) {
                      setState(() => _selectedTypeFilter = val);
                      _loadReceipts();
                    },
                  ),
                ),
                const SizedBox(width: 12),
                IconButton(
                  tooltip: 'Refresh list',
                  icon: const Icon(Icons.refresh),
                  onPressed: _loadReceipts,
                ),
              ],
            ),
          ),

          // Content
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.error_outline, size: 48, color: AppColors.red600),
                            const SizedBox(height: 12),
                            Text('Failed to load withholding receipts', style: AppTypography.h3()),
                            const SizedBox(height: 6),
                            Text(_errorMessage!, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                            const SizedBox(height: 16),
                            ElevatedButton(onPressed: _loadReceipts, child: const Text('Retry')),
                          ],
                        ),
                      )
                    : filtered.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.article_outlined, size: 56, color: AppColors.inkMuted),
                                const SizedBox(height: 16),
                                Text('No Withholding Receipts Issued', style: AppTypography.h3()),
                                const SizedBox(height: 6),
                                Text(
                                  'Issue statutory receipts for Income Tax (2%) or VAT withholding.',
                                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                ),
                                const SizedBox(height: 20),
                                ElevatedButton.icon(
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: AppColors.navy900,
                                    foregroundColor: Colors.white,
                                  ),
                                  onPressed: _openCreateDialog,
                                  icon: const Icon(Icons.add, size: 16),
                                  label: const Text('Issue First Receipt'),
                                ),
                              ],
                            ),
                          )
                        : ListView.separated(
                            padding: const EdgeInsets.all(24),
                            itemCount: filtered.length,
                            separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                            itemBuilder: (context, index) {
                              final r = filtered[index];
                              final isIncome = r.withholdingType == WithholdingType.incomeTaxWithholding;
                              return Container(
                                color: Colors.white,
                                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                                child: Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.all(10),
                                      decoration: BoxDecoration(
                                        color: isIncome
                                            ? AppColors.amber600.withValues(alpha: 0.1)
                                            : AppColors.navy700.withValues(alpha: 0.1),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Icon(
                                        isIncome ? Icons.account_balance : Icons.receipt,
                                        color: isIncome ? AppColors.amber600 : AppColors.navy700,
                                        size: 24,
                                      ),
                                    ),
                                    const SizedBox(width: 16),
                                    Expanded(
                                      flex: 2,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(r.receiptNumber, style: AppTypography.uiLabelBold(size: 14)),
                                          const SizedBox(height: 2),
                                          Text(_dateFormat.format(r.issueDate), style: AppTypography.caption(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                    ),
                                    Expanded(
                                      flex: 3,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(r.taxpayerName, style: AppTypography.body(color: AppColors.ink)),
                                          Text('TIN: ${r.taxpayerTin}', style: AppTypography.caption(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                    ),
                                    Expanded(
                                      flex: 3,
                                      child: Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                        decoration: BoxDecoration(
                                          color: AppColors.paperRaised,
                                          borderRadius: BorderRadius.circular(4),
                                          border: Border.all(color: AppColors.rule),
                                        ),
                                        child: Text(
                                          r.withholdingType.label,
                                          style: AppTypography.caption(color: AppColors.navy900),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    ),
                                    Expanded(
                                      flex: 2,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.end,
                                        children: [
                                          Text(
                                            '${_currencyFormat.format(r.withheldAmount)} ETB',
                                            style: AppTypography.uiLabelBold(color: AppColors.navy900, size: 14),
                                          ),
                                          Text(
                                            'Base: ${_currencyFormat.format(r.taxBaseAmount)} ETB',
                                            style: AppTypography.caption(color: AppColors.inkMuted),
                                          ),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    IconButton(
                                      tooltip: 'Print / View Details',
                                      icon: const Icon(Icons.print_outlined, color: AppColors.navy700),
                                      onPressed: () => _showPrintPreview(r),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),
          ),
        ],
      ),
    );
  }
}

class _CreateWithholdingReceiptDialog extends ConsumerStatefulWidget {
  final ValueChanged<WithholdingReceiptDto> onSuccess;
  const _CreateWithholdingReceiptDialog({required this.onSuccess});

  @override
  ConsumerState<_CreateWithholdingReceiptDialog> createState() => _CreateWithholdingReceiptDialogState();
}

class _CreateWithholdingReceiptDialogState extends ConsumerState<_CreateWithholdingReceiptDialog> {
  final _formKey = GlobalKey<FormState>();
  final _agentTinCtrl = TextEditingController();
  final _agentNameCtrl = TextEditingController();
  final _taxpayerTinCtrl = TextEditingController();
  final _taxpayerNameCtrl = TextEditingController();
  final _taxBaseCtrl = TextEditingController();
  final _relatedIrnCtrl = TextEditingController();
  final _paymentRefCtrl = TextEditingController();

  WithholdingType _withholdingType = WithholdingType.incomeTaxWithholding;
  bool _isSubmitting = false;
  String? _formError;

  @override
  void initState() {
    super.initState();
    final tenant = ref.read(tenantContextProvider).activeTenant;
    if (tenant != null) {
      _agentTinCtrl.text = tenant.tin;
      _agentNameCtrl.text = tenant.name;
    }
  }

  @override
  void dispose() {
    _agentTinCtrl.dispose();
    _agentNameCtrl.dispose();
    _taxpayerTinCtrl.dispose();
    _taxpayerNameCtrl.dispose();
    _taxBaseCtrl.dispose();
    _relatedIrnCtrl.dispose();
    _paymentRefCtrl.dispose();
    super.dispose();
  }

  double get _calculatedWithheldAmount {
    final base = double.tryParse(_taxBaseCtrl.text.trim()) ?? 0.0;
    if (_withholdingType == WithholdingType.incomeTaxWithholding) {
      return base * 0.02; // Statutory 2%
    } else {
      return base * 0.15 * 0.50; // Standard 50% of 15% VAT
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isSubmitting = true;
      _formError = null;
    });

    try {
      final base = double.parse(_taxBaseCtrl.text.trim());
      final payload = {
        'withholdingType': _withholdingType.code,
        'withholdingAgentTin': _agentTinCtrl.text.trim(),
        'withholdingAgentName': _agentNameCtrl.text.trim(),
        'taxpayerTin': _taxpayerTinCtrl.text.trim(),
        'taxpayerName': _taxpayerNameCtrl.text.trim(),
        'taxBaseAmount': base,
        if (_relatedIrnCtrl.text.trim().isNotEmpty) 'relatedInvoiceIrn': _relatedIrnCtrl.text.trim(),
        if (_paymentRefCtrl.text.trim().isNotEmpty) 'paymentReference': _paymentRefCtrl.text.trim(),
        'issueDate': DateTime.now().toIso8601String(),
      };

      final service = ref.read(statutoryDocumentsServiceProvider);
      final created = await service.issueWithholdingReceipt(payload);

      if (mounted) {
        Navigator.of(context).pop();
        widget.onSuccess(created);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSubmitting = false;
          _formError = e.toString();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currencyFormat = NumberFormat('#,##0.00', 'en_US');

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 600),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Form(
            key: _formKey,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Issue Statutory Withholding Receipt', style: AppTypography.h3()),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  ),
                  Text(
                    'Directive No. 1142/2026 Art. 2(17, 19) — Income Tax (2%) or VAT Withholding.',
                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                  ),
                  const Divider(height: 24, thickness: 1, color: AppColors.rule),

                  if (_formError != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.red600.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: AppColors.red600),
                      ),
                      child: Text(_formError!, style: AppTypography.bodySmall(color: AppColors.red600)),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Withholding Type Selector
                  DropdownButtonFormField<WithholdingType>(
                    value: _withholdingType,
                    decoration: const InputDecoration(labelText: 'Withholding Category *', isDense: true),
                    items: WithholdingType.values.map((t) {
                      return DropdownMenuItem(value: t, child: Text(t.label));
                    }).toList(),
                    onChanged: (val) {
                      if (val != null) setState(() => _withholdingType = val);
                    },
                  ),
                  const SizedBox(height: 12),

                  // Withholding Agent
                  Text('Withholding Agent (Issuer)', style: AppTypography.uiLabelBold()),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: _agentTinCtrl,
                          decoration: const InputDecoration(labelText: 'Agent TIN *', isDense: true),
                          validator: (v) => TinValidator.validate(v?.trim() ?? ''),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextFormField(
                          controller: _agentNameCtrl,
                          decoration: const InputDecoration(labelText: 'Agent Legal Name *', isDense: true),
                          validator: (v) => (v == null || v.trim().isEmpty) ? 'Required' : null,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Beneficiary Taxpayer
                  Text('Beneficiary / Supplier Taxpayer', style: AppTypography.uiLabelBold()),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: _taxpayerTinCtrl,
                          decoration: const InputDecoration(labelText: 'Taxpayer TIN *', isDense: true),
                          validator: (v) => TinValidator.validate(v?.trim() ?? ''),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextFormField(
                          controller: _taxpayerNameCtrl,
                          decoration: const InputDecoration(labelText: 'Taxpayer Legal Name *', isDense: true),
                          validator: (v) => (v == null || v.trim().isEmpty) ? 'Required' : null,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Tax Base & Reference
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: _taxBaseCtrl,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(
                            labelText: 'Tax Base Amount (Gross) *',
                            hintText: '0.00 ETB',
                            isDense: true,
                          ),
                          onChanged: (_) => setState(() {}),
                          validator: (v) {
                            if (v == null || v.trim().isEmpty) return 'Mandatory';
                            final d = double.tryParse(v.trim());
                            if (d == null || d <= 0) return 'Must be positive';
                            return null;
                          },
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextFormField(
                          controller: _relatedIrnCtrl,
                          decoration: const InputDecoration(
                            labelText: 'Related Invoice IRN (Optional)',
                            hintText: 'IRN-...',
                            isDense: true,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  TextFormField(
                    controller: _paymentRefCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Payment Reference / Bank Voucher Number',
                      hintText: 'e.g. FT-2026-98124',
                      isDense: true,
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Calculation Summary
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: AppColors.rule),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _withholdingType == WithholdingType.incomeTaxWithholding
                                  ? 'Income Tax Rate: 2.0%'
                                  : 'VAT Withholding: 50% of 15% (7.5%)',
                              style: AppTypography.caption(color: AppColors.inkMuted),
                            ),
                            Text('Estimated Tax Deducted:', style: AppTypography.uiLabelBold()),
                          ],
                        ),
                        Text(
                          '${currencyFormat.format(_calculatedWithheldAmount)} ETB',
                          style: AppTypography.h3(color: AppColors.navy900),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Actions
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      OutlinedButton(
                        onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
                        child: const Text('Cancel'),
                      ),
                      const SizedBox(width: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.navy900,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                        ),
                        onPressed: _isSubmitting ? null : _submit,
                        child: _isSubmitting
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                              )
                            : const Text('Issue Withholding Receipt'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
