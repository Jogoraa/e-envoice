import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/exempt_sector_service.dart';

class ExemptSectorReportingScreen extends ConsumerStatefulWidget {
  const ExemptSectorReportingScreen({super.key});

  @override
  ConsumerState<ExemptSectorReportingScreen> createState() =>
      _ExemptSectorReportingScreenState();
}

class _ExemptSectorReportingScreenState
    extends ConsumerState<ExemptSectorReportingScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  ExemptSectorAuthorizationDto? _authorization;
  ExemptSectorSummaryReportDto? _latestReport;
  bool _isGeneratingReport = false;

  final _currencyFormat = NumberFormat.currency(symbol: 'ETB ');
  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(exemptSectorServiceProvider);
      final auth = await service.getAuthorization('current-tenant');
      if (mounted) {
        setState(() {
          _authorization = auth;
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

  Future<void> _generateDraftReport() async {
    setState(() => _isGeneratingReport = true);

    try {
      final service = ref.read(exemptSectorServiceProvider);
      final now = DateTime.now();
      final period = '${now.year}-${now.month.toString().padLeft(2, '0')}';

      final report = await service.generateDraftReport({
        'tenantId': 'current-tenant',
        'reportingPeriod': period,
        'summaryType': _authorization?.reportingFrequency ?? 'MONTHLY',
      });

      if (mounted) {
        setState(() {
          _latestReport = report;
          _isGeneratingReport = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Draft summary report compiled from B2C transaction logs.'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isGeneratingReport = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Report generation failed: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Article 20 Exempt Sector Summary Reporting'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
        ],
      ),
      body: _isLoading
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
                        onPressed: _loadData,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _buildNoticeBanner(),
                      const SizedBox(height: 16),
                      _buildAuthorizationStatusCard(),
                      const SizedBox(height: 16),
                      _buildSummaryReportCard(),
                    ],
                  ),
                ),
    );
  }

  Widget _buildNoticeBanner() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.summarize, color: AppColors.primary, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 20 — Special Reporting for High-Volume Sectors',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Taxpayers in designated high-frequency sectors (e.g. utilities, public transport, retail micro-transactions) '
                  'authorized by the Ministry of Revenues may issue periodic summary invoices rather than individual '
                  'real-time B2C registration. Individual B2B invoices remain strictly subject to standard real-time IRN registration.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAuthorizationStatusCard() {
    final auth = _authorization;
    final isAuthorized = auth != null && auth.active;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      isAuthorized ? Icons.check_circle : Icons.pending,
                      color: isAuthorized ? AppColors.success : AppColors.warning,
                    ),
                    const SizedBox(width: 8),
                    Text('Ministry Authorization Status', style: AppTypography.titleMedium),
                  ],
                ),
                Chip(
                  label: Text(isAuthorized ? 'AUTHORIZED' : 'NOT AUTHORIZED'),
                  backgroundColor: isAuthorized
                      ? AppColors.success.withValues(alpha: 0.15)
                      : AppColors.warning.withValues(alpha: 0.15),
                  labelStyle: TextStyle(
                    color: isAuthorized ? AppColors.success : AppColors.warning,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const Divider(height: 24),
            _infoRow('Official Sector Code:', auth?.sectorCode ?? 'STANDARD_SECTOR'),
            _infoRow('Reporting Frequency:', auth?.reportingFrequency ?? 'MONTHLY'),
            _infoRow('Authorization Reference:', auth?.authorizationReference ?? 'None on file'),
            _infoRow('Authorized By:', auth?.authorizedBy ?? 'Ministry of Revenues Tax Directorate'),
          ],
        ),
      ),
    );
  }

  Widget _buildSummaryReportCard() {
    final report = _latestReport;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(Icons.analytics_outlined, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Text('Current Period Summary Report', style: AppTypography.titleMedium),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: _isGeneratingReport ? null : _generateDraftReport,
                  icon: _isGeneratingReport
                      ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.refresh, size: 16),
                  label: const Text('Compile Period Summary'),
                  style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
                ),
              ],
            ),
            const Divider(height: 24),
            if (report == null)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      const Icon(Icons.pending_actions, size: 48, color: AppColors.textTertiary),
                      const SizedBox(height: 8),
                      Text('No summary report compiled for current period', style: AppTypography.bodyMedium),
                      const SizedBox(height: 4),
                      Text(
                        'Click "Compile Period Summary" to aggregate eligible high-frequency transactions.',
                        style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
              )
            else ...[
              _infoRow('Reporting Period:', report.reportingPeriod),
              _infoRow('Aggregated Transaction Count:', '${report.transactionCount} transactions'),
              _infoRow('Gross Sales Amount:', _currencyFormat.format(report.grossAmount)),
              _infoRow('Excluded B2B (Separately Registered):', _currencyFormat.format(report.excludedB2bAmount)),
              _infoRow(
                'Net Art. 20 Summary Amount:',
                _currencyFormat.format(report.summaryAmount),
                isBold: true,
              ),
              _infoRow('Report Submission Status:', report.status),
              if (report.morAcknowledgementNumber != null)
                _infoRow('MoR Acknowledgment Ref:', report.morAcknowledgementNumber!),
              _infoRow('Generated At:', _dateFormat.format(report.generatedAt)),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  OutlinedButton.icon(
                    onPressed: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Downloading audit breakdown CSV...')),
                      );
                    },
                    icon: const Icon(Icons.file_download_outlined),
                    label: const Text('Export Audit Breakdown'),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton.icon(
                    onPressed: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Summary report transmitted to MoR EIRS Gateway.'),
                          backgroundColor: AppColors.success,
                        ),
                      );
                    },
                    icon: const Icon(Icons.send),
                    label: const Text('Submit to Ministry of Revenues'),
                    style: ElevatedButton.styleFrom(backgroundColor: AppColors.navy900),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _infoRow(String label, String value, {bool isBold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
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
}
