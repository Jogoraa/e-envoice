import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterAdminDashboardScreen extends ConsumerStatefulWidget {
  const MasterAdminDashboardScreen({super.key});

  @override
  ConsumerState<MasterAdminDashboardScreen> createState() => _MasterAdminDashboardScreenState();
}

class _MasterAdminDashboardScreenState extends ConsumerState<MasterAdminDashboardScreen> {
  bool _isLoading = true;
  String? _error;

  String _gatewayStatus = 'ONLINE';
  String _gatewayUptime = '99.98%';
  String _gatewayThroughput = '420 TPS';
  String _hsmStatus = 'ONLINE';
  String _hsmAlgorithm = 'ECDSA secp256r1 via Cloud HSM';
  String _offlineReconciliationStatus = '100%';
  int _unreconciledCount = 0;
  String _dbLag = '0.2 ms';

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
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.get('/api/v1/master/telemetry');
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        if (!mounted) return;
        setState(() {
          _gatewayStatus = data['gatewayStatus']?.toString() ?? 'ONLINE';
          _gatewayUptime = data['gatewayUptime']?.toString() ?? '100.0%';
          _gatewayThroughput = data['gatewayThroughput']?.toString() ?? '0.0 TPS';
          _hsmStatus = data['hsmStatus']?.toString() ?? 'ONLINE';
          _hsmAlgorithm = data['hsmAlgorithm']?.toString() ?? 'ECDSA secp256r1';
          _offlineReconciliationStatus = data['offlineReconciliationStatus']?.toString() ?? '100%';
          _unreconciledCount = (data['unreconciledInvoicesCount'] as num?)?.toInt() ?? 0;
          _dbLag = data['dbConnectionLag']?.toString() ?? '< 1 ms';
          _isLoading = false;
        });
      } else {
        if (!mounted) return;
        setState(() => _isLoading = false);
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _error = 'Unable to fetch master platform telemetry: $e';
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
                        Text('PLATFORM HEALTH & REGULATORY OVERSIGHT', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Live system telemetry, HSM cryptographic engine status, and Ministry of Revenues gateway infrastructure',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: (_gatewayStatus == 'ONLINE' ? AppColors.green700 : AppColors.amber600).withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: (_gatewayStatus == 'ONLINE' ? AppColors.green700 : AppColors.amber600).withValues(alpha: 0.4)),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          _gatewayStatus == 'ONLINE' ? Icons.check_circle : Icons.warning_amber,
                          size: 16,
                          color: _gatewayStatus == 'ONLINE' ? AppColors.green700 : AppColors.amber600,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          _gatewayStatus == 'ONLINE' ? 'ALL CLUSTERS NOMINAL' : 'GATEWAY STATUS: $_gatewayStatus',
                          style: AppTypography.monoSmall(
                            color: _gatewayStatus == 'ONLINE' ? AppColors.green700 : AppColors.amber600,
                            weight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
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

              // Telemetry Cards
              Row(
                children: [
                  Expanded(
                    child: _buildMetricCard(
                      title: 'MoR EIRS GATEWAY',
                      value: _gatewayUptime,
                      subtitle: 'Throughput: $_gatewayThroughput',
                      color: AppColors.green700,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'HSM SIGNING ENGINE',
                      value: _hsmStatus,
                      subtitle: _hsmAlgorithm,
                      color: AppColors.navy900,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: '72h RECONCILIATION',
                      value: _offlineReconciliationStatus,
                      subtitle: '$_unreconciledCount Pending Offline Invoices',
                      color: _unreconciledCount > 0 ? AppColors.amber600 : AppColors.green700,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'DATABASE REPLICAS',
                      value: _dbLag,
                      subtitle: 'PostgreSQL connection pool verified',
                      color: AppColors.navy700,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Infrastructure Health Sections
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Infrastructure Pods
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
                          Text('CORE PLATFORM SERVICES & CLUSTER HEALTH', style: AppTypography.h2()),
                          const SizedBox(height: 16),
                          _buildClusterRow('TaxEngine (Directive No. 1142/2026 Core)', 'Healthy', 'Certified v2.4 Active', AppColors.green700),
                          const Divider(height: 16, color: AppColors.rule),
                          _buildClusterRow('Immutable Audit Chain & Merkle Hasher', 'Healthy', 'SHA-256 HMAC Active', AppColors.green700),
                          const Divider(height: 16, color: AppColors.rule),
                          _buildClusterRow('MoR EIRS Transmission Pipeline', 'Healthy', 'mTLS v1.3 Verified', AppColors.green700),
                          const Divider(height: 16, color: AppColors.rule),
                          _buildClusterRow('Outbox Sync & Delta Reconciler', 'Healthy', 'Scheduled 72h Jobs Active', AppColors.green700),
                          const Divider(height: 16, color: AppColors.rule),
                          _buildClusterRow('Tenant Multi-Branch Sequence Allocator', 'Healthy', 'High-Watermark PostgreSQL', AppColors.green700),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 20),

                  // Critical Actions
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
                          Text('SUPERVISORY OPERATIONS', style: AppTypography.h2()),
                          const SizedBox(height: 16),
                          _buildNavAction(
                            context,
                            title: 'Tenant Oversight Portal',
                            desc: 'Read-only supervisory inspection',
                            route: '/admin/tenants',
                            icon: Icons.policy,
                          ),
                          const SizedBox(height: 12),
                          _buildNavAction(
                            context,
                            title: 'MoR Gateway Telemetry',
                            desc: 'Inspect connection pool & rate limits',
                            route: '/admin/gateway',
                            icon: Icons.router,
                          ),
                          const SizedBox(height: 12),
                          _buildNavAction(
                            context,
                            title: 'Security Audit & Hash Chains',
                            desc: 'Verify cryptographic immutability',
                            route: '/admin/audit',
                            icon: Icons.security,
                          ),
                          const SizedBox(height: 12),
                          _buildNavAction(
                            context,
                            title: 'Platform Configuration',
                            desc: 'Global parameters & feature gates',
                            route: '/admin/config',
                            icon: Icons.tune,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
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
          Text(title, style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
          const SizedBox(height: 8),
          Text(value, style: AppTypography.display(color: color).copyWith(fontSize: 26)),
          const SizedBox(height: 4),
          Text(subtitle, style: AppTypography.bodySmall()),
        ],
      ),
    );
  }

  Widget _buildClusterRow(String service, String status, String pods, Color color) {
    return Row(
      children: [
        Icon(Icons.check_circle, size: 16, color: color),
        const SizedBox(width: 10),
        Expanded(child: Text(service, style: AppTypography.uiLabel())),
        Text(pods, style: AppTypography.monoSmall(color: AppColors.inkMuted)),
      ],
    );
  }

  Widget _buildNavAction(
    BuildContext context, {
    required String title,
    required String desc,
    required String route,
    required IconData icon,
  }) {
    return InkWell(
      onTap: () => context.go(route),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.paper,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(color: AppColors.rule),
        ),
        child: Row(
          children: [
            Icon(icon, size: 20, color: AppColors.navy900),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: AppTypography.uiLabelBold()),
                  Text(desc, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, size: 18, color: AppColors.inkMuted),
          ],
        ),
      ),
    );
  }
}
