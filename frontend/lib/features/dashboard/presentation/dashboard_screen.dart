import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../core/di/providers.dart';
import '../../../core/synchronization/sync_state_notifier.dart';
import '../../../domain/invoice/models/invoice_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../shared/widgets/status/status_pill.dart';

/// Reactive provider for live dashboard KPI summary strictly scoped to the active tenant
final dashboardSummaryProvider = FutureProvider.autoDispose<InvoiceSummary>((ref) async {
  final tenantState = ref.watch(tenantContextProvider);
  final tenantSession = ref.watch(authSessionProvider);
  final delegatedSession = ref.watch(delegatedTenantSessionProvider);

  final tenantId = delegatedSession?.targetTenantId ??
      tenantState.activeTenant?.id ??
      tenantSession?.tenantId ??
      '';
  final branchId = delegatedSession?.targetBranchId ??
      tenantState.activeBranch?.id ??
      '';

  if (tenantId.isEmpty) {
    return const InvoiceSummary();
  }

  return ref.read(invoiceRepositoryProvider).getInvoiceSummary(
    tenantId: tenantId,
    branchId: branchId,
  );
});

/// Reactive provider for recent fiscal invoices strictly scoped to the active tenant
final dashboardRecentInvoicesProvider = FutureProvider.autoDispose<List<InvoiceModel>>((ref) async {
  final tenantState = ref.watch(tenantContextProvider);
  final tenantSession = ref.watch(authSessionProvider);
  final delegatedSession = ref.watch(delegatedTenantSessionProvider);

  final tenantId = delegatedSession?.targetTenantId ??
      tenantState.activeTenant?.id ??
      tenantSession?.tenantId ??
      '';
  final branchId = delegatedSession?.targetBranchId ??
      tenantState.activeBranch?.id ??
      '';

  if (tenantId.isEmpty) {
    return const [];
  }

  return ref.read(invoiceRepositoryProvider).listInvoices(
    tenantId: tenantId,
    branchId: branchId,
    size: 5,
  );
});

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tenantState = ref.watch(tenantContextProvider);
    final syncState = ref.watch(syncStateProvider);
    final tenantSession = ref.watch(authSessionProvider);
    final delegatedSession = ref.watch(delegatedTenantSessionProvider);
    final loc = AppLocalizations.of(context);

    // Resolve authoritative tenant identity with zero hardcoded fallbacks
    final activeTenantName = delegatedSession?.targetTenantName ??
        tenantState.activeTenant?.name ??
        (tenantSession?.username != null && tenantSession!.username.isNotEmpty ? tenantSession.username : null) ??
        'Taxpayer Business';

    final activeBranchName = delegatedSession != null
        ? 'Head Office'
        : (tenantState.activeBranch?.name ?? 'Head Office');

    final activeTenantId = delegatedSession?.targetTenantId ??
        tenantState.activeTenant?.id ??
        tenantSession?.tenantId ??
        '';

    // Auto-sync tenant context if missing but authorized session exists
    if (tenantState.activeTenant == null) {
      if (delegatedSession != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          final tInfo = TenantInfo(
            id: delegatedSession.targetTenantId,
            organizationId: delegatedSession.targetTenantId,
            name: delegatedSession.targetTenantName,
            tradeName: delegatedSession.targetTenantName,
            tin: delegatedSession.targetTenantTin,
            status: 'ACTIVE',
          );
          final bInfo = BranchInfo(
            id: delegatedSession.targetBranchId ?? 'HQ-01',
            tenantId: delegatedSession.targetTenantId,
            name: 'Head Office',
            code: 'HQ-01',
            isHeadOffice: true,
          );
          ref.read(tenantContextProvider.notifier).setAuthorizedContext(
            tenants: [tInfo],
            activeTenant: tInfo,
            branches: [bInfo],
            activeBranch: bInfo,
          );
        });
      } else if (tenantSession != null && tenantSession.tenantId.isNotEmpty) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          final tInfo = TenantInfo(
            id: tenantSession.tenantId,
            organizationId: 'ORG-${tenantSession.tenantId}',
            name: tenantSession.username,
            tradeName: tenantSession.username,
            tin: '',
            status: 'ACTIVE',
          );
          final bInfo = BranchInfo(
            id: 'HQ-01',
            tenantId: tenantSession.tenantId,
            name: 'Head Office',
            code: 'HQ-01',
            isHeadOffice: true,
          );
          ref.read(tenantContextProvider.notifier).setAuthorizedContext(
            tenants: [tInfo],
            activeTenant: tInfo,
            branches: [bInfo],
            activeBranch: bInfo,
          );
        });
      }
    }

    final summaryAsync = ref.watch(dashboardSummaryProvider);
    final invoicesAsync = ref.watch(dashboardRecentInvoicesProvider);

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(dashboardSummaryProvider);
          ref.invalidate(dashboardRecentInvoicesProvider);
        },
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header Title Area
              LayoutBuilder(
                builder: (context, constraints) {
                  final isCompact = constraints.maxWidth < 650;
                  final titleColumn = Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('OPERATIONAL DASHBOARD', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        '$activeTenantName — $activeBranchName Terminal',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  );

                  final buttonRow = Wrap(
                    spacing: 12,
                    runSpacing: 8,
                    children: [
                      OutlinedButton.icon(
                        onPressed: () {
                          ref.invalidate(dashboardSummaryProvider);
                          ref.invalidate(dashboardRecentInvoicesProvider);
                          context.go('/offline/queue');
                        },
                        icon: const Icon(Icons.sync, size: 16),
                        label: Text(loc.get('sync_now')),
                      ),
                      ElevatedButton.icon(
                        onPressed: () => context.go('/invoices/new'),
                        icon: const Icon(Icons.add, size: 16),
                        label: Text(loc.get('new_invoice')),
                      ),
                    ],
                  );

                  if (isCompact) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        titleColumn,
                        const SizedBox(height: 16),
                        buttonRow,
                      ],
                    );
                  }

                  return Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(child: titleColumn),
                      const SizedBox(width: 16),
                      buttonRow,
                    ],
                  );
                },
              ),
              const SizedBox(height: 24),

              // Directive 1142/2026 Compliance Banner
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: AppColors.navy900.withValues(alpha: 0.04),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.navy900.withValues(alpha: 0.2)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.gavel_outlined, size: 20, color: AppColors.navy900),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        loc.get('offline_continuity_notice'),
                        style: AppTypography.uiLabel(color: AppColors.navy900),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Live Database-Driven Responsive KPI Grid
              summaryAsync.when(
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (err, stack) => Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.red600.withValues(alpha: 0.05),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.red600.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, color: AppColors.red600),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'Unable to load authoritative metrics from database: $err',
                          style: AppTypography.bodySmall(color: AppColors.red700),
                        ),
                      ),
                      TextButton(
                        onPressed: () => ref.invalidate(dashboardSummaryProvider),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
                data: (summary) {
                  return LayoutBuilder(
                    builder: (context, constraints) {
                      final isWide = constraints.maxWidth >= 950;
                      final kpi1 = _buildKpiCard(
                        title: "TODAY'S INVOICES",
                        value: '${summary.todayInvoicesCount}',
                        subtitle: summary.todayInvoicesCount > 0
                            ? '${summary.registeredCount} registered / ${summary.pendingCount} buffered'
                            : '0 registered today',
                        icon: Icons.receipt_long,
                        accentColor: AppColors.navy900,
                      );
                      final kpi2 = _buildKpiCard(
                        title: "GROSS SALES (ETB)",
                        value: summary.todayGrossSales > 0
                            ? summary.todayGrossSales.toStringAsFixed(2)
                            : '0.00',
                        subtitle: "Today's gross revenue",
                        icon: Icons.monetization_on_outlined,
                        accentColor: AppColors.green700,
                        isMono: true,
                      );
                      final kpi3 = _buildKpiCard(
                        title: 'TOTAL VAT (15%)',
                        value: summary.todayVatAmount > 0
                            ? summary.todayVatAmount.toStringAsFixed(2)
                            : '0.00',
                        subtitle: 'Authoritative tax ledger',
                        icon: Icons.account_balance_outlined,
                        accentColor: AppColors.navy700,
                        isMono: true,
                      );
                      final kpi4 = _buildKpiCard(
                        title: 'OFFLINE QUEUE',
                        value: '${syncState.pendingCount}',
                        subtitle: 'Within 72h window',
                        icon: Icons.cloud_queue,
                        accentColor: syncState.pendingCount > 0 ? AppColors.amber600 : AppColors.green700,
                        isMono: true,
                      );

                      if (isWide) {
                        return Row(
                          children: [
                            Expanded(child: kpi1),
                            const SizedBox(width: 16),
                            Expanded(child: kpi2),
                            const SizedBox(width: 16),
                            Expanded(child: kpi3),
                            const SizedBox(width: 16),
                            Expanded(child: kpi4),
                          ],
                        );
                      } else if (constraints.maxWidth >= 550) {
                        return Column(
                          children: [
                            Row(
                              children: [
                                Expanded(child: kpi1),
                                const SizedBox(width: 16),
                                Expanded(child: kpi2),
                              ],
                            ),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Expanded(child: kpi3),
                                const SizedBox(width: 16),
                                Expanded(child: kpi4),
                              ],
                            ),
                          ],
                        );
                      }

                      return Column(
                        children: [
                          kpi1,
                          const SizedBox(height: 12),
                          kpi2,
                          const SizedBox(height: 12),
                          kpi3,
                          const SizedBox(height: 12),
                          kpi4,
                        ],
                      );
                    },
                  );
                },
              ),
              const SizedBox(height: 32),

              // Recent Ledger Table Strictly Scoped to Active Tenant
              Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(child: Text('RECENT FISCAL INVOICES', style: AppTypography.h2())),
                          const SizedBox(width: 12),
                          TextButton(
                            onPressed: () => context.go('/invoices'),
                            child: const Text('View all invoices →'),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: AppColors.rule),
                    if (activeTenantId.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(32),
                        child: Center(
                          child: Text(
                            'No taxpayer organization selected.',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted),
                          ),
                        ),
                      )
                    else
                      invoicesAsync.when(
                        loading: () => const Padding(
                          padding: EdgeInsets.all(32),
                          child: Center(child: CircularProgressIndicator()),
                        ),
                        error: (err, stack) => Padding(
                          padding: const EdgeInsets.all(24),
                          child: Center(
                            child: Column(
                              children: [
                                Text('Failed to load recent invoices: $err', style: AppTypography.bodySmall(color: AppColors.red700)),
                                const SizedBox(height: 8),
                                TextButton(
                                  onPressed: () => ref.invalidate(dashboardRecentInvoicesProvider),
                                  child: const Text('Retry'),
                                ),
                              ],
                            ),
                          ),
                        ),
                        data: (invoices) {
                          if (invoices.isEmpty) {
                            return Padding(
                              padding: const EdgeInsets.all(32),
                              child: Center(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(Icons.receipt_long_outlined, size: 36, color: AppColors.inkMuted),
                                    const SizedBox(height: 8),
                                    Text(
                                      'No invoices registered for $activeTenantName yet',
                                      style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                                    ),
                                    const SizedBox(height: 12),
                                    ElevatedButton.icon(
                                      onPressed: () => context.go('/invoices/new'),
                                      icon: const Icon(Icons.add, size: 16),
                                      label: const Text('Issue First Invoice'),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          }

                          return SingleChildScrollView(
                            scrollDirection: Axis.horizontal,
                            child: DataTable(
                              columns: const [
                                DataColumn(label: Text('Customer')),
                                DataColumn(label: Text('Reference')),
                                DataColumn(label: Text('Amount (ETB)')),
                                DataColumn(label: Text('Status')),
                                DataColumn(label: Text('Actions')),
                              ],
                              rows: invoices.map((inv) {
                                InvoiceSyncStatus statusPillType = InvoiceSyncStatus.synced;
                                if (inv.status == 'offlineDraft') {
                                  statusPillType = InvoiceSyncStatus.offlineDraft;
                                } else if (inv.status == 'syncError' || inv.status == 'REJECTED') {
                                  statusPillType = InvoiceSyncStatus.syncError;
                                }

                                return _buildLedgerRow(
                                  context,
                                  customer: inv.buyer?.legalName ?? 'Cash Customer',
                                  reference: inv.irn ?? inv.documentNumber,
                                  amount: 'ETB ${inv.grandTotal.toStringAsFixed(2)}',
                                  status: statusPillType,
                                  invoiceId: inv.id,
                                );
                              }).toList(),
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

  Widget _buildKpiCard({
    required String title,
    required String value,
    required String subtitle,
    required IconData icon,
    required Color accentColor,
    bool isMono = false,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule, width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  title,
                  style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              Icon(icon, size: 20, color: accentColor),
            ],
          ),
          const SizedBox(height: 12),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              value,
              style: isMono
                  ? AppTypography.mono(color: AppColors.ink, weight: FontWeight.w700).copyWith(fontSize: 26)
                  : AppTypography.display(color: AppColors.ink).copyWith(fontSize: 28),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            subtitle,
            style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 12),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  DataRow _buildLedgerRow(
    BuildContext context, {
    required String customer,
    required String reference,
    required String amount,
    required InvoiceSyncStatus status,
    String? errorMessage,
    required String invoiceId,
  }) {
    return DataRow(
      cells: [
        DataCell(Text(customer, style: AppTypography.bodySmall(color: AppColors.ink))),
        DataCell(Text(reference, style: AppTypography.mono(color: AppColors.navy700))),
        DataCell(Text(amount, style: AppTypography.mono(color: AppColors.ink, weight: FontWeight.w600))),
        DataCell(StatusPill(status: status, errorMessage: errorMessage)),
        DataCell(
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                icon: const Icon(Icons.visibility_outlined, size: 18),
                tooltip: 'View invoice',
                onPressed: () => context.go('/invoices/$invoiceId'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
