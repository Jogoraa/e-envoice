import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import '../../data/local/database/app_database.dart';
import '../connectivity/connectivity_service.dart';
import '../device/device_identity_service.dart';
import '../errors/app_error.dart';
import '../networking/api_client.dart';
import '../networking/network_profile.dart';
import '../networking/network_resilience_manager.dart';
import 'sync_state_notifier.dart';

/// Enterprise Offline Synchronization Engine.
/// Strictly enforces Directive No. 1142/2026 Art. 4(4) (72h limit),
class SyncEngine {
  final AppDatabase db;
  final ApiClient apiClient;
  final DeviceIdentityService deviceIdentity;
  final ConnectivityService connectivity;
  final SyncStateNotifier syncNotifier;
  final Random _random = Random();

  AppDatabase get _db => db;
  ApiClient get _apiClient => apiClient;
  DeviceIdentityService get _deviceIdentity => deviceIdentity;
  ConnectivityService get _connectivity => connectivity;
  SyncStateNotifier get _syncNotifier => syncNotifier;

  bool _isSyncing = false;

  SyncEngine({
    required this.db,
    required this.apiClient,
    required this.deviceIdentity,
    required this.connectivity,
    required this.syncNotifier,
  }) {
    _initConnectivityListener();
  }

  void _initConnectivityListener() {
    _connectivity.statusStream.listen((status) {
      if (status == ConnectionStatus.serverAvailable) {
        debugPrint(
          '[SyncEngine] Backend available. Triggering foreground sync.',
        );
        final tenantId = _apiClient.activeTenantId;
        final branchId = _apiClient.activeBranchId;
        if (tenantId != null && branchId != null) {
          syncScopedOutbox(tenantId: tenantId, branchId: branchId);
        }
      }
    });

    _apiClient.resilienceManager.recoveryStream.listen((state) {
      if (state == NetworkState.online) {
        final tenantId = _apiClient.activeTenantId;
        final branchId = _apiClient.activeBranchId;
        if (tenantId != null && branchId != null) {
          debugPrint(
            '[SyncEngine] Network recovery detected. Triggering outbox drain.',
          );
          syncScopedOutbox(tenantId: tenantId, branchId: branchId);
        }
      }
    });
  }

  Future<void> syncOutbox({
    required String tenantId,
    required String branchId,
  }) => syncScopedOutbox(tenantId: tenantId, branchId: branchId);

  Future<void> syncScopedOutbox({
    required String tenantId,
    required String branchId,
  }) async {
    if (_isSyncing) return;
    _isSyncing = true;

    try {
      final operations = await _db.getPendingOutboxOperations(
        tenantId: tenantId,
        branchId: branchId,
      );

      if (operations.isEmpty) {
        _syncNotifier.setSynced(DateTime.now());
        return;
      }

      _syncNotifier.setSyncing(operations.length);

      // 1. Group offline invoices for batched submission to /api/v1/offline/sync
      final offlineInvoiceOps = operations
          .where(
            (op) =>
                op.operationType == 'CREATE_INVOICE' && op.offlineSeqNo != null,
          )
          .toList();

      if (offlineInvoiceOps.isNotEmpty) {
        await _processOfflineBatch(
          tenantId: tenantId,
          branchId: branchId,
          operations: offlineInvoiceOps,
        );
      }

      // 2. Process remaining single operations (cancellations, direct mutations)
      final directOps = operations
          .where(
            (op) =>
                op.operationType != 'CREATE_INVOICE' || op.offlineSeqNo == null,
          )
          .toList();

      for (final op in directOps) {
        await _processDirectOperation(op);
      }

      final remaining = await _db.getPendingOutboxOperations(
        tenantId: tenantId,
        branchId: branchId,
      );

      if (remaining.isEmpty) {
        _syncNotifier.setSynced(DateTime.now());
      } else {
        _syncNotifier.setOfflineDrafts(remaining.length);
      }
    } catch (e) {
      debugPrint('[SyncEngine] Sync error: $e');
      _syncNotifier.setError(e.toString(), 1);
    } finally {
      _isSyncing = false;
    }
  }

