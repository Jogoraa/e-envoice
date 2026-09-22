import 'dart:convert';

class AuthSession {
  final String userId;
  final String username;
  final String tenantId;
  final Set<String> roles;
  final Set<String> scopes;
  final String? accessToken;
  final DateTime? expiresAt;

  const AuthSession({
    required this.userId,
    required this.username,
    required this.tenantId,
    this.roles = const {},
    this.scopes = const {},
    this.accessToken,
    this.expiresAt,
  });

  bool get isExpired => expiresAt != null && DateTime.now().isAfter(expiresAt!);

  // Capability checks
  bool get canCreateInvoice =>
      scopes.contains('invoice:create') ||
      roles.contains('ROLE_TENANT_ADMIN') ||
      roles.contains('ROLE_CASHIER');

  bool get canReadInvoice =>
      scopes.contains('invoice:read') ||
      roles.contains('ROLE_TENANT_ADMIN') ||
      roles.contains('ROLE_CASHIER');

  bool get canCancelInvoice =>
      scopes.contains('invoice:cancel') ||
      roles.contains('ROLE_TENANT_ADMIN');

  bool get canAdjustInvoice =>
      scopes.contains('invoice:adjust') ||
      scopes.contains('adjustment:create') ||
      roles.contains('ROLE_TENANT_ADMIN');

  bool get canAudit =>
      roles.contains('ROLE_AUTHORITY_AUDITOR') ||
      roles.contains('ROLE_PLATFORM_ADMIN') ||
      scopes.contains('authority:audit');

  bool get isTenantAdmin => roles.contains('ROLE_TENANT_ADMIN');

  factory AuthSession.fromJwt(String token) {
    final parts = token.split('.');
    if (parts.length != 3) {
      throw const FormatException('Invalid JWT token format');
    }

    final normalizedPayload = base64Url.normalize(parts[1]);
    final payloadString = utf8.decode(base64Url.decode(normalizedPayload));
    final Map<String, dynamic> claims = jsonDecode(payloadString);

    final tenantId = claims['tenant_id']?.toString() ?? claims['tid']?.toString() ?? '';
    final sub = claims['sub']?.toString() ?? 'USER';

    final Set<String> roles = {};
    if (claims['roles'] is List) {
      for (final r in claims['roles']) {
        roles.add(r.toString());
      }
    }

    final Set<String> scopes = {};
    if (claims['scopes'] is List) {
      for (final s in claims['scopes']) {
        scopes.add(s.toString());
      }
    }

    DateTime? exp;
    if (claims['exp'] != null) {
      exp = DateTime.fromMillisecondsSinceEpoch((claims['exp'] as int) * 1000);
    }

    return AuthSession(
      userId: sub,
      username: sub,
      tenantId: tenantId,
      roles: roles,
      scopes: scopes,
      accessToken: token,
      expiresAt: exp,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'username': username,
      'tenantId': tenantId,
      'roles': roles.toList(),
      'scopes': scopes.toList(),
      'accessToken': accessToken,
      'expiresAt': expiresAt?.toIso8601String(),
    };
  }

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      userId: json['userId']?.toString() ?? '',
      username: json['username']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      roles: (json['roles'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toSet() ??
          const {},
      scopes: (json['scopes'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toSet() ??
          const {},
      accessToken: json['accessToken']?.toString(),
      expiresAt: json['expiresAt'] != null
          ? DateTime.tryParse(json['expiresAt'].toString())
          : null,
    );
  }
}
