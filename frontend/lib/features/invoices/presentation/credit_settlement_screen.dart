import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/statutory_documents_service.dart';

class CreditSettlementScreen extends ConsumerStatefulWidget {
  final String? initialInvoiceId;

  const CreditSettlementScreen({super.key, this.initialInvoiceId});

  @override
  ConsumerState<CreditSettlementScreen> createState() => _CreditSettlementScreenState();
}

class _CreditSettlementScreenState extends ConsumerState<CreditSettlementScreen> {
  bool _isLoading = false;
  String? _errorMessage;
  CreditAccountSummaryDto? _summary;
  final _invoiceSearchCtrl = TextEditingController();
  final _settlementAmountCtrl = TextEditingController();
  final _paymentRefCtrl = TextEditingController();

  String _paymentMethod = 'BANK_TRANSFER';
  bool _isSettling = false;
  String? _settlementError;

  final _currencyFormat = NumberFormat('#,##0.00', 'en_US');
  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    if (widget.initialInvoiceId != null && widget.initialInvoiceId!.isNotEmpty) {
      _invoiceSearchCtrl.text = widget.initialInvoiceId!;
      _fetchSummary(widget.initialInvoiceId!);
    }
  }

  @override
  void dispose() {
    _invoiceSearchCtrl.dispose();
    _settlementAmountCtrl.dispose();
    _paymentRefCtrl.dispose();
    super.dispose();
  }

  Future<void> _fetchSummary(String invoiceId) async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(statutoryDocumentsServiceProvider);
      final data = await service.getCreditAccountSummary(invoiceId);
      if (mounted) {
        setState(() {
          _summary = data;
          _isLoading = false;
          _settlementAmountCtrl.text = data.remainingBalance > 0
              ? data.remainingBalance.toStringAsFixed(2)
              : '';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = 'Failed to load credit summary: $e';
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _submitSettlement() async {
    final summary = _summary;
    if (summary == null) return;

    final amount = double.tryParse(_settlementAmountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => _settlementError = 'Please enter a valid positive settlement amount.');
      return;
    }

    if (amount > summary.remainingBalance) {
      setState(() => _settlementError =
          'Overpayment blocked! Amount (${_currencyFormat.format(amount)} ETB) exceeds outstanding balance (${_currencyFormat.format(summary.remainingBalance)} ETB).');
      return;
    }

    setState(() {
      _isSettling = true;
      _settlementError = null;
    });

    try {
      final service = ref.read(statutoryDocumentsServiceProvider);
      final payload = {
        'invoiceId': summary.invoiceId,
        'amount': amount,
        'paymentMethod': _paymentMethod,
        if (_paymentRefCtrl.text.trim().isNotEmpty) 'paymentReference': _paymentRefCtrl.text.trim(),
        'paymentDate': DateTime.now().toIso8601String(),
      };

      await service.recordCreditSettlement(payload);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Settlement of ${_currencyFormat.format(amount)} ETB recorded. Cash Receipt generated.'),
            backgroundColor: AppColors.green700,
          ),
        );
        _fetchSummary(summary.invoiceId);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _settlementError = e.toString();
          _isSettling = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
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
              children: [
                const Icon(Icons.account_balance_outlined, color: AppColors.navy900, size: 28),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Credit Sales & Settlement / የብድር ሽያጭ አከፋፈል',
                        style: AppTypography.h2(),
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Directive No. 1142/2026 Art. 2(14) & Art. 24 — Receivables ledger, partial settlements, and cash receipt issuance.',
                        style: AppTypography.bodySmall(color: AppColors.inkMuted),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Search Box
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(bottom: BorderSide(color: AppColors.rule)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _invoiceSearchCtrl,
                    decoration: InputDecoration(
                      labelText: 'Search Invoice by ID or IRN for Credit Settlement',
                      hintText: 'e.g. 00000000-0000-0000-0000-000000000001 or IRN-...',
                      prefixIcon: const Icon(Icons.search, size: 20),
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(4)),
                    ),
                    onSubmitted: (v) {
                      if (v.trim().isNotEmpty) _fetchSummary(v.trim());
                    },
                  ),
                ),
                const SizedBox(width: 12),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy900,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                  ),
                  onPressed: () {
                    final v = _invoiceSearchCtrl.text.trim();
                    if (v.isNotEmpty) _fetchSummary(v);
                  },
                  child: const Text('Lookup Credit Sale'),
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
                            Text(_errorMessage!, style: AppTypography.body(color: AppColors.red600)),
                            const SizedBox(height: 16),
                            ElevatedButton(
                              onPressed: () {
                                if (_invoiceSearchCtrl.text.trim().isNotEmpty) {
                                  _fetchSummary(_invoiceSearchCtrl.text.trim());
                                }
                              },
                              child: const Text('Retry'),
                            ),
                          ],
                        ),
                      )
                    : _summary == null
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.credit_score_outlined, size: 64, color: AppColors.inkMuted),
                                const SizedBox(height: 16),
                                Text('Enter a Registered Credit Invoice ID to Settle', style: AppTypography.h3()),
                                const SizedBox(height: 8),
                                Text(
                                  'Lookup credit invoices to record partial payments or full settlements.',
                                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                ),
                              ],
                            ),
                          )
                        : SingleChildScrollView(
                            padding: const EdgeInsets.all(24),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                // Left: Ledger & Summary
                                Expanded(
                                  flex: 3,
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      _buildSummaryCard(_summary!),
                                      const SizedBox(height: 24),
                                      _buildSettlementsTimeline(_summary!),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 24),

                                // Right: Settlement Form
                                Expanded(
                                  flex: 2,
                                  child: _buildSettlementForm(_summary!),
                                ),
                              ],
                            ),
                          ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryCard(CreditAccountSummaryDto s) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Invoice: ${s.invoiceNumber}', style: AppTypography.h3()),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: s.isFullySettled
                      ? AppColors.green700.withValues(alpha: 0.1)
                      : AppColors.amber600.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  s.isFullySettled ? 'FULLY SETTLED' : 'OUTSTANDING BALANCE',
                  style: AppTypography.uiLabelBold(
                    color: s.isFullySettled ? AppColors.green700 : AppColors.amber600,
                  ),
                ),
              ),
            ],
          ),
          if (s.irn != null) ...[
            const SizedBox(height: 4),
            Text('IRN: ${s.irn}', style: AppTypography.monospace(size: 12)),
          ],
          const Divider(height: 24, thickness: 1, color: AppColors.rule),
          Row(
            children: [
              _buildStatTile('Original Total', '${_currencyFormat.format(s.originalTotal)} ETB', AppColors.ink),
              _buildStatTile('Total Settled', '${_currencyFormat.format(s.settledAmount)} ETB', AppColors.navy700),
              _buildStatTile(
                'Remaining Balance',
                '${_currencyFormat.format(s.remainingBalance)} ETB',
                s.remainingBalance > 0 ? AppColors.red600 : AppColors.green700,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStatTile(String label, String value, Color color) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppTypography.caption(color: AppColors.inkMuted)),
          const SizedBox(height: 4),
          Text(value, style: AppTypography.h3(color: color)),
        ],
      ),
    );
  }

  Widget _buildSettlementsTimeline(CreditAccountSummaryDto s) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Settlement History (${s.settlements.length})', style: AppTypography.uiLabelBold()),
          const SizedBox(height: 12),
          if (s.settlements.isEmpty)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Center(
                child: Text('No settlements recorded yet for this invoice.', style: AppTypography.bodySmall(color: AppColors.inkMuted)),
              ),
            )
          else
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: s.settlements.length,
              separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
              itemBuilder: (context, index) {
                final item = s.settlements[index];
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('${_currencyFormat.format(item.amount)} ETB', style: AppTypography.uiLabelBold()),
                          Text(
                            'Method: ${item.paymentMethod ?? "CASH"} | Date: ${_dateFormat.format(item.paymentDate)}',
                            style: AppTypography.caption(color: AppColors.inkMuted),
                          ),
                        ],
                      ),
                      if (item.cashReceiptNumber != null)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppColors.paperRaised,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text('Receipt #${item.cashReceiptNumber}', style: AppTypography.caption(color: AppColors.navy900)),
                        ),
                    ],
                  ),
                );
              },
            ),
        ],
      ),
    );
  }

  Widget _buildSettlementForm(CreditAccountSummaryDto s) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Record Credit Settlement', style: AppTypography.uiLabelBold(size: 16)),
          const SizedBox(height: 4),
          Text(
            'Record payment against outstanding balance. System automatically generates a statutory Cash Receipt.',
            style: AppTypography.caption(color: AppColors.inkMuted),
          ),
          const Divider(height: 24, thickness: 1, color: AppColors.rule),

          if (_settlementError != null) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.red600.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: AppColors.red600),
              ),
              child: Text(_settlementError!, style: AppTypography.bodySmall(color: AppColors.red600)),
            ),
            const SizedBox(height: 16),
          ],

          if (s.isFullySettled)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.green700.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Row(
                children: [
                  const Icon(Icons.check_circle, color: AppColors.green700),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'This invoice is fully settled. No further payments can be accepted.',
                      style: AppTypography.bodySmall(color: AppColors.green700),
                    ),
                  ),
                ],
              ),
            )
          else ...[
            TextFormField(
              controller: _settlementAmountCtrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: 'Settlement Amount (Max: ${_currencyFormat.format(s.remainingBalance)} ETB) *',
                isDense: true,
              ),
            ),
            const SizedBox(height: 14),

            DropdownButtonFormField<String>(
              value: _paymentMethod,
              decoration: const InputDecoration(labelText: 'Payment Method *', isDense: true),
              items: const [
                DropdownMenuItem(value: 'CASH', child: Text('Cash / ጥሬ ገንዘብ')),
                DropdownMenuItem(value: 'BANK_TRANSFER', child: Text('Bank Transfer / የባንክ ማስተላለፍ')),
                DropdownMenuItem(value: 'CHECK', child: Text('Check / ቼክ')),
                DropdownMenuItem(value: 'DIGITAL_WALLET', child: Text('Telebirr / Digital Wallet')),
              ],
              onChanged: (val) {
                if (val != null) setState(() => _paymentMethod = val);
              },
            ),
            const SizedBox(height: 14),

            TextFormField(
              controller: _paymentRefCtrl,
              decoration: const InputDecoration(
                labelText: 'Payment Reference / Bank Slip No.',
                hintText: 'e.g. CBE-TX-984210',
                isDense: true,
              ),
            ),
            const SizedBox(height: 24),

            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.navy900,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                onPressed: _isSettling ? null : _submitSettlement,
                icon: _isSettling
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : const Icon(Icons.check, size: 18),
                label: Text(_isSettling ? 'Recording Settlement...' : 'Confirm Settlement & Issue Receipt'),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
