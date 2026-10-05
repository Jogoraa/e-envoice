import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/government_credentials_service.dart';

class GovernmentCredentialsScreen extends ConsumerStatefulWidget {
  const GovernmentCredentialsScreen({super.key});

  @override
  ConsumerState<GovernmentCredentialsScreen> createState() =>
      _GovernmentCredentialsScreenState();
}

class _GovernmentCredentialsScreenState
    extends ConsumerState<GovernmentCredentialsScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  TenantGovernmentCredentialMetadataDto? _metadata;

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadMetadata();
  }

  Future<void> _loadMetadata() async {
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

  void _showConfigureCredentialsDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _ProvisionCredentialsDialog(
        currentMetadata: _metadata,
        onSuccess: () {
          _loadMetadata();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Ministry credentials provisioned securely to Hardware Vault.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  void _showRotateKeyDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _RotateApiKeyDialog(
        onSuccess: () {
          _loadMetadata();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('EIRS API Key rotated successfully.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Government Integration & MoR Credentials'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadMetadata,
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
                        onPressed: _loadMetadata,
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
                      _buildSecurityNoticeBanner(),
                      const SizedBox(height: 16),
                      _buildStatusSummaryCard(),
                      const SizedBox(height: 16),
                      _buildCredentialDetailsCard(),
                      const SizedBox(height: 16),
                      _buildCertificateCard(),
                      const SizedBox(height: 24),
                      _buildActionButtons(),
                    ],
                  ),
                ),
    );
  }

  Widget _buildSecurityNoticeBanner() {
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
          const Icon(Icons.lock_clock_outlined, color: AppColors.navy700, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Zero-Plaintext Vault Storage (Directive No. 1142/2026 Art. 12)',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Ministry of Revenues API Keys and Client Secrets are stored exclusively inside hardware '
                  'key vaults (HSM / AWS KMS / HashiCorp Vault). For security reasons, plaintext secrets are never '
                  'rendered in the user interface or returned via API after initial provisioning.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusSummaryCard() {
    final meta = _metadata;
    final isActive = meta?.credentialStatus == CredentialValidationStatus.active;
    final isPending = meta?.credentialStatus == CredentialValidationStatus.pendingVerification;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            CircleAvatar(
              radius: 24,
              backgroundColor: isActive
                  ? AppColors.success.withValues(alpha: 0.15)
                  : isPending
                      ? AppColors.warning.withValues(alpha: 0.15)
                      : AppColors.error.withValues(alpha: 0.15),
              child: Icon(
                isActive
                    ? Icons.verified_user
                    : isPending
                        ? Icons.hourglass_top
                        : Icons.gpp_bad,
                color: isActive
                    ? AppColors.success
                    : isPending
                        ? AppColors.warning
                        : AppColors.error,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Credential Integration Status', style: AppTypography.caption),
                  Text(
                    meta?.credentialStatus.label ?? 'Not Configured',
                    style: AppTypography.titleMedium.copyWith(
                      fontWeight: FontWeight.bold,
                      color: isActive
                          ? AppColors.success
                          : isPending
                              ? AppColors.warning
                              : AppColors.error,
                    ),
                  ),
                ],
              ),
            ),
            if (meta?.lastValidatedAt != null)
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text('Last Validated', style: AppTypography.caption),
                  Text(
                    _dateFormat.format(meta!.lastValidatedAt!),
                    style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.bold),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildCredentialDetailsCard() {
    final meta = _metadata;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.vpn_key_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('Ministry of Revenues Credentials', style: AppTypography.titleMedium),
              ],
            ),
            const Divider(height: 24),
            _infoRow('Ministry System Number (Sys#):', meta?.morSystemNumber ?? 'Not Assigned'),
            _infoRow('Taxpayer Seller TIN:', meta?.sellerTin ?? 'Not Configured'),
            _infoRow(
              'API Key Status:',
              meta?.hasMorSystemNumber == true ? 'Configured (Enclave Stored)' : 'Missing',
              isPill: true,
              pillColor: meta?.hasMorSystemNumber == true ? AppColors.success : AppColors.error,
            ),
            _infoRow(
              'Client Secret Status:',
              meta?.hasMorSystemNumber == true ? 'Configured (Enclave Stored)' : 'Missing',
              isPill: true,
              pillColor: meta?.hasMorSystemNumber == true ? AppColors.success : AppColors.error,
            ),
            _infoRow('Key Vault Version:', 'v${meta?.keyVaultVersion ?? 1}'),
          ],
        ),
      ),
    );
  }

  Widget _buildCertificateCard() {
    final meta = _metadata;
    final hasCert = meta?.hasInsaCertificate == true;
    final isExpired = meta?.isCertificateExpired == true;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.card_membership_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('INSA Digital Signature Certificate', style: AppTypography.titleMedium),
              ],
            ),
            const Divider(height: 24),
            _infoRow('Certificate Reference:', meta?.insaCertificateReference ?? 'None'),
            _infoRow('Certificate Serial Number:', meta?.certificateSerial ?? 'None'),
            _infoRow(
              'Certificate Expiry Date:',
              meta?.certificateExpiry != null ? _dateFormat.format(meta!.certificateExpiry!) : 'N/A',
            ),
            _infoRow(
              'Signature Readiness:',
              !hasCert
                  ? 'NOT CONFIGURED'
                  : isExpired
                      ? 'EXPIRED (${meta!.daysUntilExpiry} days ago)'
                      : 'READY (${meta!.daysUntilExpiry} days remaining)',
              isPill: true,
              pillColor: (!hasCert || isExpired) ? AppColors.error : AppColors.success,
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoRow(String label, String value, {bool isPill = false, Color? pillColor}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary)),
          if (isPill)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: (pillColor ?? AppColors.primary).withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                value,
                style: TextStyle(
                  color: pillColor ?? AppColors.primary,
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                ),
              ),
            )
          else
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

  Widget _buildActionButtons() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        OutlinedButton.icon(
          onPressed: _showRotateKeyDialog,
          icon: const Icon(Icons.refresh),
          label: const Text('Rotate API Key'),
        ),
        const SizedBox(width: 12),
        ElevatedButton.icon(
          onPressed: _showConfigureCredentialsDialog,
          icon: const Icon(Icons.security),
          label: const Text('Configure / Update Credentials'),
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
        ),
      ],
    );
  }
}

