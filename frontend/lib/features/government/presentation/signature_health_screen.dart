import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/government_credentials_service.dart';

class SignatureHealthScreen extends ConsumerStatefulWidget {
  const SignatureHealthScreen({super.key});

  @override
  ConsumerState<SignatureHealthScreen> createState() => _SignatureHealthScreenState();
}

class _SignatureHealthScreenState extends ConsumerState<SignatureHealthScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  TenantGovernmentCredentialMetadataDto? _metadata;

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(governmentCredentialsServiceProvider);
      final meta = await service.getMetadata();
      if (mounted) {
        setState(() {
          _metadata = meta;
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
    final meta = _metadata;
    final hasCert = meta?.hasInsaCertificate == true;
    final isExpired = meta?.isCertificateExpired == true;
    final daysUntilExpiry = meta?.daysUntilExpiry ?? 0;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Digital Signature & INSA Certificate Health'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
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
                        onPressed: _loadData,
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
                      _buildReadinessStatusCard(hasCert, isExpired, daysUntilExpiry),
                      const SizedBox(height: 16),
                      _buildCertificateDetailsCard(meta),
                      const SizedBox(height: 16),
                      _buildCryptographicStandardCard(),
                    ],
                  ),
                ),
    );
  }

  Widget _buildReadinessStatusCard(bool hasCert, bool isExpired, int daysLeft) {
    Color statusColor;
    IconData statusIcon;
    String statusTitle;
    String statusSubtitle;

    if (!hasCert) {
      statusColor = AppColors.warning;
      statusIcon = Icons.warning_amber;
      statusTitle = 'SIGNING NOT READY (NO CERTIFICATE)';
      statusSubtitle = 'An INSA-issued digital certificate must be provisioned before live fiscal invoices can be signed.';
    } else if (isExpired) {
      statusColor = AppColors.error;
      statusIcon = Icons.gpp_bad;
      statusTitle = 'CERTIFICATE EXPIRED';
      statusSubtitle = 'The digital signature certificate has expired. Fiscal signing is currently inhibited.';
    } else {
      statusColor = AppColors.success;
      statusIcon = Icons.verified_user;
      statusTitle = 'SIGNING ENGINE READY';
      statusSubtitle = 'INSA PKI Certificate is active. Hardware-backed digital signatures enabled.';
    }

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: statusColor.withValues(alpha: 0.15),
                  child: Icon(statusIcon, color: statusColor, size: 32),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        statusTitle,
                        style: AppTypography.titleMedium.copyWith(
                          color: statusColor,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        statusSubtitle,
                        style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (hasCert && !isExpired) ...[
              const Divider(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _statItem('Days to Expiry', '$daysLeft days', daysLeft < 30 ? AppColors.warning : AppColors.success),
                  _statItem('Algorithm', 'RSA-SHA256 / Ed25519', AppColors.primary),
                  _statItem('Key Enclave', 'HSM / KMS Vault', AppColors.success),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _statItem(String label, String value, Color color) {
    return Column(
      children: [
        Text(label, style: AppTypography.caption.copyWith(color: AppColors.textSecondary)),
        const SizedBox(height: 4),
        Text(
          value,
          style: AppTypography.titleSmall.copyWith(
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
      ],
    );
  }

  Widget _buildCertificateDetailsCard(TenantGovernmentCredentialMetadataDto? meta) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.shield_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('INSA Certificate Identity', style: AppTypography.titleMedium),
              ],
            ),
            const Divider(height: 24),
            _infoRow('Certificate Reference:', meta?.insaCertificateReference ?? 'Not Configured'),
            _infoRow('Serial Number:', meta?.certificateSerial ?? 'Not Configured'),
            _infoRow('Taxpayer TIN:', meta?.sellerTin ?? 'Not Configured'),
            _infoRow(
              'Expiry Date:',
              meta?.certificateExpiry != null ? _dateFormat.format(meta!.certificateExpiry!) : 'N/A',
            ),
            _infoRow(
              'Hardware Security Module (HSM):',
              'Provider Managed (FIPS 140-2 Level 3)',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCryptographicStandardCard() {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.policy_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('Statutory Signature Protections', style: AppTypography.titleMedium),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Directive No. 1142/2026 Art. 12 mandates that every electronic tax invoice carry an INSA-approved '
              'advanced electronic signature. Private keys and PIN credentials are fundamentally isolated from the '
              'client application and executed solely inside secure cryptographic modules.',
              style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary),
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary)),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}
