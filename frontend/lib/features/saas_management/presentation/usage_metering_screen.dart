import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class TenantUsageMetric {
  final String tenantName;
  final String tin;
  final String plan;
  final int invoicesUsed;
  final int invoiceQuota;
  final int devicesActive;
  final int deviceQuota;
  final double storageMb;

  const TenantUsageMetric({
    required this.tenantName,
    required this.tin,
    required this.plan,
    required this.invoicesUsed,
    required this.invoiceQuota,
    required this.devicesActive,
    required this.deviceQuota,
    required this.storageMb,
  });

  double get invoicePercent => invoiceQuota > 0 ? (invoicesUsed / invoiceQuota).clamp(0.0, 1.0) : 0.0;
  bool get isNearQuota => invoicePercent >= 0.85;
  bool get isExhausted => invoiceQuota > 0 && invoicesUsed >= invoiceQuota;
}

class UsageMeteringScreen extends ConsumerStatefulWidget {
  const UsageMeteringScreen({super.key});

  @override
  ConsumerState<UsageMeteringScreen> createState() => _UsageMeteringScreenState();
}

class _UsageMeteringScreenState extends ConsumerState<UsageMeteringScreen> {
  final List<TenantUsageMetric> _metrics = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadUsageMetrics();
  }

  Future<void> _loadUsageMetrics() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(saasManagementApiClientProvider);
      final response = await client.get('/api/v1/saas/usage');
      if (response.data is List) {
        final List list = response.data;
        _metrics.clear();
        for (final item in list) {
          _metrics.add(TenantUsageMetric(
            tenantName: item['tenantName']?.toString() ?? 'Taxpayer Entity',
            tin: item['tin']?.toString() ?? '',
            plan: item['plan']?.toString() ?? 'GROWTH',
            invoicesUsed: (item['invoicesUsed'] as num?)?.toInt() ?? 0,
            invoiceQuota: (item['invoiceQuota'] as num?)?.toInt() ?? 5000,
            devicesActive: (item['devicesActive'] as num?)?.toInt() ?? 1,
            deviceQuota: (item['deviceQuota'] as num?)?.toInt() ?? 2,
            storageMb: (item['storageMb'] as num?)?.toDouble() ?? 1.2,
          ));
        }
      }
      setState(() => _isLoading = false);
    } catch (e) {
      setState(() {
        _isLoading = false;
        _error = 'Unable to fetch usage metering records from database.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: AppColors.paper,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    final totalInvoices = _metrics.fold<int>(0, (sum, m) => sum + m.invoicesUsed);
    final totalDevices = _metrics.fold<int>(0, (sum, m) => sum + m.devicesActive);
    final exhaustedCount = _metrics.where((m) => m.isNearQuota || m.isExhausted).length;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadUsageMetrics,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Text('USAGE METERING & QUOTA CONSUMPTION', style: AppTypography.h1()),
              const SizedBox(height: 4),
              Text(
                'Live database-backed invoice consumption, terminal quotas, and storage metering across registered taxpayers',
                style: AppTypography.bodySmall(),
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.amber600.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(3),
                    border: Border.all(color: AppColors.amber600),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.warning_amber, size: 18, color: AppColors.amber600),
                      const SizedBox(width: 8),
                      Expanded(child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.ink))),
                      TextButton(onPressed: _loadUsageMetrics, child: const Text('Retry')),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),

              // Telemetry Cards
              Row(
                children: [
                  Expanded(
                    child: _buildMetricCard('TOTAL INVOICES METERED', '$totalInvoices', 'Current billing cycle', AppColors.navy900),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard('ACTIVE TERMINALS', '$totalDevices', 'POS devices active in DB', AppColors.navy700),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard('QUOTA EXHAUSTION ALERTS', '$exhaustedCount Tenants', '>= 85% quota consumed', exhaustedCount > 0 ? AppColors.amber600 : AppColors.green700),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Metering Table
              Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(20),
                      child: Text('TENANT CONSUMPTION BREAKDOWN', style: AppTypography.h2()),
                    ),
                    const Divider(height: 1, color: AppColors.rule),
                    if (_metrics.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(32),
                        child: Center(
                          child: Text(
                            'No tenant usage records found in database.',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                          ),
                        ),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _metrics.length,
                        separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                        itemBuilder: (context, index) {
                          final m = _metrics[index];
                          return Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                            child: Row(
                              children: [
                                // Tenant Info
                                Expanded(
                                  flex: 4,
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(m.tenantName, style: AppTypography.uiLabelBold()),
                                      const SizedBox(height: 2),
                                      Text('TIN: ${m.tin} | Plan: ${m.plan}', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                    ],
                                  ),
                                ),
                                // Invoice Usage Progress
                                Expanded(
                                  flex: 4,
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                        children: [
                                          Text('Invoice Quota:', style: AppTypography.uiLabel()),
                                          Text(
                                            '${m.invoicesUsed} / ${m.invoiceQuota} (${(m.invoicePercent * 100).toInt()}%)',
                                            style: AppTypography.monoSmall(
                                              color: m.isExhausted ? AppColors.red600 : (m.isNearQuota ? AppColors.amber600 : AppColors.ink),
                                              weight: FontWeight.w600,
                                            ),
                                          ),
                                        ],
                                      ),
                                      const SizedBox(height: 6),
                                      LinearProgressIndicator(
                                        value: m.invoicePercent,
                                        color: m.isExhausted ? AppColors.red600 : (m.isNearQuota ? AppColors.amber600 : AppColors.navy900),
                                        backgroundColor: AppColors.rule,
                                        minHeight: 6,
                                        borderRadius: BorderRadius.circular(3),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 24),
                                // Devices & Storage
                                Expanded(
                                  flex: 3,
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.end,
                                    children: [
                                      Text('Devices: ${m.devicesActive} / ${m.deviceQuota}', style: AppTypography.mono(weight: FontWeight.w600)),
                                      Text('Storage: ${m.storageMb.toStringAsFixed(1)} MB', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMetricCard(String title, String value, String subtitle, Color color) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
          const SizedBox(height: 8),
          Text(value, style: AppTypography.display(color: color).copyWith(fontSize: 28)),
          const SizedBox(height: 4),
          Text(subtitle, style: AppTypography.bodySmall()),
        ],
      ),
    );
  }
}
