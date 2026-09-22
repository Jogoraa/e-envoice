import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/networking/gateway_config.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../core/errors/app_error.dart';

class ExportJobItem {
  final String id;
  final String status;
  final String? artifactUrl;
  final String? artifactChecksum;
  final DateTime createdAt;
  final DateTime? completedAt;

  ExportJobItem({
    required this.id,
    required this.status,
    this.artifactUrl,
    this.artifactChecksum,
    required this.createdAt,
    this.completedAt,
  });

  factory ExportJobItem.fromJson(Map<String, dynamic> json) {
    return ExportJobItem(
      id: json['id']?.toString() ?? '',
      status: json['status']?.toString() ?? 'REQUESTED',
      artifactUrl: json['artifactUrl']?.toString(),
      artifactChecksum: json['artifactChecksum']?.toString(),
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      completedAt: json['completedAt'] != null
          ? DateTime.tryParse(json['completedAt'].toString())
          : null,
    );
  }
}

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _isLoadingExports = false;
  bool _isExporting = false;
  String? _exportError;
  List<ExportJobItem> _exports = [];

  final DateFormat _dateFormat = DateFormat('dd/MM/yyyy HH:mm:ss');

  @override
  void initState() {
    super.initState();
    _loadExportJobs();
  }

  Future<void> _loadExportJobs() async {
    setState(() => _isLoadingExports = true);
    try {
      final client = ref.read(apiClientProvider);
      final response = await client.get('/api/v1/portability/exports');
      final list = (response.data as List<dynamic>? ?? [])
          .map((item) => ExportJobItem.fromJson(item as Map<String, dynamic>))
          .toList();

      if (mounted) {
        setState(() {
          _exports = list;
          _isLoadingExports = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoadingExports = false);
      }
    }
  }

  Future<void> _handleInitiateExport() async {
    setState(() {
      _isExporting = true;
      _exportError = null;
    });

    try {
      final client = ref.read(apiClientProvider);
      await client.post('/api/v1/portability/exports');
      await _loadExportJobs();

      if (mounted) {
        setState(() => _isExporting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Statutory data export package initiated. Processing asynchronous cryptographic bundle...',
            ),
            backgroundColor: AppColors.navy900,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isExporting = false;
          _exportError = e is AppError ? e.userMessage : e.toString();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final tenantState = ref.watch(tenantContextProvider);
    final activeTenant = tenantState.activeTenant;
    final activeBranch = tenantState.activeBranch;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Text('TENANT & SYSTEM SETTINGS', style: AppTypography.h1()),
            const SizedBox(height: 4),
            Text(
              'Taxpayer identification, branch hardware binding, and regulatory gateway parameters',
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
            const SizedBox(height: 24),

            // Section 1: Active Taxpayer Profile
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
                  Text('TAXPAYER LEGAL ENTITY', style: AppTypography.h2()),
                  const SizedBox(height: 16),
                  _buildProfileRow('Legal Registered Name', activeTenant?.name ?? 'UT Solutions Test Entity'),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Ethiopian Taxpayer ID (TIN)', activeTenant?.tin ?? '0012345678', isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Tenant UUID', activeTenant?.id ?? '00000000-0000-0000-0000-000000000001', isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('VAT Status', 'Registered (Directive No. 1142/2026 Mandate)'),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Assigned Tax Office', 'Addis Ababa Large Taxpayers Office (LTO)'),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 2: Active Branch & Terminal Context
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
                  Text('ACTIVE BRANCH & POS TERMINAL HARDWARE', style: AppTypography.h2()),
                  const SizedBox(height: 16),
                  _buildProfileRow('Branch Name', activeBranch?.name ?? 'Head Office (ዋና መ/ቤት)'),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Branch Code', activeBranch?.code ?? 'BR-HO-01', isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Branch UUID', activeBranch?.id ?? '00000000-0000-0000-0000-000000000010', isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Assigned Device ID', 'DEV-ETH-POS-98124', isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Hardware Binding Status', 'Bound & Certified (Non-Authoritative Device Certificate)'),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 3: Gateway Boundary & Offline Invariants
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
                  Text('GATEWAY ISOLATION & REGULATORY INVARIANTS', style: AppTypography.h2()),
                  const SizedBox(height: 16),
                  _buildProfileRow('Active Surface', 'TENANT CLIENT APP (Application A)'),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Tenant Gateway URL', GatewayConfig.tenantApiBaseUrl, isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Header Boundary', 'X-Gateway-Surface: TENANT_CLIENT', isMono: true),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Offline Storage Engine', 'Drift SQLite (Tenant & Branch Partitioned)'),
                  const Divider(height: 20, color: AppColors.rule),
                  _buildProfileRow('Buffer Expiration Mandate', '72-Hour Maximum Offline Buffering (Directive No. 1142/2026)'),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Section 4: Data Portability & Statutory Export (Directive No. 1142/2026 Art. 5(3))
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
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('TENANT DATA PORTABILITY & STATUTORY EXPORT', style: AppTypography.h2()),
                          const SizedBox(height: 4),
                          Text(
                            'Directive No. 1142/2026 Art. 5(3) mandatory vendor lock-in prevention and complete data portability',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                          ),
                        ],
                      ),
                      ElevatedButton.icon(
                        onPressed: _isExporting ? null : _handleInitiateExport,
                        style: ElevatedButton.styleFrom(backgroundColor: AppColors.navy900),
                        icon: _isExporting
                            ? const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Icon(Icons.cloud_download_outlined, size: 16),
                        label: const Text('Initiate Data Export Package'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  if (_exportError != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.red600.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(2),
                        border: Border.all(color: AppColors.red600),
                      ),
                      child: Text(_exportError!, style: AppTypography.bodySmall(color: AppColors.red600)),
                    ),
                    const SizedBox(height: 16),
                  ],
                  if (_isLoadingExports)
                    const Padding(
                      padding: EdgeInsets.all(24.0),
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if (_exports.isEmpty)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: AppColors.paper,
                        borderRadius: BorderRadius.circular(2),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        children: [
                          const Icon(Icons.archive_outlined, size: 36, color: AppColors.inkMuted),
                          const SizedBox(height: 8),
                          Text('No Data Export Archives Generated Yet', style: AppTypography.uiLabelBold(color: AppColors.ink)),
                          const SizedBox(height: 4),
                          Text(
                            'Click "Initiate Data Export Package" to generate a cryptographically signed, portable JSON/ZIP bundle.',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    )
                  else
                    Table(
                      columnWidths: const {
                        0: FlexColumnWidth(1.8), // Job ID
                        1: FlexColumnWidth(1.2), // Status
                        2: FlexColumnWidth(1.4), // Created
                        3: FlexColumnWidth(2.6), // SHA-256
                        4: FlexColumnWidth(1.8), // Artifact
                      },
                      children: [
                        TableRow(
                          decoration: const BoxDecoration(
                            color: AppColors.paper,
                            border: Border(bottom: BorderSide(color: AppColors.rule)),
                          ),
                          children: [
                            _buildHeaderCell('JOB ID'),
                            _buildHeaderCell('STATUS'),
                            _buildHeaderCell('INITIATED AT'),
                            _buildHeaderCell('INTEGRITY CHECKSUM (SHA-256)'),
                            _buildHeaderCell('ARTIFACT PACKAGE'),
                          ],
                        ),
                        ..._exports.map((job) => TableRow(
                          decoration: const BoxDecoration(
                            border: Border(bottom: BorderSide(color: AppColors.ruleLight)),
                          ),
                          children: [
                            _buildMonoCell(job.id),
                            _buildStatusCell(job.status),
                            _buildTextCell(_dateFormat.format(job.createdAt)),
                            _buildMonoCell(job.artifactChecksum ?? 'PROCESSING'),
                            _buildArtifactCell(job),
                          ],
                        )),
                      ],
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildProfileRow(String label, String value, {bool isMono = false}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 260,
          child: Text(label, style: AppTypography.uiLabel(color: AppColors.inkMuted)),
        ),
        Expanded(
          child: Text(
            value,
            style: isMono ? AppTypography.mono() : AppTypography.body(color: AppColors.ink),
          ),
        ),
      ],
    );
  }

  Widget _buildHeaderCell(String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Text(
        title,
        style: AppTypography.uiLabelBold(color: AppColors.inkMuted).copyWith(fontSize: 11),
      ),
    );
  }

  Widget _buildTextCell(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      child: Text(text, style: AppTypography.uiLabel(color: AppColors.ink)),
    );
  }

  Widget _buildMonoCell(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      child: Text(
        text,
        style: AppTypography.mono(color: AppColors.navy700).copyWith(fontSize: 11),
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  Widget _buildStatusCell(String status) {
    final isDone = status == 'COMPLETED';
    final isFail = status == 'FAILED';
    final color = isDone ? AppColors.green700 : (isFail ? AppColors.red600 : AppColors.amber600);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(2),
        ),
        child: Text(
          status,
          style: AppTypography.uiLabelBold(color: color).copyWith(fontSize: 10),
          textAlign: TextAlign.center,
        ),
      ),
    );
  }

  Widget _buildArtifactCell(ExportJobItem job) {
    if (job.status != 'COMPLETED' || job.artifactUrl == null) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        child: Text(
          job.status == 'FAILED' ? 'FAILED' : 'PACKAGING...',
          style: AppTypography.bodySmall(color: AppColors.inkMuted),
        ),
      );
    }

    final fullUrl = '${GatewayConfig.tenantApiBaseUrl}${job.artifactUrl}';

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: OutlinedButton.icon(
        onPressed: () {
          showDialog(
            context: context,
            builder: (ctx) => AlertDialog(
              title: Row(
                children: [
                  const Icon(Icons.archive, color: AppColors.navy900, size: 20),
                  const SizedBox(width: 8),
                  Text('Statutory Data Export Package', style: AppTypography.h2()),
                ],
              ),
              content: SizedBox(
                width: 500,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'FDRE MoR Directive No. 1142/2026 Art. 5(3) complete tenant data portability bundle.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                    const SizedBox(height: 16),
                    Text('SHA-256 INTEGRITY CHECKSUM:', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                    const SizedBox(height: 4),
                    SelectableText(
                      job.artifactChecksum ?? 'N/A',
                      style: AppTypography.monoSmall(color: AppColors.navy700, weight: FontWeight.w600),
                    ),
                    const SizedBox(height: 16),
                    Text('DOWNLOAD ENDPOINT URL:', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.paper,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: SelectableText(
                        fullUrl,
                        style: AppTypography.monoSmall(color: AppColors.ink),
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('Close'),
                ),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(backgroundColor: AppColors.navy900),
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: fullUrl));
                    Navigator.of(ctx).pop();
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Download URL copied to clipboard! Access via browser or authenticated HTTP client.'),
                        backgroundColor: AppColors.navy900,
                      ),
                    );
                  },
                  icon: const Icon(Icons.copy, size: 14),
                  label: const Text('Copy Download URL'),
                ),
              ],
            ),
          );
        },
        icon: const Icon(Icons.download, size: 14),
        label: const Text('Download Bundle', style: TextStyle(fontSize: 11)),
      ),
    );
  }
}

