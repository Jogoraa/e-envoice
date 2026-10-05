import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/authority_investigation_service.dart';

class AuthorityInvestigationPortalScreen extends ConsumerStatefulWidget {
  const AuthorityInvestigationPortalScreen({super.key});

  @override
  ConsumerState<AuthorityInvestigationPortalScreen> createState() =>
      _AuthorityInvestigationPortalScreenState();
}

class _AuthorityInvestigationPortalScreenState
    extends ConsumerState<AuthorityInvestigationPortalScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _caseRefController = TextEditingController(text: 'MOR-INV-2026-');
  final _reasonController = TextEditingController(text: 'Directive 1142/2026 Art. 24 Compliance Audit');
  final _tinController = TextEditingController();
  final _nameController = TextEditingController();

  bool _isSearching = false;
  String? _errorMessage;
  List<AuthorityCustomerDto> _searchResults = [];
  List<AuthorityExportJobDto> _exportJobs = [];

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadExportJobs();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _caseRefController.dispose();
    _reasonController.dispose();
    _tinController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _loadExportJobs() async {
    try {
      final service = ref.read(authorityInvestigationServiceProvider);
      final jobs = await service.listExportJobs();
      if (mounted) {
        setState(() => _exportJobs = jobs);
      }
    } catch (_) {}
  }

  Future<void> _performSearch() async {
    final caseRef = _caseRefController.text.trim();
    final reason = _reasonController.text.trim();

    if (caseRef.isEmpty || reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Tax Authority Case Reference and Statutory Reason are strictly mandatory!'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() {
      _isSearching = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(authorityInvestigationServiceProvider);
      final results = await service.searchCustomers(
        tin: _tinController.text.trim().isNotEmpty ? _tinController.text.trim() : null,
        legalName: _nameController.text.trim().isNotEmpty ? _nameController.text.trim() : null,
        caseReference: caseRef,
        reason: reason,
      );

      if (mounted) {
        setState(() {
          _searchResults = results;
          _isSearching = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString();
          _isSearching = false;
        });
      }
    }
  }

  void _showNewExportJobDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _CreateExportJobDialog(
        initialCaseRef: _caseRefController.text.trim(),
        initialReason: _reasonController.text.trim(),
        onSuccess: () {
          _loadExportJobs();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Encrypted authority audit export job created.'),
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
        title: const Text('Tax Authority & Regulatory Investigation Portal'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: [
            Tab(text: 'Target Cross-Examination (${_searchResults.length})'),
            Tab(text: 'Encrypted Export Jobs (${_exportJobs.length})'),
          ],
        ),
      ),
      body: Column(
        children: [
          _buildAuthorityAuditNotice(),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _buildSearchTab(),
                _buildExportsTab(),
              ],
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showNewExportJobDialog,
        backgroundColor: AppColors.navy900,
        icon: const Icon(Icons.security),
        label: const Text('New Encrypted Investigation Export'),
      ),
    );
  }

  Widget _buildAuthorityAuditNotice() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.error.withValues(alpha: 0.08),
        border: Border(bottom: BorderSide(color: AppColors.error.withValues(alpha: 0.3))),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.gavel, color: AppColors.error, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'HIGH-PRIVILEGE AUDIT ACCESS — STRICT AUDIT LOGGING ACTIVE',
                  style: AppTypography.titleSmall.copyWith(color: AppColors.error, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'All queries in this portal require a valid Ministry of Revenues case reference and legal justification. '
                  'Every search query, document view, and export action is immutably logged to the regulatory audit log '
                  'with the examiner\'s cryptographic identity.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Mandatory Regulatory Search Justification', style: AppTypography.titleMedium),
                  const Divider(height: 24),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _caseRefController,
                          decoration: const InputDecoration(
                            labelText: 'MoR Formal Case / Warrant Ref *',
                            border: OutlineInputBorder(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextField(
                          controller: _reasonController,
                          decoration: const InputDecoration(
                            labelText: 'Statutory Investigation Purpose *',
                            border: OutlineInputBorder(),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _tinController,
                          decoration: const InputDecoration(
                            labelText: 'Taxpayer / Customer TIN (10 Digits)',
                            border: OutlineInputBorder(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextField(
                          controller: _nameController,
                          decoration: const InputDecoration(
                            labelText: 'Legal Entity Name',
                            border: OutlineInputBorder(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      ElevatedButton.icon(
                        onPressed: _isSearching ? null : _performSearch,
                        icon: _isSearching
                            ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.search),
                        label: const Text('Execute Audited Query'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.navy900,
                          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          if (_errorMessage != null) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.error.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: AppColors.error),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline, color: AppColors.error),
                  const SizedBox(width: 8),
                  Expanded(child: Text(_errorMessage!, style: const TextStyle(color: AppColors.error))),
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],
          if (_searchResults.isEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  children: [
                    const Icon(Icons.policy_outlined, size: 64, color: AppColors.textTertiary),
                    const SizedBox(height: 16),
                    Text('No investigation targets retrieved yet', style: AppTypography.titleMedium),
                    const SizedBox(height: 4),
                    Text(
                      'Enter case credentials above and execute a search.',
                      style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                    ),
                  ],
                ),
              ),
            )
          else ...[
            Text('Investigation Results (${_searchResults.length})', style: AppTypography.titleMedium),
            const SizedBox(height: 8),
            ..._searchResults.map(
              (c) => Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: const CircleAvatar(
                    backgroundColor: AppColors.navy700,
                    child: Icon(Icons.business, color: Colors.white),
                  ),
                  title: Text(c.legalName, style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text(
                    'TIN: ${c.tin} • Address: ${c.address} • Registered: ${_dateFormat.format(c.registeredAt)}',
                  ),
                  trailing: c.auditReference != null
                      ? Chip(
                          label: Text(
                            'AUDIT: ${c.auditReference!.substring(0, c.auditReference!.length > 12 ? 12 : c.auditReference!.length)}...',
                            style: const TextStyle(fontSize: 10, color: AppColors.navy700),
                          ),
                          backgroundColor: AppColors.navy700.withValues(alpha: 0.1),
                        )
                      : null,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildExportsTab() {
    if (_exportJobs.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.archive_outlined, size: 64, color: AppColors.textTertiary),
            const SizedBox(height: 16),
            Text('No encrypted export jobs created yet', style: AppTypography.titleMedium),
            const SizedBox(height: 4),
            Text(
              'Use "New Encrypted Investigation Export" to extract certified evidence packages.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: _exportJobs.length,
      itemBuilder: (ctx, index) {
        final job = _exportJobs[index];
        final isComplete = job.status == 'COMPLETED';

        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Case Ref: ${job.caseReference}', style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold)),
                    Chip(
                      label: Text(job.status),
                      backgroundColor: isComplete
                          ? AppColors.success.withValues(alpha: 0.15)
                          : AppColors.warning.withValues(alpha: 0.15),
                      labelStyle: TextStyle(
                        color: isComplete ? AppColors.success : AppColors.warning,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text('Statutory Purpose: ${job.reason}', style: AppTypography.bodySmall),
                Text('Records Compiled: ${job.recordCount} • Requested by: ${job.requestedBy}', style: AppTypography.caption),
                if (job.encryptedArtifactSha256 != null) ...[
                  const SizedBox(height: 6),
                  Text(
                    'SHA-256 Digest: ${job.encryptedArtifactSha256}',
                    style: AppTypography.caption.copyWith(color: AppColors.navy700, fontWeight: FontWeight.bold),
                  ),
                ],
                const Divider(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Created: ${_dateFormat.format(job.createdAt)}', style: AppTypography.caption),
                    ElevatedButton.icon(
                      onPressed: isComplete
                          ? () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Downloading encrypted evidence package (.gpg)...')),
                              );
                            }
                          : null,
                      icon: const Icon(Icons.download, size: 16),
                      label: const Text('Download Evidence Package'),
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _CreateExportJobDialog extends ConsumerStatefulWidget {
  final String initialCaseRef;
  final String initialReason;
  final VoidCallback onSuccess;

  const _CreateExportJobDialog({
    required this.initialCaseRef,
    required this.initialReason,
    required this.onSuccess,
  });

  @override
  ConsumerState<_CreateExportJobDialog> createState() => _CreateExportJobDialogState();
}

class _CreateExportJobDialogState extends ConsumerState<_CreateExportJobDialog> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _caseRefController;
  late TextEditingController _reasonController;
  final _tinController = TextEditingController();
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _caseRefController = TextEditingController(text: widget.initialCaseRef);
    _reasonController = TextEditingController(text: widget.initialReason);
  }

  @override
  void dispose() {
    _caseRefController.dispose();
    _reasonController.dispose();
    _tinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(authorityInvestigationServiceProvider);
      await service.createExportJob(
        caseReference: _caseRefController.text.trim(),
        reason: _reasonController.text.trim(),
        taxpayerTin: _tinController.text.trim().isNotEmpty ? _tinController.text.trim() : null,
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to initiate export: $e'), backgroundColor: AppColors.error),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Initiate Encrypted Authority Investigation Export'),
      content: SizedBox(
        width: 480,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _caseRefController,
                decoration: const InputDecoration(labelText: 'Case Reference *', border: OutlineInputBorder()),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _reasonController,
                maxLines: 2,
                decoration: const InputDecoration(labelText: 'Statutory Reason *', border: OutlineInputBorder()),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _tinController,
                decoration: const InputDecoration(
                  labelText: 'Target Taxpayer TIN (Leave blank for whole-sample)',
                  border: OutlineInputBorder(),
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
              : const Text('Create Export Job'),
        ),
      ],
    );
  }
}
