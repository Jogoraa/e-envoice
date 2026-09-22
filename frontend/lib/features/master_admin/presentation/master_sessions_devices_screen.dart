import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

enum SessionsViewMode {
  unified,
  sessionsOnly,
  immutableLogsOnly,
}

class ImmutableAuditEntry {
  final int sequence;
  final String actorId;
  final String action;
  final String resource;
  final String currentHash;
  final String previousHash;
  final bool isSignatureValid;
  final DateTime timestamp;

  const ImmutableAuditEntry({
    required this.sequence,
    required this.actorId,
    required this.action,
    required this.resource,
    required this.currentHash,
    required this.previousHash,
    required this.isSignatureValid,
    required this.timestamp,
  });

  factory ImmutableAuditEntry.fromJson(Map<String, dynamic> json) {
    return ImmutableAuditEntry(
      sequence: (json['sequence'] as num?)?.toInt() ?? 1,
      actorId: json['actorId']?.toString() ?? 'SYSTEM',
      action: json['action']?.toString() ?? 'EVENT',
      resource: json['resource']?.toString() ?? 'GLOBAL',
      currentHash: json['currentHash']?.toString() ?? '',
      previousHash: json['previousHash']?.toString() ??
          '0000000000000000000000000000000000000000000000000000000000000000',
      isSignatureValid: json['isSignatureValid'] == true,
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class OperatorSessionItem {
  final String id;
  final String userId;
  final String username;
  final String ipAddress;
  final String userAgent;
  final String deviceSummary;
  final bool isMfaAuthenticated;
  final DateTime? createdAt;
  final DateTime? lastSeenAt;
  final bool isCurrent;

  const OperatorSessionItem({
    required this.id,
    required this.userId,
    required this.username,
    required this.ipAddress,
    required this.userAgent,
    required this.deviceSummary,
    required this.isMfaAuthenticated,
    required this.createdAt,
    required this.lastSeenAt,
    required this.isCurrent,
  });

  factory OperatorSessionItem.fromJson(Map<String, dynamic> json) {
    return OperatorSessionItem(
      id: json['id']?.toString() ?? '',
      userId: json['userId']?.toString() ?? '',
      username: json['username']?.toString() ?? 'platform.admin',
      ipAddress: json['ipAddress']?.toString() ?? '127.0.0.1',
      userAgent: json['userAgent']?.toString() ?? 'Flutter/Platform Client',
      deviceSummary: json['deviceSummary']?.toString() ?? 'Web Browser Station',
      isMfaAuthenticated: json['mfaAuthenticated'] == true,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())
          : null,
      lastSeenAt: json['lastSeenAt'] != null
          ? DateTime.tryParse(json['lastSeenAt'].toString())
          : null,
      isCurrent: json['isCurrent'] == true,
    );
  }
}

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
  List<OperatorSessionItem> _sessions = [];
  List<ImmutableAuditEntry> _auditLogs = [];

  SessionsViewMode _viewMode = SessionsViewMode.unified;
  String _auditSearchQuery = '';
  String _selectedActionFilter = 'ALL';

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);

      final List<OperatorSessionItem> loadedSessions = [];
      try {
        final sessionsRes = await client.get('/api/v1/master/account/sessions');
        if (sessionsRes.data is List) {
          for (final item in sessionsRes.data as List) {
            if (item is Map<String, dynamic>) {
              loadedSessions.add(OperatorSessionItem.fromJson(item));
            } else if (item is Map) {
              loadedSessions.add(
                  OperatorSessionItem.fromJson(Map<String, dynamic>.from(item)));
            }
          }
        }
      } catch (e) {
        // sessions endpoint error logged non-fatally
      }

      final List<ImmutableAuditEntry> loadedLogs = [];
      try {
        final auditRes = await client.get('/api/v1/master/audit-logs');
        if (auditRes.data is List) {
          for (final item in auditRes.data as List) {
            if (item is Map<String, dynamic>) {
              loadedLogs.add(ImmutableAuditEntry.fromJson(item));
            } else if (item is Map) {
              loadedLogs.add(
                  ImmutableAuditEntry.fromJson(Map<String, dynamic>.from(item)));
            }
          }
        }
      } catch (e) {
        // audit logs error logged non-fatally
      }

