import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterAccessReviewsScreen extends ConsumerStatefulWidget {
  const MasterAccessReviewsScreen({super.key});

  @override
  ConsumerState<MasterAccessReviewsScreen> createState() =>
      _MasterAccessReviewsScreenState();
}

class _MasterAccessReviewsScreenState
    extends ConsumerState<MasterAccessReviewsScreen> {
  bool _isLoading = true;
  String? _error;
  List<dynamic> _campaigns = [];
  dynamic _selectedCampaign;
  List<dynamic> _entries = [];

  @override
  void initState() {
    super.initState();
    _loadCampaigns();
  }

  Future<void> _loadCampaigns() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.get('/api/v1/master/access-reviews/campaigns');
      final list = res.data is List ? res.data as List : [];
      setState(() {
        _campaigns = list;
        _isLoading = false;
      });

      if (list.isNotEmpty && _selectedCampaign == null) {
        _selectCampaign(list.first);
      }
    } catch (e) {
      setState(() {
        _error = 'Failed to load access review campaigns: $e';
        _isLoading = false;
      });
    }
  }

  Future<void> _selectCampaign(dynamic campaign) async {
    setState(() {
      _selectedCampaign = campaign;
      _entries = [];
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.get(
        '/api/v1/master/access-reviews/campaigns/${campaign['id']}/entries',
      );
      setState(() {
        _entries = res.data is List ? res.data as List : [];
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load campaign entries: $e')),
        );
      }
    }
  }

  void _showCreateCampaignDialog() {
    final titleCtrl = TextEditingController(text: 'Q3 Enterprise Access Certification');
    final descCtrl = TextEditingController(
      text: 'Mandatory review of privileged administrator accounts pursuant to Directive No. 1142/2026 Art. 4',
    );
    String? dialogError;
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.assignment_turned_in, color: AppColors.navy900, size: 22),
              const SizedBox(width: 8),
              Text('Launch Access Certification Campaign', style: AppTypography.h3()),
            ],
          ),
          content: SizedBox(
            width: 500,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Automatically snapshots all active platform and tenant administrators and flags inactive accounts (>90 days without login).',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
                const SizedBox(height: 16),
                if (dialogError != null) ...[
                  Text(dialogError!, style: AppTypography.bodySmall(color: AppColors.red700)),
                  const SizedBox(height: 8),
                ],
                TextField(
                  controller: titleCtrl,
                  decoration: const InputDecoration(labelText: 'Campaign Title'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: descCtrl,
                  maxLines: 2,
                  decoration: const InputDecoration(labelText: 'Regulatory Purpose & Justification'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });
                      try {
                        final client = ref.read(masterAdminApiClientProvider);
                        await client.post(
                          '/api/v1/master/access-reviews/campaigns',
                          data: {
                            'title': titleCtrl.text.trim(),
                            'description': descCtrl.text.trim(),
                          },
                        );
                        if (ctx.mounted) Navigator.pop(ctx);
                        _loadCampaigns();
                      } catch (e) {
                        setDialogState(() {
                          dialogError = 'Failed to create campaign: $e';
                          isSubmitting = false;
                        });
                      }
                    },
              child: const Text('Launch Campaign'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submitDecision(String entryId, String decision, String notes) async {
    try {
      final client = ref.read(masterAdminApiClientProvider);
      await client.post(
        '/api/v1/master/access-reviews/entries/$entryId/decision',
        data: {
          'decision': decision,
          'notes': notes,
        },
      );
      if (_selectedCampaign != null) {
        _selectCampaign(_selectedCampaign);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to submit decision: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: AppColors.paperMuted,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.paperMuted,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Page Header
            Row(
              children: [
                const Icon(
                  Icons.assignment_turned_in_outlined,
                  size: 28,
                  color: AppColors.navy900,
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Privileged Access Certification & Audit Reviews', style: AppTypography.h1()),
                    Text(
                      'Periodic verification of administrator privileges, inactive account remediation, and certified compliance audits.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                const Spacer(),
                ElevatedButton.icon(
                  onPressed: _showCreateCampaignDialog,
                  icon: const Icon(Icons.add_task, size: 16),
                  label: const Text('New Audit Campaign'),
                ),
              ],
            ),
            const SizedBox(height: 20),

            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.red700.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.red700)),
              ),
              const SizedBox(height: 16),
            ],

            Expanded(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Left panel: Campaigns list
                  SizedBox(
                    width: 320,
                    child: Container(
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Padding(
                            padding: const EdgeInsets.all(14),
                            child: Text('Audit Campaigns', style: AppTypography.uiLabelBold()),
                          ),
                          const Divider(height: 1, color: AppColors.rule),
                          Expanded(
                            child: _campaigns.isEmpty
                                ? Center(
                                    child: Text(
                                      'No campaigns initiated.',
                                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                    ),
                                  )
                                : ListView.separated(
                                    itemCount: _campaigns.length,
                                    separatorBuilder: (c, i) => const Divider(height: 1, color: AppColors.rule),
                                    itemBuilder: (c, i) {
                                      final camp = _campaigns[i];
                                      final isSelected = _selectedCampaign?['id'] == camp['id'];
                                      final status = camp['status'] ?? 'ACTIVE';

                                       return Material(
                                         type: MaterialType.transparency,
                                         child: ListTile(
                                           selected: isSelected,
                                           selectedTileColor: AppColors.navy900.withValues(alpha: 0.05),
                                           title: Text(camp['title'] ?? '', style: AppTypography.uiLabelBold()),
                                           subtitle: Text(
                                             'Status: $status | ${camp['reviewedEntries']}/${camp['totalEntries']} reviewed',
                                             style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                           ),
                                           onTap: () => _selectCampaign(camp),
                                         ),
                                       );
                                    },
                                  ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 20),

                  // Right panel: Entries & Decisions
                  Expanded(
                    child: Container(
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: _selectedCampaign == null
                          ? Center(
                              child: Text(
                                'Select a campaign to inspect administrator privilege entries.',
                                style: AppTypography.bodySmall(color: AppColors.inkMuted),
                              ),
                            )
                          : Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Padding(
                                  padding: const EdgeInsets.all(16),
                                  child: Row(
                                    children: [
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(_selectedCampaign['title'] ?? '', style: AppTypography.h3()),
                                            Text(
                                              _selectedCampaign['description'] ?? '',
                                              style: AppTypography.bodySmall(color: AppColors.inkMuted),
                                            ),
                                          ],
                                        ),
                                      ),
                                      if (_selectedCampaign['status'] == 'ACTIVE')
                                        ElevatedButton.icon(
                                          style: ElevatedButton.styleFrom(backgroundColor: AppColors.green700),
                                          onPressed: () async {
                                            final client = ref.read(masterAdminApiClientProvider);
                                            await client.post(
                                              '/api/v1/master/access-reviews/campaigns/${_selectedCampaign['id']}/finalize',
                                            );
                                            _loadCampaigns();
                                          },
                                          icon: const Icon(Icons.check, size: 16),
                                          label: const Text('Finalize Campaign'),
                                        ),
                                    ],
                                  ),
                                ),
                                const Divider(height: 1, color: AppColors.rule),
                                Expanded(
                                  child: _entries.isEmpty
                                      ? Center(
                                          child: Text('No entries found.', style: AppTypography.bodySmall()),
                                        )
                                      : ListView.separated(
                                          itemCount: _entries.length,
                                          separatorBuilder: (c, i) => const Divider(height: 1, color: AppColors.rule),
                                          itemBuilder: (c, i) {
                                            final entry = _entries[i];
                                            final decision = entry['decision'] ?? 'PENDING';
                                            final notes = entry['notes'] as String?;
                                            final isInactive = notes != null && notes.contains('INACTIVE WARNING');

                                            Color decColor = AppColors.inkMuted;
                                            if (decision == 'APPROVED') decColor = AppColors.green700;
                                            if (decision == 'SUSPENDED') decColor = AppColors.red700;

                                             return Material(
                                               type: MaterialType.transparency,
                                               child: ListTile(
                                                 contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                                                 title: Row(
                                                   children: [
                                                     Text(entry['fullName'] ?? '', style: AppTypography.uiLabelBold()),
                                                     const SizedBox(width: 8),
                                                     Text('(@${entry['username']})', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                                     const Spacer(),
                                                     Container(
                                                       padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                                       decoration: BoxDecoration(
                                                         color: decColor.withValues(alpha: 0.1),
                                                         borderRadius: BorderRadius.circular(3),
                                                         border: Border.all(color: decColor.withValues(alpha: 0.3)),
                                                       ),
                                                       child: Text(
                                                         decision,
                                                         style: AppTypography.monoSmall(color: decColor, weight: FontWeight.w700),
                                                       ),
                                                     ),
                                                   ],
                                                 ),
                                                 subtitle: Column(
                                                   crossAxisAlignment: CrossAxisAlignment.start,
                                                   children: [
                                                     const SizedBox(height: 4),
                                                     Text('Assigned Roles: ${entry['currentRoles']}', style: AppTypography.bodySmall()),
                                                     Text(
                                                       'Summary: ${entry['effectivePermissionsSummary']}',
                                                       style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                                     ),
                                                     if (isInactive) ...[
                                                       const SizedBox(height: 4),
                                                       Container(
                                                         padding: const EdgeInsets.all(6),
                                                         decoration: BoxDecoration(
                                                           color: AppColors.amber700.withValues(alpha: 0.1),
                                                           borderRadius: BorderRadius.circular(3),
                                                         ),
                                                         child: Row(
                                                           children: [
                                                             const Icon(Icons.warning, size: 14, color: AppColors.amber700),
                                                             const SizedBox(width: 6),
                                                             Expanded(
                                                               child: Text(notes, style: AppTypography.monoSmall(color: AppColors.amber700)),
                                                             ),
                                                           ],
                                                         ),
                                                       ),
                                                     ],
                                                   ],
                                                 ),
                                                 trailing: decision == 'PENDING'
                                                     ? Row(
                                                         mainAxisSize: MainAxisSize.min,
                                                         children: [
                                                           IconButton(
                                                             icon: const Icon(Icons.check_circle_outline, color: AppColors.green700),
                                                             tooltip: 'Approve Access',
                                                             onPressed: () => _submitDecision(
                                                               entry['id'],
                                                               'APPROVED',
                                                               'Access verified and certified.',
                                                             ),
                                                           ),
                                                           IconButton(
                                                             icon: const Icon(Icons.remove_circle_outline, color: AppColors.red700),
                                                             tooltip: 'Suspend Privileges',
                                                             onPressed: () => _submitDecision(
                                                               entry['id'],
                                                               'SUSPENDED',
                                                               'Privileges suspended during access review.',
                                                             ),
                                                           ),
                                                         ],
                                                       )
                                                     : null,
                                               ),
                                             );
                                          },
                                        ),
                                ),
                              ],
                            ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
