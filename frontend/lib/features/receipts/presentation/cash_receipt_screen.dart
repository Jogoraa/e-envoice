import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/utils/tin_validator.dart';
import '../../../data/services/compliance/statutory_documents_service.dart';

class CashReceiptScreen extends ConsumerStatefulWidget {
  const CashReceiptScreen({super.key});

  @override
  ConsumerState<CashReceiptScreen> createState() => _CashReceiptScreenState();
}

class _CashReceiptScreenState extends ConsumerState<CashReceiptScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  List<CashReceiptDto> _receipts = [];
  CashReceiptPurpose? _selectedFilterPurpose;
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
      final list = await service.listCashReceipts(purpose: _selectedFilterPurpose);
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

  void _openCreateReceiptDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _CreateCashReceiptDialog(
        onSuccess: (receipt) {
          _loadReceipts();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Cash Receipt ${receipt.receiptNumber} issued successfully.'),
              backgroundColor: AppColors.green700,
            ),
          );
        },
      ),
    );
  }

  void _showReceiptPrintPreview(CashReceiptDto receipt) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Text(
                  'CASH RECEIPT / የጥሬ ገንዘብ መቀበያ ደረሰኝ',
                  style: AppTypography.h3(),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
                Text(
                  'Directive No. 1142/2026 Art. 2(16) & Art. 4(1)(f)',
                  style: AppTypography.caption(color: AppColors.inkMuted),
                  textAlign: TextAlign.center,
                ),
                const Divider(height: 24, thickness: 1, color: AppColors.rule),
                _buildReceiptRow('Receipt Number', receipt.receiptNumber),
                _buildReceiptRow('Date & Time', _dateFormat.format(receipt.receivedAt)),
                _buildReceiptRow('Payer Name', receipt.payerName),
                if (receipt.payerTin != null && receipt.payerTin!.isNotEmpty)
                  _buildReceiptRow('Payer TIN', receipt.payerTin!),
                _buildReceiptRow('Purpose', receipt.purpose.label),
                if (receipt.purposeDescription != null)
                  _buildReceiptRow('Description', receipt.purposeDescription!),
                _buildReceiptRow('Payment Method', receipt.paymentMethod.label),
                if (receipt.referenceNumber != null)
                  _buildReceiptRow('Reference', receipt.referenceNumber!),
                const Divider(height: 20, thickness: 1, color: AppColors.rule),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('TOTAL AMOUNT:', style: AppTypography.uiLabelBold(size: 16)),
                    Text(
                      '${_currencyFormat.format(receipt.amount)} ${receipt.currency}',
                      style: AppTypography.h3(color: AppColors.green700),
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

  Widget _buildReceiptRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
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
          r.payerName.toLowerCase().contains(query) ||
          (r.payerTin != null && r.payerTin!.contains(query));
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
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.payments_outlined, color: AppColors.navy900, size: 28),
                          const SizedBox(width: 10),
                          Flexible(
                            child: Text(
                              'Cash Receipts / የጥሬ ገንዘብ መቀበያ ደረሰኞች',
                              style: AppTypography.h2(),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Statutory non-sale receipting pursuant to FDRE MoR Directive No. 1142/2026 Art. 2(16) & Art. 4(1)(f)',
                        style: AppTypography.bodySmall(color: AppColors.inkMuted),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy900,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                  ),
                  onPressed: _openCreateReceiptDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Issue Cash Receipt'),
                ),
              ],
            ),
          ),

          // Filters Bar
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
                      hintText: 'Search by receipt number, payer name, or TIN...',
                      prefixIcon: const Icon(Icons.search, size: 20),
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  flex: 2,
                  child: DropdownButtonFormField<CashReceiptPurpose?>(
                    isExpanded: true,
                    value: _selectedFilterPurpose,
                    decoration: InputDecoration(
                      labelText: 'Filter by Purpose',
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('All Statutory Purposes')),
                      ...CashReceiptPurpose.values.map(
                        (p) => DropdownMenuItem(value: p, child: Text(p.label, maxLines: 1, overflow: TextOverflow.ellipsis)),
                      ),
                    ],
                    onChanged: (val) {
                      setState(() => _selectedFilterPurpose = val);
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
                            Text('Failed to load cash receipts', style: AppTypography.h3()),
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
                                const Icon(Icons.receipt_outlined, size: 56, color: AppColors.inkMuted),
                                const SizedBox(height: 16),
                                Text('No Cash Receipts Found', style: AppTypography.h3()),
                                const SizedBox(height: 6),
                                Text(
                                  'Issue standalone receipts for advance collections, credit settlements, or loans.',
                                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                ),
                                const SizedBox(height: 20),
                                ElevatedButton.icon(
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: AppColors.navy900,
                                    foregroundColor: Colors.white,
                                  ),
                                  onPressed: _openCreateReceiptDialog,
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
                              return Container(
                                color: Colors.white,
                                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                                child: Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.all(10),
                                      decoration: BoxDecoration(
                                        color: AppColors.green700.withValues(alpha: 0.1),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: const Icon(Icons.receipt, color: AppColors.green700, size: 24),
                                    ),
                                    const SizedBox(width: 16),
                                    Expanded(
                                      flex: 2,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(r.receiptNumber, style: AppTypography.uiLabelBold(size: 14)),
                                          const SizedBox(height: 2),
                                          Text(_dateFormat.format(r.receivedAt), style: AppTypography.caption(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                    ),
                                    Expanded(
                                      flex: 3,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(r.payerName, style: AppTypography.body(color: AppColors.ink)),
                                          if (r.payerTin != null && r.payerTin!.isNotEmpty)
                                            Text('TIN: ${r.payerTin}', style: AppTypography.caption(color: AppColors.inkMuted)),
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
                                          r.purpose.label,
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
                                            '${_currencyFormat.format(r.amount)} ${r.currency}',
                                            style: AppTypography.uiLabelBold(color: AppColors.green700, size: 14),
                                          ),
                                          Text(r.paymentMethod.label, style: AppTypography.caption(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    IconButton(
                                      tooltip: 'Print / View Details',
                                      icon: const Icon(Icons.print_outlined, color: AppColors.navy700),
                                      onPressed: () => _showReceiptPrintPreview(r),
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

class _CreateCashReceiptDialog extends ConsumerStatefulWidget {
  final ValueChanged<CashReceiptDto> onSuccess;
  const _CreateCashReceiptDialog({required this.onSuccess});

  @override
  ConsumerState<_CreateCashReceiptDialog> createState() => _CreateCashReceiptDialogState();
}

class _CreateCashReceiptDialogState extends ConsumerState<_CreateCashReceiptDialog> {
  final _formKey = GlobalKey<FormState>();
  final _payerNameCtrl = TextEditingController();
  final _payerTinCtrl = TextEditingController();
  final _amountCtrl = TextEditingController();
  final _purposeDescCtrl = TextEditingController();
  final _referenceNumberCtrl = TextEditingController();

  CashReceiptPurpose _purpose = CashReceiptPurpose.advancePayment;
  CashReceiptPaymentMethod _paymentMethod = CashReceiptPaymentMethod.cash;
  String _currency = 'ETB';
  bool _isSubmitting = false;
  String? _formError;

  @override
  void dispose() {
    _payerNameCtrl.dispose();
    _payerTinCtrl.dispose();
    _amountCtrl.dispose();
    _purposeDescCtrl.dispose();
    _referenceNumberCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isSubmitting = true;
      _formError = null;
    });

    try {
      final amount = double.parse(_amountCtrl.text.trim());
      final payload = {
        'payerName': _payerNameCtrl.text.trim(),
        if (_payerTinCtrl.text.trim().isNotEmpty) 'payerTin': _payerTinCtrl.text.trim(),
        'amount': amount,
        'currency': _currency,
        'purpose': _purpose.code,
        if (_purposeDescCtrl.text.trim().isNotEmpty) 'purposeDescription': _purposeDescCtrl.text.trim(),
        'paymentMethod': _paymentMethod.code,
        if (_referenceNumberCtrl.text.trim().isNotEmpty) 'referenceNumber': _referenceNumberCtrl.text.trim(),
        'receivedAt': DateTime.now().toIso8601String(),
      };

      final service = ref.read(statutoryDocumentsServiceProvider);
      final created = await service.issueCashReceipt(payload);

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
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 580),
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
                      Text('Issue Standalone Cash Receipt', style: AppTypography.h3()),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  ),
                  Text(
                    'Directive No. 1142/2026 Art. 2(16) — Advance, settlement, or non-sale collection.',
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

                  // Payer Details
                  TextFormField(
                    controller: _payerNameCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Payer Full Name *',
                      hintText: 'e.g. Abebe Bikila or ABC Trading PLC',
                      isDense: true,
                    ),
                    validator: (v) => (v == null || v.trim().isEmpty) ? 'Payer name is mandatory' : null,
                  ),
                  const SizedBox(height: 12),

                  TextFormField(
                    controller: _payerTinCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Payer TIN (Optional for B2C)',
                      hintText: '10 digits (e.g. 0012345678)',
                      isDense: true,
                    ),
                    validator: (v) {
                      if (v != null && v.trim().isNotEmpty) {
                        return TinValidator.validate(v.trim());
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 12),

                  // Purpose & Description
                  DropdownButtonFormField<CashReceiptPurpose>(
                    value: _purpose,
                    decoration: const InputDecoration(
                      labelText: 'Statutory Purpose *',
                      isDense: true,
                    ),
                    items: CashReceiptPurpose.values.map((p) {
                      return DropdownMenuItem(value: p, child: Text(p.label));
                    }).toList(),
                    onChanged: (val) {
                      if (val != null) setState(() => _purpose = val);
                    },
                  ),
                  const SizedBox(height: 12),

                  TextFormField(
                    controller: _purposeDescCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Purpose Description / Reason Details',
                      hintText: 'e.g. 30% advance procurement deposit for Contract #UT-2026',
                      isDense: true,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Amount & Currency
                  Row(
                    children: [
                      Expanded(
                        flex: 3,
                        child: TextFormField(
                          controller: _amountCtrl,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(
                            labelText: 'Amount Collected *',
                            hintText: '0.00',
                            isDense: true,
                          ),
                          validator: (v) {
                            if (v == null || v.trim().isEmpty) return 'Amount is mandatory';
                            final d = double.tryParse(v.trim());
                            if (d == null || d <= 0) return 'Must be a positive amount';
                            return null;
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 1,
                        child: DropdownButtonFormField<String>(
                          value: _currency,
                          decoration: const InputDecoration(labelText: 'Currency', isDense: true),
                          items: const [
                            DropdownMenuItem(value: 'ETB', child: Text('ETB')),
                            DropdownMenuItem(value: 'USD', child: Text('USD')),
                          ],
                          onChanged: (val) {
                            if (val != null) setState(() => _currency = val);
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Payment Method & Reference
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<CashReceiptPaymentMethod>(
                          value: _paymentMethod,
                          decoration: const InputDecoration(labelText: 'Payment Method *', isDense: true),
                          items: CashReceiptPaymentMethod.values.map((m) {
                            return DropdownMenuItem(value: m, child: Text(m.label));
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) setState(() => _paymentMethod = val);
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextFormField(
                          controller: _referenceNumberCtrl,
                          decoration: const InputDecoration(
                            labelText: 'Transaction Ref / Check No.',
                            hintText: 'e.g. CBE-TX-984210',
                            isDense: true,
                          ),
                        ),
                      ),
                    ],
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
                            : const Text('Issue Receipt'),
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
