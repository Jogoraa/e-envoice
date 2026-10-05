import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/provider_compliance_service.dart';

class ProviderExitGovernanceScreen extends ConsumerStatefulWidget {
  const ProviderExitGovernanceScreen({super.key});

  @override
  ConsumerState<ProviderExitGovernanceScreen> createState() =>
      _ProviderExitGovernanceScreenState();
}

class _ProviderExitGovernanceScreenState
    extends ConsumerState<ProviderExitGovernanceScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  ProviderExitPlanDto? _exitPlan;
  bool _isFinalizing = false;

  final _dateFormat = DateFormat('dd/MM/yyyy');

  @override
  void initState() {
    super.initState();
    _loadExitPlan();
  }

  Future<void> _loadExitPlan() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(providerComplianceServiceProvider);
      final plan = await service.getExitPlan('current-provider');
      if (mounted) {
        setState(() {
          _exitPlan = plan;
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

  Future<void> _finalizeExit() async {
    if (_exitPlan == null) return;
    if (_exitPlan!.remainingTenants > 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'CANNOT FINALIZE EXIT: ${_exitPlan!.remainingTenants} tenants have not completed migration!',
          ),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() => _isFinalizing = true);

    try {
      final service = ref.read(providerComplianceServiceProvider);
      final finalized = await service.finalizeExit(_exitPlan!.id);
      if (mounted) {
        setState(() {
          _exitPlan = finalized;
          _isFinalizing = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Provider service cessation finalized under Directive Art. 25.'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isFinalizing = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Finalization failed: $e'), backgroundColor: AppColors.error),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final plan = _exitPlan;
    final hasActivePlan = plan != null;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Provider Market Exit Governance (Directive Art. 25)'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadExitPlan,
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
                        onPressed: _loadExitPlan,
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
                      if (hasActivePlan) ...[
                        _buildStatusSummaryCard(plan),
                        const SizedBox(height: 16),
                        _buildMigrationProgressCard(plan),
                        const SizedBox(height: 16),
                        if (plan.blockingTenants.isNotEmpty) ...[
                          _buildBlockingTenantsCard(plan.blockingTenants),
                          const SizedBox(height: 16),
                        ],
                        _buildFinalizeSection(plan),
                      ] else ...[
                        _buildNoActiveExitCard(),
                      ],
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
          const Icon(Icons.warning_amber, color: AppColors.navy700, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 25 — Provider Cessation of Service Protocol',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'A licensed e-invoicing service provider intending to cease operations in Ethiopia must give at least '
                  'six (6) months formal advance notice to the Ministry of Revenues and all active taxpayers. '
                  'The provider must guarantee data migration and cannot terminate platform operations while blocking '
                  'taxpayers remain active without a certified successor.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusSummaryCard(ProviderExitPlanDto plan) {
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
                Text('Active Cessation Plan: ${plan.id}', style: AppTypography.titleMedium),
                Chip(
                  label: Text(plan.status),
                  backgroundColor: plan.status == 'FINALIZED'
                      ? AppColors.success.withValues(alpha: 0.15)
                      : AppColors.warning.withValues(alpha: 0.15),
                  labelStyle: TextStyle(
                    color: plan.status == 'FINALIZED' ? AppColors.success : AppColors.warning,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const Divider(height: 24),
            _infoRow('Required 6-Month Notice Date:', _dateFormat.format(plan.sixMonthsNoticeDate)),
            _infoRow('Target Cessation Date:', _dateFormat.format(plan.plannedCessationDate)),
            _infoRow(
              'Ministry of Revenues Formal Notice:',
              plan.authorityNotified ? 'ACKNOWLEDGED BY MOR' : 'PENDING NOTIFICATION',
              color: plan.authorityNotified ? AppColors.success : AppColors.error,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMigrationProgressCard(ProviderExitPlanDto plan) {
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
                Text('Taxpayer Migration Completion', style: AppTypography.titleMedium),
                Text(
                  '${plan.completionPercentage.toStringAsFixed(1)}%',
                  style: AppTypography.titleLarge.copyWith(color: AppColors.primary, fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 12),
            LinearProgressIndicator(
              value: plan.completionPercentage / 100.0,
              minHeight: 10,
              backgroundColor: AppColors.divider,
              valueColor: const AlwaysStoppedAnimation<Color>(AppColors.primary),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _metric('Total Taxpayers', '${plan.totalTenants}'),
                _metric('Migrated to Successor', '${plan.migratedTenants}', color: AppColors.success),
                _metric('Remaining on Platform', '${plan.remainingTenants}', color: plan.remainingTenants > 0 ? AppColors.error : AppColors.success),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _metric(String label, String value, {Color? color}) {
    return Column(
      children: [
        Text(label, style: AppTypography.caption.copyWith(color: AppColors.textSecondary)),
        const SizedBox(height: 4),
        Text(value, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.bold, color: color)),
      ],
    );
  }

  Widget _buildBlockingTenantsCard(List<String> blocking) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.block, color: AppColors.error),
                const SizedBox(width: 8),
                Text('Blocking Tenants (Unmigrated Taxpayers)', style: AppTypography.titleMedium),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'These taxpayers have not verified an archive checksum or completed migration to an alternate provider. '
              'Cessation finalization is prohibited until all blocking entities are resolved.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
            const Divider(height: 20),
            ...blocking.map(
              (t) => ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.business_center, size: 20, color: AppColors.error),
                title: Text(t, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: const Text('Status: Migration Pending / No Confirmed Successor'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFinalizeSection(ProviderExitPlanDto plan) {
    final canFinalize = plan.remainingTenants == 0 && plan.status != 'FINALIZED';

    return Center(
      child: ElevatedButton.icon(
        onPressed: canFinalize ? _finalizeExit : null,
        icon: _isFinalizing
            ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
            : const Icon(Icons.done_all),
        label: const Text('FINALIZE PROVIDER CESSATION OF SERVICE'),
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.error,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        ),
      ),
    );
  }

  Widget _buildNoActiveExitCard() {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          children: [
            const Icon(Icons.domain_verification, size: 64, color: AppColors.success),
            const SizedBox(height: 16),
            Text('Provider in Full Active Operation', style: AppTypography.titleMedium),
            const SizedBox(height: 8),
            Text(
              'No formal cessation of service or market exit is currently pending. '
              'The platform is operating under standard Directive No. 1142/2026 accreditation.',
              textAlign: TextAlign.center,
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoRow(String label, String value, {Color? color}) {
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
              style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold, color: color),
            ),
          ),
        ],
      ),
    );
  }
}
