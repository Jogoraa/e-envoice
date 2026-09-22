import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/auth/models/auth_session.dart';
import '../di/providers.dart';
import '../networking/gateway_config.dart';
import '../storage/secure_storage_service.dart';

/// Secure Storage Key Constants
class SecureStorageKeys {
  static const String tenantJwt = 'ut_tenant_jwt';
  static const String tenantRefresh = 'ut_tenant_refresh';
  static const String masterAdminJwt = 'ut_master_admin_jwt';
  static const String masterAdminRefresh = 'ut_master_admin_refresh';
  static const String saasAdminJwt = 'ut_saas_admin_jwt';
  static const String saasAdminRefresh = 'ut_saas_admin_refresh';
  static const String delegatedTenantJwt = 'ut_delegated_tenant_jwt';
}

/// UT Master Admin Session (Platform administration & regulatory oversight)
class MasterAdminSession {
  final String adminId;
  final String username;
  final String email;
  final Set<String> roles;
  final String? accessToken;
  final DateTime? expiresAt;

  const MasterAdminSession({
    required this.adminId,
    required this.username,
    required this.email,
    this.roles = const {'ROLE_PLATFORM_ADMIN', 'ROLE_AUTHORITY_AUDITOR'},
    this.accessToken,
    this.expiresAt,
  });

  String get name => username;
  bool get isExpired => expiresAt != null && DateTime.now().isAfter(expiresAt!);
  bool get canOversight => roles.contains('ROLE_PLATFORM_ADMIN');
  bool get canConfigurePlatform => roles.contains('ROLE_PLATFORM_ADMIN');
  bool get canAuditCrossTenant =>
      roles.contains('ROLE_AUTHORITY_AUDITOR') || roles.contains('ROLE_PLATFORM_ADMIN');

  Map<String, dynamic> toJson() => {
        'adminId': adminId,
        'username': username,
        'email': email,
        'roles': roles.toList(),
        'accessToken': accessToken,
        'expiresAt': expiresAt?.toIso8601String(),
      };