  Future<void> _processOfflineBatch({
    required String tenantId,
    required String branchId,
    required List<OutboxOperationRecord> operations,
  }) async {
    final deviceId = await _deviceIdentity.getDeviceId();
    final now = DateTime.now();

    final List<Map<String, dynamic>> validTransactions = [];
    final List<OutboxOperationRecord> validOps = [];

    for (final op in operations) {
      // 72-Hour statutory continuity validation (Directive 1142/2026 Art. 4(4))
      final hoursElapsed = now.difference(op.bufferedAt).inHours;
      if (hoursElapsed > 72) {
        debugPrint(
          '[SyncEngine] Statutory 72-hour limit exceeded for op ${op.operationId}',
        );
        await _db.updateOutboxState(
          operationId: op.operationId,
          syncState: 'permanentFailure',
          lastError:
              'OFFLINE_BATCH_EXPIRED: Exceeded 72h limit ($hoursElapsed hrs elapsed)',
        );
        continue;
      }

      final signature =
          op.deviceSignature ??
          await _deviceIdentity.signOfflineTransaction(
            documentNumber: 'DOC-${op.offlineSeqNo}',
            totalAmount: '0.00',
            timestampIso: op.bufferedAt.toIso8601String(),
          );

      validTransactions.add({
        'offlineSeqNo': op.offlineSeqNo,
        'bufferedAt': op.bufferedAt.toUtc().toIso8601String(),
        'payloadJson': op.payloadJson,
        'deviceSignature': signature,
      });
      validOps.add(op);
    }

    if (validTransactions.isEmpty) return;

    final batchRequest = {
      'deviceId': deviceId,
      'transactions': validTransactions,
    };

    final batchIdempotencyKey =
        'sync_batch_${deviceId}_${validOps.map((e) => e.operationId).join('_')}';

    try {
      final response = await _apiClient.post(
        '/api/v1/offline/sync',
        data: batchRequest,
        idempotencyKey: batchIdempotencyKey,
        profile: NetworkProfiles.background,
      );

      if (response.statusCode == 202 || response.statusCode == 200) {
        // Batch successfully buffered on backend for MoR reconciliation
        for (final op in validOps) {
          await _db.deleteCompletedOutboxOperation(op.operationId);
          // Mark corresponding local invoice as synced / server buffered
          await _db.updateInvoiceStatus(
            tenantId: tenantId,
            branchId: branchId,
            localId: op.operationId, // localId matches operationId
            status: 'synced',
          );
        }
      }
    } on AppError catch (err) {
      if (err.status == 409) {
        // Replayed / Duplicate sequence detected -> Reconcile state
        debugPrint(
          '[SyncEngine] Duplicate sequence detected. Reconciling server state.',
        );
        for (final op in validOps) {
          await _db.deleteCompletedOutboxOperation(op.operationId);
          await _db.updateInvoiceStatus(
            tenantId: tenantId,
            branchId: branchId,
            localId: op.operationId,
            status: 'synced',
          );
        }
      } else {
        _applyExponentialBackoff(validOps, err.message);
      }
    } catch (e) {
      _applyExponentialBackoff(validOps, e.toString());
    }
  }

  Future<void> _processDirectOperation(OutboxOperationRecord op) async {
    try {
      final payload = jsonDecode(op.payloadJson);
      final response = await _apiClient.post(
        op.endpoint,
        data: payload,
        idempotencyKey: op.idempotencyKey,
      );

      if (response.statusCode == 200 ||
          response.statusCode == 201 ||
          response.statusCode == 202) {
        await _db.deleteCompletedOutboxOperation(op.operationId);
        final data = response.data;
        if (data is Map<String, dynamic>) {
          await _db.updateInvoiceStatus(
            tenantId: op.tenantId,
            branchId: op.branchId,
            localId: op.operationId,
            status: 'synced',
            serverId: data['id']?.toString(),
            irn: data['irn']?.toString(),
            rrn: data['rrn']?.toString(),
            signedQr: data['signedQr']?.toString(),
          );
        }
      }
    } on AppError catch (err) {
      await _db.updateOutboxState(
        operationId: op.operationId,
        syncState: 'retryableFailure',
        lastError: err.message,
      );
    } catch (e) {
      await _db.updateOutboxState(
        operationId: op.operationId,
        syncState: 'retryableFailure',
        lastError: e.toString(),
      );
    }
  }

  void _applyExponentialBackoff(
    List<OutboxOperationRecord> ops,
    String errorMessage,
  ) {
    for (final op in ops) {
      final attempts = op.attemptCount + 1;
      // Exponential backoff with jitter: min(300s, 2^attempts * 2s + rand(0..3s))
      final backoffSeconds = min(
        300,
        pow(2, min(attempts, 8)).toInt() * 2 + _random.nextInt(4),
      );
      final nextAttempt = DateTime.now().add(Duration(seconds: backoffSeconds));

      _db.updateOutboxState(
        operationId: op.operationId,
        syncState: 'retryableFailure',
        lastError: errorMessage,
        nextAttemptAt: nextAttempt,
      );
    }
  }
}