class _ProvisionCredentialsDialog extends ConsumerStatefulWidget {
  final TenantGovernmentCredentialMetadataDto? currentMetadata;
  final VoidCallback onSuccess;

  const _ProvisionCredentialsDialog({
    this.currentMetadata,
    required this.onSuccess,
  });

  @override
  ConsumerState<_ProvisionCredentialsDialog> createState() =>
      _ProvisionCredentialsDialogState();
}

class _ProvisionCredentialsDialogState
    extends ConsumerState<_ProvisionCredentialsDialog> {
  final _formKey = GlobalKey<FormState>();
  final _morSystemNumberController = TextEditingController();
  final _apiKeyController = TextEditingController();
  final _clientSecretController = TextEditingController();
  final _certRefController = TextEditingController();
  final _certSerialController = TextEditingController();
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    if (widget.currentMetadata?.morSystemNumber != null) {
      _morSystemNumberController.text = widget.currentMetadata!.morSystemNumber!;
    }
    if (widget.currentMetadata?.insaCertificateReference != null) {
      _certRefController.text = widget.currentMetadata!.insaCertificateReference!;
    }
    if (widget.currentMetadata?.certificateSerial != null) {
      _certSerialController.text = widget.currentMetadata!.certificateSerial!;
    }
    // ApiKey and ClientSecret deliberately left BLANK (Never pre-filled with secrets)
  }

  @override
  void dispose() {
    _morSystemNumberController.dispose();
    _apiKeyController.dispose();
    _clientSecretController.dispose();
    _certRefController.dispose();
    _certSerialController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(governmentCredentialsServiceProvider);
      await service.provisionCredentials(
        morSystemNumber: _morSystemNumberController.text.trim(),
        apiKey: _apiKeyController.text.trim(),
        clientSecret: _clientSecretController.text.trim(),
        insaCertificateReference: _certRefController.text.trim().isNotEmpty
            ? _certRefController.text.trim()
            : null,
        certificateSerial: _certSerialController.text.trim().isNotEmpty
            ? _certSerialController.text.trim()
            : null,
        certificateExpiry: DateTime.now().add(const Duration(days: 365)),
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to save credentials: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Configure Ministry Gateway Credentials'),
      content: SizedBox(
        width: 500,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Enter the Ministry of Revenues System Number and API credentials. '
                  'Values will be securely stored in the platform hardware key vault.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _morSystemNumberController,
                  decoration: const InputDecoration(
                    labelText: 'MoR Assigned System Number *',
                    hintText: 'e.g. MOR-SYS-10293',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _apiKeyController,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'MoR Gateway API Key *',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _clientSecretController,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'MoR Client Secret *',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _certRefController,
                  decoration: const InputDecoration(
                    labelText: 'INSA Certificate Reference (Optional)',
                    hintText: 'e.g. INSA-PKI-CERT-2026-004',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _certSerialController,
                  decoration: const InputDecoration(
                    labelText: 'Certificate Serial Number (Optional)',
                    hintText: 'e.g. 7A:B4:91:02:44:E1',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Save to Enclave Vault'),
        ),
      ],
    );
  }
}

class _RotateApiKeyDialog extends ConsumerStatefulWidget {
  final VoidCallback onSuccess;

  const _RotateApiKeyDialog({required this.onSuccess});

  @override
  ConsumerState<_RotateApiKeyDialog> createState() => _RotateApiKeyDialogState();
}

class _RotateApiKeyDialogState extends ConsumerState<_RotateApiKeyDialog> {
  final _formKey = GlobalKey<FormState>();
  final _newApiKeyController = TextEditingController();
  bool _isSubmitting = false;

  @override
  void dispose() {
    _newApiKeyController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(governmentCredentialsServiceProvider);
      await service.rotateApiKey(_newApiKeyController.text.trim());

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Rotation failed: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Rotate MoR API Key'),
      content: SizedBox(
        width: 450,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Rotating the API key updates the active cryptographic credential used for live EIRS transmission.',
                style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _newApiKeyController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'New Ministry API Key *',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Apply Key Rotation'),
        ),
      ],
    );
  }
}