  factory MasterAdminSession.fromJson(Map<String, dynamic> json) => MasterAdminSession(
        adminId: json['adminId']?.toString() ?? '',
        username: json['username']?.toString() ?? '',
        email: json['email']?.toString() ?? '',
        roles: (json['roles'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toSet() ??
            const {'ROLE_PLATFORM_ADMIN', 'ROLE_AUTHORITY_AUDITOR'},
        accessToken: json['accessToken']?.toString(),
        expiresAt: json['expiresAt'] != null
            ? DateTime.tryParse(json['expiresAt'].toString())
            : null,
      );
}

/// UT SaaS Management Session (Commercial lifecycle, onboarding, billing)
class SaasManagementSession {
  final String operatorId;
  final String username;
  final String email;
  final Set<String> roles;
  final String? accessToken;
  final DateTime? expiresAt;

  const SaasManagementSession({
    required this.operatorId,
    required this.username,
    required this.email,
    this.roles = const {'ROLE_SAAS_ADMIN', 'ROLE_SAAS_OPERATOR'},
    this.accessToken,
    this.expiresAt,
  });

  String get name => username;
  bool get isExpired => expiresAt != null && DateTime.now().isAfter(expiresAt!);
  bool get canOnboardTenants =>
      roles.contains('ROLE_SAAS_ADMIN') || roles.contains('ROLE_SAAS_OPERATOR');
  bool get canManageLifecycle => roles.contains('ROLE_SAAS_ADMIN');
  bool get canManagePlans => roles.contains('ROLE_SAAS_ADMIN');
  bool get canAccessSupport =>
      roles.contains('ROLE_SAAS_ADMIN') || roles.contains('ROLE_SAAS_OPERATOR');

  Map<String, dynamic> toJson() => {
        'operatorId': operatorId,
        'username': username,
        'email': email,
        'roles': roles.toList(),
        'accessToken': accessToken,
        'expiresAt': expiresAt?.toIso8601String(),
      };

  factory SaasManagementSession.fromJson(Map<String, dynamic> json) => SaasManagementSession(
        operatorId: json['operatorId']?.toString() ?? '',
        username: json['username']?.toString() ?? '',
        email: json['email']?.toString() ?? '',
        roles: (json['roles'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toSet() ??
            const {'ROLE_SAAS_ADMIN', 'ROLE_SAAS_OPERATOR'},
        accessToken: json['accessToken']?.toString(),
        expiresAt: json['expiresAt'] != null
            ? DateTime.tryParse(json['expiresAt'].toString())
            : null,
      );
}

/// UT Delegated Tenant Session (Short-lived, server-authorized testing/support session)
class DelegatedTenantSession {
  final String sessionId;
  final String initiatingMasterUserId;
  final String targetTenantId;
  final String targetTenantName;
  final String targetTenantTin;
  final String? targetBranchId;
  final String accessType; // 'TESTING' or 'READ_ONLY_SUPPORT'
  final String accessToken;
  final DateTime expiresAt;
  final String? reason;

  const DelegatedTenantSession({
    required this.sessionId,
    required this.initiatingMasterUserId,
    required this.targetTenantId,
    required this.targetTenantName,
    required this.targetTenantTin,
    this.targetBranchId,
    required this.accessType,
    required this.accessToken,
    required this.expiresAt,
    this.reason,
  });

  String get delegatedBy => initiatingMasterUserId;
  String get token => accessToken;

  bool get isExpired => DateTime.now().isAfter(expiresAt);
  bool get isReadOnly => accessType == 'READ_ONLY_SUPPORT';
  bool get isTesting => accessType == 'TESTING';

  Duration get remainingDuration {
    final diff = expiresAt.difference(DateTime.now());
    return diff.isNegative ? Duration.zero : diff;
  }

  Map<String, dynamic> toJson() => {
        'sessionId': sessionId,
        'initiatingMasterUserId': initiatingMasterUserId,
        'targetTenantId': targetTenantId,
        'targetTenantName': targetTenantName,
        'targetTenantTin': targetTenantTin,
        'targetBranchId': targetBranchId,
        'accessType': accessType,
        'accessToken': accessToken,
        'expiresAt': expiresAt.toIso8601String(),
        'reason': reason,
      };

  factory DelegatedTenantSession.fromJson(Map<String, dynamic> json) => DelegatedTenantSession(
        sessionId: json['sessionId']?.toString() ?? '',
        initiatingMasterUserId: json['initiatingMasterUserId']?.toString() ?? '',
        targetTenantId: json['targetTenantId']?.toString() ?? '',
        targetTenantName: json['targetTenantName']?.toString() ?? '',
        targetTenantTin: json['targetTenantTin']?.toString() ?? '',
        targetBranchId: json['targetBranchId']?.toString(),
        accessType: json['accessType']?.toString() ?? 'TESTING',
        accessToken: json['accessToken']?.toString() ?? '',
        expiresAt: DateTime.tryParse(json['expiresAt']?.toString() ?? '') ??
            DateTime.now().add(const Duration(minutes: 30)),
        reason: json['reason']?.toString(),
      );
}

/// Tenant Auth Session Notifier with Storage Persistence
class AuthSessionNotifier extends StateNotifier<AuthSession?> {
  final SecureStorageService? _storage;
  final Dio _dio;

  AuthSessionNotifier([this._storage, super.initial, Dio? customDio])
      : _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: GatewayConfig.tenantApiBaseUrl,
              connectTimeout: GatewayConfig.connectTimeout,
              receiveTimeout: GatewayConfig.receiveTimeout,
            ));

  Future<void> setSession(AuthSession? session) async {
    state = session;
    if (session != null) {
      await _storage?.saveTenantAuthSession(session);
      if (session.accessToken != null) {
        await _storage?.saveToken(session.accessToken!);
      }
    } else {
      await _storage?.wipeTenantAuthSession();
    }
  }

  Future<Map<String, dynamic>> login({
    required String tin,
    required String username,
    required String password,
    String? branchCode,
  }) async {
    try {
      final response = await _dio.post(
        '${GatewayConfig.activeBackendHost}/api/v1/auth/login',
        data: {
          'tin': tin,
          'username': username,
          'password': password,
          if (branchCode != null && branchCode.isNotEmpty) 'branchCode': branchCode,
        },
      );
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        final token = data['accessToken']?.toString() ?? '';
        final tenantId = data['tenantId']?.toString() ?? '';
        final role = data['role']?.toString() ?? 'ROLE_TENANT_ADMIN';
        final userFullName = data['fullName']?.toString() ?? username;
        final legalName = data['legalName']?.toString() ?? '';
        final tradeName = data['tradeName']?.toString() ?? '';
        final expiresIn = (data['expiresInSeconds'] as num?)?.toInt() ?? 43200;

        final Set<String> roles = (data['roles'] is List)
            ? Set<String>.from(data['roles'])
            : {role};
        final Set<String> scopes = (data['scopes'] is List)
            ? Set<String>.from(data['scopes'])
            : {'invoice:create', 'invoice:read', 'customer:read', 'catalog:read'};

        final session = AuthSession(
          userId: username,
          username: userFullName,
          tenantId: tenantId,
          roles: roles,
          scopes: scopes,
          accessToken: token,
          expiresAt: DateTime.now().add(Duration(seconds: expiresIn)),
        );

        state = session;
        await _storage?.saveTenantAuthSession(session);
        if (token.isNotEmpty) {
          await _storage?.saveToken(token);
        }
        if (tenantId.isNotEmpty) {
          await _storage?.saveActiveTenantId(tenantId);
        }

        return {
          'session': session,
          'tenantId': tenantId,
          'legalName': legalName,
          'tradeName': tradeName,
          'tin': tin,
        };
      }
      throw Exception('Malformed authentication response from server.');
    } on DioException catch (e) {
      if (e.response?.data is Map) {
        final err = e.response!.data as Map;
        final msg = err['message']?.toString() ?? err['error']?.toString();
        if (msg != null && msg.isNotEmpty) {
          throw Exception(msg);
        }
      }
      throw Exception(e.message ?? 'Authentication failed. Please check credentials and server connection.');
    }
  }

  Future<void> logout() async {
    state = null;
    await _storage?.wipeTenantAuthSession();
    await _storage?.wipeSession();
  }
}

/// Master Admin Session Notifier with Storage Persistence
class MasterAdminSessionNotifier extends StateNotifier<MasterAdminSession?> {
  final SecureStorageService? _storage;
  final Dio _dio;

