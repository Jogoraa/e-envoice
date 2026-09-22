import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class SaasDashboardScreen extends ConsumerStatefulWidget {
  const SaasDashboardScreen({super.key});

  @override
  ConsumerState<SaasDashboardScreen> createState() => _SaasDashboardScreenState();
}

class _SaasDashboardScreenState extends ConsumerState<SaasDashboardScreen> {
  bool _isLoading = true;
  String? _error;

  int _totalTenants = 0;
  int _activeTenants = 0;
  int _provisioningTenants = 0;
  int _suspendedTenants = 0;
  int _totalBranches = 0;
  int _totalInvoices = 0;
  double _totalGrossInvoiced = 0.0;
  String _gatewayStatus = 'ONLINE';
  String _gatewayHealth = '99.98%';
  final List<Map<String, dynamic>> _recentTenants = [];

  @override
  void initState() {
    super.initState();
    _loadTelemetry();
  }

  Future<void> _loadTelemetry() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(saasManagementApiClientProvider);
      final response = await client.get('/api/v1/saas/telemetry');
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        setState(() {
          _totalTenants = (data['totalTenants'] as num?)?.toInt() ?? 0;
          _activeTenants = (data['activeTenants'] as num?)?.toInt() ?? 0;
          _provisioningTenants = (data['provisioningTenants'] as num?)?.toInt() ?? 0;
          _suspendedTenants = (data['suspendedTenants'] as num?)?.toInt() ?? 0;
          _totalBranches = (data['totalBranches'] as num?)?.toInt() ?? 0;
          _totalInvoices = (data['totalInvoices'] as num?)?.toInt() ?? 0;
          _totalGrossInvoiced = (data['totalGrossInvoiced'] as num?)?.toDouble() ?? 0.0;
          _gatewayStatus = data['gatewayStatus']?.toString() ?? 'ONLINE';
          _gatewayHealth = data['gatewayHealth']?.toString() ?? '99.98%';

          _recentTenants.clear();
          if (data['recentTenants'] is List) {
            for (final item in data['recentTenants']) {
              _recentTenants.add(Map<String, dynamic>.from(item));
            }
          }
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _error = 'Unable to load real-time telemetry: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: AppColors.paper,
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    final activePercent = _totalTenants > 0 ? (_activeTenants / _totalTenants).clamp(0.0, 1.0) : 1.0;
    final provPercent = _totalTenants > 0 ? (_provisioningTenants / _totalTenants).clamp(0.0, 1.0) : 0.0;
    final suspPercent = _totalTenants > 0 ? (_suspendedTenants / _totalTenants).clamp(0.0, 1.0) : 0.0;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadTelemetry,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('COMMERCIAL SaaS OPERATIONS DASHBOARD', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Live database telemetry: tenant growth, subscriptions, and quota utilization across Ethiopian enterprise taxpayers',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  ElevatedButton.icon(
                    onPressed: () => context.go('/saas/onboarding'),
                    icon: const Icon(Icons.add_business, size: 16),
                    label: const Text('Onboard New Tenant'),
                  ),
                ],
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
                      TextButton(onPressed: _loadTelemetry, child: const Text('Retry')),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),

