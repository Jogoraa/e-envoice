import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/portability_exit_service.dart';

class RetentionScheduleScreen extends ConsumerStatefulWidget {
  const RetentionScheduleScreen({super.key});

  @override
  ConsumerState<RetentionScheduleScreen> createState() => _RetentionScheduleScreenState();
}

class _RetentionScheduleScreenState extends ConsumerState<RetentionScheduleScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = true;
  String? _errorMessage;
  RetentionClassificationSummaryDto? _summary;

  final _dateFormat = DateFormat('dd/MM/yyyy');

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(portabilityExitServiceProvider);
      final summary = await service.getRetentionSummary('current-tenant');
      if (mounted) {
        setState(() {
          _summary = summary;
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
        title: const Text('Statutory Retention & Legal Hold Schedule'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: const [
            Tab(text: 'Retained by Law (10 Years)'),
            Tab(text: 'Active Legal Holds'),
            Tab(text: 'Purge-Eligible (Post-Exit)'),
          ],
        ),
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
              : TabBarView(
                  controller: _tabController,
                  children: [
                    _buildRetainedByLawTab(),
                    _buildLegalHoldTab(),
                    _buildPurgeEligibleTab(),
                  ],
                ),
    );
  }

  Widget _buildRetainedByLawTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildInfoBanner(
            title: 'Directive No. 1142/2026 Art. 24 & Tax Administration Proclamation',
            body: 'All fiscal tax invoices, debit notes, credit notes, receipts, and digital signature records '
                'must remain immutably archived for a mandatory minimum of ten (10) years from the date of issuance.',
            color: AppColors.navy700,
            icon: Icons.gavel,
          ),
          const SizedBox(height: 16),
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Mandatory Retention Summary', style: AppTypography.titleMedium),
                  const Divider(height: 24),
                  _row('Total Protected Records:', '${_summary?.retainByLawCount ?? 0} documents'),
                  _row(
                    'Oldest Fiscal Record:',
                    _summary?.oldestFiscalRecord != null
                        ? _dateFormat.format(_summary!.oldestFiscalRecord!)
                        : 'None on record',
                  ),
                  _row(
                    'Earliest Permissible Expiry:',
                    _summary?.retentionExpiryDate != null
                        ? _dateFormat.format(_summary!.retentionExpiryDate!)
                        : 'N/A (Active 10-Yr Window)',
                  ),
                  _row('Storage Status:', 'Encrypted & Hash-Chained in WORM Storage'),
                  _row('Immutability Guarantee:', 'Enforced via PostgreSQL append-only tables & SHA-256 chain'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLegalHoldTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildInfoBanner(
            title: 'Active Legal & Regulatory Holds',
            body: 'Records placed under legal hold during Ministry of Revenues tax audits, disputes, or formal '
                'investigations cannot be purged regardless of retention expiration.',
            color: AppColors.warning,
            icon: Icons.lock,
          ),
          const SizedBox(height: 16),
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Legal Hold Status', style: AppTypography.titleMedium),
                  const Divider(height: 24),
                  _row('Total Records Under Hold:', '${_summary?.legalHoldCount ?? 0} records'),
                  _row('Authority Agency:', 'Ministry of Revenues (MoR) Audit Directorate'),
                  _row('Automatic Purge Protection:', 'Active (Indefinite until formal release)'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPurgeEligibleTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildInfoBanner(
            title: 'Post-Exit Purge-Eligible Operational Data',
            body: 'Operational session caches, client telemetry logs, and temporary sync buffers that are NOT '
                'part of the statutory fiscal invoice ledger are eligible for deletion after tenant exit confirmation.',
            color: AppColors.success,
            icon: Icons.delete_sweep,
          ),
          const SizedBox(height: 16),
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Purge Classification', style: AppTypography.titleMedium),
                  const Divider(height: 24),
                  _row('Purge-Eligible Records:', '${_summary?.purgeEligibleCount ?? 0} records'),
                  _row('Dual-Authorization Gate:', 'Requires Tenant Admin AND SaaS Platform Admin signatures'),
                  _row('Audit Certificate:', 'Generates SHA-256 Purge Audit Certificate upon completion'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoBanner({
    required String title,
    required String body,
    required Color color,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text(body, style: AppTypography.caption.copyWith(color: AppColors.textSecondary)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _row(String label, String value) {
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
              style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }
}
