import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/networking/gateway_config.dart';

class PublicVerificationScreen extends StatefulWidget {
  final String irn;

  const PublicVerificationScreen({super.key, required this.irn});

  @override
  State<PublicVerificationScreen> createState() => _PublicVerificationScreenState();
}

class _PublicVerificationScreenState extends State<PublicVerificationScreen> {
  late TextEditingController _irnSearchController;
  late String _currentIrn;
  bool _isLoading = false;
  Map<String, dynamic>? _verificationData;
  String? _errorMessage;
  String? _amharicErrorMessage;

  final NumberFormat _currencyFormat = NumberFormat.currency(symbol: 'ETB ', decimalDigits: 2);
  final DateFormat _dateFormat = DateFormat('dd/MM/yyyy HH:mm:ss');

  @override
  void initState() {
    super.initState();
    _currentIrn = widget.irn;
    _irnSearchController = TextEditingController(text: _currentIrn);
    if (_currentIrn.isNotEmpty) {
      _fetchVerification(_currentIrn);
    }
  }

  @override
  void dispose() {
    _irnSearchController.dispose();
    super.dispose();
  }

  Future<void> _fetchVerification(String irn) async {
    if (irn.trim().isEmpty) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _amharicErrorMessage = null;
      _verificationData = null;
    });

    try {
      final dio = Dio(
        BaseOptions(
          baseUrl: GatewayConfig.activeBackendHost,
          connectTimeout: const Duration(seconds: 8),
          receiveTimeout: const Duration(seconds: 8),
        ),
      );

      final response = await dio.get('/api/v1/public/verify/${irn.trim()}');

      if (mounted) {
        setState(() {
          _verificationData = response.data as Map<String, dynamic>;
          _isLoading = false;
        });
      }
    } on DioException catch (e) {
      if (mounted) {
        String msg = 'Failed to verify invoice.';
        String? amharicMsg;
        if (e.response?.data is Map<String, dynamic>) {
          msg = e.response!.data['message']?.toString() ?? msg;
          amharicMsg = e.response!.data['amharicMessage']?.toString();
        } else if (e.response?.statusCode == 404) {
          msg = 'No registered invoice found matching IRN: $irn';
          amharicMsg = 'የተጠቀሰው የደረሰኝ ማመሳከሪያ ቁጥር (IRN) አልተገኘም።';
        }
        setState(() {
          _errorMessage = msg;
          _amharicErrorMessage = amharicMsg;
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF4F6F8),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
          child: Center(
            child: Container(
              constraints: const BoxConstraints(maxWidth: 820),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Government Brand & Verification Header
                  _buildHeader(),

                  const SizedBox(height: 24),

                  // Search Card
                  _buildSearchCard(),

                  const SizedBox(height: 24),

                  // Main Verification Result View
                  if (_isLoading)
                    _buildLoadingState()
                  else if (_errorMessage != null)
                    _buildNotFoundCard()
                  else if (_verificationData != null)
                    _buildVerifiedCard(_verificationData!)
                  else
                    _buildEmptyPrompt(),

                  const SizedBox(height: 32),

                  // Legal Compliance Footer
                  _buildFooterDisclaimer(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.navy900.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(4),
            ),
            child: const Icon(Icons.verified_user, color: AppColors.navy900, size: 36),
          ),
          const SizedBox(width: 20),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'FEDERAL DEMOCRATIC REPUBLIC OF ETHIOPIA',
                  style: AppTypography.uiLabelBold(color: AppColors.inkMuted).copyWith(fontSize: 11),
                ),
                Text(
                  'MINISTRY OF REVENUES (MoR) — E-INVOICE VERIFICATION',
                  style: AppTypography.h2().copyWith(color: AppColors.navy900),
                ),
                const SizedBox(height: 2),
                Text(
                  'የኢትዮጵያ ገቢዎች ሚኒስቴር — የኤሌክትሮኒክ የሽያጭ ደረሰኝ ትክክለኛነት ማረጋገጫ ፖርታል (መመሪያ ቁጥር 1142/2018)',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _irnSearchController,
              decoration: InputDecoration(
                labelText: 'Enter Invoice Reference Number (IRN)',
                hintText: 'e.g. MOR-2026-INV-86906a19-001',
                border: const OutlineInputBorder(),
                prefixIcon: const Icon(Icons.qr_code_scanner, size: 20),
                suffixIcon: _irnSearchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _irnSearchController.clear();
                          setState(() {});
                        },
                      )
                    : null,
              ),
              onSubmitted: (val) {
                _currentIrn = val;
                _fetchVerification(val);
              },
            ),
          ),
          const SizedBox(width: 16),
          ElevatedButton.icon(
            onPressed: () {
              _currentIrn = _irnSearchController.text;
              _fetchVerification(_currentIrn);
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.navy900,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            ),
            icon: const Icon(Icons.search, size: 18),
            label: const Text('Verify Invoice'),
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingState() {
    return Container(
      padding: const EdgeInsets.all(48),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: const Center(
        child: Column(
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Querying central MoR fiscal registry...'),
          ],
        ),
      ),
    );
  }

  Widget _buildNotFoundCard() {
    return Container(
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.red600.withValues(alpha: 0.4)),
      ),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.red600.withValues(alpha: 0.1),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.gpp_bad, color: AppColors.red600, size: 48),
          ),
          const SizedBox(height: 16),
          Text(
            'INVOICE NOT FOUND OR INVALID',
            style: AppTypography.h2().copyWith(color: AppColors.red600),
          ),
          const SizedBox(height: 8),
          Text(
            _errorMessage ?? 'No registered electronic invoice exists with this reference number.',
            textAlign: TextAlign.center,
            style: AppTypography.bodySmall(color: AppColors.ink),
          ),
          if (_amharicErrorMessage != null) ...[
            const SizedBox(height: 4),
            Text(
              _amharicErrorMessage!,
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildVerifiedCard(Map<String, dynamic> data) {
    final status = data['status']?.toString() ?? 'REGISTERED';
    final isRegistered = status == 'REGISTERED';
    final statusColor = isRegistered ? AppColors.green700 : AppColors.amber600;

    DateTime? invoiceDate;
    if (data['invoiceDate'] != null) {
      invoiceDate = DateTime.tryParse(data['invoiceDate'].toString());
    }

    final preTax = (data['preTaxTotal'] as num?)?.toDouble() ?? 0.0;
    final tax = (data['taxTotal'] as num?)?.toDouble() ?? 0.0;
    final excise = (data['exciseTotal'] as num?)?.toDouble() ?? 0.0;
    final grandTotal = (data['grandTotal'] as num?)?.toDouble() ?? 0.0;

    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: statusColor, width: 2),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Authenticity Banner
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
            color: statusColor.withValues(alpha: 0.1),
            child: Row(
              children: [
                Icon(isRegistered ? Icons.check_circle : Icons.warning, color: statusColor, size: 28),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isRegistered ? 'AUTHENTIC FISCAL INVOICE VERIFIED' : 'STATUS: $status',
                        style: AppTypography.h3().copyWith(color: statusColor, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        isRegistered
                            ? 'Official record verified against Ministry of Revenues Electronic Invoice Registry.'
                            : 'This invoice is currently in $status status.',
                        style: AppTypography.bodySmall(color: AppColors.ink),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: statusColor,
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Text(
                    data['verificationStatus']?.toString() ?? status,
                    style: AppTypography.uiLabelBold(color: Colors.white),
                  ),
                ),
              ],
            ),
          ),

          Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Section: Seller Identity
                Text('SELLER TAXPAYER IDENTITY', style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
                const SizedBox(height: 12),
                _buildInfoRow('Seller Legal Name', data['sellerLegalName']?.toString() ?? 'N/A', isBold: true),
                const Divider(height: 16, color: AppColors.rule),
                _buildInfoRow('Seller TIN', data['sellerTin']?.toString() ?? 'N/A', isMono: true),
                const Divider(height: 16, color: AppColors.rule),
                _buildInfoRow('Document / Fiscal Number', data['documentNumber']?.toString() ?? 'N/A', isMono: true),
                const Divider(height: 16, color: AppColors.rule),
                _buildInfoRow('Invoice Reference Number (IRN)', data['irn']?.toString() ?? 'N/A', isMono: true),
                const Divider(height: 16, color: AppColors.rule),
                _buildInfoRow('Invoice Issue Date', invoiceDate != null ? _dateFormat.format(invoiceDate) : 'N/A'),
                if (data['ackDate'] != null) ...[
                  const Divider(height: 16, color: AppColors.rule),
                  _buildInfoRow('MoR Registration Timestamp', data['ackDate'].toString(), isMono: true),
                ],

                const SizedBox(height: 28),

                // Section: Financial Totals
                Text('FINANCIAL & TAX SUMMARY (ETB)', style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.paper,
                    borderRadius: BorderRadius.circular(3),
                    border: Border.all(color: AppColors.rule),
                  ),
                  child: Column(
                    children: [
                      _buildAmountRow('Pre-Tax Subtotal', _currencyFormat.format(preTax)),
                      const SizedBox(height: 8),
                      _buildAmountRow('Value Added Tax (15% VAT)', _currencyFormat.format(tax)),
                      if (excise > 0) ...[
                        const SizedBox(height: 8),
                        _buildAmountRow('Excise Duty', _currencyFormat.format(excise)),
                      ],
                      const Divider(height: 20, color: AppColors.rule),
                      _buildAmountRow('Grand Total', _currencyFormat.format(grandTotal), isGrandTotal: true),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {bool isMono = false, bool isBold = false}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 220,
          child: Text(label, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
        ),
        Expanded(
          child: Text(
            value,
            style: isMono
                ? AppTypography.mono(color: AppColors.navy700, weight: FontWeight.w600)
                : (isBold ? AppTypography.uiLabelBold(color: AppColors.ink) : AppTypography.uiLabel(color: AppColors.ink)),
          ),
        ),
      ],
    );
  }

  Widget _buildAmountRow(String label, String value, {bool isGrandTotal = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: isGrandTotal
              ? AppTypography.h3().copyWith(fontWeight: FontWeight.bold)
              : AppTypography.bodySmall(color: AppColors.inkMuted),
        ),
        Text(
          value,
          style: isGrandTotal
              ? AppTypography.h2().copyWith(color: AppColors.navy900)
              : AppTypography.uiLabelBold(color: AppColors.ink),
        ),
      ],
    );
  }

  Widget _buildEmptyPrompt() {
    return Container(
      padding: const EdgeInsets.all(48),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: const Center(
        child: Column(
          children: [
            Icon(Icons.qr_code_2, size: 48, color: AppColors.inkMuted),
            SizedBox(height: 16),
            Text('Scan QR code or enter IRN above to verify fiscal authenticity.'),
          ],
        ),
      ),
    );
  }

  Widget _buildFooterDisclaimer() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, size: 20, color: AppColors.inkMuted),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Public verification portal conforming to Directive No. 1142/2018 EC (2026 GC) Art. 20(6) & Art. 4(1)(d). '
              'Values displayed represent the authoritative cryptographic state certified by the Ethiopian Ministry of Revenues.',
              style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 11),
            ),
          ),
        ],
      ),
    );
  }
}
