import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterSessionsDevicesScreen extends ConsumerStatefulWidget {
  const MasterSessionsDevicesScreen({super.key});

  @override
  ConsumerState<MasterSessionsDevicesScreen> createState() =>
      _MasterSessionsDevicesScreenState();
}

class _MasterSessionsDevicesScreenState
    extends ConsumerState<MasterSessionsDevicesScreen> {
  bool _isLoading = true;
  String? _error;
  List<dynamic> _sessions = [];

  @override
  void initState() {
    super.initState();
    _loadSessions();
  }

  Future<void> _loadSessions() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.get('/api/v1/master/account/sessions');
      setState(() {
        _sessions = res.data is List ? res.data as List : [];
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to load operator sessions: $e';
        _isLoading = false;
      });
    }
  }

  Future<void> _revokeSession(String sessionId) async {
    try {
      final client = ref.read(masterAdminApiClientProvider);
      await client.post('/api/v1/master/account/sessions/$sessionId/revoke');
      _loadSessions();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to revoke session: $e')),
        );
      }
    }
  }

  Future<void> _revokeAllOtherSessions() async {
    try {
      final client = ref.read(masterAdminApiClientProvider);
      await client.post('/api/v1/master/account/sessions/revoke-others');
      _loadSessions();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('All other operator sessions terminated.')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to terminate sessions: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: AppColors.paperMuted,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.paperMuted,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Page Header
            Row(
              children: [
                const Icon(
                  Icons.devices_outlined,
                  size: 28,
                  color: AppColors.navy900,
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Active Sessions & Enrolled Devices', style: AppTypography.h1()),
                    Text(
                      'Monitor operator sessions, cryptographic device telemetry, and enforce remote session revocation.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                const Spacer(),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(backgroundColor: AppColors.red700),
                  onPressed: _revokeAllOtherSessions,
                  icon: const Icon(Icons.power_settings_new, size: 16),
                  label: const Text('Revoke All Other Sessions'),
                ),
              ],
            ),
            const SizedBox(height: 20),

            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.red700.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.red700)),
              ),
              const SizedBox(height: 16),
            ],

            Expanded(
              child: _sessions.isEmpty
                  ? Center(
                      child: Text(
                        'No active sessions found.',
                        style: AppTypography.bodySmall(color: AppColors.inkMuted),
                      ),
                    )
                  : Container(
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: ListView.separated(
                        itemCount: _sessions.length,
                        separatorBuilder: (c, i) => const Divider(height: 1, color: AppColors.rule),
                        itemBuilder: (c, i) {
                          final s = _sessions[i];
                          final isCurrent = s['isCurrent'] == true;
                          final mfaAuth = s['mfaAuthenticated'] == true;

                          return Material(
                            type: MaterialType.transparency,
                            child: ListTile(
                              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                              leading: Icon(
                                Icons.laptop_mac_outlined,
                                size: 28,
                                color: isCurrent ? AppColors.green700 : AppColors.navy900,
                              ),
                              title: Row(
                                children: [
                                  Text(
                                    s['deviceSummary'] ?? 'Web Browser Session',
                                    style: AppTypography.uiLabelBold(),
                                  ),
                                  const SizedBox(width: 8),
                                  if (isCurrent) ...[
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: AppColors.green700.withValues(alpha: 0.1),
                                        borderRadius: BorderRadius.circular(3),
                                      ),
                                      child: Text(
                                        'CURRENT SESSION',
                                        style: AppTypography.monoSmall(color: AppColors.green700, weight: FontWeight.w700),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                  ],
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: (mfaAuth ? AppColors.green700 : AppColors.amber700).withValues(alpha: 0.1),
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                    child: Text(
                                      mfaAuth ? 'MFA VERIFIED' : 'PASSWORD ONLY',
                                      style: AppTypography.monoSmall(
                                        color: mfaAuth ? AppColors.green700 : AppColors.amber700,
                                        weight: FontWeight.w700,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              subtitle: Padding(
                                padding: const EdgeInsets.only(top: 4),
                                child: Text(
                                  'IP: ${s['ipAddress'] ?? '127.0.0.1'} | Last seen: ${s['lastSeenAt'] ?? 'Now'} | User-Agent: ${s['userAgent'] ?? 'Flutter/Platform'}',
                                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                ),
                              ),
                              trailing: !isCurrent
                                  ? OutlinedButton.icon(
                                      onPressed: () => _revokeSession(s['id']),
                                      style: OutlinedButton.styleFrom(foregroundColor: AppColors.red700),
                                      icon: const Icon(Icons.delete_outline, size: 14),
                                      label: const Text('Revoke'),
                                    )
                                  : null,
                            ),
                          );
                        },
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
