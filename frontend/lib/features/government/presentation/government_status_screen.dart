import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../core/di/providers.dart';
import '../../../domain/tenant/models/tenant_context.dart';

enum EirsSubmissionState {
  queued('QUEUED', 'Queued for Transmission', AppColors.inkMuted),
  submitting('SUBMITTING', 'Submitting to EIRS Gateway', AppColors.navy700),
  submitted('SUBMITTED', 'Transmitted to Ministry of Revenues', AppColors.navy900),
  accepted('ACCEPTED', 'Approved & Fiscalized by MoR', AppColors.green700),
  rejected('REJECTED', 'Rejected by EIRS Gateway', AppColors.red600),
  pendingReconciliation('PENDING_RECONCILIATION', 'Pending Server Reconciliation', AppColors.amber600),
  requiresAction('REQUIRES_ACTION', 'Action Required by Operator', AppColors.red600);

  final String code;
  final String label;
  final Color color;

  const EirsSubmissionState(this.code, this.label, this.color);
}

class EirsSubmissionItem {
  final String invoiceId;
  final String documentNumber;
  final String? irn;
  final String? rrn;
  final DateTime issuedAt;
  final DateTime? submittedAt;
  final EirsSubmissionState state;
  final String? rejectionReason;
  final double grandTotal;

  const EirsSubmissionItem({
    required this.invoiceId,
    required this.documentNumber,
    this.irn,
    this.rrn,
    required this.issuedAt,
    this.submittedAt,
    required this.state,
    this.rejectionReason,
    required this.grandTotal,
  });

  // Calculate 72-hour regulatory buffering window
  Duration get age => DateTime.now().difference(issuedAt);
  Duration get remainingTime {
    const limit = Duration(hours: 72);
    final diff = limit - age;
    return diff.isNegative ? Duration.zero : diff;
  }

  bool get isApproachingDeadline => age.inHours >= 48 && age.inHours < 72;
  bool get isDeadlineExpired => age.inHours >= 72;
}

class GovernmentStatusScreen extends ConsumerStatefulWidget {
  const GovernmentStatusScreen({super.key});

  @override
  ConsumerState<GovernmentStatusScreen> createState() => _GovernmentStatusScreenState();
}

