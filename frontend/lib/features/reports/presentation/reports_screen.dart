import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../core/di/providers.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class ReportDefinition {
  final String id;
  final String title;
  final String amharicTitle;
  final String description;
  final String iconName;
  final IconData icon;
  final List<String> exportFormats;
  final String category;
  final bool isActive;
  final int displayOrder;

  const ReportDefinition({
    required this.id,
    required this.title,
    required this.amharicTitle,
    required this.description,
    required this.iconName,
    required this.icon,
    required this.exportFormats,
    required this.category,
    this.isActive = true,
    this.displayOrder = 0,
  });

  factory ReportDefinition.fromJson(Map<String, dynamic> json) {
    List<String> formats = ['PDF', 'EXCEL', 'CSV'];
    if (json['exportFormats'] != null) {
      final val = json['exportFormats'];
      if (val is List) {
        formats = val.map((e) => e.toString()).toList();
      } else if (val is String) {
        formats = val.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
      }
    }

    final iconStr = json['iconName']?.toString() ?? 'assessment';

    return ReportDefinition(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      amharicTitle: json['amharicTitle']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      iconName: iconStr,
      icon: iconNameToData(iconStr),
      exportFormats: formats,
      category: json['category']?.toString() ?? 'COMPLIANCE',
      isActive: json['isActive'] == true || json['active'] == true,
      displayOrder: (json['displayOrder'] as num?)?.toInt() ?? 0,
    );
  }

  static IconData iconNameToData(String name) {
    switch (name.toLowerCase()) {
      case 'receipt_long':
        return Icons.receipt_long;
      case 'point_of_sale':
        return Icons.point_of_sale;
      case 'trending_up':
        return Icons.trending_up;
      case 'cloud_sync':
        return Icons.cloud_sync;
      case 'account_balance':
        return Icons.account_balance;
      case 'pie_chart':
        return Icons.pie_chart;
      case 'table_chart':
        return Icons.table_chart;
      case 'summarize':
        return Icons.summarize;
      case 'description':
        return Icons.description;
      case 'bar_chart':
        return Icons.bar_chart;
      case 'menu_book':
        return Icons.menu_book;
      case 'assessment':
      default:
        return Icons.assessment;
    }
  }
}

class GeneratedReportJob {
  final String jobId;
  final String reportTitle;
  final String dateRange;
  final String format;
  final String status; // 'PROCESSING', 'COMPLETED', 'FAILED'
  final DateTime requestedAt;
  final String? downloadUrl;
  final String? reportContent;
  final int recordCount;

  const GeneratedReportJob({
    required this.jobId,
    required this.reportTitle,
    required this.dateRange,
    required this.format,
    required this.status,
    required this.requestedAt,
    this.downloadUrl,
    this.reportContent,
    this.recordCount = 0,
  });

  factory GeneratedReportJob.fromJson(Map<String, dynamic> json) {
    final id = json['id']?.toString() ?? 'JOB-000';
    final createdAtStr = json['createdAt']?.toString();
    final createdAt = createdAtStr != null ? DateTime.tryParse(createdAtStr) ?? DateTime.now() : DateTime.now();

    return GeneratedReportJob(
      jobId: id,
      reportTitle: json['reportTitle']?.toString() ?? 'Compliance Export Archive',
      dateRange: json['dateRange']?.toString() ?? 'Authorized Tenant Ledger',
      format: json['format']?.toString() ?? 'CSV',
      status: json['status']?.toString() ?? 'COMPLETED',
      requestedAt: createdAt,
      downloadUrl: json['artifactUrl']?.toString(),
      reportContent: json['reportContent']?.toString(),
      recordCount: (json['recordCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class ReportsScreen extends ConsumerStatefulWidget {
  const ReportsScreen({super.key});

  @override
  ConsumerState<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends ConsumerState<ReportsScreen> {
  DateTimeRange _selectedRange = DateTimeRange(
    start: DateTime.now().subtract(const Duration(days: 30)),
    end: DateTime.now(),
  );

  List<ReportDefinition> _availableReports = [];
  bool _isLoadingReports = true;
  String? _reportsError;

  List<GeneratedReportJob> _recentJobs = [];
  bool _isLoadingJobs = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadAll();
    });
  }

  Future<void> _loadAll() async {
    await Future.wait([
      _loadReportDefinitions(),
      _loadRecentJobs(),
    ]);
  }

  Future<void> _loadReportDefinitions() async {
    if (!mounted) return;
    setState(() {
      _isLoadingReports = true;
      _reportsError = null;
    });

    try {
      final apiClient = ref.read(apiClientProvider);
      final response = await apiClient.get('/api/v1/reports/definitions');

      if (response.statusCode == 200 && response.data is List) {
        final List<ReportDefinition> list = [];
        for (final item in response.data as List) {
          if (item is Map) {
            list.add(ReportDefinition.fromJson(Map<String, dynamic>.from(item)));
          }
        }
        if (mounted) {
          setState(() {
            _availableReports = list;
            _isLoadingReports = false;
          });
        }
        return;
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _reportsError = 'Failed to load report templates from database: $e';
          _isLoadingReports = false;
        });
      }
    }
  }

