import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/authority_investigation_service.dart';

class SystemIntegrityScreen extends ConsumerStatefulWidget {
  const SystemIntegrityScreen({super.key});

  @override
  ConsumerState<SystemIntegrityScreen> createState() => _SystemIntegrityScreenState();
}

class _SystemIntegrityScreenState extends ConsumerState<SystemIntegrityScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  SystemChecksumDto? _integrity;

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm:ss');

  @override
  void initState() {
    super.initState();
    _loadIntegrityData();
  }

  Future<void> _loadIntegrityData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(authorityInvestigationServiceProvider);
      final checksum = await service.getSystemChecksum();
      if (mounted) {
        setState(() {
          _integrity = checksum;
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
    final integ = _integrity;
    final isMatch = integ?.isMatch == true;
    final isMismatch = integ?.isMismatch == true;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('System Integrity & Certified Checksum Verification'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadIntegrityData,
          ),
        ],
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
                        onPressed: _loadIntegrityData,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _buildDirectiveNoticeCard(),
                      const SizedBox(height: 16),
                      _buildStatusVerificationBanner(integ, isMatch, isMismatch),
                      const SizedBox(height: 16),
                      _buildArtifactChecksumsCard(integ),
                      const SizedBox(height: 16),
                      _buildEnvironmentDigestCard(integ),
                    ],
                  ),
                ),
    );
  }

  Widget _buildDirectiveNoticeCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.navy700.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.navy700.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.verified, color: AppColors.navy700, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 10 & Art. 11 — Certified Build Integrity',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Under Ethiopian electronic invoicing accreditation, the production binary digest, Flyway database schema, '
                  'and container image must match the official cryptographic hash registered with the Ministry of Revenues '
                  'and INSA during accreditation testing. Any checksum mismatch indicates uncertified code modification.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusVerificationBanner(SystemChecksumDto? integ, bool isMatch, bool isMismatch) {
    Color bannerBg;
    Color iconColor;
    IconData icon;
    String title;
    String subtitle;

    if (isMismatch) {
      bannerBg = AppColors.error;
      iconColor = Colors.white;
      icon = Icons.dangerous;
      title = 'CRITICAL ALERT: ACCREDITATION CHECKSUM MISMATCH';
      subtitle = 'The active build SHA-256 does NOT match the Ministry of Revenues registered release hash!';
    } else if (isMatch) {
      bannerBg = AppColors.success;
      iconColor = Colors.white;
      icon = Icons.verified_user;
      title = 'CERTIFIED SYSTEM INTEGRITY VERIFIED (MATCH)';
      subtitle = 'All platform artifacts strictly match the accreditation hash registered with the Ministry of Revenues.';
    } else {
      bannerBg = AppColors.warning;
      iconColor = Colors.black87;
      icon = Icons.hourglass_empty;
      title = 'STATUS: NOT REGISTERED / AUDIT PENDING';
      subtitle = 'Development or staging environment build hash is pending formal authority benchmark registration.';
    }

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: bannerBg,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(icon, color: iconColor, size: 40),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.titleMedium.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  style: AppTypography.bodySmall.copyWith(
                    color: Colors.white.withValues(alpha: 0.9),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildArtifactChecksumsCard(SystemChecksumDto? integ) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.fingerprint, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('Cryptographic Artifact Hashes (SHA-256)', style: AppTypography.titleMedium),
              ],
            ),
            const Divider(height: 24),
            _hashRow('Registered MoR Benchmark Hash:', integ?.registeredChecksum ?? 'N/A', isHighlight: true),
            _hashRow('Backend Spring Boot Artifact SHA-256:', integ?.backendSha256 ?? 'Calculating...'),
            _hashRow('Frontend Flutter Bundle SHA-256:', integ?.frontendBuildSha256 ?? 'Calculating...'),
            _hashRow('Container Image Digest:', integ?.containerDigest ?? 'sha256:docker.io/ut-invoice/prod@...'),
          ],
        ),
      ),
    );
  }

  Widget _buildEnvironmentDigestCard(SystemChecksumDto? integ) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.storage_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('Release & Schema Metadata', style: AppTypography.titleMedium),
              ],
            ),
            const Divider(height: 24),
            _metaRow('Release Version:', integ?.version ?? '1.0.0-PROD'),
            _metaRow('Git Commit SHA:', integ?.gitSha ?? 'N/A'),
            _metaRow('Flyway Database Schema Version:', integ?.databaseSchemaVersion ?? 'V18'),
            _metaRow(
              'Build Timestamp:',
              integ?.buildTimestamp != null ? _dateFormat.format(integ!.buildTimestamp) : 'N/A',
            ),
          ],
        ),
      ),
    );
  }

  Widget _hashRow(String label, String hash, {bool isHighlight = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppTypography.caption.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isHighlight
                  ? AppColors.primary.withValues(alpha: 0.1)
                  : AppColors.background,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: isHighlight ? AppColors.primary : AppColors.divider),
            ),
            child: SelectableText(
              hash,
              style: TextStyle(
                fontFamily: 'monospace',
                fontSize: 12,
                fontWeight: isHighlight ? FontWeight.bold : FontWeight.normal,
                color: isHighlight ? AppColors.primary : AppColors.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _metaRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary)),
          Text(value, style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
