import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_error.dart';

class TaxAdjustmentItem {
  final String id;
  final String noteType; // CREDIT_NOTE or DEBIT_NOTE
  final String originalIrn;
  final String? irn;
  final String reason;
  final double adjustedPreTax;
  final double adjustedTax;
  final double adjustedTotal;
  final String status;
  final String? ackDate;
  final DateTime createdAt;

  TaxAdjustmentItem({
    required this.id,
    required this.noteType,
    required this.originalIrn,
    this.irn,
    required this.reason,
    required this.adjustedPreTax,
    required this.adjustedTax,
    required this.adjustedTotal,
    required this.status,
    this.ackDate,
    required this.createdAt,
  });

  factory TaxAdjustmentItem.fromJson(Map<String, dynamic> json) {
    return TaxAdjustmentItem(
      id: json['id']?.toString() ?? '',
      noteType: json['noteType']?.toString() ?? 'CREDIT_NOTE',
      originalIrn: json['originalIrn']?.toString() ?? '',
      irn: json['irn']?.toString(),
      reason: json['adjustmentReason']?.toString() ?? '',
      adjustedPreTax: (json['adjustedPreTax'] as num?)?.toDouble() ?? 0.0,
      adjustedTax: (json['adjustedTax'] as num?)?.toDouble() ?? 0.0,
      adjustedTotal: (json['adjustedTotal'] as num?)?.toDouble() ?? 0.0,
      status: json['status']?.toString() ?? 'REGISTERED',
      ackDate: json['ackDate']?.toString(),
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class TaxAdjustmentScreen extends ConsumerStatefulWidget {
  const TaxAdjustmentScreen({super.key});

  @override
  ConsumerState<TaxAdjustmentScreen> createState() => _TaxAdjustmentScreenState();
}

class _TaxAdjustmentScreenState extends ConsumerState<TaxAdjustmentScreen> {
  bool _isLoading = false;
  String? _errorMessage;
  List<TaxAdjustmentItem> _adjustments = [];
  String _filterType = 'ALL'; // ALL, CREDIT_NOTE, DEBIT_NOTE

  final NumberFormat _currencyFormat = NumberFormat.currency(symbol: 'ETB ', decimalDigits: 2);
  final DateFormat _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadAdjustments();
  }

  Future<void> _loadAdjustments() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final client = ref.read(apiClientProvider);
      final response = await client.get('/api/v1/adjustments');
      
      final content = response.data['content'] as List<dynamic>? ?? [];
      final list = content.map((item) => TaxAdjustmentItem.fromJson(item as Map<String, dynamic>)).toList();

      if (mounted) {
        setState(() {
          _adjustments = list;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e is AppError ? e.userMessage : e.toString();
          _isLoading = false;
        });
      }
    }
  }