class _GovernmentStatusScreenState extends ConsumerState<GovernmentStatusScreen> {
  final List<EirsSubmissionItem> _items = [];
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadGovernmentStatus();
    });
  }

  Future<void> _loadGovernmentStatus() async {
    if (!mounted) return;
    setState(() => _isLoading = true);

    final tenantState = ref.read(tenantContextProvider);
    final tenantSession = ref.read(authSessionProvider);
    final delegatedSession = ref.read(delegatedTenantSessionProvider);

    final activeTenantId = delegatedSession?.targetTenantId ??
        tenantState.activeTenant?.id ??
        tenantSession?.tenantId ??
        '';

    final activeBranchId = delegatedSession?.targetBranchId ??
        tenantState.activeBranch?.id ??
        '';

    if (activeTenantId.isEmpty) {
      if (mounted) {
        setState(() {
          _items.clear();
          _isLoading = false;
        });
      }
      return;
    }

    try {
      final invoices = await ref.read(invoiceRepositoryProvider).listInvoices(
            tenantId: activeTenantId,
            branchId: activeBranchId,
            size: 100,
          );

      final List<EirsSubmissionItem> items = [];
      for (final inv in invoices) {
        EirsSubmissionState state;
        final statusLower = inv.status.toLowerCase();

        final issuedAt = inv.invoiceDate;
        final age = DateTime.now().difference(issuedAt);
        final isExpired = age.inHours >= 72;

        if (statusLower == 'registered' || statusLower == 'accepted' || statusLower == 'synced') {
          state = EirsSubmissionState.accepted;
        } else if (statusLower == 'rejected' || statusLower == 'syncerror') {
          state = EirsSubmissionState.rejected;
        } else if (statusLower == 'syncing' || statusLower == 'in_flight' || statusLower == 'submitting') {
          state = EirsSubmissionState.submitting;
        } else if (statusLower == 'queued') {
          state = EirsSubmissionState.queued;
        } else {
          // offlineDraft, OFFLINE_BUFFERED, or PENDING_REGISTRATION
          state = isExpired ? EirsSubmissionState.requiresAction : EirsSubmissionState.pendingReconciliation;
        }

        items.add(
          EirsSubmissionItem(
            invoiceId: inv.id,
            documentNumber: inv.documentNumber,
            irn: inv.irn,
            rrn: inv.rrn,
            issuedAt: issuedAt,
            submittedAt: (state == EirsSubmissionState.accepted) ? issuedAt : null,
            state: state,
            grandTotal: inv.grandTotal,
          ),
        );
      }

      if (mounted) {
        setState(() {
          _items.clear();
          _items.addAll(items);
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to load live government status: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final tenant = ref.watch(tenantContextProvider);
    final tenantSession = ref.watch(authSessionProvider);
    final delegatedSession = ref.watch(delegatedTenantSessionProvider);

    final activeTenantName = delegatedSession?.targetTenantName ??
        tenant.activeTenant?.name ??
        (tenantSession?.username != null && tenantSession!.username.isNotEmpty ? tenantSession.username : null) ??
        'Taxpayer Business';

    final acceptedCount = _items.where((i) => i.state == EirsSubmissionState.accepted).length;
    final pendingCount = _items.where((i) => i.state == EirsSubmissionState.submitting || i.state == EirsSubmissionState.pendingReconciliation || i.state == EirsSubmissionState.queued).length;
    final actionCount = _items.where((i) => i.state == EirsSubmissionState.requiresAction || i.state == EirsSubmissionState.rejected).length;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: _loadGovernmentStatus,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('GOVERNMENT STATUS & EIRS RECONCILIATION', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Ethiopian Ministry of Revenues Electronic Invoicing Gateway status for $activeTenantName (Directive No. 1142/2026)',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  OutlinedButton.icon(
                    onPressed: _isLoading ? null : _loadGovernmentStatus,
                    icon: _isLoading
                        ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.refresh, size: 16),
                    label: const Text('Recheck Gateway'),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // 72-Hour Legal Mandate Warning
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.navy900.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.navy900.withValues(alpha: 0.2)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.timer_outlined, size: 28, color: AppColors.navy900),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '72-HOUR OFFLINE BUFFERING DIRECTIVE (No. 1142/2026)',
                            style: AppTypography.uiLabelBold(color: AppColors.navy900),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Offline prepared electronic invoices must be reconciled with the Ministry of Revenues within 72 hours of local issuance. Submissions beyond this regulatory window require authorized reconciliation.',
                            style: AppTypography.bodySmall(color: AppColors.ink),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Telemetry Cards
              Row(
                children: [
                  Expanded(
                    child: _buildMetricCard(
                      title: 'CONFIRMED FISCALIZED',
                      value: '$acceptedCount',
                      subtitle: 'Directly verified by MoR with IRN/RRN',
                      color: AppColors.green700,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'PENDING TRANSMISSION',
                      value: '$pendingCount',
                      subtitle: 'In flight or awaiting gateway response',
                      color: AppColors.navy700,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildMetricCard(
                      title: 'REQUIRES OPERATOR ACTION',
                      value: '$actionCount',
                      subtitle: 'Rejected or nearing 72-hour window',
                      color: AppColors.red600,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // EIRS Transmission Audit Table
              Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(child: Text('RECENT EIRS TRANSMISSIONS & RECONCILIATIONS', style: AppTypography.h2())),
                          if (_items.isNotEmpty)
                            Text(
                              '${_items.length} records',
                              style: AppTypography.monoSmall(color: AppColors.inkMuted),
                            ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: AppColors.rule),
                    if (_isLoading)
                      const Padding(
                        padding: EdgeInsets.all(40),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (_items.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(40),
                        child: Center(
                          child: Column(
                            children: [
                              const Icon(Icons.cloud_done_outlined, size: 40, color: AppColors.inkMuted),
                              const SizedBox(height: 12),
                              Text(
                                'No government transmissions registered for $activeTenantName yet.',
                                style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Invoices issued offline or online will be monitored here under Directive No. 1142/2026.',
                                style: AppTypography.bodySmall(color: AppColors.inkMuted),
                              ),
                              const SizedBox(height: 16),
                              ElevatedButton.icon(
                                onPressed: () => context.go('/invoices/new'),
                                icon: const Icon(Icons.add, size: 16),
                                label: const Text('Create New Invoice'),
                              ),
                            ],
                          ),
                        ),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _items.length,
                        separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                        itemBuilder: (context, index) {
                          final item = _items[index];
                          return InkWell(
                            onTap: () => context.go('/invoices/${item.invoiceId}'),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  // Status Indicator
                                  Container(
                                    width: 12,
                                    height: 12,
                                    margin: const EdgeInsets.only(top: 4, right: 16),
                                    decoration: BoxDecoration(
                                      color: item.state.color,
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                                  // Invoice & IRN Details
                                  Expanded(
                                    flex: 4,
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Wrap(
                                          crossAxisAlignment: WrapCrossAlignment.center,
                                          spacing: 10,
                                          runSpacing: 4,
                                          children: [
                                            Text(
                                              item.documentNumber,
                                              style: AppTypography.mono(weight: FontWeight.w700),
                                            ),
                                            Container(
                                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                              decoration: BoxDecoration(
                                                color: item.state.color.withValues(alpha: 0.1),
                                                borderRadius: BorderRadius.circular(3),
                                                border: Border.all(color: item.state.color.withValues(alpha: 0.3)),
                                              ),
                                              child: Text(
                                                item.state.label,
                                                style: AppTypography.monoSmall(color: item.state.color, weight: FontWeight.w600),
                                              ),
                                            ),
                                          ],
                                        ),
                                        const SizedBox(height: 4),
                                        if (item.irn != null && item.irn!.isNotEmpty)
                                          Text('IRN: ${item.irn}', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                        if (item.rrn != null && item.rrn!.isNotEmpty)
                                          Text('RRN: ${item.rrn}', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                                        if (item.rejectionReason != null)
                                          Padding(
                                            padding: const EdgeInsets.only(top: 4),
                                            child: Text(
                                              'Rejection Notice: ${item.rejectionReason}',
                                              style: AppTypography.bodySmall(color: AppColors.red600),
                                            ),
                                          ),
                                      ],
                                    ),
                                  ),
                                  // 72-Hour Timer Indicator
                                  Expanded(
                                    flex: 3,
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text('72h Window Remaining:', style: AppTypography.uiLabel(color: AppColors.inkMuted)),
                                        const SizedBox(height: 2),
                                        Text(
                                          item.state == EirsSubmissionState.accepted
                                              ? 'Fully Fiscalized'
                                              : (item.isDeadlineExpired
                                                  ? 'Deadline Expired'
                                                  : '${item.remainingTime.inHours}h ${item.remainingTime.inMinutes % 60}m remaining'),
                                          style: AppTypography.mono(
                                            color: item.isDeadlineExpired
                                                ? AppColors.red600
                                                : (item.isApproachingDeadline ? AppColors.amber600 : AppColors.ink),
                                            weight: FontWeight.w600,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  // Amount
                                  Container(
                                    width: 140,
                                    alignment: Alignment.centerRight,
                                    child: Text(
                                      'ETB ${item.grandTotal.toStringAsFixed(2)}',
                                      style: AppTypography.mono(weight: FontWeight.w700, color: AppColors.navy900),
                                    ),
                                  ),
                                ],
                              ),
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

  Widget _buildMetricCard({
    required String title,
    required String value,
    required String subtitle,
    required Color color,
  }) {
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
          Text(title, style: AppTypography.uiLabelBold(color: AppColors.inkMuted)),
          const SizedBox(height: 8),
          Text(value, style: AppTypography.display(color: color).copyWith(fontSize: 32)),
          const SizedBox(height: 4),
          Text(subtitle, style: AppTypography.bodySmall()),
        ],
      ),
    );
  }
}
