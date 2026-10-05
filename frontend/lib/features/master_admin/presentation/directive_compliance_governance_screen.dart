import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class DirectiveComplianceGovernanceScreen extends ConsumerStatefulWidget {
  const DirectiveComplianceGovernanceScreen({super.key});

  @override
  ConsumerState<DirectiveComplianceGovernanceScreen> createState() =>
      _DirectiveComplianceGovernanceScreenState();
}

class _DirectiveComplianceGovernanceScreenState
    extends ConsumerState<DirectiveComplianceGovernanceScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = true;
  // Provider Tier Data (Art. 12 & 13)
  Map<String, dynamic>? _tierStatus;
  List<dynamic> _allTiers = [];

  // Provider Exit Data (Art. 17)
  Map<String, dynamic>? _latestExitPlan;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _loadComplianceData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadComplianceData() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);

      // 1. Fetch Tier & Dashboard Summary
      try {
        final tierRes = await client.get('/api/v1/compliance/tiers/summary');
        if (tierRes.data is Map) {
          _tierStatus = Map<String, dynamic>.from(tierRes.data as Map);
        }
      } catch (_) {}

      try {
        final allTiersRes = await client.get('/api/v1/compliance/tiers');
        if (allTiersRes.data is List) {
          _allTiers = allTiersRes.data as List;
        }
      } catch (_) {}

      // 2. Fetch Latest Exit Plan
      try {
        final exitRes = await client.get(
          '/api/v1/master/provider-exit/plans/latest',
        );
        if (exitRes.data is Map) {
          _latestExitPlan = Map<String, dynamic>.from(exitRes.data as Map);
        }
      } catch (_) {}

      if (mounted) {
        setState(() => _isLoading = false);
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoading = false;
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
          _buildHeader(),
          _buildTabBar(),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : TabBarView(
                    controller: _tabController,
                    children: [
                      _buildTieringTab(),
                      _buildAuthorityExportsTab(),
                      _buildExemptSectorTab(),
                      _buildExitGovernanceTab(),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
      decoration: const BoxDecoration(
        color: AppColors.paperRaised,
        border: Border(bottom: BorderSide(color: AppColors.rule)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.navy900.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: AppColors.navy900.withValues(alpha: 0.2),
              ),
            ),
            child: const Icon(
              Icons.gavel_rounded,
              color: AppColors.navy900,
              size: 28,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      'Statutory Compliance Governance',
                      style: AppTypography.h2(color: AppColors.navy900),
                    ),
                    const SizedBox(width: 12),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.green700.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: AppColors.green700.withValues(alpha: 0.3),
                        ),
                      ),
                      child: Text(
                        'DIRECTIVE NO. 1142/2026 (2018 E.C.)',
                        style: AppTypography.monoSmall(
                          color: AppColors.green700,
                          weight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  'End-to-end statutory governance enforcement: Provider Accreditation, Guarantee Bonds, Authority Audit, and Exit Cessation.',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Refresh Data',
            icon: const Icon(Icons.refresh_rounded, color: AppColors.navy900),
            onPressed: _loadComplianceData,
          ),
        ],
      ),
    );
  }

  Widget _buildTabBar() {
    return Container(
      color: AppColors.paperRaised,
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: TabBar(
        controller: _tabController,
        isScrollable: true,
        labelColor: AppColors.navy900,
        unselectedLabelColor: AppColors.inkMuted,
        indicatorColor: AppColors.navy900,
        indicatorWeight: 3,
        labelStyle: AppTypography.uiLabelBold(),
        unselectedLabelStyle: AppTypography.uiLabel(),
        tabs: const [
          Tab(
            icon: Icon(Icons.military_tech_outlined, size: 20),
            text: 'Provider Tiering (Art. 12-13)',
          ),
          Tab(
            icon: Icon(Icons.search_rounded, size: 20),
            text: 'Authority Exports (Art. 4)',
          ),
          Tab(
            icon: Icon(Icons.receipt_long_outlined, size: 20),
            text: 'Exempt Sectors (Art. 20)',
          ),
          Tab(
            icon: Icon(Icons.exit_to_app_rounded, size: 20),
            text: 'Exit Governance (Art. 17)',
          ),
        ],
      ),
    );
  }

  Widget _buildTieringTab() {
    final tier = _tierStatus?['currentTier'] ?? 1;
    final maxTenants = _tierStatus?['maxActiveTenants'] ?? 50;
    final activeTenants = _tierStatus?['currentActiveTenants'] ?? 5;
    final bondAmount =
        _tierStatus?['currentGuaranteeBondAmount'] ?? '5,000,000.00';
    final isNearCap = _tierStatus?['isNearCapacity'] ?? false;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: _buildMetricCard(
                  title: 'CURRENT COMPLIANCE TIER',
                  value: 'Tier Level $tier',
                  subtitle: 'Directive Art. 12(1) Accreditation',
                  icon: Icons.workspace_premium_rounded,
                  color: AppColors.navy900,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: _buildMetricCard(
                  title: 'MANDATORY GUARANTEE BOND',
                  value: 'ETB $bondAmount',
                  subtitle: 'Deposited & Verified with MoR',
                  icon: Icons.shield_outlined,
                  color: AppColors.green700,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: _buildMetricCard(
                  title: 'ACTIVE TENANTS / CAPACITY',
                  value: '$activeTenants / $maxTenants',
                  subtitle: isNearCap
                      ? 'PROXIMITY WARNING (>80%)'
                      : 'Compliant Capacity Window',
                  icon: Icons.storefront_outlined,
                  color: isNearCap ? Colors.amber[800]! : AppColors.navy900,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildCard(
            title: 'Statutory Tier Scale (Directive No. 1142/2026 Annex 1)',
            subtitle:
                'Formal accreditation levels tied to guarantee bonds and maximum concurrent active user taxpayers.',
            child: _allTiers.isEmpty
                ? const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24),
                      child: Text('No tier scale records loaded.'),
                    ),
                  )
                : ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: _allTiers.length,
                    separatorBuilder: (context, index) =>
                        const Divider(height: 1, color: AppColors.rule),
                    itemBuilder: (context, index) {
                      final item = _allTiers[index];
                      final tNum = item['tierLevel'] ?? (index + 1);
                      final isCurrent = tNum == tier;

                      return Container(
                        color: isCurrent
                            ? AppColors.navy900.withValues(alpha: 0.04)
                            : Colors.transparent,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: isCurrent
                                    ? AppColors.navy900
                                    : AppColors.rule,
                              ),
                              child: Center(
                                child: Text(
                                  '$tNum',
                                  style: AppTypography.monoSmall(
                                    color: isCurrent
                                        ? Colors.white
                                        : AppColors.ink,
                                    weight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    item['tierName'] ?? 'Tier $tNum Provider',
                                    style: AppTypography.uiLabelBold(),
                                  ),
                                  Text(
                                    'Max Taxpayers: ${item['maxActiveTenants']} | Guarantee Bond: ETB ${item['requiredGuaranteeBond']}',
                                    style: AppTypography.monoSmall(
                                      color: AppColors.inkMuted,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            if (isCurrent)
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 10,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: AppColors.navy900,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  'ACTIVE ACCREDITATION',
                                  style: AppTypography.monoSmall(
                                    color: Colors.white,
                                    weight: FontWeight.w700,
                                  ),
                                ),
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

  Widget _buildAuthorityExportsTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Authority Audit & Investigation (Art. 4(2)(c))',
                    style: AppTypography.h3(),
                  ),
                  Text(
                    'Cryptographically signed, AES-256 encrypted batch export for MoR tax inspection officers.',
                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                  ),
                ],
              ),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.navy900,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                ),
                icon: const Icon(Icons.file_download_outlined, size: 18),
                label: const Text('Initiate Inspection Export'),
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Authority inspection export wizard ready'),
                    ),
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildCard(
            title: 'Audit Investigation Protocol Requirements',
            subtitle:
                'Enforced security parameters for inspection exports under Directive Art. 4(2)(c):',
            child: Column(
              children: [
                _buildAuditFeatureTile(
                  '1. Rate Limiting Enforced',
                  'Capped to 10 bulk requests per hour per authority auditor.',
                ),
                _buildAuditFeatureTile(
                  '2. Dual Encryption',
                  'Payload encrypted via AES-256-GCM; key wrapped with Authority X.509 certificate.',
                ),
                _buildAuditFeatureTile(
                  '3. Cryptographic Verification Hash',
                  'Deterministic SHA-256 manifest computed over all export files.',
                ),
                _buildAuditFeatureTile(
                  '4. Access Logging & Immutable Audit',
                  'Every parameter query logged to tamper-evident audit trail.',
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildExemptSectorTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'High-Volume Exempt-Sector Summary Reporting (Art. 20)',
            style: AppTypography.h3(),
          ),
          Text(
            'Authorized high-volume consumer billing entities (Telecommunications, Banking, Electric Power, Water Utility).',
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
          const SizedBox(height: 24),
          _buildCard(
            title: 'Statutory Exemptions Lifecycle',
            subtitle: 'Directives and reporting cycles:',
            child: Column(
              children: [
                _buildAuditFeatureTile(
                  'Article 20(1) Approval',
                  'Special authorization granted by Authority upon formal review.',
                ),
                _buildAuditFeatureTile(
                  'Article 20(3) Reporting',
                  'Monthly or daily consolidated aggregate fiscal reporting.',
                ),
                _buildAuditFeatureTile(
                  'Article 20(5) B2B Exclusion',
                  'B2B commercial transactions strictly excluded from aggregate summaries and must be invoiced individually.',
                ),
                _buildAuditFeatureTile(
                  'Article 20(4) Cryptographic Freeze',
                  'Reports are SHA-256 hashed and frozen upon submission.',
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildExitGovernanceTab() {
    final plan = _latestExitPlan;
    final status = plan?['status'] ?? 'NONE';
    final progress =
        (plan?['migrationProgressPercent'] as num?)?.toDouble() ?? 0.0;
    final activeCount = plan?['totalActiveTenants'] ?? 0;
    final migratedCount = plan?['migratedTenantsCount'] ?? 0;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Provider Exit Strategy & Cessation Governance (Art. 17)',
                    style: AppTypography.h3(),
                  ),
                  Text(
                    'Statutory 6-month voluntary exit lifecycle, taxpayer data retrieval, and 100% migration verification.',
                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: AppColors.navy900.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppColors.navy900),
                ),
                child: Text(
                  'PLAN STATUS: $status',
                  style: AppTypography.monoSmall(
                    color: AppColors.navy900,
                    weight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildCard(
            title: 'Tenant Migration Progress (Art. 17(5))',
            subtitle:
                '100% of user taxpayers must be migrated before cessation of service can be legally confirmed by the Authority.',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Migration Completion: ${progress.toStringAsFixed(1)}%',
                      style: AppTypography.uiLabelBold(),
                    ),
                    Text(
                      '$migratedCount of $activeCount Taxpayers Migrated',
                      style: AppTypography.monoSmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: activeCount > 0
                        ? (migratedCount / activeCount)
                        : 0.0,
                    minHeight: 12,
                    backgroundColor: AppColors.rule,
                    valueColor: AlwaysStoppedAnimation<Color>(
                      progress >= 100.0
                          ? AppColors.green700
                          : AppColors.navy900,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Statutory Rule: Cessation confirmation reference and certificate surrender cannot be registered until progress reaches 100%.',
                  style: AppTypography.caption(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetricCard({
    required String title,
    required String value,
    required String subtitle,
    required IconData icon,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 20, color: color),
              const SizedBox(width: 8),
              Text(
                title,
                style: AppTypography.monoSmall(
                  color: AppColors.inkMuted,
                  weight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(value, style: AppTypography.h2(color: color)),
          const SizedBox(height: 4),
          Text(
            subtitle,
            style: AppTypography.caption(color: AppColors.inkMuted),
          ),
        ],
      ),
    );
  }

  Widget _buildCard({
    required String title,
    required String subtitle,
    required Widget child,
  }) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.uiLabelBold(color: AppColors.navy900),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  style: AppTypography.caption(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.rule),
          Padding(padding: const EdgeInsets.all(16), child: child),
        ],
      ),
    );
  }

  Widget _buildAuditFeatureTile(String title, String desc) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.check_circle_outline_rounded,
            color: AppColors.green700,
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppTypography.uiLabelBold()),
                Text(
                  desc,
                  style: AppTypography.caption(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