              // Top Telemetry Metrics
              Row(
                children: [
                  Expanded(
                    child: _buildMetricCard(
                      title: 'REGISTERED TENANTS',
                      value: '$_totalTenants',
                      subtitle: '$_activeTenants active in database',
                      icon: Icons.domain,
                      color: AppColors.navy900,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'ACTIVE POS BRANCHES',
                      value: '$_totalBranches',
                      subtitle: 'Database-backed branches',
                      icon: Icons.storefront,
                      color: AppColors.navy700,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'REGISTERED INVOICES',
                      value: '$_totalInvoices',
                      subtitle: 'ETB ${_totalGrossInvoiced.toStringAsFixed(2)} Invoiced',
                      icon: Icons.receipt_long,
                      color: AppColors.green700,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'GATEWAY HEALTH',
                      value: _gatewayHealth,
                      subtitle: 'Status: $_gatewayStatus',
                      icon: Icons.speed,
                      color: AppColors.green700,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Middle Section: Lifecycle Pipeline & Quick Actions
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Lifecycle Breakdown
                  Expanded(
                    flex: 3,
                    child: Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('TENANT LIFECYCLE DISTRIBUTION', style: AppTypography.h2()),
                          const SizedBox(height: 16),
                          _buildLifecycleRow('Active Operational', '$_activeTenants', activePercent, AppColors.green700),
                          const SizedBox(height: 12),
                          _buildLifecycleRow('Provisioning Pipeline', '$_provisioningTenants', provPercent, AppColors.navy700),
                          const SizedBox(height: 12),
                          _buildLifecycleRow('Suspended / Delinquent', '$_suspendedTenants', suspPercent, AppColors.red600),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 20),

                  // Quick Operational Tasks
                  Expanded(
                    flex: 2,
                    child: Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('OPERATIONAL ACTIONS', style: AppTypography.h2()),
                          const SizedBox(height: 16),
                          _buildActionButton(
                            icon: Icons.person_add_alt_1,
                            label: 'Launch Tenant Onboarding Wizard',
                            onTap: () => context.go('/saas/onboarding'),
                          ),
                          const SizedBox(height: 10),
                          _buildActionButton(
                            icon: Icons.domain,
                            label: 'Inspect Tenant Lifecycle States',
                            onTap: () => context.go('/saas/tenants'),
                          ),
                          const SizedBox(height: 10),
                          _buildActionButton(
                            icon: Icons.card_membership,
                            label: 'Manage Plans & Entitlements',
                            onTap: () => context.go('/saas/subscriptions'),
                          ),
                          const SizedBox(height: 10),
                          _buildActionButton(
                            icon: Icons.headset_mic,
                            label: 'View Support & Diagnostics',
                            onTap: () => context.go('/saas/support'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Recent Onboarded Tenants Table
              Material(
                color: AppColors.paperRaised,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(3),
                  side: const BorderSide(color: AppColors.rule),
                ),
                clipBehavior: Clip.antiAlias,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('RECENTLY ONBOARDED TAXPAYERS', style: AppTypography.h2()),
                          TextButton(
                            onPressed: () => context.go('/saas/tenants'),
                            child: const Text('View All Tenants ->'),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: AppColors.rule),
                    if (_recentTenants.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(32),
                        child: Center(
                          child: Text(
                            'No registered tenants in database yet. Click "Onboard New Tenant" to create one.',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                          ),
                        ),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _recentTenants.length,
                        separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                        itemBuilder: (context, index) {
                          final item = _recentTenants[index];
                          final legalName = item['legalName']?.toString() ?? 'Taxpayer Entity';
                          final tin = item['tin']?.toString() ?? '';
                          final plan = item['plan']?.toString() ?? 'GROWTH';
                          final status = item['status']?.toString() ?? 'ACTIVE';

                          return Material(
                            type: MaterialType.transparency,
                            child: ListTile(
                              contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                              leading: CircleAvatar(
                                backgroundColor: AppColors.navy700.withValues(alpha: 0.1),
                                child: Text(
                                  legalName.isNotEmpty ? legalName[0].toUpperCase() : 'T',
                                  style: AppTypography.mono(weight: FontWeight.w700, color: AppColors.navy700),
                                ),
                              ),
                              title: Row(
                                children: [
                                  Text(legalName, style: AppTypography.uiLabelBold()),
                                  const SizedBox(width: 8),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppColors.navy700.withValues(alpha: 0.1),
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                    child: Text(plan, style: AppTypography.monoSmall(color: AppColors.navy700)),
                                  ),
                                ],
                              ),
                              subtitle: Text(
                                'TIN: $tin | Status: $status',
                                style: AppTypography.monoSmall(color: AppColors.inkMuted),
                              ),
                              trailing: OutlinedButton(
                                onPressed: () => context.go('/saas/tenants'),
                                child: const Text('Manage'),
                              ),
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
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(title, style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
              Icon(icon, size: 20, color: color),
            ],
          ),
          const SizedBox(height: 10),
          Text(value, style: AppTypography.display(color: color).copyWith(fontSize: 32)),
          const SizedBox(height: 4),
          Text(subtitle, style: AppTypography.bodySmall()),
        ],
      ),
    );
  }

  Widget _buildLifecycleRow(String label, String count, double percent, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label, style: AppTypography.uiLabel()),
            Text(count, style: AppTypography.mono(weight: FontWeight.w700)),
          ],
        ),
        const SizedBox(height: 6),
        LinearProgressIndicator(
          value: percent.isNaN ? 0.0 : percent,
          color: color,
          backgroundColor: AppColors.rule,
          minHeight: 6,
          borderRadius: BorderRadius.circular(3),
        ),
      ],
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: AppColors.paper,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(color: AppColors.rule),
        ),
        child: Row(
          children: [
            Icon(icon, size: 18, color: AppColors.navy700),
            const SizedBox(width: 12),
            Expanded(child: Text(label, style: AppTypography.uiLabel())),
            const Icon(Icons.chevron_right, size: 18, color: AppColors.inkMuted),
          ],
        ),
      ),
    );
  }
}
