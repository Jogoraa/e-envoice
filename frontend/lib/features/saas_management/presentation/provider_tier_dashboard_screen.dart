import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/provider_compliance_service.dart';

class ProviderTierDashboardScreen extends ConsumerStatefulWidget {
  const ProviderTierDashboardScreen({super.key});

  @override
  ConsumerState<ProviderTierDashboardScreen> createState() =>
      _ProviderTierDashboardScreenState();
}

class _ProviderTierDashboardScreenState
    extends ConsumerState<ProviderTierDashboardScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  ProviderDashboardSummaryDto? _summary;
  List<ProviderComplianceTierDto> _tiers = [];

  final _currencyFormatUsd = NumberFormat.currency(symbol: 'USD ');
  final _currencyFormatEtb = NumberFormat.currency(symbol: 'ETB ');

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
      final service = ref.read(providerComplianceServiceProvider);
      final summary = await service.getDashboardSummary();
      final tiers = await service.listTiers();

      if (mounted) {
        setState(() {
          _summary = summary;
          _tiers = tiers;
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
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Provider Tiering & Guarantee Governance (Annex 1)'),
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
                      _buildDirectiveNoticeCard(),
                      const SizedBox(height: 16),
                      if (_summary?.thresholdAlert != null) ...[
                        _buildThresholdAlertBanner(_summary!.thresholdAlert!),
                        const SizedBox(height: 16),
                      ],
                      _buildSummaryMetricsGrid(),
                      const SizedBox(height: 16),
                      _buildTiersTable(),
                    ],
                  ),
                ),
    );
  }

  Widget _buildDirectiveNoticeCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.navy700.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.navy700.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.account_balance, color: AppColors.navy700, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Annex 1 — 10-Tier Provider Qualification Scale',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Service providers are tiered according to active taxpayer count and annual sales volume. '
                  'Each tier mandates a verified bank guarantee bond deposited with the National Bank of Ethiopia '
                  'and a minimum staff of dedicated certified software engineers.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildThresholdAlertBanner(String alert) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.warning.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.warning),
      ),
      child: Row(
        children: [
          const Icon(Icons.warning_amber, color: AppColors.warning, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Tier Threshold Warning', style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(height: 2),
                Text(alert, style: AppTypography.caption),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryMetricsGrid() {
    final s = _summary;

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
                    const Icon(Icons.speed, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Text(
                      'Current Provider Level: Tier ${s?.currentTierLevel ?? 1} (${s?.currentTierName ?? 'Baseline'})',
                      style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
                Chip(
                  label: Text('BOND: ${s?.currentBondStatus ?? 'VERIFIED'}'),
                  backgroundColor: AppColors.success.withValues(alpha: 0.15),
                  labelStyle: const TextStyle(color: AppColors.success, fontSize: 11, fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const Divider(height: 24),
            Row(
              children: [
                Expanded(
                  child: _metricBox(
                    'Active Taxpayers',
                    '${s?.activeTaxpayers ?? 0}',
                    Icons.groups,
                    AppColors.primary,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _metricBox(
                    'Annual Invoicing Volume',
                    _currencyFormatEtb.format(s?.annualSalesVolume ?? 0),
                    Icons.trending_up,
                    AppColors.navy700,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _metricBox(
                    'Bank Guarantee Bond',
                    _currencyFormatUsd.format(s?.requiredGuaranteeBondUsd ?? 50000),
                    Icons.lock,
                    AppColors.success,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _metricBox(
                    'Software Engineers',
                    '${s?.currentEngineers ?? 6} / ${s?.requiredEngineers ?? 6} Req',
                    Icons.engineering,
                    AppColors.secondary,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _metricBox(String label, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 16, color: color),
              const SizedBox(width: 4),
              Flexible(child: Text(label, style: AppTypography.caption, overflow: TextOverflow.ellipsis)),
            ],
          ),
          const SizedBox(height: 6),
          Text(value, style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }

  Widget _buildTiersTable() {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Official Annex 1 Tier Progression Scale', style: AppTypography.titleMedium),
            const Divider(height: 24),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: DataTable(
                columns: const [
                  DataColumn(label: Text('Tier')),
                  DataColumn(label: Text('Name')),
                  DataColumn(label: Text('Taxpayer Capacity')),
                  DataColumn(label: Text('Annual Volume Limit')),
                  DataColumn(label: Text('Required Bond (USD)')),
                  DataColumn(label: Text('Min. Engineers')),
                ],
                rows: _tiers.map((t) {
                  final isCurrent = _summary?.currentTierLevel == t.tierLevel;

                  return DataRow(
                    color: isCurrent
                        ? WidgetStateProperty.all(AppColors.primary.withValues(alpha: 0.08))
                        : null,
                    cells: [
                      DataCell(
                        Row(
                          children: [
                            if (isCurrent)
                              const Icon(Icons.arrow_right, color: AppColors.primary, size: 20),
                            Text('Tier ${t.tierLevel}', style: TextStyle(fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal)),
                          ],
                        ),
                      ),
                      DataCell(Text(t.name)),
                      DataCell(Text('${t.minTaxpayers} – ${t.maxTaxpayers}')),
                      DataCell(Text(_currencyFormatEtb.format(t.maxAnnualVolume))),
                      DataCell(Text(_currencyFormatUsd.format(t.requiredGuaranteeUsd))),
                      DataCell(Text('${t.requiredEngineers} Engineers')),
                    ],
                  );
                }).toList(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
