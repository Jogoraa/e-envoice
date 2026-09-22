import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class DiagnosticTrace {
  final String traceId;
  final String tenantName;
  final String branchCode;
  final String eventType;
  final String severity; // 'INFO', 'WARNING', 'ERROR'
  final String message;
  final DateTime timestamp;

  const DiagnosticTrace({
    required this.traceId,
    required this.tenantName,
    required this.branchCode,
    required this.eventType,
    required this.severity,
    required this.message,
    required this.timestamp,
  });

  Color get color {
    switch (severity) {
      case 'ERROR':
        return AppColors.red600;
      case 'WARNING':
        return AppColors.amber600;
      default:
        return AppColors.green700;
    }
  }
}

class SupportDiagnosticsScreen extends ConsumerStatefulWidget {
  const SupportDiagnosticsScreen({super.key});

  @override
  ConsumerState<SupportDiagnosticsScreen> createState() => _SupportDiagnosticsScreenState();
}

class _SupportDiagnosticsScreenState extends ConsumerState<SupportDiagnosticsScreen> {
  final List<DiagnosticTrace> _traces = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadDiagnostics();
  }

  Future<void> _loadDiagnostics() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(saasManagementApiClientProvider);
      final response = await client.get('/api/v1/saas/diagnostics');
      if (response.data is List) {
        final List list = response.data;
        _traces.clear();
        for (final item in list) {
          _traces.add(DiagnosticTrace(
            traceId: item['traceId']?.toString() ?? '',
            tenantName: item['tenantName']?.toString() ?? 'Platform',
            branchCode: item['branchCode']?.toString() ?? 'MAIN_HQ',
            eventType: item['eventType']?.toString() ?? 'EVENT',
            severity: item['severity']?.toString() ?? 'INFO',
            message: item['message']?.toString() ?? '',
            timestamp: item['timestamp'] != null
                ? DateTime.tryParse(item['timestamp'].toString()) ?? DateTime.now()
                : DateTime.now(),
          ));
        }
      }
      setState(() => _isLoading = false);
    } catch (e) {
      setState(() {
        _isLoading = false;
        _error = 'Unable to fetch real-time diagnostic traces from database.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadDiagnostics,
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
                        Text('OPERATIONAL SUPPORT & DIAGNOSTICS', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Live database audit traces, security events, and operational logs',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  ElevatedButton.icon(
                    onPressed: _loadDiagnostics,
                    icon: const Icon(Icons.refresh, size: 16),
                    label: const Text('Refresh Traces'),
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
                      TextButton(onPressed: _loadDiagnostics, child: const Text('Retry')),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),

              // Telemetry stream
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
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('LIVE DIAGNOSTIC LOG STREAM', style: AppTypography.h2()),
                          if (_isLoading)
                            const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: AppColors.rule),
                    if (_traces.isEmpty && !_isLoading)
                      Padding(
                        padding: const EdgeInsets.all(32),
                        child: Center(
                          child: Text(
                            'No operational audit traces recorded in database yet.',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                          ),
                        ),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _traces.length,
                        separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                        itemBuilder: (context, index) {
                          final t = _traces[index];
                          return Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: t.color.withValues(alpha: 0.1),
                                    borderRadius: BorderRadius.circular(3),
                                    border: Border.all(color: t.color.withValues(alpha: 0.3)),
                                  ),
                                  child: Text(
                                    t.severity,
                                    style: AppTypography.monoSmall(color: t.color, weight: FontWeight.w700),
                                  ),
                                ),
                                const SizedBox(width: 16),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          Text(t.eventType, style: AppTypography.mono(weight: FontWeight.w700)),
                                          const SizedBox(width: 8),
                                          Text('• ${t.tenantName} (${t.branchCode})', style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                                        ],
                                      ),
                                      const SizedBox(height: 4),
                                      Text(t.message, style: AppTypography.bodySmall()),
                                    ],
                                  ),
                                ),
                                Text(
                                  t.timestamp.toLocal().toString().length >= 19
                                      ? t.timestamp.toLocal().toString().substring(11, 19)
                                      : t.timestamp.toLocal().toString(),
                                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
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
}
