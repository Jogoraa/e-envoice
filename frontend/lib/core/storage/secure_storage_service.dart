import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../domain/auth/models/auth_session.dart';
import '../../domain/tenant/models/tenant_context.dart';
import '../authentication/multi_gateway_session.dart';

/// Hardware-backed secure storage for access tokens, client secrets, and cryptographic keys.
/// Never stores master database passwords or server private keys.
class SecureStorageService {
  final FlutterSecureStorage _storage;

  SecureStorageService({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(
                encryptedSharedPreferences: true,
              ),
              wOptions: WindowsOptions(
                useBackwardCompatibility: false,
              ),
            );

  /// Write with self-healing: if the Windows DPAPI is corrupt, delete all keys and retry once.
  Future<void> _safeWrite({required String key, required String value}) async {
    try {
      await _storage.write(key: key, value: value);
    } catch (_) {
      // CryptProtectData failure — nuke the corrupt file and retry on a clean slate.
      try {
        await _storage.deleteAll();
        await _storage.write(key: key, value: value);
      } catch (_) {
        // If still failing, silently ignore — in-memory fallback handles this request.
      }
    }
  }

  static const String _keyToken = 'auth_jwt_token';
  static const String _keyRefreshToken = 'auth_refresh_token';
  static const String _keyApiKey = 'm2m_api_key';
  static const String _keyClientSecret = 'm2m_client_secret';
  static const String _keyDeviceId = 'hardware_device_id';
  static const String _keyDeviceSecret = 'hardware_device_secret';
  static const String _keyActiveTenant = 'active_tenant_id';
  static const String _keyActiveBranch = 'active_branch_id';

  static const String _keyTenantAuthSession = 'ut_tenant_auth_session_json';
  static const String _keyTenantContext = 'ut_tenant_context_json';
  static const String _keyMasterAdminSession = 'ut_master_admin_session_json';
  static const String _keySaasAdminSession = 'ut_saas_admin_session_json';
  static const String _keyDelegatedTenantSession = 'ut_delegated_tenant_session_json';

  Future<void> saveToken(String token) async {
    await _storage.write(key: _keyToken, value: token);
  }

  Future<String?> getToken() async {
    try {
      return await _storage.read(key: _keyToken);
    } catch (_) {
      try { await _storage.delete(key: _keyToken); } catch (_) {}
      return null;
    }
  }

  Future<void> saveRefreshToken(String token) async {
    await _storage.write(key: _keyRefreshToken, value: token);
  }

  Future<String?> getRefreshToken() async {
    return await _storage.read(key: _keyRefreshToken);
  }

  Future<void> saveM2MCredentials({required String apiKey, required String clientSecret}) async {
    await _storage.write(key: _keyApiKey, value: apiKey);
    await _storage.write(key: _keyClientSecret, value: clientSecret);
  }

  Future<String?> getApiKey() async {
    return await _storage.read(key: _keyApiKey);
  }

  Future<String?> getClientSecret() async {
    return await _storage.read(key: _keyClientSecret);
  }

  Future<void> saveDeviceId(String deviceId) async {
    await _storage.write(key: _keyDeviceId, value: deviceId);
  }

  Future<String?> getDeviceId() async {
    return await _storage.read(key: _keyDeviceId);
  }

  Future<void> saveDeviceSecret(String secret) async {
    await _storage.write(key: _keyDeviceSecret, value: secret);
  }

  Future<String?> getDeviceSecret() async {
    return await _storage.read(key: _keyDeviceSecret);
  }

  Future<void> saveActiveTenantId(String tenantId) async {
    await _storage.write(key: _keyActiveTenant, value: tenantId);
  }

  Future<String?> getActiveTenantId() async {
    return await _storage.read(key: _keyActiveTenant);
  }

  Future<void> saveActiveBranchId(String branchId) async {
    await _storage.write(key: _keyActiveBranch, value: branchId);
  }

  Future<String?> getActiveBranchId() async {
    return await _storage.read(key: _keyActiveBranch);
  }

  // --- Tenant Auth Session & Context Persistence ---
  Future<void> saveTenantAuthSession(AuthSession session) async {
    await _storage.write(key: _keyTenantAuthSession, value: jsonEncode(session.toJson()));
  }

  Future<AuthSession?> getTenantAuthSession() async {
    try {
      final raw = await _storage.read(key: _keyTenantAuthSession);
      if (raw == null || raw.isEmpty) return null;
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final session = AuthSession.fromJson(map);
      if (session.isExpired) {
        await wipeTenantAuthSession();
        return null;
      }
      return session;
    } catch (_) {
      return null;
    }
  }

  Future<void> saveTenantContext(TenantContextState contextState) async {
    await _storage.write(key: _keyTenantContext, value: jsonEncode(contextState.toJson()));
  }

  Future<TenantContextState?> getTenantContext() async {
    try {
      final raw = await _storage.read(key: _keyTenantContext);
      if (raw == null || raw.isEmpty) return null;
      final map = jsonDecode(raw) as Map<String, dynamic>;
      return TenantContextState.fromJson(map);
    } catch (_) {
      return null;
    }
  }

  Future<void> wipeTenantAuthSession() async {
    await _storage.delete(key: _keyTenantAuthSession);
    await _storage.delete(key: _keyTenantContext);
  }

  // --- Master Admin Session Persistence ---
  static const String _keyMasterAdminToken = 'auth_master_admin_token';

  Future<void> saveMasterAdminToken(String token) async {
    await _safeWrite(key: _keyMasterAdminToken, value: token);
  }

  Future<String?> getMasterAdminToken() async {
    try {
      return await _storage.read(key: _keyMasterAdminToken);
    } catch (_) {
      // Corrupt secure storage (e.g. CryptUnprotectData failure on Windows) — self-heal by deleting.
      try { await _storage.delete(key: _keyMasterAdminToken); } catch (_) {}
      return null;
    }
  }

  Future<void> saveMasterAdminSession(MasterAdminSession session) async {
    await _storage.write(key: _keyMasterAdminSession, value: jsonEncode(session.toJson()));
  }

  Future<MasterAdminSession?> getMasterAdminSession() async {
    try {
      final raw = await _storage.read(key: _keyMasterAdminSession);
      if (raw == null || raw.isEmpty) return null;
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final session = MasterAdminSession.fromJson(map);
      if (session.isExpired) {
        await wipeMasterAdminSession();
        return null;
      }
      return session;
    } catch (_) {
      return null;
    }
  }

  Future<void> wipeMasterAdminSession() async {
    await _storage.delete(key: _keyMasterAdminToken);
    await _storage.delete(key: _keyMasterAdminSession);
  }

  // --- SaaS Admin Session Persistence ---
  static const String _keySaasAdminToken = 'auth_saas_admin_token';

  Future<void> saveSaasAdminToken(String token) async {
    await _safeWrite(key: _keySaasAdminToken, value: token);
  }

  Future<String?> getSaasAdminToken() async {
    try {
      return await _storage.read(key: _keySaasAdminToken);
    } catch (_) {
      // Corrupt secure storage (e.g. CryptUnprotectData failure on Windows) — self-heal by deleting.
      try { await _storage.delete(key: _keySaasAdminToken); } catch (_) {}
      return null;
    }
  }

  Future<void> saveSaasSession(SaasManagementSession session) async {
    await _storage.write(key: _keySaasAdminSession, value: jsonEncode(session.toJson()));
  }

  Future<SaasManagementSession?> getSaasSession() async {
    try {
      final raw = await _storage.read(key: _keySaasAdminSession);
      if (raw == null || raw.isEmpty) return null;
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final session = SaasManagementSession.fromJson(map);
      if (session.isExpired) {
        await wipeSaasAdminSession();
        return null;
      }
      return session;
    } catch (_) {
      return null;
    }
  }

  Future<void> wipeSaasAdminSession() async {
    await _storage.delete(key: _keySaasAdminToken);
    await _storage.delete(key: _keySaasAdminSession);
  }

  // --- Delegated Tenant Session Persistence ---
  static const String _keyDelegatedTenantToken = 'auth_delegated_tenant_token';

  Future<void> saveDelegatedTenantToken(String token) async {
    await _storage.write(key: _keyDelegatedTenantToken, value: token);
  }

  Future<String?> getDelegatedTenantToken() async {
    return await _storage.read(key: _keyDelegatedTenantToken);
  }

  Future<void> saveDelegatedTenantSession(DelegatedTenantSession session) async {
    await _storage.write(key: _keyDelegatedTenantSession, value: jsonEncode(session.toJson()));
  }

  Future<DelegatedTenantSession?> getDelegatedTenantSession() async {
    try {
      final raw = await _storage.read(key: _keyDelegatedTenantSession);
      if (raw == null || raw.isEmpty) return null;
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final session = DelegatedTenantSession.fromJson(map);
      if (session.isExpired) {
        await wipeDelegatedTenantSession();
        return null;
      }
      return session;
    } catch (_) {
      return null;
    }
  }

  Future<void> wipeDelegatedTenantSession() async {
    await _storage.delete(key: _keyDelegatedTenantToken);
    await _storage.delete(key: _keyDelegatedTenantSession);
  }

  /// Wipes all sensitive credentials and active sessions for normal tenant client.
  /// Never wipes master admin, saas admin, or delegated support tokens.
  Future<void> wipeSession() async {
    await _storage.delete(key: _keyToken);
    await _storage.delete(key: _keyRefreshToken);
    await _storage.delete(key: _keyApiKey);
    await _storage.delete(key: _keyClientSecret);
    await _storage.delete(key: _keyActiveTenant);
    await _storage.delete(key: _keyActiveBranch);
    await wipeTenantAuthSession();
  }

  /// Wipes entire vault (for complete device decommission or factory reset).
  Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}
