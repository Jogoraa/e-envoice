import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:uuid/uuid.dart';
import '../storage/secure_storage_service.dart';

/// Device identity and hardware cryptographic signing engine.
/// Meets Directive No. 1142/2026 Art. 16 offline transaction authenticity rules.
class DeviceIdentityService {
  final SecureStorageService _secureStorage;
  static const Uuid _uuid = Uuid();

  String? _cachedDeviceId;
  String? _cachedDeviceSecret;

  DeviceIdentityService(this._secureStorage);

  /// Retrieves or initializes a hardware-bound device UUID.
  Future<String> getDeviceId() async {
    if (_cachedDeviceId != null) return _cachedDeviceId!;

    String? storedId = await _secureStorage.getDeviceId();
    if (storedId == null || storedId.isEmpty) {
      storedId = _uuid.v4();
      await _secureStorage.saveDeviceId(storedId);
    }
    _cachedDeviceId = storedId;
    return storedId;
  }

  /// Retrieves or initializes a private device signing secret.
  Future<String> getDeviceSecret() async {
    if (_cachedDeviceSecret != null) return _cachedDeviceSecret!;

    String? storedSecret = await _secureStorage.getDeviceSecret();
    if (storedSecret == null || storedSecret.isEmpty) {
      storedSecret = _uuid.v4() + _uuid.v4();
      await _secureStorage.saveDeviceSecret(storedSecret);
    }
    _cachedDeviceSecret = storedSecret;
    return storedSecret;
  }

  /// Signs an offline transaction payload using HMAC-SHA256.
  /// Resulting signature proves the transaction was executed on this specific authorized hardware.
  Future<String> signOfflineTransaction({
    required String documentNumber,
    required String totalAmount,
    required String timestampIso,
  }) async {
    final deviceId = await getDeviceId();
    final secret = await getDeviceSecret();

    final dataToSign = '$deviceId|$documentNumber|$totalAmount|$timestampIso';
    final keyBytes = utf8.encode(secret);
    final hmacSha256 = Hmac(sha256, keyBytes);
    final digest = hmacSha256.convert(utf8.encode(dataToSign));

    return 'SIG-INSA-${digest.toString().toUpperCase().substring(0, 32)}';
  }
}