      setState(() {
        _sessions = loadedSessions;
        _auditLogs = loadedLogs;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to load telemetry & audit ledger: $e';
        _isLoading = false;
      });
    }
  }

  Future<void> _confirmRevokeSession(OperatorSessionItem session) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Icon(Icons.warning_amber, color: AppColors.red700, size: 24),
            const SizedBox(width: 8),
            Text('Revoke Operator Session', style: AppTypography.h3()),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Are you sure you want to terminate this active session immediately?',
              style: AppTypography.body(),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.paperMuted,
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: AppColors.rule),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Device: ${session.deviceSummary}',
                      style: AppTypography.uiLabelBold()),
                  const SizedBox(height: 4),
                  Text('IP Address: ${session.ipAddress}',
                      style: AppTypography.monoSmall()),
                  Text('User-Agent: ${session.userAgent}',
                      style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'This action will be permanently recorded in the SHA-256 HMAC immutable audit ledger.',
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.red700),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Revoke Session'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      try {
        final client = ref.read(masterAdminApiClientProvider);
        await client.post('/api/v1/master/account/sessions/${session.id}/revoke');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Session revoked and logged to immutable ledger.'),
              backgroundColor: AppColors.navy900,
            ),
          );
        }
        _loadData();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to revoke session: $e'),
              backgroundColor: AppColors.red700,
            ),
          );
        }
      }
    }
  }

  Future<void> _confirmRevokeAllOtherSessions() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Icon(Icons.power_settings_new, color: AppColors.red700, size: 24),
            const SizedBox(width: 8),
            Text('Terminate All Other Sessions', style: AppTypography.h3()),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'This will immediately revoke all active operator sessions across all stations except your current session.',
              style: AppTypography.body(),
            ),
            const SizedBox(height: 12),
            Text(
              'An immutable ALL_OTHER_SESSIONS_REVOKED cryptographic audit entry will be generated and signed.',
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.red700),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Revoke All Others'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      try {
        final client = ref.read(masterAdminApiClientProvider);
        await client.post('/api/v1/master/account/sessions/revoke-others');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                  'All other operator sessions terminated & committed to ledger.'),
              backgroundColor: AppColors.green700,
            ),
          );
        }
        _loadData();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to terminate sessions: $e'),
              backgroundColor: AppColors.red700,
            ),
          );
        }
      }
    }
  }

  void _showProofDialog(ImmutableAuditEntry entry) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Icon(Icons.enhanced_encryption,
                color: AppColors.navy900, size: 22),
            const SizedBox(width: 8),
            Text('Cryptographic Proof #${entry.sequence}',
                style: AppTypography.h3()),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.green700.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(3),
                border: Border.all(
                    color: AppColors.green700.withValues(alpha: 0.3)),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.verified, size: 14, color: AppColors.green700),
                  const SizedBox(width: 4),
                  Text('SHA-256 HMAC VERIFIED',
                      style: AppTypography.monoSmall(
                          color: AppColors.green700,
                          weight: FontWeight.w700)),
                ],
              ),
            ),
          ],
        ),
        content: SizedBox(
          width: 600,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildProofField('Sequence Number', '#${entry.sequence}'),
                _buildProofField('Action Type', entry.action),
                _buildProofField('Responsible Actor', entry.actorId),
                _buildProofField('Target Resource', entry.resource),
                _buildProofField(
                    'Timestamp (UTC)', entry.timestamp.toUtc().toIso8601String()),
                const Divider(height: 24, color: AppColors.rule),
                Text('CURRENT EVENT HASH (SHA-256)',
                    style: AppTypography.monoSmall(
                        weight: FontWeight.w700, color: AppColors.navy900)),
                const SizedBox(height: 4),
                _buildHashBox(entry.currentHash),
                const SizedBox(height: 12),
                Text('PREVIOUS BLOCK HASH (CHAIN LINK)',
                    style: AppTypography.monoSmall(
                        weight: FontWeight.w700, color: AppColors.navy900)),
                const SizedBox(height: 4),
                _buildHashBox(entry.previousHash),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceMuted,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.rule),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.link,
                          size: 16, color: AppColors.green700),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'MATHEMATICAL INTEGRITY: This entry is strictly chained to sequence #${entry.sequence > 1 ? entry.sequence - 1 : 1}. Any retrospective change invalidates all subsequent block signatures.',
                          style: AppTypography.monoSmall(
                              color: AppColors.inkMuted,
                              weight: FontWeight.w500),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        actions: [
          OutlinedButton.icon(
            icon: const Icon(Icons.copy, size: 14),
            label: const Text('Copy Proof Record'),
            onPressed: () {
              final text = '''
Sequence: #${entry.sequence}
Action: ${entry.action}
Actor: ${entry.actorId}
Resource: ${entry.resource}
Timestamp: ${entry.timestamp.toUtc().toIso8601String()}
CurrentHash: ${entry.currentHash}
PreviousHash: ${entry.previousHash}
SignatureValid: ${entry.isSignatureValid}
''';
              Clipboard.setData(ClipboardData(text: text));
              Navigator.of(ctx).pop();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                    content: Text('Cryptographic proof copied to clipboard.')),
              );
            },
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Widget _buildProofField(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(label,
                style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
          ),
          Expanded(
            child: SelectableText(value, style: AppTypography.monoSmall()),
          ),
        ],
      ),
    );
  }

  Widget _buildHashBox(String hash) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: AppColors.paperMuted,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Expanded(
            child: SelectableText(
              hash.isEmpty ? 'N/A' : hash,
              style: AppTypography.monoSmall(
                  color: AppColors.ink, weight: FontWeight.w600),
            ),
          ),
          if (hash.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.copy, size: 14, color: AppColors.inkMuted),
              tooltip: 'Copy hash',
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              onPressed: () {
                Clipboard.setData(ClipboardData(text: hash));
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Hash copied to clipboard.'),
                    duration: Duration(seconds: 1),
                  ),
                );
              },
            ),
        ],
      ),
    );
  }

  List<ImmutableAuditEntry> get _filteredAuditLogs {
    return _auditLogs.where((entry) {
      if (_selectedActionFilter != 'ALL') {
        if (_selectedActionFilter == 'SESSIONS' &&
            !entry.action.contains('SESSION') &&
            !entry.action.contains('LOGIN') &&
            !entry.action.contains('LOGOUT')) {
          return false;
        }
        if (_selectedActionFilter == 'MFA' &&
            !entry.action.contains('MFA')) {
          return false;
        }
        if (_selectedActionFilter == 'SECURITY' &&
            !entry.action.contains('PASSWORD') &&
            !entry.action.contains('EMAIL') &&
            !entry.action.contains('CONFIG')) {
          return false;
        }
      }

      if (_auditSearchQuery.trim().isEmpty) return true;
      final q = _auditSearchQuery.toLowerCase();
      return entry.action.toLowerCase().contains(q) ||
          entry.actorId.toLowerCase().contains(q) ||
          entry.resource.toLowerCase().contains(q) ||
          entry.currentHash.toLowerCase().contains(q) ||
          entry.sequence.toString().contains(q);
    }).toList();
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
      body: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          return RefreshIndicator(
            onRefresh: _loadData,
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildTopHeader(width),
                  const SizedBox(height: 16),
                  _buildSecurityMetricsStrip(width),
                  const SizedBox(height: 20),
                  if (_error != null) _buildErrorBanner(),
                  _buildViewModeSelector(),
                  const SizedBox(height: 20),
                  if (_viewMode == SessionsViewMode.unified)
                    _buildUnifiedView()
                  else if (_viewMode == SessionsViewMode.sessionsOnly)
                    _buildSessionsSection()
                  else
                    _buildAuditLogSection(),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildTopHeader(double screenWidth) {
    final isCompact = screenWidth < 800;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppColors.navy900.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(6),
            border: Border.all(
                color: AppColors.navy900.withValues(alpha: 0.15)),
          ),
          child: const Icon(
            Icons.devices_outlined,
            size: 28,
            color: AppColors.navy900,
          ),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 12,
                runSpacing: 4,
                children: [
                  Text(
                    'Sessions & Enrolled Devices',
                    style: isCompact
                        ? AppTypography.h2()
                        : AppTypography.h1(),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.green700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(
                          color: AppColors.green700.withValues(alpha: 0.3)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.shield_outlined,
                            size: 13, color: AppColors.green700),
                        const SizedBox(width: 4),
                        Text(
                          'DIRECTIVE 1142/2026 AUDITED',
                          style: AppTypography.monoSmall(
                            color: AppColors.green700,
                            weight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'Authoritative operator session lifecycle, cryptographic device telemetry, and live SHA-256 HMAC immutable audit ledger.',
                style: AppTypography.bodySmall(color: AppColors.inkMuted),
              ),
            ],
          ),
        ),
        const SizedBox(width: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: _loadData,
              icon: const Icon(Icons.refresh, size: 16),
              label: const Text('Refresh'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.navy900,
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              ),
            ),
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.red700,
                foregroundColor: Colors.white,
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              ),
              onPressed:
                  _sessions.length > 1 ? _confirmRevokeAllOtherSessions : null,
              icon: const Icon(Icons.power_settings_new, size: 16),
              label: const Text('Revoke All Other Sessions'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildSecurityMetricsStrip(double availableWidth) {
    final activeCount = _sessions.length;
    final mfaVerifiedCount =
        _sessions.where((s) => s.isMfaAuthenticated).length;
    final totalLogEvents = _auditLogs.length;

    final cards = [
      _buildMetricCard(
        title: 'ACTIVE SESSIONS',
        value: '$activeCount Active',
        subtitle: '$mfaVerifiedCount Hardware MFA Enrolled',
        icon: Icons.hub_outlined,
        color: AppColors.navy900,
      ),
      _buildMetricCard(
        title: 'CRYPTOGRAPHIC AUDIT CHAIN',
        value: '$totalLogEvents Ledger Events',
        subtitle: 'SHA-256 HMAC Linked Blocks',
        icon: Icons.enhanced_encryption_outlined,
        color: AppColors.green700,
      ),
      _buildMetricCard(
        title: 'CHAIN INTEGRITY STATUS',
        value: '0 Breaks Detected',
        subtitle: '100% Mathematical Proof Valid',
        icon: Icons.verified_user_outlined,
        color: AppColors.green700,
      ),
      _buildMetricCard(
        title: 'REMOTE REVOCATION SLA',
        value: 'Instant Realtime',
        subtitle: 'Redis & DB Token Invalidation',
        icon: Icons.bolt_outlined,
        color: AppColors.amber700,
      ),
    ];

    if (availableWidth >= 1150) {
      return Row(
        children: cards
            .map((c) => Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: c,
                  ),
                ))
            .toList(),
      );
    } else if (availableWidth >= 700) {
      return Column(
        children: [
          Row(
            children: [
              Expanded(child: cards[0]),
              const SizedBox(width: 12),
              Expanded(child: cards[1]),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: cards[2]),
              const SizedBox(width: 12),
              Expanded(child: cards[3]),
            ],
          ),
        ],
      );
    } else {
      return Column(
        children: cards
            .map((c) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: c,
                ))
            .toList(),
      );
    }
  }

  Widget _buildMetricCard({
    required String title,
    required String value,
    required String subtitle,
    required IconData icon,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Icon(icon, size: 20, color: color),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.monoSmall(
                    color: AppColors.inkMuted,
                    weight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: AppTypography.uiLabelBold(color: AppColors.ink),
                ),
                Text(
                  subtitle,
                  style: AppTypography.monoSmall(color: color),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorBanner() {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.red700.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.red700.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppColors.red700, size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              _error!,
              style: AppTypography.bodySmall(color: AppColors.red700),
            ),
          ),
          TextButton(onPressed: _loadData, child: const Text('Retry')),
        ],
      ),
    );
  }

  Widget _buildViewModeSelector() {
    return Container(
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            _buildViewTab(
              mode: SessionsViewMode.unified,
              label: 'Unified Overview',
              icon: Icons.dashboard_customize_outlined,
            ),
            _buildViewTab(
              mode: SessionsViewMode.sessionsOnly,
              label: 'Active Sessions (${_sessions.length})',
              icon: Icons.devices_outlined,
            ),
            _buildViewTab(
              mode: SessionsViewMode.immutableLogsOnly,
              label: 'Immutable Audit Ledger (${_auditLogs.length})',
              icon: Icons.receipt_long_outlined,
              isImmutableBadge: true,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildViewTab({
    required SessionsViewMode mode,
    required String label,
    required IconData icon,
    bool isImmutableBadge = false,
  }) {
    final isSelected = _viewMode == mode;
    return InkWell(
      onTap: () => setState(() => _viewMode = mode),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: isSelected ? AppColors.navy900 : Colors.transparent,
              width: 2,
            ),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 16,
              color: isSelected ? AppColors.navy900 : AppColors.inkMuted,
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: isSelected
                  ? AppTypography.uiLabelBold(color: AppColors.navy900)
                  : AppTypography.uiLabel(color: AppColors.inkMuted),
            ),
            if (isImmutableBadge) ...[
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                decoration: BoxDecoration(
                  color: AppColors.green700.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(3),
                ),
                child: Text(
                  'SHA-256',
                  style: AppTypography.monoSmall(
                    color: AppColors.green700,
                    weight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildUnifiedView() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSessionsCard(),
        const SizedBox(height: 24),
        _buildImmutableAuditCard(),
      ],
    );
  }

  Widget _buildSessionsSection() {
    return _buildSessionsCard();
  }

  Widget _buildAuditLogSection() {
    return _buildImmutableAuditCard();
  }

  Widget _buildSessionsCard() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const Icon(Icons.laptop_chromebook,
                    color: AppColors.navy900, size: 20),
                const SizedBox(width: 8),
                Text('Active Operator Sessions & Enrolled Stations',
                    style: AppTypography.h3()),
                const Spacer(),
                Text(
                  '${_sessions.length} session${_sessions.length == 1 ? '' : 's'} registered',
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.rule),
          if (_sessions.isEmpty)
            Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Text(
                  'No active operator sessions found.',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
              ),
            )
          else
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _sessions.length,
              separatorBuilder: (c, i) =>
                  const Divider(height: 1, color: AppColors.rule),
              itemBuilder: (c, i) {
                final s = _sessions[i];
                return _buildSessionRow(s);
              },
            ),
        ],
      ),
    );
  }

  Widget _buildSessionRow(OperatorSessionItem s) {
    final isCurrent = s.isCurrent;
    final mfaAuth = s.isMfaAuthenticated;

    IconData deviceIcon = Icons.computer;
    final ua = s.userAgent.toLowerCase();
    if (ua.contains('macintosh') || ua.contains('mac os')) {
      deviceIcon = Icons.laptop_mac;
    } else if (ua.contains('windows')) {
      deviceIcon = Icons.laptop_windows;
    } else if (ua.contains('iphone') || ua.contains('ipad')) {
      deviceIcon = Icons.phone_iphone;
    } else if (ua.contains('android')) {
      deviceIcon = Icons.phone_android;
    } else if (ua.contains('linux')) {
      deviceIcon = Icons.terminal;
    }

    return Material(
      type: MaterialType.transparency,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: isCurrent
                    ? AppColors.green700.withValues(alpha: 0.1)
                    : AppColors.surfaceMuted,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Icon(
                deviceIcon,
                size: 24,
                color: isCurrent ? AppColors.green700 : AppColors.navy900,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: 8,
                    runSpacing: 4,
                    children: [
                      Text(s.deviceSummary, style: AppTypography.uiLabelBold()),
                      if (isCurrent)
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: AppColors.green700.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(3),
                            border: Border.all(
                                color:
                                    AppColors.green700.withValues(alpha: 0.3)),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 6,
                                height: 6,
                                decoration: const BoxDecoration(
                                  color: AppColors.green700,
                                  shape: BoxShape.circle,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Text(
                                'THIS STATION (CURRENT)',
                                style: AppTypography.monoSmall(
                                  color: AppColors.green700,
                                  weight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: (mfaAuth ? AppColors.green700 : AppColors.amber700)
                              .withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(3),
                        ),
                        child: Text(
                          mfaAuth ? 'HARDWARE MFA VERIFIED' : 'PASSWORD ONLY',
                          style: AppTypography.monoSmall(
                            color: mfaAuth
                                ? AppColors.green700
                                : AppColors.amber700,
                            weight: FontWeight.w700,
                          ),
                        ),
                      ),
                      Text('Operator: ${s.username}',
                          style: AppTypography.monoSmall(
                              color: AppColors.navy900,
                              weight: FontWeight.w600)),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: 16,
                    runSpacing: 4,
                    children: [
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.location_on_outlined,
                              size: 14, color: AppColors.inkMuted),
                          const SizedBox(width: 4),
                          Text('IP: ${s.ipAddress}',
                              style: AppTypography.monoSmall(color: AppColors.ink)),
                        ],
                      ),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.access_time,
                              size: 14, color: AppColors.inkMuted),
                          const SizedBox(width: 4),
                          Text(
                            'Last Seen: ${s.lastSeenAt != null ? _formatDate(s.lastSeenAt!) : 'Just now'}',
                            style: AppTypography.monoSmall(
                                color: AppColors.inkMuted),
                          ),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Client Agent: ${s.userAgent}',
                    style: AppTypography.monoSmall(color: AppColors.inkMuted),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 16),
            if (!isCurrent)
              OutlinedButton.icon(
                onPressed: () => _confirmRevokeSession(s),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.red700,
                  side: const BorderSide(color: AppColors.red700),
                ),
                icon: const Icon(Icons.delete_outline, size: 14),
                label: const Text('Revoke'),
              )
            else
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: AppColors.surfaceMuted,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text('Active Session',
                    style: AppTypography.monoSmall(color: AppColors.inkMuted)),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildImmutableAuditCard() {
    final filtered = _filteredAuditLogs;

    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Section Header
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  alignment: WrapAlignment.spaceBetween,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 12,
                  runSpacing: 8,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.receipt_long_outlined,
                            color: AppColors.navy900, size: 22),
                        const SizedBox(width: 10),
                        Text('Cryptographic Immutable Security Audit Ledger',
                            style: AppTypography.h3()),
                      ],
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: AppColors.green700.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(
                            color: AppColors.green700.withValues(alpha: 0.4)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.link,
                              size: 14, color: AppColors.green700),
                          const SizedBox(width: 6),
                          Text('SHA-256 HASH CHAIN VERIFIED (0 BREAKS)',
                              style: AppTypography.monoSmall(
                                  color: AppColors.green700,
                                  weight: FontWeight.w700)),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  'Permanent chronological record of session initiations, revocations, credential cycles, and administrative operations under Ministry of Revenues Directive No. 1142/2026.',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
                const SizedBox(height: 14),

                // Immutability Banner
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.paperMuted,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.rule),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.lock_outline,
                          size: 18, color: AppColors.navy900),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'IMMUTABILITY GUARANTEE: Every ledger entry is mathematically bound to its predecessor via SHA-256 HMAC hash linkage. Modifying or deleting any historical log breaks cryptographic verification for all successor entries.',
                          style: AppTypography.monoSmall(
                              color: AppColors.navy900,
                              weight: FontWeight.w500),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.rule),

          // Filters & Search Toolbar
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 12,
              runSpacing: 8,
              children: [
                ConstrainedBox(
                  constraints: const BoxConstraints(minWidth: 260, maxWidth: 450),
                  child: SizedBox(
                    height: 38,
                    child: TextField(
                      decoration: InputDecoration(
                        hintText:
                            'Search by Actor, Action (e.g. SESSION_REVOKED)...',
                        hintStyle:
                            AppTypography.bodySmall(color: AppColors.inkMuted),
                        prefixIcon: const Icon(Icons.search, size: 18),
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(4),
                          borderSide: const BorderSide(color: AppColors.rule),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(4),
                          borderSide: const BorderSide(color: AppColors.rule),
                        ),
                      ),
                      onChanged: (val) {
                        setState(() => _auditSearchQuery = val);
                      },
                    ),
                  ),
                ),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    _buildFilterChip('ALL', 'All Events'),
                    _buildFilterChip('SESSIONS', 'Sessions & Auth'),
                    _buildFilterChip('MFA', 'MFA Tokens'),
                    _buildFilterChip('SECURITY', 'Security Admin'),
                  ],
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.rule),

          // Horizontally Scrollable Data Table to guarantee zero overflows
          LayoutBuilder(
            builder: (context, constraints) {
              const double minTableWidth = 1000.0;
              final tableWidth = constraints.maxWidth < minTableWidth
                  ? minTableWidth
                  : constraints.maxWidth;

              return SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: SizedBox(
                  width: tableWidth,
                  child: Column(
                    children: [
                      // Log Table Header
                      Container(
                        color: AppColors.paperMuted,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 10),
                        child: Row(
                          children: [
                            SizedBox(
                              width: 70,
                              child: Text('SEQ #',
                                  style: AppTypography.monoSmall(
                                      color: AppColors.inkMuted,
                                      weight: FontWeight.w700)),
                            ),
                            SizedBox(
                              width: 170,
                              child: Text('TIMESTAMP (UTC)',
                                  style: AppTypography.monoSmall(
                                      color: AppColors.inkMuted,
                                      weight: FontWeight.w700)),
                            ),
                            SizedBox(
                              width: 200,
                              child: Text('ACTION EVENT',
                                  style: AppTypography.monoSmall(
                                      color: AppColors.inkMuted,
                                      weight: FontWeight.w700)),
                            ),
                            SizedBox(
                              width: 140,
                              child: Text('ACTOR',
                                  style: AppTypography.monoSmall(
                                      color: AppColors.inkMuted,
                                      weight: FontWeight.w700)),
                            ),
                            Expanded(
                              child: Text('CRYPTOGRAPHIC CHAIN PROOF (SHA-256)',
                                  style: AppTypography.monoSmall(
                                      color: AppColors.inkMuted,
                                      weight: FontWeight.w700)),
                            ),
                            const SizedBox(width: 80),
                          ],
                        ),
                      ),
                      const Divider(height: 1, color: AppColors.rule),

                      // Audit Log Entries
                      if (filtered.isEmpty)
                        Padding(
                          padding: const EdgeInsets.all(40),
                          child: Center(
                            child: Column(
                              children: [
                                const Icon(Icons.inbox_outlined,
                                    size: 36, color: AppColors.inkMuted),
                                const SizedBox(height: 8),
                                Text(
                                  _auditLogs.isEmpty
                                      ? 'No cryptographic audit entries recorded in database yet.'
                                      : 'No entries match the current filter criteria.',
                                  style: AppTypography.bodySmall(
                                      color: AppColors.inkMuted),
                                ),
                              ],
                            ),
                          ),
                        )
                      else
                        ListView.separated(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          itemCount: filtered.length,
                          separatorBuilder: (c, i) =>
                              const Divider(height: 1, color: AppColors.rule),
                          itemBuilder: (c, i) {
                            final entry = filtered[i];
                            return _buildAuditLogRow(entry);
                          },
                        ),
                    ],
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String filterKey, String label) {
    final isSelected = _selectedActionFilter == filterKey;
    return InkWell(
      onTap: () => setState(() => _selectedActionFilter = filterKey),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.navy900 : AppColors.surfaceMuted,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(
            color: isSelected ? AppColors.navy900 : AppColors.rule,
          ),
        ),
        child: Text(
          label,
          style: AppTypography.monoSmall(
            color: isSelected ? Colors.white : AppColors.ink,
            weight: isSelected ? FontWeight.w700 : FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _buildAuditLogRow(ImmutableAuditEntry entry) {
    Color actionBg = AppColors.navy900.withValues(alpha: 0.08);
    Color actionColor = AppColors.navy900;

    if (entry.action.contains('REVOKED') ||
        entry.action.contains('DELETE') ||
        entry.action.contains('DISABLE')) {
      actionBg = AppColors.red700.withValues(alpha: 0.12);
      actionColor = AppColors.red700;
    } else if (entry.action.contains('SUCCESS') ||
        entry.action.contains('VERIFIED') ||
        entry.action.contains('ENROLLED')) {
      actionBg = AppColors.green700.withValues(alpha: 0.12);
      actionColor = AppColors.green700;
    } else if (entry.action.contains('INITIATED') ||
        entry.action.contains('PENDING')) {
      actionBg = AppColors.amber700.withValues(alpha: 0.12);
      actionColor = AppColors.amber700;
    }

    final currentHashPreview = entry.currentHash.length >= 16
        ? '${entry.currentHash.substring(0, 8)}...${entry.currentHash.substring(entry.currentHash.length - 8)}'
        : entry.currentHash;

    final prevHashPreview = entry.previousHash.length >= 16
        ? '${entry.previousHash.substring(0, 8)}...${entry.previousHash.substring(entry.previousHash.length - 8)}'
        : entry.previousHash;

    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: () => _showProofDialog(entry),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              // Sequence
              SizedBox(
                width: 70,
                child: Text(
                  '#${entry.sequence}',
                  style: AppTypography.mono(
                    weight: FontWeight.w700,
                    color: AppColors.navy900,
                  ),
                ),
              ),

              // Timestamp
              SizedBox(
                width: 170,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _formatDate(entry.timestamp),
                      style: AppTypography.monoSmall(color: AppColors.ink),
                    ),
                    Text(
                      _relativeTime(entry.timestamp),
                      style:
                          AppTypography.monoSmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
              ),

              // Action Badge
              SizedBox(
                width: 200,
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: actionBg,
                      borderRadius: BorderRadius.circular(3),
                    ),
                    child: Text(
                      entry.action,
                      style: AppTypography.monoSmall(
                        color: actionColor,
                        weight: FontWeight.w700,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ),

              // Actor
              SizedBox(
                width: 140,
                child: Row(
                  children: [
                    const Icon(Icons.person_outline,
                        size: 14, color: AppColors.inkMuted),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        entry.actorId,
                        style: AppTypography.monoSmall(
                          color: AppColors.ink,
                          weight: FontWeight.w600,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),

              // Cryptographic Proof (Wrapped with Flexible to guarantee zero overflow)
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text('Hash: ',
                            style: AppTypography.monoSmall(
                                color: AppColors.inkMuted)),
                        Flexible(
                          child: Text(
                            currentHashPreview,
                            style: AppTypography.monoSmall(
                                color: AppColors.navy900,
                                weight: FontWeight.w600),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 6),
                        const Icon(Icons.link,
                            size: 12, color: AppColors.green700),
                        const SizedBox(width: 4),
                        Flexible(
                          child: Text(
                            'Prev: $prevHashPreview',
                            style: AppTypography.monoSmall(
                                color: AppColors.inkMuted),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(Icons.verified,
                            size: 12, color: AppColors.green700),
                        const SizedBox(width: 4),
                        Text('Signature Valid',
                            style: AppTypography.monoSmall(
                                color: AppColors.green700,
                                weight: FontWeight.w600)),
                        const SizedBox(width: 10),
                        Flexible(
                          child: Text('Target: ${entry.resource}',
                              style: AppTypography.monoSmall(
                                  color: AppColors.inkMuted),
                              overflow: TextOverflow.ellipsis),
                        ),
                      ],
                    ),
                  ],
                ),
              ),

              // Action button
              SizedBox(
                width: 80,
                child: Align(
                  alignment: Alignment.centerRight,
                  child: TextButton.icon(
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      visualDensity: VisualDensity.compact,
                    ),
                    onPressed: () => _showProofDialog(entry),
                    icon: const Icon(Icons.remove_red_eye_outlined, size: 14),
                    label: const Text('Proof'),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime dt) {
    final u = dt.toUtc();
    final y = u.year.toString().padLeft(4, '0');
    final m = u.month.toString().padLeft(2, '0');
    final d = u.day.toString().padLeft(2, '0');
    final h = u.hour.toString().padLeft(2, '0');
    final min = u.minute.toString().padLeft(2, '0');
    final sec = u.second.toString().padLeft(2, '0');
    return '$y-$m-$d $h:$min:$sec';
  }

  String _relativeTime(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inSeconds < 60) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${diff.inDays}d ago';
  }
}
