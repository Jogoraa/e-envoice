import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../shared/widgets/status/status_pill.dart';

class SyncState {
  final InvoiceSyncStatus status;
  final int pendingCount;
  final int errorCount;
  final DateTime? lastSyncTime;
  final String? lastError;

  const SyncState({
    this.status = InvoiceSyncStatus.synced,
    this.pendingCount = 0,
    this.errorCount = 0,
    this.lastSyncTime,
    this.lastError,
  });

  SyncState copyWith({
    InvoiceSyncStatus? status,
    int? pendingCount,
    int? errorCount,
    DateTime? lastSyncTime,
    String? lastError,
  }) {
    return SyncState(
      status: status ?? this.status,
      pendingCount: pendingCount ?? this.pendingCount,
      errorCount: errorCount ?? this.errorCount,
      lastSyncTime: lastSyncTime ?? this.lastSyncTime,
      lastError: lastError ?? this.lastError,
    );
  }
}

class SyncStateNotifier extends StateNotifier<SyncState> {
  SyncStateNotifier() : super(const SyncState());

  void setSyncing(int count) {
    state = state.copyWith(
      status: InvoiceSyncStatus.syncing,
      pendingCount: count,
      lastError: null,
    );
  }

  void setSynced(DateTime time) {
    state = state.copyWith(
      status: InvoiceSyncStatus.synced,
      pendingCount: 0,
      errorCount: 0,
      lastSyncTime: time,
      lastError: null,
    );
  }

  void setOfflineDrafts(int count) {
    state = state.copyWith(
      status: InvoiceSyncStatus.offlineDraft,
      pendingCount: count,
      lastError: null,
    );
  }

  void setError(String error, int errorCount) {
    state = state.copyWith(
      status: InvoiceSyncStatus.syncError,
      errorCount: errorCount,
      lastError: error,
    );
  }
}

final syncStateProvider = StateNotifierProvider<SyncStateNotifier, SyncState>((ref) {
  return SyncStateNotifier();
});
