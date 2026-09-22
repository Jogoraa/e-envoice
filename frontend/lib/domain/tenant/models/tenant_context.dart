import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/di/providers.dart';
import '../../../core/storage/secure_storage_service.dart';

class TenantInfo {
  final String id;
  final String organizationId;
  final String name;
  final String tradeName;
  final String tin;
  final String status;

  const TenantInfo({
    required this.id,
    required this.organizationId,
    required this.name,
    required this.tradeName,
    required this.tin,
    required this.status,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'organizationId': organizationId,
        'name': name,
        'tradeName': tradeName,
        'tin': tin,
        'status': status,
      };

  factory TenantInfo.fromJson(Map<String, dynamic> json) => TenantInfo(
        id: json['id']?.toString() ?? '',
        organizationId: json['organizationId']?.toString() ?? '',
        name: json['name']?.toString() ?? '',
        tradeName: json['tradeName']?.toString() ?? '',
        tin: json['tin']?.toString() ?? '',
        status: json['status']?.toString() ?? 'ACTIVE',
      );
}

class BranchInfo {
  final String id;
  final String tenantId;
  final String name;
  final String code;
  final bool isHeadOffice;

  const BranchInfo({
    required this.id,
    required this.tenantId,
    required this.name,
    required this.code,
    this.isHeadOffice = false,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'tenantId': tenantId,
        'name': name,
        'code': code,
        'isHeadOffice': isHeadOffice,
      };

  factory BranchInfo.fromJson(Map<String, dynamic> json) => BranchInfo(
        id: json['id']?.toString() ?? '',
        tenantId: json['tenantId']?.toString() ?? '',
        name: json['name']?.toString() ?? '',
        code: json['code']?.toString() ?? '',
        isHeadOffice: json['isHeadOffice'] == true,
      );
}

class TenantContextState {
  final TenantInfo? activeTenant;
  final BranchInfo? activeBranch;
  final List<TenantInfo> authorizedTenants;
  final List<BranchInfo> authorizedBranches;

  const TenantContextState({
    this.activeTenant,
    this.activeBranch,
    this.authorizedTenants = const [],
    this.authorizedBranches = const [],
  });

  Map<String, dynamic> toJson() => {
        'activeTenant': activeTenant?.toJson(),
        'activeBranch': activeBranch?.toJson(),
        'authorizedTenants': authorizedTenants.map((t) => t.toJson()).toList(),
        'authorizedBranches': authorizedBranches.map((b) => b.toJson()).toList(),
      };

  factory TenantContextState.fromJson(Map<String, dynamic> json) {
    return TenantContextState(
      activeTenant: json['activeTenant'] != null
          ? TenantInfo.fromJson(Map<String, dynamic>.from(json['activeTenant'] as Map))
          : null,
      activeBranch: json['activeBranch'] != null
          ? BranchInfo.fromJson(Map<String, dynamic>.from(json['activeBranch'] as Map))
          : null,
      authorizedTenants: (json['authorizedTenants'] as List<dynamic>?)
              ?.map((t) => TenantInfo.fromJson(Map<String, dynamic>.from(t as Map)))
              .toList() ??
          const [],
      authorizedBranches: (json['authorizedBranches'] as List<dynamic>?)
              ?.map((b) => BranchInfo.fromJson(Map<String, dynamic>.from(b as Map)))
              .toList() ??
          const [],
    );
  }

  TenantContextState copyWith({
    TenantInfo? activeTenant,
    BranchInfo? activeBranch,
    List<TenantInfo>? authorizedTenants,
    List<BranchInfo>? authorizedBranches,
  }) {
    return TenantContextState(
      activeTenant: activeTenant ?? this.activeTenant,
      activeBranch: activeBranch ?? this.activeBranch,
      authorizedTenants: authorizedTenants ?? this.authorizedTenants,
      authorizedBranches: authorizedBranches ?? this.authorizedBranches,
    );
  }
}

class TenantContextNotifier extends StateNotifier<TenantContextState> {
  final SecureStorageService? _storage;

  TenantContextNotifier([this._storage, super.initial = const TenantContextState()]);

  void setAuthorizedContext({
    required List<TenantInfo> tenants,
    required TenantInfo activeTenant,
    required List<BranchInfo> branches,
    required BranchInfo activeBranch,
  }) {
    final newState = TenantContextState(
      activeTenant: activeTenant,
      activeBranch: activeBranch,
      authorizedTenants: tenants,
      authorizedBranches: branches,
    );
    state = newState;
    _storage?.saveTenantContext(newState);
  }

  void switchTenant(TenantInfo newTenant, List<BranchInfo> newBranches) {
    final newState = state.copyWith(
      activeTenant: newTenant,
      authorizedBranches: newBranches,
      activeBranch: newBranches.isNotEmpty ? newBranches.first : null,
    );
    state = newState;
    _storage?.saveTenantContext(newState);
  }

  void switchBranch(BranchInfo newBranch) {
    if (state.activeTenant != null && newBranch.tenantId == state.activeTenant!.id) {
      final newState = state.copyWith(activeBranch: newBranch);
      state = newState;
      _storage?.saveTenantContext(newState);
    }
  }

  void clear() {
    state = const TenantContextState();
    _storage?.saveTenantContext(const TenantContextState());
  }
}

final tenantContextProvider = StateNotifierProvider<TenantContextNotifier, TenantContextState>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return TenantContextNotifier(storage);
});
