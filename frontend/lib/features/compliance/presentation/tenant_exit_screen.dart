import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/portability_exit_service.dart';

class TenantExitScreen extends ConsumerStatefulWidget {
  const TenantExitScreen({super.key});

  @override
  ConsumerState<TenantExitScreen> createState() => _TenantExitScreenState();
}

class _TenantExitScreenState extends ConsumerState<TenantExitScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  TenantExitRequestDto? _activeRequest;
  RetentionClassificationSummaryDto? _retentionSummary;

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
      final service = ref.read(portabilityExitServiceProvider);
      // Fetch retention summary
      final retention = await service.getRetentionSummary('current-tenant');
      if (mounted) {
        setState(() {
          _retentionSummary = retention;
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

  void _showInitiateExitDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _InitiateExitDialog(
        onSuccess: (req) {
          setState(() => _activeRequest = req);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tenant portability and exit process initiated.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  void _showVerifyChecksumDialog() {
    if (_activeRequest == null) return;
    showDialog(
      context: context,
      builder: (ctx) => _VerifyChecksumDialog(
        exitRequestId: _activeRequest!.id,
        onSuccess: (updated) {
          setState(() => _activeRequest = updated);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Export archive SHA-256 verified successfully!'),
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
        title: const Text('Data Portability & Statutory Tenant Exit'),
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
                      _buildDirectiveNoticeCard(),
                      const SizedBox(height: 16),
                      _buildRetentionClassificationCard(),
                      const SizedBox(height: 16),
                      _buildExitTimelineCard(),
                      const SizedBox(height: 24),
                      _buildActionSection(),
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
          const Icon(Icons.account_balance, color: AppColors.navy700, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 24 & Art. 25 — Data Portability & Retention',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Taxpayers have the legal right to export their complete invoicing ledger and migrate to '
                  'another certified e-invoicing provider. However, fiscal invoices must be retained for ten (10) years '
                  'under Ethiopian tax law. No "delete all" function is permitted.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRetentionClassificationCard() {
    final ret = _retentionSummary;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.archive_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('Statutory Data Retention Breakdown', style: AppTypography.titleMedium),
              ],
            ),
            const Divider(height: 24),
            Row(
              children: [
                Expanded(
                  child: _retentionCategory(
                    'Retained By Law (10 Yrs)',
                    '${ret?.retainByLawCount ?? 0} Records',
                    'Tax invoices, credit notes, fiscal logs',
                    AppColors.navy700,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _retentionCategory(
                    'Legal Hold',
                    '${ret?.legalHoldCount ?? 0} Records',
                    'Audited or disputed transactions',
                    AppColors.warning,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _retentionCategory(
                    'Purge-Eligible',
                    '${ret?.purgeEligibleCount ?? 0} Records',
                    'Operational caches, transient buffers',
                    AppColors.success,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _retentionCategory(String title, String count, String desc, Color color) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppTypography.caption.copyWith(color: color, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text(count, style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 2),
          Text(desc, style: AppTypography.caption.copyWith(color: AppColors.textSecondary, fontSize: 10)),
        ],
      ),
    );
  }

  Widget _buildExitTimelineCard() {
    final currentStatus = _activeRequest?.status ?? TenantExitStatus.initiated;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(Icons.timeline, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Text('8-Step Statutory Exit Governance Timeline', style: AppTypography.titleMedium),
                  ],
                ),
                if (_activeRequest != null)
                  Chip(
                    label: Text(_activeRequest!.status.label),
                    backgroundColor: AppColors.primary.withValues(alpha: 0.1),
                    labelStyle: const TextStyle(color: AppColors.primary, fontSize: 11, fontWeight: FontWeight.bold),
                  ),
              ],
            ),
            const Divider(height: 24),
            ...TenantExitStatus.values.map((step) {
              final isPassed = _activeRequest != null && step.index <= currentStatus.index;
              final isCurrent = _activeRequest != null && step.index == currentStatus.index;

              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 12,
                      backgroundColor: isPassed
                          ? AppColors.success
                          : isCurrent
                              ? AppColors.primary
                              : AppColors.divider,
                      child: isPassed
                          ? const Icon(Icons.check, size: 14, color: Colors.white)
                          : Text(
                              '${step.index + 1}',
                              style: TextStyle(
                                fontSize: 10,
                                color: isCurrent ? Colors.white : AppColors.textSecondary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        step.label,
                        style: AppTypography.bodySmall.copyWith(
                          fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                          color: isPassed || isCurrent ? AppColors.textPrimary : AppColors.textSecondary,
                        ),
                      ),
                    ),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  Widget _buildActionSection() {
    if (_activeRequest == null) {
      return Center(
        child: ElevatedButton.icon(
          onPressed: _showInitiateExitDialog,
          icon: const Icon(Icons.exit_to_app),
          label: const Text('Initiate Formal Exit & Export Request'),
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.navy900,
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          ),
        ),
      );
    }

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Active Exit Case: ${_activeRequest!.id}', style: AppTypography.titleSmall),
            const SizedBox(height: 4),
            Text(
              'Destination: ${_activeRequest!.destinationProvider} • Requested: ${_dateFormat.format(_activeRequest!.requestedAt)}',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                OutlinedButton.icon(
                  onPressed: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Downloading encrypted export archive (.zip)...')),
                    );
                  },
                  icon: const Icon(Icons.download),
                  label: const Text('Download Archive Package'),
                ),
                const SizedBox(width: 12),
                ElevatedButton.icon(
                  onPressed: _showVerifyChecksumDialog,
                  icon: const Icon(Icons.verified),
                  label: const Text('Verify Package Checksum'),
                  style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _InitiateExitDialog extends ConsumerStatefulWidget {
  final ValueChanged<TenantExitRequestDto> onSuccess;

  const _InitiateExitDialog({required this.onSuccess});

  @override
  ConsumerState<_InitiateExitDialog> createState() => _InitiateExitDialogState();
}

class _InitiateExitDialogState extends ConsumerState<_InitiateExitDialog> {
  final _formKey = GlobalKey<FormState>();
  final _destinationController = TextEditingController(text: 'SAFARICOM_ET_FINTECH');
  final _reasonController = TextEditingController();
  bool _confirmedRetention = false;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _destinationController.dispose();
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (!_confirmedRetention) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('You must acknowledge statutory 10-year data retention.'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(portabilityExitServiceProvider);
      final req = await service.requestExit(
        destinationProvider: _destinationController.text.trim(),
        reason: _reasonController.text.trim(),
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess(req);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to initiate exit: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Initiate Statutory Tenant Exit'),
      content: SizedBox(
        width: 500,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'This will prepare a cryptographically sealed archive of all taxpayer invoices, '
                'registers, and audit logs according to Directive No. 1142/2026 Art. 24.',
                style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _destinationController,
                decoration: const InputDecoration(
                  labelText: 'Destination Certified Provider / System *',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _reasonController,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Reason for Exit / Portability *',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: _confirmedRetention,
                onChanged: (val) => setState(() => _confirmedRetention = val ?? false),
                title: Text(
                  'I understand that past tax invoices will be retained for 10 years per Ministry of Revenues regulations.',
                  style: AppTypography.caption.copyWith(fontWeight: FontWeight.bold),
                ),
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
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.navy900),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Submit Formal Exit Request'),
        ),
      ],
    );
  }
}

class _VerifyChecksumDialog extends ConsumerStatefulWidget {
  final String exitRequestId;
  final ValueChanged<TenantExitRequestDto> onSuccess;

  const _VerifyChecksumDialog({
    required this.exitRequestId,
    required this.onSuccess,
  });

  @override
  ConsumerState<_VerifyChecksumDialog> createState() => _VerifyChecksumDialogState();
}

class _VerifyChecksumDialogState extends ConsumerState<_VerifyChecksumDialog> {
  final _checksumController = TextEditingController(
    text: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  );
  bool _isSubmitting = false;

  @override
  void dispose() {
    _checksumController.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(portabilityExitServiceProvider);
      final updated = await service.verifyArchive(
        exitRequestId: widget.exitRequestId,
        archiveChecksum: _checksumController.text.trim(),
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess(updated);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Checksum verification failed: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Verify Export SHA-256 Checksum'),
      content: SizedBox(
        width: 500,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Input the SHA-256 digest of the downloaded export package to cryptographically prove archive completeness.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _checksumController,
              decoration: const InputDecoration(
                labelText: 'SHA-256 Checksum *',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _verify,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Verify Checksum'),
        ),
      ],
    );
  }
}
