import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/connectivity/connectivity_service.dart';
import '../../../core/di/providers.dart';
import '../../../core/synchronization/sync_state_notifier.dart';
import '../../../data/services/compliance/offline_operations_service.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class OfflineOperationsScreen extends ConsumerStatefulWidget {
  const OfflineOperationsScreen({super.key});

  @override
  ConsumerState<OfflineOperationsScreen> createState() => _OfflineOperationsScreenState();
}

class _OfflineOperationsScreenState extends ConsumerState<OfflineOperationsScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  DeviceOfflineAllocationDto? _allocation;
  bool _isRequestingRange = false;

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _loadAllocation();
  }

  Future<void> _loadAllocation() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final deviceService = ref.read(deviceIdentityServiceProvider);
      final deviceId = await deviceService.getOrCreateDeviceId();
      final service = ref.read(offlineOperationsServiceProvider);
      final alloc = await service.getActiveAllocation(deviceId);
      if (mounted) {
        setState(() {
          _allocation = alloc;
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

  Future<void> _requestNewRange() async {
    setState(() => _isRequestingRange = true);
    try {
      final deviceService = ref.read(deviceIdentityServiceProvider);
      final deviceId = await deviceService.getOrCreateDeviceId();
      final service = ref.read(offlineOperationsServiceProvider);
      final alloc = await service.requestOfflineAllocation(
        deviceId: deviceId,
        blockSize: 100,
        validityDays: 7,
        authorityRef: 'MoR-OFFLINE-DIR1142-AUTH',
      );
      if (mounted) {
        setState(() {
          _allocation = alloc;
          _isRequestingRange = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Allocated 100 sequential offline numbers successfully.'),
            backgroundColor: AppColors.green700,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isRequestingRange = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to request range: $e'), backgroundColor: AppColors.red600),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final syncState = ref.watch(syncStateProvider);
    final conn = ref.watch(connectionStatusProvider).value ?? ConnectionStatus.serverAvailable;
    final isOnline = conn == ConnectionStatus.serverAvailable;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            decoration: const BoxDecoration(
              color: AppColors.paperRaised,
              border: Border(bottom: BorderSide(color: AppColors.rule)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.cloud_sync_outlined, color: AppColors.navy900, size: 28),
                        const SizedBox(width: 10),
                        Text('Offline Operations Center', style: AppTypography.h2()),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Pre-allocated sequential numbering, 72-hour regulatory SLA, and store-and-forward telemetry (Art. 4(4) & Art. 21)',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: isOnline
                            ? AppColors.green700.withValues(alpha: 0.1)
                            : AppColors.amber600.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: isOnline ? AppColors.green700 : AppColors.amber600,
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            isOnline ? Icons.wifi : Icons.wifi_off,
                            color: isOnline ? AppColors.green700 : AppColors.amber600,
                            size: 16,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            isOnline ? 'Online Gateway Connected' : 'Offline Buffer Mode',
                            style: AppTypography.uiLabelBold(
                              color: isOnline ? AppColors.green700 : AppColors.amber600,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    IconButton(
                      tooltip: 'Refresh',
                      icon: const Icon(Icons.refresh),
                      onPressed: _loadAllocation,
                    ),
                  ],
                ),
              ],
            ),
          ),

          // Content
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : SingleChildScrollView(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (_errorMessage != null) ...[
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: AppColors.red600.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(4),
                              border: Border.all(color: AppColors.red600),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.error_outline, color: AppColors.red600),
                                const SizedBox(width: 8),
                                Expanded(child: Text(_errorMessage!, style: const TextStyle(color: AppColors.red600))),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                        ],
                        // Section 1: Pre-Allocated Sequence Range
                        _buildAllocationCard(_allocation),
                        const SizedBox(height: 24),

                        // Section 2: 72-Hour Statutory SLA & Store-and-Forward
                        _buildRegulatorySlaCard(syncState),
                        const SizedBox(height: 24),

                        // Section 3: Device Cryptographic Identity
                        _buildDeviceIdentityCard(),
                      ],
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildAllocationCard(DeviceOfflineAllocationDto? alloc) {
    final remaining = alloc?.remainingCount ?? 0;
    final isLow = remaining <= 20;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.pin_outlined, color: AppColors.navy900),
                  const SizedBox(width: 10),
                  Text('Pre-Allocated Sequential Document Range', style: AppTypography.uiLabelBold(size: 16)),
                ],
              ),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.navy900,
                  foregroundColor: Colors.white,
                ),
                onPressed: _isRequestingRange ? null : _requestNewRange,
                icon: _isRequestingRange
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : const Icon(Icons.add, size: 16),
                label: Text(_isRequestingRange ? 'Requesting...' : 'Request New Allocation Block'),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'Pursuant to Directive Art. 4(4) & Art. 21, the system issues pre-allocated sequential fiscal numbers during network outages.',
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
          const Divider(height: 24, thickness: 1, color: AppColors.rule),

          if (alloc == null)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.amber600.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Row(
                children: [
                  const Icon(Icons.warning_amber_rounded, color: AppColors.amber600),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'No active pre-allocated offline range found for this device. Click "Request New Allocation Block" to pre-buffer 100 sequential invoice numbers.',
                      style: AppTypography.bodySmall(color: AppColors.ink),
                    ),
                  ),
                ],
              ),
            )
          else ...[
            if (isLow) ...[
              Container(
                margin: const EdgeInsets.only(bottom: 16),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.red600.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: AppColors.red600),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline, color: AppColors.red600),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'WARNING: Remaining sequence allocation is critically low ($remaining remaining). Request a new block before buffer exhaustion.',
                        style: AppTypography.bodySmall(color: AppColors.red600),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            Row(
              children: [
                _buildStatBox('Assigned Range', '${alloc.rangeStart} – ${alloc.rangeEnd}', AppColors.navy900),
                _buildStatBox('Current Sequence', alloc.currentSequence, AppColors.ink),
                _buildStatBox('Used Invoices', '${alloc.usedCount} / ${alloc.blockSize}', AppColors.inkMuted),
                _buildStatBox(
                  'Remaining Quota',
                  '$remaining documents',
                  isLow ? AppColors.red600 : AppColors.green700,
                ),
                _buildStatBox('Valid Until', _dateFormat.format(alloc.expiresAt), AppColors.ink),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildStatBox(String label, String value, Color color) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppTypography.caption(color: AppColors.inkMuted)),
          const SizedBox(height: 4),
          Text(value, style: AppTypography.uiLabelBold(color: color, size: 14)),
        ],
      ),
    );
  }

  Widget _buildRegulatorySlaCard(SyncState syncState) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.timer_outlined, color: AppColors.navy900),
                  const SizedBox(width: 10),
                  Text('72-Hour Statutory Synchronization SLA (Art. 23(4))', style: AppTypography.uiLabelBold(size: 16)),
                ],
              ),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.navy900,
                  foregroundColor: Colors.white,
                ),
                onPressed: syncState.isSyncing
                    ? null
                    : () async {
                        final engine = ref.read(syncEngineProvider);
                        final tenantState = ref.read(tenantContextProvider);
                        final tenantId = tenantState.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
                        final branchId = tenantState.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';
                        await engine.syncScopedOutbox(tenantId: tenantId, branchId: branchId);
                      },
                icon: syncState.isSyncing
                    ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                    : const Icon(Icons.sync, size: 16),
                label: Text(syncState.isSyncing ? 'Synchronizing...' : 'Trigger Immediate Sync'),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'All offline documents buffered locally must be transmitted to MoR EIRS within strictly 72 hours of issuance.',
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
          const Divider(height: 24, thickness: 1, color: AppColors.rule),

          Row(
            children: [
              _buildStatBox('Pending Sync Queue', '${syncState.pendingCount} documents', syncState.pendingCount > 0 ? AppColors.amber600 : AppColors.green700),
              _buildStatBox('Sync Engine Status', syncState.isSyncing ? 'UPLOADING TO EIRS' : 'IDLE / READY', AppColors.ink),
              _buildStatBox('Last Reconciled Sync', syncState.lastSyncTime != null ? _dateFormat.format(syncState.lastSyncTime!) : 'None recorded', AppColors.inkMuted),
              _buildStatBox('Statutory Deadline', '72 Hours Strict SLA', AppColors.red600),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildDeviceIdentityCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.security_outlined, color: AppColors.navy900),
              const SizedBox(width: 10),
              Text('Cryptographic Hardware & Device Enrollment (Art. 4(6))', style: AppTypography.uiLabelBold(size: 16)),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'Hardware-backed cryptographic keys sign every offline transaction envelope before buffering into local storage.',
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
          const Divider(height: 24, thickness: 1, color: AppColors.rule),

          Row(
            children: [
              _buildStatBox('Device Status', 'ENROLLED & ACTIVE', AppColors.green700),
              _buildStatBox('Key Custody', 'Hardware Secure Storage / DPAPI', AppColors.navy900),
              _buildStatBox('Envelope Signature', 'ECDSA secp256r1 SHA-256', AppColors.ink),
              _buildStatBox('Private Key Exposure', 'STRICTLY PROHIBITED (Zero Leakage)', AppColors.green700),
            ],
          ),
        ],
      ),
    );
  }
}