  MasterAdminSessionNotifier([this._storage, super.initial, Dio? customDio])
      : _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: GatewayConfig.masterAdminApiBaseUrl,
              connectTimeout: GatewayConfig.connectTimeout,
              receiveTimeout: GatewayConfig.receiveTimeout,
            ));

  Future<void> login({
    required String email,
    required String password,
    required String mfaCode,
  }) async {
    try {
      final response = await _dio.post(
        '${GatewayConfig.activeBackendHost}/api/v1/master/auth/login',
        data: {'username': email, 'password': password},
      );
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        final token = data['accessToken']?.toString() ?? data['token']?.toString() ?? '';
        final role = data['role']?.toString() ?? 'ROLE_PLATFORM_ADMIN';
        final username = data['username']?.toString() ?? email.split('@').first;
        final adminEmail = data['email']?.toString() ?? email;
        final adminId = data['userId']?.toString() ?? 'ADM-001';

        final session = MasterAdminSession(
          adminId: adminId,
          username: username,
          email: adminEmail,
          roles: {role, 'ROLE_AUTHORITY_AUDITOR'},
          accessToken: token,
          expiresAt: DateTime.now().add(const Duration(hours: 8)),
        );
        state = session;
        await _storage?.saveMasterAdminSession(session);
        if (token.isNotEmpty) {
          await _storage?.saveMasterAdminToken(token);
        }
        return;
      }
      throw Exception('Unexpected response format from Master Admin gateway.');
    } on DioException catch (e) {
      if (e.response?.data is Map) {
        final err = e.response!.data as Map;
        final msg = err['message']?.toString() ?? err['error']?.toString();
        if (msg != null && msg.isNotEmpty) {
          throw Exception(msg);
        }
      }
      throw Exception(e.message ?? 'Master Admin authentication failed. Check credentials.');
    }
  }

  Future<void> logout() async {
    state = null;
    await _storage?.wipeMasterAdminSession();
  }
}

