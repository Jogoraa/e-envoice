import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class PlatformConfigScreen extends ConsumerStatefulWidget {
  const PlatformConfigScreen({super.key});

  @override
  ConsumerState<PlatformConfigScreen> createState() => _PlatformConfigScreenState();
}

class _PlatformConfigScreenState extends ConsumerState<PlatformConfigScreen> {
  bool _isLoading = true;
  String? _error;

  String _governingDirective = 'Ministry of Revenues Directive No. 1142/2026';
  String _standardVatRate = '15.00% (Certified TaxEngine v2.4)';
  String _offlineBufferingCeiling = '72 Hours Strict (Automatic Non-Sync Flagging)';
  String _sequenceGeneration = 'Authoritative Sequence Server (PostgreSQL High-Watermark)';
  String _signatureAlgorithm = 'ECDSA with SHA-256 (secp256r1 via Cloud HSM)';
  bool _enforceSingleFlightRefresh = true;
  bool _outboxExponentialBackoff = true;
  bool _requireHardwareMfa = true;
  bool _automatedDeltaReconciliation = true;

  @override
  void initState() {
    super.initState();
    _loadConfig();
  }

  Future<void> _loadConfig() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.get('/api/v1/master/config');
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        setState(() {
          _governingDirective = data['governingDirective']?.toString() ?? _governingDirective;
          _standardVatRate = data['standardVatRate']?.toString() ?? _standardVatRate;
          _offlineBufferingCeiling = data['offlineBufferingCeiling']?.toString() ?? _offlineBufferingCeiling;
          _sequenceGeneration = data['invoiceSequenceGeneration']?.toString() ?? _sequenceGeneration;
          _signatureAlgorithm = data['digitalSignatureAlgorithm']?.toString() ?? _signatureAlgorithm;
          _enforceSingleFlightRefresh = data['enforceSingleFlightRefresh'] == true;
          _outboxExponentialBackoff = data['outboxExponentialBackoff'] == true;
          _requireHardwareMfa = data['requireHardwareMfa'] == true;
          _automatedDeltaReconciliation = data['automatedDeltaReconciliation'] == true;
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _error = 'Unable to fetch platform configuration from database.';
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
        onRefresh: _loadConfig,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('PLATFORM CONFIGURATION & COMPLIANCE RULES', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'Backend-authoritative regulatory parameters and platform runtime switches from database',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                  ElevatedButton.icon(
                    onPressed: _loadConfig,
                    icon: const Icon(Icons.refresh, size: 16),
                    label: const Text('Refresh'),
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
                      TextButton(onPressed: _loadConfig, child: const Text('Retry')),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),

              // Fiscal Law & Tax Engine Governance Notice
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.navy900.withValues(alpha: 0.06),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.navy900.withValues(alpha: 0.2)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.account_balance, size: 24, color: AppColors.navy900),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        'REGULATORY AUTHORITY NOTICE: Ethiopian tax rates (15% Standard VAT), rounding algorithms, and fiscal sequence allocation are permanently anchored in the certified backend TaxEngine. Frontend interfaces cannot override statutory parameters.',
                        style: AppTypography.bodySmall(color: AppColors.ink).copyWith(fontWeight: FontWeight.w500),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Section 1: Read-Only Statutory Parameters
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
                    Text('STATUTORY FISCAL PARAMETERS (READ-ONLY)', style: AppTypography.h2()),
                    const SizedBox(height: 16),
                    _buildParamRow('Governing Directive', _governingDirective, isLocked: true),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildParamRow('Standard VAT Rate', _standardVatRate, isLocked: true),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildParamRow('Offline Buffering Ceiling', _offlineBufferingCeiling, isLocked: true),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildParamRow('Invoice Sequence Generation', _sequenceGeneration, isLocked: true),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildParamRow('Digital Signature Algorithm', _signatureAlgorithm, isLocked: true),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Section 2: Platform Runtime Controls
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
                    Text('PLATFORM RUNTIME SWITCHES', style: AppTypography.h2()),
                    const SizedBox(height: 16),
                    _buildSwitchRow('Enforce Single-Flight Token Refresh on All Gateways', _enforceSingleFlightRefresh),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildSwitchRow('Outbox Exponential Backoff with Jitter (30s - 300s)', _outboxExponentialBackoff),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildSwitchRow('Require Hardware MFA for all Master Admin Sessions', _requireHardwareMfa),
                    const Divider(height: 20, color: AppColors.rule),
                    _buildSwitchRow('Automated Delta Reconciliation for 72h Batches', _automatedDeltaReconciliation),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildParamRow(String label, String value, {bool isLocked = false}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 260,
          child: Row(
            children: [
              Text(label, style: AppTypography.uiLabel(color: AppColors.inkMuted)),
              if (isLocked) ...[
                const SizedBox(width: 6),
                const Icon(Icons.lock, size: 14, color: AppColors.inkMuted),
              ],
            ],
          ),
        ),
        Expanded(
          child: Text(value, style: AppTypography.mono(weight: FontWeight.w600)),
        ),
      ],
    );
  }

  Widget _buildSwitchRow(String label, bool value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Expanded(child: Text(label, style: AppTypography.uiLabel())),
        Switch(
          value: value,
          onChanged: (_) {}, // Backend controlled
          activeThumbColor: AppColors.navy900,
        ),
      ],
    );
  }
}
