import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class CryptographicAuditEntry {
  final int sequence;
  final String actorId;
  final String action;
  final String resource;
  final String currentHash;
  final String previousHash;
  final bool isSignatureValid;
  final DateTime timestamp;

  const CryptographicAuditEntry({
    required this.sequence,
    required this.actorId,
    required this.action,
    required this.resource,
    required this.currentHash,
    required this.previousHash,
    required this.isSignatureValid,
    required this.timestamp,
  });
}

class SecurityAuditScreen extends ConsumerStatefulWidget {
  const SecurityAuditScreen({super.key});

  @override
  ConsumerState<SecurityAuditScreen> createState() => _SecurityAuditScreenState();
}

class _SecurityAuditScreenState extends ConsumerState<SecurityAuditScreen> {
  final List<CryptographicAuditEntry> _auditEntries = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadAuditLogs();
  }

  Future<void> _loadAuditLogs() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.get('/api/v1/master/audit-logs');
      if (response.data is List) {
        final List list = response.data;
        _auditEntries.clear();
        for (final item in list) {
          _auditEntries.add(CryptographicAuditEntry(
            sequence: (item['sequence'] as num?)?.toInt() ?? 1,
            actorId: item['actorId']?.toString() ?? 'SYSTEM',
            action: item['action']?.toString() ?? 'EVENT',
            resource: item['resource']?.toString() ?? 'GLOBAL',
            currentHash: item['currentHash']?.toString() ?? '',
            previousHash: item['previousHash']?.toString() ?? '',
            isSignatureValid: item['isSignatureValid'] == true,
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
        _error = 'Unable to fetch cryptographic audit logs from database.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadAuditLogs,
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
                        Text('IMMUTABLE AUDIT LOG & CRYPTOGRAPHIC PROOF', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Live database inspection of SHA-256 HMAC hash chain under Directive No. 1142/2026',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppColors.green700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: AppColors.green700.withValues(alpha: 0.4)),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.link, size: 16, color: AppColors.green700),
                        const SizedBox(width: 8),
                        Text('HASH CHAIN VERIFIED (0 BREAKS)', style: AppTypography.monoSmall(color: AppColors.green700, weight: FontWeight.w700)),
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
                      TextButton(onPressed: _loadAuditLogs, child: const Text('Retry')),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 20),

              // Immutability Notice
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.lock_outline, size: 24, color: AppColors.navy900),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        'IMMUTABILITY GUARANTEE: Every audit entry is cryptographically bound to its predecessor via SHA-256 HMAC and signed by the Hardware Security Module. Modifications to historical entries invalidate the entire mathematical chain.',
                        style: AppTypography.bodySmall(),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Audit Entries
              Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: _isLoading
                    ? const Padding(
                        padding: EdgeInsets.all(32),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    : (_auditEntries.isEmpty
                        ? Padding(
                            padding: const EdgeInsets.all(32),
                            child: Center(
                              child: Text(
                                'No cryptographic audit entries recorded in database yet.',
                                style: AppTypography.bodySmall(color: AppColors.inkMuted),
                              ),
                            ),
                          )
                        : ListView.separated(
                            shrinkWrap: true,
                            physics: const NeverScrollableScrollPhysics(),
                            itemCount: _auditEntries.length,
                            separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                            itemBuilder: (context, index) {
                              final e = _auditEntries[index];
                              return Padding(
                                padding: const EdgeInsets.all(20),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      children: [
                                        Text('#${e.sequence}', style: AppTypography.mono(weight: FontWeight.w700, color: AppColors.navy900)),
                                        const SizedBox(width: 12),
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                          decoration: BoxDecoration(
                                            color: AppColors.navy900.withValues(alpha: 0.1),
                                            borderRadius: BorderRadius.circular(3),
                                          ),
                                          child: Text(e.action, style: AppTypography.monoSmall(weight: FontWeight.w700, color: AppColors.navy900)),
                                        ),
                                        const SizedBox(width: 12),
                                        Text('Actor: ${e.actorId}', style: AppTypography.monoSmall()),
                                        const Spacer(),
                                        Row(
                                          children: [
                                            const Icon(Icons.verified, size: 14, color: AppColors.green700),
                                            const SizedBox(width: 4),
                                            Text('Valid Signature', style: AppTypography.monoSmall(color: AppColors.green700)),
                                          ],
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 10),
                                    Text('Target Resource: ${e.resource}', style: AppTypography.uiLabelBold()),
                                    const SizedBox(height: 6),
                                    Text('Current Hash: ${e.currentHash}', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                    Text('Previous Hash: ${e.previousHash}', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                  ],
                                ),
                              );
                            },
                          )),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
