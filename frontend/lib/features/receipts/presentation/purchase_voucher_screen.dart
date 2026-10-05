import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/statutory_documents_service.dart';

class PurchaseVoucherScreen extends ConsumerStatefulWidget {
  const PurchaseVoucherScreen({super.key});

  @override
  ConsumerState<PurchaseVoucherScreen> createState() => _PurchaseVoucherScreenState();
}

class _PurchaseVoucherScreenState extends ConsumerState<PurchaseVoucherScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  List<PurchaseVoucherDto> _vouchers = [];
  final _searchController = TextEditingController();

  final _currencyFormat = NumberFormat('#,##0.00', 'en_US');
  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadVouchers();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadVouchers() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(statutoryDocumentsServiceProvider);
      final list = await service.listPurchaseVouchers();
      if (mounted) {
        setState(() {
          _vouchers = list;
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

  void _openCreateVoucherDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _CreatePurchaseVoucherDialog(
        onSuccess: (voucher) {
          _loadVouchers();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Purchase Voucher ${voucher.voucherNumber} created successfully.'),
              backgroundColor: AppColors.green700,
            ),
          );
        },
      ),
    );
  }

  void _showVoucherPrintPreview(PurchaseVoucherDto voucher) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Text(
                  'PURCHASE VOUCHER / የግዥ ማረጋገጫ ሰነድ',
                  style: AppTypography.h3(),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
                Text(
                  'Buyer-Issued Fiscal Document (Directive No. 1142/2026 Art. 2(18))',
                  style: AppTypography.caption(color: AppColors.inkMuted),
                  textAlign: TextAlign.center,
                ),
                const Divider(height: 24, thickness: 1, color: AppColors.rule),
                _buildRow('Voucher Number', voucher.voucherNumber),
                _buildRow('Transaction Date', _dateFormat.format(voucher.transactionDate)),
                _buildRow('Supplier Name', voucher.supplierName),
                if (voucher.supplierTin != null && voucher.supplierTin!.isNotEmpty)
                  _buildRow('Supplier TIN', voucher.supplierTin!),
                if (voucher.supplierIdNumber != null && voucher.supplierIdNumber!.isNotEmpty)
                  _buildRow('National ID / Kebele ID', '${voucher.supplierIdType ?? "ID"}: ${voucher.supplierIdNumber}'),
                _buildRow('Statutory Reason', voucher.unavailableReason.label),
                if (voucher.reasonDescription != null)
                  _buildRow('Reason Details', voucher.reasonDescription!),
                const Divider(height: 16, thickness: 1, color: AppColors.rule),

                // Items summary
                Text('Line Items (${voucher.items.length}):', style: AppTypography.uiLabelBold()),
                const SizedBox(height: 6),
                ...voucher.items.map((item) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Flexible(
                          child: Text(
                            '${item.quantity} ${item.unitOfMeasure} × ${item.itemDescription}',
                            style: AppTypography.bodySmall(),
                          ),
                        ),
                        Text(
                          '${_currencyFormat.format(item.lineTotal)} ETB',
                          style: AppTypography.bodySmall().copyWith(fontWeight: FontWeight.w600),
                        ),
                      ],
                    ),
                  );
                }),

                const Divider(height: 16, thickness: 1, color: AppColors.rule),
                if (voucher.withholdingAmount != null && voucher.withholdingAmount! > 0)
                  _buildRow('Withholding Deducted', '-${_currencyFormat.format(voucher.withholdingAmount)} ETB'),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('TOTAL NET PAYABLE:', style: AppTypography.uiLabelBold(size: 16)),
                    Text(
                      '${_currencyFormat.format(voucher.totalAmount)} ${voucher.currency}',
                      style: AppTypography.h3(color: AppColors.green700),
                    ),
                  ],
                ),
                if (voucher.irn != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      'IRN: ${voucher.irn}',
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
    final filtered = _vouchers.where((v) {
      if (query.isEmpty) return true;
      return v.voucherNumber.toLowerCase().contains(query) ||
          v.supplierName.toLowerCase().contains(query) ||
          (v.supplierTin != null && v.supplierTin!.contains(query));
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
                        const Icon(Icons.receipt_long_outlined, color: AppColors.navy900, size: 28),
                        const SizedBox(width: 10),
                        Text('Purchase Vouchers / የግዥ ማረጋገጫ ሰነዶች', style: AppTypography.h2()),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Buyer-issued fiscal vouchers for purchases without seller tax invoices (Directive No. 1142/2026 Art. 2(18))',
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
                  onPressed: _openCreateVoucherDialog,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Generate Purchase Voucher'),
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
                  child: TextField(
                    controller: _searchController,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      hintText: 'Search by voucher number, supplier name, or supplier TIN...',
                      prefixIcon: const Icon(Icons.search, size: 20),
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                IconButton(
                  tooltip: 'Refresh list',
                  icon: const Icon(Icons.refresh),
                  onPressed: _loadVouchers,
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
                            Text('Failed to load purchase vouchers', style: AppTypography.h3()),
                            const SizedBox(height: 6),
                            Text(_errorMessage!, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                            const SizedBox(height: 16),
                            ElevatedButton(onPressed: _loadVouchers, child: const Text('Retry')),
                          ],
                        ),
                      )
                    : filtered.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.description_outlined, size: 56, color: AppColors.inkMuted),
                                const SizedBox(height: 16),
                                Text('No Purchase Vouchers Recorded', style: AppTypography.h3()),
                                const SizedBox(height: 6),
                                Text(
                                  'Issue purchase vouchers for agricultural produce, informal vendors, or exempt procurements.',
                                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                ),
                                const SizedBox(height: 20),
                                ElevatedButton.icon(
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: AppColors.navy900,
                                    foregroundColor: Colors.white,
                                  ),
                                  onPressed: _openCreateVoucherDialog,
                                  icon: const Icon(Icons.add, size: 16),
                                  label: const Text('Generate First Voucher'),
                                ),
                              ],
                            ),
                          )
                        : ListView.separated(
                            padding: const EdgeInsets.all(24),
                            itemCount: filtered.length,
                            separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                            itemBuilder: (context, index) {
                              final v = filtered[index];
                              return Container(
                                color: Colors.white,
                                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                                child: Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.all(10),
                                      decoration: BoxDecoration(
                                        color: AppColors.navy700.withValues(alpha: 0.1),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: const Icon(Icons.description, color: AppColors.navy700, size: 24),
                                    ),
                                    const SizedBox(width: 16),
                                    Expanded(
                                      flex: 2,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(v.voucherNumber, style: AppTypography.uiLabelBold(size: 14)),
                                          const SizedBox(height: 2),
                                          Text(_dateFormat.format(v.transactionDate), style: AppTypography.caption(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                    ),
                                    Expanded(
                                      flex: 3,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(v.supplierName, style: AppTypography.body(color: AppColors.ink)),
                                          if (v.supplierTin != null && v.supplierTin!.isNotEmpty)
                                            Text('TIN: ${v.supplierTin}', style: AppTypography.caption(color: AppColors.inkMuted)),
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
                                          v.unavailableReason.label,
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
                                            '${_currencyFormat.format(v.totalAmount)} ${v.currency}',
                                            style: AppTypography.uiLabelBold(color: AppColors.navy900, size: 14),
                                          ),
                                          Text('${v.items.length} line items', style: AppTypography.caption(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    IconButton(
                                      tooltip: 'Print / View Details',
                                      icon: const Icon(Icons.print_outlined, color: AppColors.navy700),
                                      onPressed: () => _showVoucherPrintPreview(v),
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

class _CreatePurchaseVoucherDialog extends ConsumerStatefulWidget {
  final ValueChanged<PurchaseVoucherDto> onSuccess;
  const _CreatePurchaseVoucherDialog({required this.onSuccess});

  @override
  ConsumerState<_CreatePurchaseVoucherDialog> createState() => _CreatePurchaseVoucherDialogState();
}

class _CreatePurchaseVoucherDialogState extends ConsumerState<_CreatePurchaseVoucherDialog> {
  final _formKey = GlobalKey<FormState>();
  final _supplierNameCtrl = TextEditingController();
  final _supplierTinCtrl = TextEditingController();
  final _supplierIdNumCtrl = TextEditingController();
  final _supplierPhoneCtrl = TextEditingController();
  final _supplierAddressCtrl = TextEditingController();
  final _reasonDescCtrl = TextEditingController();

  UnavailableReceiptReason _reason = UnavailableReceiptReason.farmerAgriculturalProduce;
  String _idType = 'NATIONAL_ID';

  final List<PurchaseVoucherLineDto> _lines = [
    PurchaseVoucherLineDto(itemDescription: '', quantity: 1.0, unitOfMeasure: 'KG', unitPrice: 0.0),
  ];

  bool _isSubmitting = false;
  String? _formError;

  @override
  void dispose() {
    _supplierNameCtrl.dispose();
    _supplierTinCtrl.dispose();
    _supplierIdNumCtrl.dispose();
    _supplierPhoneCtrl.dispose();
    _supplierAddressCtrl.dispose();
    _reasonDescCtrl.dispose();
    super.dispose();
  }

  void _addLine() {
    setState(() {
      _lines.add(PurchaseVoucherLineDto(itemDescription: '', quantity: 1.0, unitOfMeasure: 'KG', unitPrice: 0.0));
    });
  }

  void _removeLine(int index) {
    if (_lines.length > 1) {
      setState(() => _lines.removeAt(index));
    }
  }

  double get _totalAmount {
    return _lines.fold(0.0, (acc, item) => acc + item.lineTotal);
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    for (final l in _lines) {
      if (l.itemDescription.trim().isEmpty || l.quantity <= 0 || l.unitPrice <= 0) {
        setState(() => _formError = 'Every item must have a description, positive quantity, and positive price.');
        return;
      }
    }

    setState(() {
      _isSubmitting = true;
      _formError = null;
    });

    try {
      final payload = {
        'supplierName': _supplierNameCtrl.text.trim(),
        if (_supplierTinCtrl.text.trim().isNotEmpty) 'supplierTin': _supplierTinCtrl.text.trim(),
        if (_supplierIdNumCtrl.text.trim().isNotEmpty) 'supplierIdNumber': _supplierIdNumCtrl.text.trim(),
        'supplierIdType': _idType,
        if (_supplierPhoneCtrl.text.trim().isNotEmpty) 'supplierPhone': _supplierPhoneCtrl.text.trim(),
        if (_supplierAddressCtrl.text.trim().isNotEmpty) 'supplierAddress': _supplierAddressCtrl.text.trim(),
        'unavailableReason': _reason.code,
        if (_reasonDescCtrl.text.trim().isNotEmpty) 'reasonDescription': _reasonDescCtrl.text.trim(),
        'transactionDate': DateTime.now().toIso8601String(),
        'currency': 'ETB',
        'items': _lines.map((l) => l.toJson()).toList(),
      };

      final service = ref.read(statutoryDocumentsServiceProvider);
      final created = await service.createPurchaseVoucher(payload);

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
        constraints: const BoxConstraints(maxWidth: 680),
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
                      Text('Generate Buyer-Issued Purchase Voucher', style: AppTypography.h3()),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  ),
                  Text(
                    'Directive No. 1142/2026 Art. 2(18) — Buyer generates fiscal voucher when seller is unable to issue tax invoice.',
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

                  // Supplier Identification
                  Text('Supplier / Vendor Particulars', style: AppTypography.uiLabelBold()),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _supplierNameCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Supplier Name *',
                      hintText: 'e.g. Farmer Kebede Haile or Rural Coffee Producer',
                      isDense: true,
                    ),
                    validator: (v) => (v == null || v.trim().isEmpty) ? 'Supplier name is mandatory' : null,
                  ),
                  const SizedBox(height: 10),

                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: _supplierTinCtrl,
                          decoration: const InputDecoration(labelText: 'Supplier TIN (If available)', isDense: true),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: _idType,
                          decoration: const InputDecoration(labelText: 'Supplier ID Type', isDense: true),
                          items: const [
                            DropdownMenuItem(value: 'NATIONAL_ID', child: Text('National Digital ID')),
                            DropdownMenuItem(value: 'KEBELE_ID', child: Text('Kebele Resident ID')),
                            DropdownMenuItem(value: 'PASSPORT', child: Text('Passport')),
                            DropdownMenuItem(value: 'DRIVING_LICENSE', child: Text('Driver License')),
                          ],
                          onChanged: (val) {
                            if (val != null) setState(() => _idType = val);
                          },
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextFormField(
                          controller: _supplierIdNumCtrl,
                          decoration: const InputDecoration(labelText: 'ID / Resident Number', isDense: true),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: _supplierPhoneCtrl,
                          decoration: const InputDecoration(labelText: 'Supplier Phone Number', isDense: true),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextFormField(
                          controller: _supplierAddressCtrl,
                          decoration: const InputDecoration(labelText: 'Supplier Address / Location', isDense: true),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Statutory Reason
                  Text('Statutory Exemption Reason (Directive Art. 2(18))', style: AppTypography.uiLabelBold()),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<UnavailableReceiptReason>(
                    value: _reason,
                    decoration: const InputDecoration(labelText: 'Statutory Reason *', isDense: true),
                    items: UnavailableReceiptReason.values.map((r) {
                      return DropdownMenuItem(value: r, child: Text(r.label, maxLines: 1, overflow: TextOverflow.ellipsis));
                    }).toList(),
                    onChanged: (val) {
                      if (val != null) setState(() => _reason = val);
                    },
                  ),
                  const SizedBox(height: 10),
                  TextFormField(
                    controller: _reasonDescCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Exemption Justification / Context Details',
                      hintText: 'e.g. Procured from smallholder farmer at regional collection center.',
                      isDense: true,
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Line Items
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Purchased Items / Goods', style: AppTypography.uiLabelBold()),
                      TextButton.icon(
                        onPressed: _addLine,
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text('Add Line'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),

                  ...List.generate(_lines.length, (idx) {
                    final item = _lines[idx];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        children: [
                          Expanded(
                            flex: 3,
                            child: TextFormField(
                              initialValue: item.itemDescription,
                              decoration: InputDecoration(labelText: 'Item Description #${idx + 1}', isDense: true),
                              onChanged: (v) {
                                _lines[idx] = PurchaseVoucherLineDto(
                                  itemDescription: v,
                                  quantity: _lines[idx].quantity,
                                  unitOfMeasure: _lines[idx].unitOfMeasure,
                                  unitPrice: _lines[idx].unitPrice,
                                );
                                setState(() {});
                              },
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            flex: 1,
                            child: TextFormField(
                              initialValue: item.quantity.toString(),
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(labelText: 'Qty', isDense: true),
                              onChanged: (v) {
                                final qty = double.tryParse(v) ?? 1.0;
                                _lines[idx] = PurchaseVoucherLineDto(
                                  itemDescription: _lines[idx].itemDescription,
                                  quantity: qty,
                                  unitOfMeasure: _lines[idx].unitOfMeasure,
                                  unitPrice: _lines[idx].unitPrice,
                                );
                                setState(() {});
                              },
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            flex: 1,
                            child: TextFormField(
                              initialValue: item.unitOfMeasure,
                              decoration: const InputDecoration(labelText: 'Unit', isDense: true),
                              onChanged: (v) {
                                _lines[idx] = PurchaseVoucherLineDto(
                                  itemDescription: _lines[idx].itemDescription,
                                  quantity: _lines[idx].quantity,
                                  unitOfMeasure: v,
                                  unitPrice: _lines[idx].unitPrice,
                                );
                              },
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            flex: 2,
                            child: TextFormField(
                              initialValue: item.unitPrice > 0 ? item.unitPrice.toString() : '',
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(labelText: 'Price (ETB)', isDense: true),
                              onChanged: (v) {
                                final price = double.tryParse(v) ?? 0.0;
                                _lines[idx] = PurchaseVoucherLineDto(
                                  itemDescription: _lines[idx].itemDescription,
                                  quantity: _lines[idx].quantity,
                                  unitOfMeasure: _lines[idx].unitOfMeasure,
                                  unitPrice: price,
                                );
                                setState(() {});
                              },
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline, size: 20, color: AppColors.red600),
                            onPressed: _lines.length > 1 ? () => _removeLine(idx) : null,
                          ),
                        ],
                      ),
                    );
                  }),

                  const Divider(height: 24, thickness: 1, color: AppColors.rule),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('TOTAL PURCHASE VOUCHER PAYABLE:', style: AppTypography.uiLabelBold(size: 14)),
                      Text(
                        '${currencyFormat.format(_totalAmount)} ETB',
                        style: AppTypography.h3(color: AppColors.green700),
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
                            : const Text('Issue Purchase Voucher'),
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