/// SaaS Management Session Notifier with Storage Persistence
class SaasManagementSessionNotifier extends StateNotifier<SaasManagementSession?> {
  final SecureStorageService? _storage;
  final Dio _dio;

  SaasManagementSessionNotifier([this._storage, super.initial, Dio? customDio])
      : _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: GatewayConfig.saasManagementApiBaseUrl,
              connectTimeout: GatewayConfig.connectTimeout,
              receiveTimeout: GatewayConfig.receiveTimeout,
            ));

  Future<void> login({
    required String email,
    required String password,
  }) async {
    try {
      final response = await _dio.post(
        '${GatewayConfig.activeBackendHost}/api/v1/saas/auth/login',
        data: {'username': email, 'password': password},
      );
      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        final token = data['accessToken']?.toString() ?? data['token']?.toString() ?? '';
        final role = data['role']?.toString() ?? 'ROLE_SAAS_ADMIN';
        final username = data['username']?.toString() ?? email.split('@').first;
        final opEmail = data['email']?.toString() ?? email;
        final opId = data['userId']?.toString() ?? 'OP-001';

        final session = SaasManagementSession(
          operatorId: opId,
          username: username,
          email: opEmail,
          roles: {role, 'ROLE_SAAS_OPERATOR'},
          accessToken: token,
          expiresAt: DateTime.now().add(const Duration(hours: 12)),
        );
        state = session;
        await _storage?.saveSaasSession(session);
        if (token.isNotEmpty) {
          await _storage?.saveSaasAdminToken(token);
        }
        return;
      }
      throw Exception('Unexpected response format from SaaS Management gateway.');
    } on DioException catch (e) {
      if (e.response?.data is Map) {
        final err = e.response!.data as Map;
        final msg = err['message']?.toString() ?? err['error']?.toString();
        if (msg != null && msg.isNotEmpty) {
          throw Exception(msg);
        }
      }
      throw Exception(e.message ?? 'SaaS Management authentication failed. Check credentials.');
    }
  }

  Future<void> logout() async {
    state = null;
    await _storage?.wipeSaasAdminSession();
  }
}

/// Delegated Tenant Session Notifier with Storage Persistence
class DelegatedTenantSessionNotifier extends StateNotifier<DelegatedTenantSession?> {
  final SecureStorageService? _storage;

  DelegatedTenantSessionNotifier([this._storage, super.initial]);

  void startDelegatedSession(DelegatedTenantSession session) {
    state = session;
    _storage?.saveDelegatedTenantSession(session);
    _storage?.saveDelegatedTenantToken(session.accessToken);
  }

  void setSession(DelegatedTenantSession session) {
    state = session;
    _storage?.saveDelegatedTenantSession(session);
    _storage?.saveDelegatedTenantToken(session.accessToken);
  }

  void endDelegatedSession() {
    state = null;
    _storage?.wipeDelegatedTenantSession();
  }

  void clearSession() {
    state = null;
    _storage?.wipeDelegatedTenantSession();
  }
}

/// Tenant Auth Session State Notifier Provider
final authSessionProvider =
    StateNotifierProvider<AuthSessionNotifier, AuthSession?>(
  (ref) {
    final storage = ref.watch(secureStorageProvider);
    return AuthSessionNotifier(storage);
  },
);

/// Master Admin Session State Notifier Provider
final masterAdminSessionProvider =
    StateNotifierProvider<MasterAdminSessionNotifier, MasterAdminSession?>(
  (ref) {
    final storage = ref.watch(secureStorageProvider);
    return MasterAdminSessionNotifier(storage);
  },
);

/// SaaS Management Session State Notifier Provider
final saasSessionProvider =
    StateNotifierProvider<SaasManagementSessionNotifier, SaasManagementSession?>(
  (ref) {
    final storage = ref.watch(secureStorageProvider);
    return SaasManagementSessionNotifier(storage);
  },
);

/// Delegated Tenant Session State Notifier Provider
final delegatedTenantSessionProvider =
    StateNotifierProvider<DelegatedTenantSessionNotifier, DelegatedTenantSession?>(
  (ref) {
    final storage = ref.watch(secureStorageProvider);
    return DelegatedTenantSessionNotifier(storage);
  },
);

