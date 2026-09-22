import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/local/database/app_database.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../shared/widgets/status/status_pill.dart';

class OfflineQueueScreen extends ConsumerStatefulWidget {
  const OfflineQueueScreen({super.key});

  @override
  ConsumerState<OfflineQueueScreen> createState() => _OfflineQueueScreenState();
}

class _OfflineQueueScreenState extends ConsumerState<OfflineQueueScreen> {
  bool _isSyncing = false;
  List<OutboxOperationRecord> _operations = [];

  @override
  void initState() {
    super.initState();
    _loadOutbox();
  }

  Future<void> _loadOutbox() async {
    final tenantState = ref.read(tenantContextProvider);
    final tenantId =
        tenantState.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
    final branchId =
        tenantState.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

    final db = ref.read(appDatabaseProvider);
    final list = await db.getPendingOutboxOperations(
      tenantId: tenantId,
      branchId: branchId,
    );

    setState(() {
      _operations = list;
    });
  }

  Future<void> _triggerManualSync() async {
    setState(() => _isSyncing = true);

    try {
      final tenantState = ref.read(tenantContextProvider);
      final tenantId =
          tenantState.activeTenant?.id ??
          '00000000-0000-0000-0000-000000000001';
      final branchId =
          tenantState.activeBranch?.id ??
          '00000000-0000-0000-0000-000000000010';

      final engine = ref.read(syncEngineProvider);
      await engine.syncScopedOutbox(tenantId: tenantId, branchId: branchId);

      await _loadOutbox();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sync failed: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    } finally {
      setState(() => _isSyncing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final loc = AppLocalizations.of(context);
    final now = DateTime.now();

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'OFFLINE SYNCHRONIZATION QUEUE',
                        style: AppTypography.h1(),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Directive No. 1142/2026 Art. 4(4) Resilient Transaction Outbox',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  onPressed: _isSyncing ? null : _triggerManualSync,
                  icon: _isSyncing
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor: AlwaysStoppedAnimation(Colors.white),
                          ),
                        )
                      : const Icon(Icons.sync, size: 16),
                  label: Text(_isSyncing ? 'Syncing...' : loc.get('sync_now')),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Statutory 72-Hour Compliance Meter Banner
            Container(
              padding: const EdgeInsets.all(16),
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
                        child: Row(
                          children: [
                            const Icon(
                              Icons.timer_outlined,
                              size: 18,
                              color: AppColors.navy900,
                            ),
                            const SizedBox(width: 8),
                            Flexible(
                              child: Text(
                                'STATUTORY 72-HOUR RECONCILIATION WINDOW',
                                style: AppTypography.uiLabelBold(),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text(
                        '${_operations.length} pending items buffered locally',
                        style: AppTypography.monoSmall(
                          color: AppColors.inkMuted,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(2),
                    child: LinearProgressIndicator(
                      value: _operations.isEmpty
                          ? 0.0
                          : 0.15, // 15% of statutory window elapsed
                      backgroundColor: AppColors.rule,
                      valueColor: const AlwaysStoppedAnimation(
                        AppColors.amber600,
                      ),
                      minHeight: 6,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Outbox Ledger Table
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: _operations.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.cloud_done_outlined,
                              size: 48,
                              color: AppColors.green700,
                            ),
                            const SizedBox(height: 12),
                            Text(
                              'All offline transactions are synchronized',
                              style: AppTypography.h2(),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'No pending outbox operations waiting for transmission.',
                              style: AppTypography.bodySmall(
                                color: AppColors.inkMuted,
                              ),
                            ),
                          ],
                        ),
                      )
                    : SingleChildScrollView(
                        scrollDirection: Axis.horizontal,
                        child: SingleChildScrollView(
                          scrollDirection: Axis.vertical,
                          child: DataTable(
                            columns: const [
                              DataColumn(label: Text('Operation ID')),
                              DataColumn(label: Text('Type')),
                              DataColumn(label: Text('Offline Seq #')),
                              DataColumn(label: Text('Buffered At')),
                              DataColumn(label: Text('Age / Limit')),
                              DataColumn(label: Text('Attempts')),
                              DataColumn(label: Text('Sync State')),
                            ],
                            rows: _operations.map((op) {
                              final hoursElapsed = now
                                  .difference(op.bufferedAt)
                                  .inHours;
                              final isExpired = hoursElapsed > 72;

                              return DataRow(
                                cells: [
                                  DataCell(
                                    Text(
                                      op.operationId.substring(0, 8),
                                      style: AppTypography.mono(),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      op.operationType,
                                      style: AppTypography.bodySmall(),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      op.offlineSeqNo != null
                                          ? '#${op.offlineSeqNo}'
                                          : 'N/A',
                                      style: AppTypography.mono(),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      op.bufferedAt
                                          .toLocal()
                                          .toString()
                                          .substring(0, 16),
                                      style: AppTypography.monoSmall(),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      '$hoursElapsed hrs / 72 hrs',
                                      style: AppTypography.monoSmall(
                                        color: isExpired
                                            ? AppColors.red600
                                            : AppColors.ink,
                                        weight: isExpired
                                            ? FontWeight.w700
                                            : FontWeight.w400,
                                      ),
                                    ),
                                  ),
                                  DataCell(
                                    Text(
                                      '${op.attemptCount}',
                                      style: AppTypography.mono(),
                                    ),
                                  ),
                                  DataCell(
                                    StatusPill(
                                      status: isExpired
                                          ? InvoiceSyncStatus.syncError
                                          : (op.syncState == 'syncing'
                                                ? InvoiceSyncStatus.syncing
                                                : InvoiceSyncStatus
                                                      .offlineDraft),
                                      errorMessage: isExpired
                                          ? 'Exceeded statutory 72h limit'
                                          : op.lastError,
                                    ),
                                  ),
                                ],
                              );
                            }).toList(),
                          ),
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
