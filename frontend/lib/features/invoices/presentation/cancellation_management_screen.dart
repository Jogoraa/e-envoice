import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/cancellation_service.dart';

class CancellationManagementScreen extends ConsumerStatefulWidget {
  const CancellationManagementScreen({super.key});

  @override
  ConsumerState<CancellationManagementScreen> createState() =>
      _CancellationManagementScreenState();
}

class _CancellationManagementScreenState
    extends ConsumerState<CancellationManagementScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = true;
  String? _errorMessage;
  List<CancellationRequestDto> _allRequests = [];

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 7, vsync: this);
    _loadCancellations();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadCancellations() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(cancellationServiceProvider);
      final requests = await service.listCancellations();
      if (mounted) {
        setState(() {
          _allRequests = requests;
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

  List<CancellationRequestDto> _filterByStatus(CancellationStatus status) {
    return _allRequests.where((r) => r.status == status).toList();
  }

  void _showNewCancellationDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _NewCancellationDialog(
        onSuccess: () {
          _loadCancellations();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Cancellation request dispatched to Ministry of Revenues.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  void _showEvidenceUploadDialog(CancellationRequestDto request) {
    showDialog(
      context: context,
      builder: (ctx) => _EvidenceUploadDialog(
        cancellationId: request.id,
        onSuccess: () {
          _loadCancellations();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Regulatory evidence submitted with cryptographic SHA-256 digest.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  void _showDetailModal(CancellationRequestDto request) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _CancellationDetailSheet(
        request: request,
        onUploadEvidence: () {
          Navigator.pop(ctx);
          _showEvidenceUploadDialog(request);
        },
        onRefresh: () {
          Navigator.pop(ctx);
          _loadCancellations();
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final evidenceDemandedCount = _filterByStatus(CancellationStatus.evidenceRequested).length;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Statutory Invoice Cancellations (Directive Art. 14 & 15)'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadCancellations,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: [
            Tab(text: 'All (${_allRequests.length})'),
            Tab(text: 'Requested (${_filterByStatus(CancellationStatus.requested).length})'),
            Tab(text: 'Under Review (${_filterByStatus(CancellationStatus.underReview).length})'),
            Tab(
              child: Row(
                children: [
                  Text('Evidence Demanded ($evidenceDemandedCount)'),
                  if (evidenceDemandedCount > 0) ...[
                    const SizedBox(width: 4),
                    const Icon(Icons.warning, color: AppColors.error, size: 14),
                  ],
                ],
              ),
            ),
            Tab(text: 'Evidence Submitted (${_filterByStatus(CancellationStatus.evidenceSubmitted).length})'),
            Tab(text: 'Approved (${_filterByStatus(CancellationStatus.approved).length})'),
            Tab(text: 'Rejected (${_filterByStatus(CancellationStatus.rejected).length})'),
          ],
        ),
      ),
      body: Column(
        children: [
          _buildDirectiveNoticeBanner(),
          Expanded(
            child: _isLoading
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
                              onPressed: _loadCancellations,
                              child: const Text('Retry'),
                            ),
                          ],
                        ),
                      )
                    : TabBarView(
                        controller: _tabController,
                        children: [
                          _buildRequestList(_allRequests),
                          _buildRequestList(_filterByStatus(CancellationStatus.requested)),
                          _buildRequestList(_filterByStatus(CancellationStatus.underReview)),
                          _buildRequestList(_filterByStatus(CancellationStatus.evidenceRequested)),
                          _buildRequestList(_filterByStatus(CancellationStatus.evidenceSubmitted)),
                          _buildRequestList(_filterByStatus(CancellationStatus.approved)),
                          _buildRequestList(_filterByStatus(CancellationStatus.rejected)),
                        ],
                      ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showNewCancellationDialog,
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.cancel_presentation),
        label: const Text('Request Cancellation'),
      ),
    );
  }

  Widget _buildDirectiveNoticeBanner() {
    return Container(
      padding: const EdgeInsets.all(16),
      color: AppColors.primary.withValues(alpha: 0.08),
      child: Row(
        children: [
          const Icon(Icons.gavel, color: AppColors.primary, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 14 — Zero-Unilateral Cancellation Rule',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Once registered with the Ministry of Revenues, an invoice CANNOT be cancelled locally. '
                  'A formal cancellation request must be submitted to the EIRS Gateway. When evidence is demanded, '
                  'it must be uploaded within 48 hours or the request expires automatically.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRequestList(List<CancellationRequestDto> requests) {
    if (requests.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.assignment_turned_in_outlined, size: 64, color: AppColors.textTertiary),
            const SizedBox(height: 16),
            Text('No cancellation requests in this status', style: AppTypography.titleMedium),
            const SizedBox(height: 8),
            Text(
              'Use "Request Cancellation" to initiate formal voiding of an erroneous invoice.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: requests.length,
      itemBuilder: (ctx, index) {
        final req = requests[index];
        final isEvidenceDemanded = req.status == CancellationStatus.evidenceRequested;
        final remainingTime = req.remainingEvidenceTime;

        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          child: InkWell(
            borderRadius: BorderRadius.circular(8),
            onTap: () => _showDetailModal(req),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Invoice #${req.invoiceNumber ?? req.invoiceId}',
                        style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                      ),
                      _buildStatusBadge(req.status),
                    ],
                  ),
                  const SizedBox(height: 6),
                  if (req.originalIrn != null)
                    Text('Original IRN: ${req.originalIrn}', style: AppTypography.caption),
                  Text('Reason: ${req.reasonCode.label}', style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.w500)),
                  Text(
                    req.reasonDescription,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                  ),
                  const SizedBox(height: 8),
                  if (isEvidenceDemanded && remainingTime != null) ...[
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.error.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: AppColors.error),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.timer, color: AppColors.error, size: 16),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              'EVIDENCE DEADLINE: ${remainingTime.inHours}h ${remainingTime.inMinutes % 60}m remaining (48h Statutory SLA)',
                              style: const TextStyle(
                                color: AppColors.error,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          ElevatedButton(
                            onPressed: () => _showEvidenceUploadDialog(req),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.error,
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              minimumSize: Size.zero,
                            ),
                            child: const Text('Upload Evidence', style: TextStyle(fontSize: 11)),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Requested: ${_dateFormat.format(req.requestedAt)}', style: AppTypography.caption),
                      if (req.attachments.isNotEmpty)
                        Text('${req.attachments.length} attachment(s) uploaded', style: AppTypography.caption.copyWith(color: AppColors.primary)),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatusBadge(CancellationStatus status) {
    Color bg;
    Color fg;

    switch (status) {
      case CancellationStatus.approved:
        bg = AppColors.success.withValues(alpha: 0.15);
        fg = AppColors.success;
        break;
      case CancellationStatus.rejected:
        bg = AppColors.error.withValues(alpha: 0.15);
        fg = AppColors.error;
        break;
      case CancellationStatus.evidenceRequested:
        bg = AppColors.error.withValues(alpha: 0.15);
        fg = AppColors.error;
        break;
      case CancellationStatus.evidenceSubmitted:
        bg = AppColors.secondary.withValues(alpha: 0.15);
        fg = AppColors.secondary;
        break;
      case CancellationStatus.underReview:
        bg = AppColors.warning.withValues(alpha: 0.15);
        fg = AppColors.warning;
        break;
      case CancellationStatus.requested:
        bg = AppColors.primary.withValues(alpha: 0.15);
        fg = AppColors.primary;
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(10)),
      child: Text(
        status.label,
        style: TextStyle(color: fg, fontSize: 10, fontWeight: FontWeight.bold),
      ),
    );
  }
}

class _NewCancellationDialog extends ConsumerStatefulWidget {
  final VoidCallback onSuccess;

  const _NewCancellationDialog({required this.onSuccess});

  @override
  ConsumerState<_NewCancellationDialog> createState() => _NewCancellationDialogState();
}

class _NewCancellationDialogState extends ConsumerState<_NewCancellationDialog> {
  final _formKey = GlobalKey<FormState>();
  final _invoiceIdController = TextEditingController();
  final _descriptionController = TextEditingController();
  CancellationReasonCode _reasonCode = CancellationReasonCode.wrongItemOrQuantity;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _invoiceIdController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(cancellationServiceProvider);
      await service.requestCancellation(
        invoiceId: _invoiceIdController.text.trim(),
        reasonCode: _reasonCode,
        reasonDescription: _descriptionController.text.trim(),
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Cancellation request failed: $e'), backgroundColor: AppColors.error),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Request Fiscal Invoice Cancellation'),
      content: SizedBox(
        width: 480,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Directive No. 1142/2026 Art. 14 mandates that cancellation requests must cite a valid statutory reason. '
                'The Ministry of Revenues may demand evidentiary documentation before approving cancellation.',
                style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _invoiceIdController,
                decoration: const InputDecoration(
                  labelText: 'Invoice Number or IRN *',
                  hintText: 'e.g. INV-2026-00412 or IRN-ET-2026...',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<CancellationReasonCode>(
                value: _reasonCode,
                decoration: const InputDecoration(
                  labelText: 'Statutory Reason Code *',
                  border: OutlineInputBorder(),
                ),
                items: CancellationReasonCode.values.map((code) {
                  return DropdownMenuItem(
                    value: code,
                    child: Text(code.label, style: const TextStyle(fontSize: 12)),
                  );
                }).toList(),
                onChanged: (val) {
                  if (val != null) setState(() => _reasonCode = val);
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _descriptionController,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Detailed Explanation for MoR Audit *',
                  hintText: 'Describe why this registered transaction must be revoked...',
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
              : const Text('Submit to MoR EIRS'),
        ),
      ],
    );
  }
}

class _EvidenceUploadDialog extends ConsumerStatefulWidget {
  final String cancellationId;
  final VoidCallback onSuccess;

  const _EvidenceUploadDialog({
    required this.cancellationId,
    required this.onSuccess,
  });

  @override
  ConsumerState<_EvidenceUploadDialog> createState() => _EvidenceUploadDialogState();
}

class _EvidenceUploadDialogState extends ConsumerState<_EvidenceUploadDialog> {
  final _fileNameController = TextEditingController(text: 'signed_cancellation_affidavit.pdf');
  final _fileHashController = TextEditingController(
    text: 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e',
  );
  String _attachmentType = 'COMMERCIAL_PROOF';
  final int _fileSizeBytes = 245800; // ~240 KB
  bool _isSubmitting = false;

  @override
  void dispose() {
    _fileNameController.dispose();
    _fileHashController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(cancellationServiceProvider);
      await service.submitEvidence(
        cancellationId: widget.cancellationId,
        attachmentType: _attachmentType,
        fileReference: 's3://ut-invoice-compliance/cancellations/${widget.cancellationId}/${_fileNameController.text.trim()}',
        fileHash: _fileHashController.text.trim(),
        fileName: _fileNameController.text.trim(),
        fileSizeBytes: _fileSizeBytes,
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Evidence upload failed: $e'), backgroundColor: AppColors.error),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Upload Statutory Cancellation Evidence'),
      content: SizedBox(
        width: 480,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Upload supporting documentation (commercial dispute resolution, returned delivery note, or bank reversal proof). '
              'Directive Art. 14(3) requires SHA-256 integrity verification.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _fileNameController,
              decoration: const InputDecoration(labelText: 'Evidence File Name *', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: _attachmentType,
              decoration: const InputDecoration(labelText: 'Evidence Category *', border: OutlineInputBorder()),
              items: const [
                DropdownMenuItem(value: 'COMMERCIAL_PROOF', child: Text('Commercial Agreement / Mutual Cancellation')),
                DropdownMenuItem(value: 'RETURNED_DELIVERY_NOTE', child: Text('Signed Goods Return Delivery Note')),
                DropdownMenuItem(value: 'BANK_CREDIT_ADVICE', child: Text('Bank Reversal / Refund Slip')),
                DropdownMenuItem(value: 'POLICE_OR_LEGAL_AFFIDAVIT', child: Text('Police / Legal Technical Affidavit')),
              ],
              onChanged: (val) {
                if (val != null) setState(() => _attachmentType = val);
              },
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _fileHashController,
              decoration: const InputDecoration(labelText: 'SHA-256 Checksum *', border: OutlineInputBorder()),
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
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Submit & Seal Evidence'),
        ),
      ],
    );
  }
}

class _CancellationDetailSheet extends ConsumerWidget {
  final CancellationRequestDto request;
  final VoidCallback onUploadEvidence;
  final VoidCallback onRefresh;

  const _CancellationDetailSheet({
    required this.request,
    required this.onUploadEvidence,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dateFormat = DateFormat('dd/MM/yyyy HH:mm');
    final isEvidenceDemanded = request.status == CancellationStatus.evidenceRequested;

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Cancellation Audit Record', style: AppTypography.titleMedium),
              IconButton(icon: const Icon(Icons.close), onPressed: () => Navigator.pop(context)),
            ],
          ),
          const Divider(height: 24),
          _detailRow('Case ID:', request.id),
          _detailRow('Invoice Number:', request.invoiceNumber ?? request.invoiceId),
          if (request.originalIrn != null) _detailRow('Original IRN:', request.originalIrn!),
          _detailRow('Status:', request.status.label),
          _detailRow('Reason Category:', request.reasonCode.label),
          _detailRow('Description:', request.reasonDescription),
          _detailRow('Requested At:', dateFormat.format(request.requestedAt)),
          if (request.evidenceDeadline != null)
            _detailRow('Evidence Deadline:', dateFormat.format(request.evidenceDeadline!)),
          if (request.cancellationReference != null)
            _detailRow('MoR Cancellation Ref:', request.cancellationReference!),
          if (request.rejectionReason != null)
            _detailRow('Rejection Reason:', request.rejectionReason!),
          const SizedBox(height: 16),
          if (request.attachments.isNotEmpty) ...[
            Text('Submitted Evidentiary Attachments (${request.attachments.length})', style: AppTypography.titleSmall),
            const SizedBox(height: 8),
            ...request.attachments.map(
              (att) => ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.attachment, color: AppColors.primary),
                title: Text(att.fileName, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text('SHA-256: ${att.fileHash.substring(0, 16)}... • ${att.uploadedAt}'),
              ),
            ),
          ],
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              if (isEvidenceDemanded)
                ElevatedButton.icon(
                  onPressed: onUploadEvidence,
                  icon: const Icon(Icons.upload_file),
                  label: const Text('Upload Evidence Document'),
                  style: ElevatedButton.styleFrom(backgroundColor: AppColors.error),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _detailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary)),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }
}
