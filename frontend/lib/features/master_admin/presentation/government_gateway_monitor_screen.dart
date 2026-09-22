import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class GovernmentGatewayMonitorScreen extends ConsumerStatefulWidget {
  const GovernmentGatewayMonitorScreen({super.key});

  @override
  ConsumerState<GovernmentGatewayMonitorScreen> createState() => _GovernmentGatewayMonitorScreenState();
}

class _GovernmentGatewayMonitorScreenState extends ConsumerState<GovernmentGatewayMonitorScreen> {
  bool _isLoading = true;
  String? _error;

  String _gatewayStatus = 'ONLINE';
  String _throughput = '0.0 TPS';
  String _latency = '- ms';
  String _connectionPool = '- / -';
  String _errorRate = '0.00%';
  String _circuitBreakerStatus = 'State: CLOSED (Normal Operation) • Failure Threshold: 50 consecutive timeouts • Half-Open Reset: 30s • Automatic Offline Outbox Fallback: ENGAGED';
  List<Map<String, dynamic>> _channels = [];

  @override
  void initState() {
    super.initState();
    _loadGatewayStatus();
  }

  Future<void> _loadGatewayStatus() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.get('/api/v1/master/gateway-status');
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        setState(() {
          _gatewayStatus = data['gatewayStatus']?.toString() ?? 'ONLINE';
          _throughput = data['currentThroughput']?.toString() ?? '0.0 TPS';
          _latency = data['averageLatency']?.toString() ?? '- ms';
          _connectionPool = data['connectionPool']?.toString() ?? '- / -';
          _errorRate = data['gatewayErrorRate']?.toString() ?? '0.00%';
          _circuitBreakerStatus = data['circuitBreakerStatus']?.toString() ?? _circuitBreakerStatus;
          
          final rawChannels = data['channels'];
          if (rawChannels is List) {
            _channels = rawChannels.map((c) => Map<String, dynamic>.from(c as Map)).toList();
          } else {
            _channels = [];
          }
          if (!mounted) return;
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
        _error = 'Unable to fetch real-time MoR gateway status from platform.';
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

    final isOnline = _gatewayStatus == 'ONLINE';

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadGatewayStatus,
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
                        Text('MINISTRY OF REVENUES (MoR) GATEWAY MONITOR', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Direct electronic invoicing infrastructure connection pool under Directive No. 1142/2026',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  Row(
                    children: [
                      IconButton(
                        onPressed: _loadGatewayStatus,
                        icon: const Icon(Icons.refresh, size: 20, color: AppColors.navy700),
                        tooltip: 'Refresh Status',
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: isOnline ? AppColors.green700.withValues(alpha: 0.1) : AppColors.red700.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(3),
                          border: Border.all(color: isOnline ? AppColors.green700.withValues(alpha: 0.4) : AppColors.red700.withValues(alpha: 0.4)),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.circle, size: 10, color: isOnline ? AppColors.green700 : AppColors.red700),
                            const SizedBox(width: 8),
                            Text(
                              isOnline ? 'GATEWAY ONLINE & SYNCHRONIZING' : 'GATEWAY DEGRADED / OFFLINE',
                              style: AppTypography.monoSmall(
                                color: isOnline ? AppColors.green700 : AppColors.red700,
                                weight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.red700.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.red700.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, color: AppColors.red700, size: 20),
                      const SizedBox(width: 8),
                      Expanded(child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.red700))),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),

              // Live Performance Metrics
              Row(
                children: [
                  Expanded(
                    child: _buildMetricCard('CURRENT THROUGHPUT', _throughput, 'Avg transmission rate', AppColors.navy900),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard('AVERAGE LATENCY', _latency, 'End-to-end MoR ACK', AppColors.green700),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard('CONNECTION POOL', _connectionPool, 'Active mTLS sockets', AppColors.navy700),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard('GATEWAY ERROR RATE', _errorRate, 'HTTP 422 schema rejections', AppColors.green700),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Gateway Channel Health
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('ACTIVE EIRS GATEWAY CHANNELS', style: AppTypography.h2()),
                    const SizedBox(height: 16),
                    if (_channels.isEmpty)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 16.0),
                        child: Center(
                          child: Text(
                            'No active gateway channels registered.',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                          ),
                        ),
                      )
                    else
                      for (int i = 0; i < _channels.length; i++) ...[
                        _buildChannelRow(
                          _channels[i]['name']?.toString() ?? 'Gateway Channel',
                          _channels[i]['status']?.toString() ?? 'Active',
                          _channels[i]['protocol']?.toString() ?? 'mTLS v1.3',
                          (_channels[i]['status']?.toString().contains('Standby') ?? false)
                              ? AppColors.amber700
                              : AppColors.green700,
                        ),
                        if (i < _channels.length - 1)
                          const Divider(height: 16, color: AppColors.rule),
                      ],
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Circuit Breaker Status
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('CIRCUIT BREAKER & RESILIENCY POLICY', style: AppTypography.h2()),
                    const SizedBox(height: 12),
                    Text(
                      _circuitBreakerStatus,
                      style: AppTypography.monoSmall(color: AppColors.inkMuted),
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
          Text(value, style: AppTypography.display(color: color).copyWith(fontSize: 26)),
          const SizedBox(height: 4),
          Text(subtitle, style: AppTypography.bodySmall()),
        ],
      ),
    );
  }

  Widget _buildChannelRow(String channel, String status, String details, Color color) {
    return Row(
      children: [
        Icon(Icons.check_circle, size: 16, color: color),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(channel, style: AppTypography.uiLabelBold()),
              Text(details, style: AppTypography.monoSmall(color: AppColors.inkMuted)),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(3),
          ),
          child: Text(status, style: AppTypography.monoSmall(color: color, weight: FontWeight.w700)),
        ),
      ],
    );
  }
}