  Future<void> _loadRecentJobs() async {
    if (!mounted) return;
    setState(() {
      _isLoadingJobs = true;
    });

    try {
      final apiClient = ref.read(apiClientProvider);
      final response = await apiClient.get('/api/v1/reports/jobs');

      if (response.statusCode == 200 && response.data is List) {
        final List<GeneratedReportJob> serverJobs = [];
        for (final item in response.data as List) {
          if (item is Map) {
            serverJobs.add(GeneratedReportJob.fromJson(Map<String, dynamic>.from(item)));
          }
        }

        if (mounted) {
          setState(() {
            _recentJobs = serverJobs;
            _isLoadingJobs = false;
          });
        }
        return;
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoadingJobs = false;
        });
      }
    }
  }

  Future<void> _requestReportGeneration(ReportDefinition report, String format) async {
    final dateRangeStr =
        '${_selectedRange.start.toLocal().toString().split(' ')[0]} to ${_selectedRange.end.toLocal().toString().split(' ')[0]}';

    final tempJobId = 'JOB-${DateTime.now().millisecondsSinceEpoch.toString().substring(8)}';

    final placeholderJob = GeneratedReportJob(
      jobId: tempJobId,
      reportTitle: '${report.title} ($format)',
      dateRange: dateRangeStr,
      format: format,
      status: 'PROCESSING',
      requestedAt: DateTime.now(),
    );

    setState(() {
      _recentJobs.insert(0, placeholderJob);
    });

    try {
      final apiClient = ref.read(apiClientProvider);
      final response = await apiClient.post(
        '/api/v1/reports/jobs',
        data: {
          'reportId': report.id,
          'format': format,
          'startDate': _selectedRange.start.toUtc().toIso8601String(),
          'endDate': _selectedRange.end.toUtc().toIso8601String(),
        },
      );

      if ((response.statusCode == 200 || response.statusCode == 201) && response.data is Map) {
        final createdJob = GeneratedReportJob.fromJson(Map<String, dynamic>.from(response.data));

        if (mounted) {
          setState(() {
            final idx = _recentJobs.indexWhere((j) => j.jobId == tempJobId);
            if (idx != -1) {
              _recentJobs[idx] = createdJob;
            } else {
              _recentJobs.insert(0, createdJob);
            }
          });

          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                '${report.title} ($format) compiled from ${createdJob.recordCount} live database records.',
              ),
              backgroundColor: AppColors.green700,
            ),
          );
        }
        return;
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          final idx = _recentJobs.indexWhere((j) => j.jobId == tempJobId);
          if (idx != -1) {
            _recentJobs[idx] = GeneratedReportJob(
              jobId: tempJobId,
              reportTitle: '${report.title} ($format)',
              dateRange: dateRangeStr,
              format: format,
              status: 'FAILED',
              requestedAt: DateTime.now(),
            );
          }
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to generate report from database: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  void _showReportViewerDialog(GeneratedReportJob job) async {
    String? content = job.reportContent;

    // If reportContent is not in memory, fetch it directly from database endpoint
    if (content == null || content.isEmpty) {
      try {
        final apiClient = ref.read(apiClientProvider);
        final res = await apiClient.get('/api/v1/reports/jobs/${job.jobId}');
        if (res.data is Map && res.data['reportContent'] != null) {
          content = res.data['reportContent'].toString();
        }
      } catch (_) {}
    }

    if (!mounted) return;

    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: Row(
            children: [
              const Icon(Icons.assessment_outlined, color: AppColors.navy900),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(job.reportTitle, style: AppTypography.h2()),
                    Text('Record Count: ${job.recordCount} | Format: ${job.format} | Range: ${job.dateRange}',
                        style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                  ],
                ),
              ),
            ],
          ),
          content: SizedBox(
            width: 750,
            height: 450,
            child: (content != null && content.isNotEmpty)
                ? Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      border: Border.all(color: AppColors.rule),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: SingleChildScrollView(
                      child: SelectableText(
                        content,
                        style: AppTypography.monoSmall(),
                      ),
                    ),
                  )
                : Center(
                    child: Text(
                      'Server archive download ready:\n${job.downloadUrl ?? "Archived in database"}',
                      textAlign: TextAlign.center,
                      style: AppTypography.bodySmall(),
                    ),
                  ),
          ),
          actions: [
            if (content != null && content.isNotEmpty)
              OutlinedButton.icon(
                onPressed: () {
                  final textToCopy = content!;
                  Clipboard.setData(ClipboardData(text: textToCopy));
                  Navigator.of(ctx).pop();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Report data copied to clipboard!'),
                      backgroundColor: AppColors.green700,
                    ),
                  );
                },
                icon: const Icon(Icons.copy, size: 16),
                label: const Text('Copy Data'),
              ),
            ElevatedButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Close'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final tenant = ref.watch(tenantContextProvider);
    final tenantSession = ref.watch(authSessionProvider);
    final delegatedSession = ref.watch(delegatedTenantSessionProvider);

    final activeTenantName = delegatedSession?.targetTenantName ??
        tenant.activeTenant?.name ??
        (tenantSession?.username != null && tenantSession!.username.isNotEmpty
            ? tenantSession.username
            : null) ??
        'Taxpayer Business';

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadAll,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              LayoutBuilder(
                builder: (context, constraints) {
                  final isCompact = constraints.maxWidth < 650;
                  final titleColumn = Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'TAX & FISCAL REPORTING',
                        style: AppTypography.h1(),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Live database compliance reports & exports for $activeTenantName (Directive No. 1142/2026)',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  );

                  final dateRangeButton = OutlinedButton.icon(
                    onPressed: () async {
                      final picked = await showDateRangePicker(
                        context: context,
                        initialDateRange: _selectedRange,
                        firstDate: DateTime(2024, 1, 1),
                        lastDate: DateTime.now().add(const Duration(days: 1)),
                      );
                      if (picked != null && mounted) {
                        setState(() => _selectedRange = picked);
                      }
                    },
                    icon: const Icon(Icons.date_range, size: 16),
                    label: Text(
                      'Range: ${_selectedRange.start.toString().split(' ')[0]} — ${_selectedRange.end.toString().split(' ')[0]}',
                    ),
                  );

                  if (isCompact) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        titleColumn,
                        const SizedBox(height: 14),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: dateRangeButton,
                        ),
                      ],
                    );
                  }
                  return Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(child: titleColumn),
                      const SizedBox(width: 16),
                      dateRangeButton,
                    ],
                  );
                },
              ),
              const SizedBox(height: 24),

              // Database Processing Notice
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.cloud_done_outlined,
                      size: 24,
                      color: AppColors.navy900,
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        'All report templates and generation jobs are synchronized directly from the database and calculated strictly from tenant ledger entries under Directive No. 1142/2026.',
                        style: AppTypography.bodySmall(),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Available Reports Grid
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'AVAILABLE COMPLIANCE & OPERATIONAL REPORTS',
                    style: AppTypography.h2(),
                  ),
                  if (_isLoadingReports)
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  else
                    IconButton(
                      icon: const Icon(Icons.refresh, size: 18),
                      tooltip: 'Reload templates from database',
                      onPressed: _loadReportDefinitions,
                    ),
                ],
              ),
              const SizedBox(height: 16),

              if (_isLoadingReports)
                const Padding(
                  padding: EdgeInsets.all(48),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (_reportsError != null)
                Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: AppColors.paperRaised,
                    borderRadius: BorderRadius.circular(3),
                    border: Border.all(color: AppColors.red600),
                  ),
                  child: Center(
                    child: Column(
                      children: [
                        const Icon(Icons.error_outline, size: 36, color: AppColors.red600),
                        const SizedBox(height: 8),
                        Text(_reportsError!, style: AppTypography.bodySmall(color: AppColors.red600)),
                        const SizedBox(height: 12),
                        OutlinedButton(
                          onPressed: _loadReportDefinitions,
                          child: const Text('Retry Database Query'),
                        ),
                      ],
                    ),
                  ),
                )
              else if (_availableReports.isEmpty)
                Container(
                  padding: const EdgeInsets.all(36),
                  decoration: BoxDecoration(
                    color: AppColors.paperRaised,
                    borderRadius: BorderRadius.circular(3),
                    border: Border.all(color: AppColors.rule),
                  ),
                  child: Center(
                    child: Column(
                      children: [
                        const Icon(Icons.folder_open_outlined, size: 40, color: AppColors.inkMuted),
                        const SizedBox(height: 8),
                        Text('No report definitions found in the database.', style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
                        const SizedBox(height: 4),
                        Text('Report definitions can be configured and activated from the SaaS Admin dashboard.', style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                      ],
                    ),
                  ),
                )
              else
                LayoutBuilder(
                  builder: (context, constraints) {
                    final isWide = constraints.maxWidth >= 900;
                    return GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: isWide ? 2 : 1,
                        childAspectRatio: isWide ? 2.4 : 1.8,
                        crossAxisSpacing: 16,
                        mainAxisSpacing: 16,
                      ),
                      itemCount: _availableReports.length,
                      itemBuilder: (context, index) {
                        final report = _availableReports[index];
                        return Container(
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
                                children: [
                                  Icon(
                                    report.icon,
                                    size: 22,
                                    color: AppColors.navy900,
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      report.title,
                                      style: AppTypography.uiLabelBold(),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppColors.paper,
                                      borderRadius: BorderRadius.circular(2),
                                      border: Border.all(color: AppColors.rule),
                                    ),
                                    child: Text(
                                      report.category,
                                      style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 4),
                              Text(
                                report.amharicTitle,
                                style: AppTypography.bodySmall(
                                  color: AppColors.inkMuted,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Expanded(
                                child: Text(
                                  report.description,
                                  style: AppTypography.bodySmall(),
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const Divider(height: 16, color: AppColors.rule),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.end,
                                children: report.exportFormats.map((fmt) {
                                  return Padding(
                                    padding: const EdgeInsets.only(left: 8),
                                    child: OutlinedButton(
                                      onPressed: () => _requestReportGeneration(report, fmt),
                                      style: OutlinedButton.styleFrom(
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 10,
                                          vertical: 4,
                                        ),
                                        minimumSize: const Size(60, 30),
                                      ),
                                      child: Text(
                                        fmt,
                                        style: AppTypography.monoSmall(),
                                      ),
                                    ),
                                  );
                                }).toList(),
                              ),
                            ],
                          ),
                        );
                      },
                    );
                  },
                ),
              const SizedBox(height: 32),

              // Generated Report Jobs Table
              Material(
                color: AppColors.paperRaised,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(3),
                  side: const BorderSide(color: AppColors.rule),
                ),
                clipBehavior: Clip.antiAlias,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              'RECENT EXPORT JOBS (FROM DATABASE)',
                              style: AppTypography.h2(),
                            ),
                          ),
                          if (_isLoadingJobs)
                            const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          else
                            IconButton(
                              icon: const Icon(Icons.refresh, size: 18),
                              tooltip: 'Refresh jobs',
                              onPressed: _loadRecentJobs,
                            ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: AppColors.rule),
                    if (_recentJobs.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(32),
                        child: Center(
                          child: Column(
                            children: [
                              const Icon(
                                Icons.folder_open_outlined,
                                size: 36,
                                color: AppColors.inkMuted,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'No report export jobs initiated yet for $activeTenantName.',
                                style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Select a format (PDF, EXCEL, CSV, JSON) above to generate a real-time compliance report.',
                                style: AppTypography.bodySmall(color: AppColors.inkMuted),
                              ),
                            ],
                          ),
                        ),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _recentJobs.length,
                        separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                        itemBuilder: (context, index) {
                          final job = _recentJobs[index];
                          final isDone = job.status == 'COMPLETED';
                          final isFailed = job.status == 'FAILED';

                          Color statusColor = AppColors.navy700;
                          IconData statusIcon = Icons.sync;
                          if (isDone) {
                            statusColor = AppColors.green700;
                            statusIcon = Icons.check_circle_outline;
                          } else if (isFailed) {
                            statusColor = AppColors.red600;
                            statusIcon = Icons.error_outline;
                          }

                          final shortJobId = job.jobId.length > 8 ? job.jobId.substring(0, 8).toUpperCase() : job.jobId;

                          return Material(
                            type: MaterialType.transparency,
                            child: ListTile(
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 20,
                                vertical: 8,
                              ),
                              leading: Icon(statusIcon, color: statusColor),
                              title: Text(
                                job.reportTitle,
                                style: AppTypography.uiLabelBold(),
                              ),
                              subtitle: Text(
                                'Job ID: $shortJobId | Format: ${job.format} | Range: ${job.dateRange} | ${job.recordCount > 0 ? "${job.recordCount} records | " : ""}Requested: ${job.requestedAt.toLocal().toString().substring(0, 16)}',
                                style: AppTypography.monoSmall(
                                  color: AppColors.inkMuted,
                                ),
                              ),
                              trailing: isDone
                                  ? ElevatedButton.icon(
                                      onPressed: () => _showReportViewerDialog(job),
                                      icon: const Icon(Icons.download, size: 14),
                                      label: const Text('View / Download'),
                                      style: ElevatedButton.styleFrom(
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 12,
                                          vertical: 6,
                                        ),
                                      ),
                                    )
                                  : (isFailed
                                      ? Text(
                                          'FAILED',
                                          style: AppTypography.monoSmall(color: AppColors.red600),
                                        )
                                      : const SizedBox(
                                          width: 20,
                                          height: 20,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                          ),
                                        )),
                            ),
                          );
                        },
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