  void _openAdjustmentDialog([String initialType = 'CREDIT_NOTE']) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _CreateAdjustmentDialog(
        initialType: initialType,
        onSuccess: () {
          Navigator.of(ctx).pop();
          _loadAdjustments();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('$initialType successfully registered with MoR under Directive No. 1142/2026 Art. 25.'),
              backgroundColor: AppColors.green700,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _adjustments.where((adj) {
      if (_filterType == 'ALL') return true;
      return adj.noteType == _filterType;
    }).toList();

    final totalCredit = _adjustments
        .where((a) => a.noteType == 'CREDIT_NOTE')
        .fold(0.0, (acc, item) => acc + item.adjustedTotal);
    final totalDebit = _adjustments
        .where((a) => a.noteType == 'DEBIT_NOTE')
        .fold(0.0, (acc, item) => acc + item.adjustedTotal);

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Column(
        children: [
          // Header Bar
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
                      Text('TAX ADJUSTMENTS (CREDIT / DEBIT NOTES)', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'Statutory price and tax adjustments referencing registered electronic invoices (Directive No. 1142/2026 Art. 25)',
                        style: AppTypography.bodySmall(color: AppColors.inkMuted),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Row(
                  children: [
                    OutlinedButton.icon(
                      onPressed: () => _openAdjustmentDialog('DEBIT_NOTE'),
                      icon: const Icon(Icons.arrow_upward, size: 16, color: AppColors.blue600),
                      label: const Text('Issue Debit Note'),
                    ),
                    const SizedBox(width: 12),
                    ElevatedButton.icon(
                      onPressed: () => _openAdjustmentDialog('CREDIT_NOTE'),
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.navy900),
                      icon: const Icon(Icons.arrow_downward, size: 16),
                      label: const Text('Issue Credit Note'),
                    ),
                  ],
                ),
              ],
            ),
          ),

          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // KPI Summary Cards
                  Row(
                    children: [
                      Expanded(
                        child: _buildSummaryCard(
                          title: 'Total Credit Notes (Downward)',
                          value: _currencyFormat.format(totalCredit),
                          count: _adjustments.where((a) => a.noteType == 'CREDIT_NOTE').length,
                          color: AppColors.amber600,
                          icon: Icons.assignment_returned_outlined,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: _buildSummaryCard(
                          title: 'Total Debit Notes (Upward)',
                          value: _currencyFormat.format(totalDebit),
                          count: _adjustments.where((a) => a.noteType == 'DEBIT_NOTE').length,
                          color: AppColors.blue600,
                          icon: Icons.note_add_outlined,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: _buildSummaryCard(
                          title: 'Net Adjusted Turnover',
                          value: _currencyFormat.format(totalDebit - totalCredit),
                          count: _adjustments.length,
                          color: (totalDebit - totalCredit) >= 0 ? AppColors.green700 : AppColors.red600,
                          icon: Icons.account_balance_wallet_outlined,
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 24),

                  // Filter & Search Controls
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Wrap(
                        spacing: 8,
                        children: [
                          ChoiceChip(
                            label: const Text('All Adjustments'),
                            selected: _filterType == 'ALL',
                            onSelected: (sel) {
                              if (sel) setState(() => _filterType = 'ALL');
                            },
                          ),
                          ChoiceChip(
                            label: const Text('Credit Notes (ክሬዲት ኖት)'),
                            selected: _filterType == 'CREDIT_NOTE',
                            onSelected: (sel) {
                              if (sel) setState(() => _filterType = 'CREDIT_NOTE');
                            },
                          ),
                          ChoiceChip(
                            label: const Text('Debit Notes (ዴቢት ኖት)'),
                            selected: _filterType == 'DEBIT_NOTE',
                            onSelected: (sel) {
                              if (sel) setState(() => _filterType = 'DEBIT_NOTE');
                            },
                          ),
                        ],
                      ),
                      IconButton(
                        onPressed: _loadAdjustments,
                        icon: const Icon(Icons.refresh, size: 20, color: AppColors.inkMuted),
                        tooltip: 'Refresh adjustments',
                      ),
                    ],
                  ),

                  const SizedBox(height: 16),

                  // Content View
                  if (_isLoading)
                    const Padding(
                      padding: EdgeInsets.all(48.0),
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if (_errorMessage != null)
                    _buildErrorState()
                  else if (filtered.isEmpty)
                    _buildEmptyState()
                  else
                    _buildAdjustmentsTable(filtered),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryCard({
    required String title,
    required String value,
    required int count,
    required Color color,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(3),
            ),
            child: Icon(icon, color: color, size: 28),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppTypography.uiLabel(color: AppColors.inkMuted)),
                const SizedBox(height: 4),
                Text(value, style: AppTypography.h2().copyWith(color: AppColors.navy900)),
                const SizedBox(height: 2),
                Text('$count registered notes', style: AppTypography.bodySmall(color: AppColors.inkMuted)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAdjustmentsTable(List<TaxAdjustmentItem> list) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Table(
        columnWidths: const {
          0: FlexColumnWidth(1.2), // Type
          1: FlexColumnWidth(1.8), // IRN / Note IRN
          2: FlexColumnWidth(1.8), // Original IRN
          3: FlexColumnWidth(2.0), // Reason
          4: FlexColumnWidth(1.2), // Pre-Tax
          5: FlexColumnWidth(1.0), // Tax
          6: FlexColumnWidth(1.4), // Total
          7: FlexColumnWidth(1.2), // Status
          8: FlexColumnWidth(1.4), // Date
        },
        children: [
          TableRow(
            decoration: const BoxDecoration(
              color: AppColors.paper,
              border: Border(bottom: BorderSide(color: AppColors.rule)),
            ),
            children: [
              _buildHeaderCell('NOTE TYPE'),
              _buildHeaderCell('ADJUSTMENT IRN'),
              _buildHeaderCell('ORIGINAL INVOICE IRN'),
              _buildHeaderCell('REASON / JUSTIFICATION'),
              _buildHeaderCell('PRE-TAX (ETB)', alignRight: true),
              _buildHeaderCell('TAX (15%)', alignRight: true),
              _buildHeaderCell('TOTAL AMOUNT', alignRight: true),
              _buildHeaderCell('GOV STATUS'),
              _buildHeaderCell('REGISTERED AT'),
            ],
          ),
          ...list.map((adj) => TableRow(
            decoration: const BoxDecoration(
              border: Border(bottom: BorderSide(color: AppColors.ruleLight)),
            ),
            children: [
              _buildTypeCell(adj.noteType),
              _buildMonoCell(adj.irn ?? 'PENDING'),
              _buildMonoCell(adj.originalIrn),
              _buildTextCell(adj.reason),
              _buildTextCell(_currencyFormat.format(adj.adjustedPreTax), alignRight: true),
              _buildTextCell(_currencyFormat.format(adj.adjustedTax), alignRight: true),
              _buildTextCell(_currencyFormat.format(adj.adjustedTotal), alignRight: true, bold: true),
              _buildStatusCell(adj.status),
              _buildTextCell(_dateFormat.format(adj.createdAt)),
            ],
          )),
        ],
      ),
    );
  }

  Widget _buildHeaderCell(String title, {bool alignRight = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Text(
        title,
        textAlign: alignRight ? TextAlign.right : TextAlign.left,
        style: AppTypography.uiLabelBold(color: AppColors.inkMuted).copyWith(fontSize: 11),
      ),
    );
  }

  Widget _buildTextCell(String text, {bool alignRight = false, bool bold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Text(
        text,
        textAlign: alignRight ? TextAlign.right : TextAlign.left,
        style: bold
            ? AppTypography.uiLabelBold(color: AppColors.ink)
            : AppTypography.uiLabel(color: AppColors.ink),
      ),
    );
  }

  Widget _buildMonoCell(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Text(
        text,
        style: AppTypography.mono(color: AppColors.navy700, weight: FontWeight.w500).copyWith(fontSize: 11),
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  Widget _buildTypeCell(String type) {
    final isCredit = type == 'CREDIT_NOTE';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isCredit ? Icons.arrow_downward : Icons.arrow_upward,
            size: 14,
            color: isCredit ? AppColors.amber600 : AppColors.blue600,
          ),
          const SizedBox(width: 6),
          Text(
            isCredit ? 'CREDIT' : 'DEBIT',
            style: AppTypography.uiLabelBold(
              color: isCredit ? AppColors.amber600 : AppColors.blue600,
            ).copyWith(fontSize: 11),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusCell(String status) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.green700.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(2),
        ),
        child: Text(
          status,
          style: AppTypography.uiLabelBold(color: AppColors.green700).copyWith(fontSize: 10),
          textAlign: TextAlign.center,
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(48),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        children: [
          const Icon(Icons.assignment_turned_in_outlined, size: 48, color: AppColors.inkMuted),
          const SizedBox(height: 16),
          Text('No Tax Adjustments Found', style: AppTypography.h2()),
          const SizedBox(height: 8),
          Text(
            'No credit or debit notes have been issued for this tenant yet under Directive No. 1142/2026 Art. 25.',
            textAlign: TextAlign.center,
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorState() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: AppColors.red600.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.red600.withValues(alpha: 0.3)),
      ),
      child: Column(
        children: [
          const Icon(Icons.error_outline, size: 40, color: AppColors.red600),
          const SizedBox(height: 12),
          Text('Failed to load tax adjustments', style: AppTypography.h3()),
          const SizedBox(height: 6),
          Text(_errorMessage!, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _loadAdjustments,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}

class _CreateAdjustmentDialog extends ConsumerStatefulWidget {
  final String initialType;
  final VoidCallback onSuccess;

  const _CreateAdjustmentDialog({
    required this.initialType,
    required this.onSuccess,
  });

  @override
  ConsumerState<_CreateAdjustmentDialog> createState() => _CreateAdjustmentDialogState();
}

class _CreateAdjustmentDialogState extends ConsumerState<_CreateAdjustmentDialog> {
  final _formKey = GlobalKey<FormState>();
  late String _noteType;
  final _originalIrnController = TextEditingController();
  final _reasonController = TextEditingController();
  final _preTaxController = TextEditingController();
  final _taxController = TextEditingController();

  bool _isSubmitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _noteType = widget.initialType;
    _preTaxController.addListener(_recomputeTax);
  }

  @override
  void dispose() {
    _originalIrnController.dispose();
    _reasonController.dispose();
    _preTaxController.dispose();
    _taxController.dispose();
    super.dispose();
  }

  void _recomputeTax() {
    final preTax = double.tryParse(_preTaxController.text) ?? 0.0;
    final standardTax = (preTax * 0.15); // Standard Ethiopian VAT 15%
    _taxController.text = standardTax.toStringAsFixed(2);
    setState(() {});
  }

  Future<void> _submitAdjustment() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    try {
      final client = ref.read(apiClientProvider);
      final preTax = double.parse(_preTaxController.text.trim());
      final tax = double.parse(_taxController.text.trim());

      final endpoint = _noteType == 'CREDIT_NOTE'
          ? '/api/v1/adjustments/credit-notes'
          : '/api/v1/adjustments/debit-notes';

      await client.post(
        endpoint,
        data: {
          'originalIrn': _originalIrnController.text.trim(),
          'reason': _reasonController.text.trim(),
          'adjustedPreTax': preTax,
          'adjustedTax': tax,
        },
      );

      widget.onSuccess();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e is AppError ? e.userMessage : e.toString();
          _isSubmitting = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final preTax = double.tryParse(_preTaxController.text) ?? 0.0;
    final tax = double.tryParse(_taxController.text) ?? 0.0;
    final total = preTax + tax;

    return Dialog(
      backgroundColor: AppColors.paperRaised,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(3)),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 580),
        padding: const EdgeInsets.all(28),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'ISSUE TAX ADJUSTMENT NOTE',
                    style: AppTypography.h2(),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'Directive No. 1142/2026 Art. 25 statutory adjustment against registered tax invoice',
                style: AppTypography.bodySmall(color: AppColors.inkMuted),
              ),
              const Divider(height: 28, color: AppColors.rule),

              if (_error != null) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.red600.withValues(alpha: 0.1),
                    border: Border.all(color: AppColors.red600),
                    borderRadius: BorderRadius.circular(2),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, color: AppColors.red600, size: 18),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.red600)),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // Note Type Selector
              Row(
                children: [
                  Expanded(
                    child: RadioListTile<String>(
                      title: const Text('Credit Note (Price Reduction / Return)'),
                      value: 'CREDIT_NOTE',
                      groupValue: _noteType,
                      contentPadding: EdgeInsets.zero,
                      onChanged: (val) => setState(() => _noteType = val!),
                    ),
                  ),
                  Expanded(
                    child: RadioListTile<String>(
                      title: const Text('Debit Note (Upward Correction)'),
                      value: 'DEBIT_NOTE',
                      groupValue: _noteType,
                      contentPadding: EdgeInsets.zero,
                      onChanged: (val) => setState(() => _noteType = val!),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Original IRN
              TextFormField(
                controller: _originalIrnController,
                decoration: const InputDecoration(
                  labelText: 'Original Registered Invoice IRN *',
                  hintText: 'e.g. MOR-2026-INV-98213894',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.receipt, size: 18),
                ),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Original IRN is mandatory' : null,
              ),
              const SizedBox(height: 16),

              // Reason
              TextFormField(
                controller: _reasonController,
                decoration: const InputDecoration(
                  labelText: 'Statutory Reason / Justification *',
                  hintText: 'e.g. Returned damaged goods per agreement #492',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.description, size: 18),
                ),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Reason is mandatory' : null,
              ),
              const SizedBox(height: 16),

              // Pre-Tax and Tax Row
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _preTaxController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Adjusted Pre-Tax Amount (ETB) *',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.attach_money, size: 18),
                      ),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) return 'Amount required';
                        final num = double.tryParse(v);
                        if (num == null || num <= 0) return 'Must be positive';
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: TextFormField(
                      controller: _taxController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Adjusted Tax Amount (15% VAT) *',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.percent, size: 18),
                      ),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) return 'Tax required';
                        final num = double.tryParse(v);
                        if (num == null || num < 0) return 'Must be positive or zero';
                        return null;
                      },
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 16),

              // Calculated Total Banner
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: AppColors.paper,
                  borderRadius: BorderRadius.circular(2),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Total Adjustment Amount:', style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
                    Text(
                      'ETB ${total.toStringAsFixed(2)}',
                      style: AppTypography.h3().copyWith(
                        color: _noteType == 'CREDIT_NOTE' ? AppColors.amber600 : AppColors.blue600,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 24),

              // Actions
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
                    child: const Text('Cancel'),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton(
                    onPressed: _isSubmitting ? null : _submitAdjustment,
                    style: ElevatedButton.styleFrom(backgroundColor: AppColors.navy900),
                    child: _isSubmitting
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : Text('Submit $_noteType'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
