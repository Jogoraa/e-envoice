import '../../../core/networking/api_client.dart';

enum CredentialValidationStatus {
  active('ACTIVE', 'Active & Validated / ትክክለኛ'),
  expired('EXPIRED', 'Certificate Expired / ጊዜው ያለፈበት'),
  revoked('REVOKED', 'Revoked by Authority / የተሰረዘ'),
  pendingVerification('PENDING_VERIFICATION', 'Pending Validation / በመጠባበቅ ላይ'),
  missing('MISSING', 'Not Configured / ያልተዋቀረ');

  final String code;
  final String label;
  const CredentialValidationStatus(this.code, this.label);

  static CredentialValidationStatus fromString(String val) {
    return CredentialValidationStatus.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => CredentialValidationStatus.pendingVerification,
    );
  }
}

class TenantGovernmentCredentialMetadataDto {
  final String? tenantId;
  final String? morSystemNumber;
  final String? sellerTin;
  final String? taxpayerRegistrationIdentity;
  final String? insaCertificateReference;
  final String? certificateSerial;
  final DateTime? certificateExpiry;
  final CredentialValidationStatus credentialStatus;
  final DateTime? lastValidatedAt;
  final int keyVaultVersion;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  TenantGovernmentCredentialMetadataDto({
    this.tenantId,
    this.morSystemNumber,
    this.sellerTin,
    this.taxpayerRegistrationIdentity,
    this.insaCertificateReference,
    this.certificateSerial,
    this.certificateExpiry,
    required this.credentialStatus,
    this.lastValidatedAt,
    this.keyVaultVersion = 1,
    this.createdAt,
    this.updatedAt,
  });

  factory TenantGovernmentCredentialMetadataDto.fromJson(Map<String, dynamic> json) {
    return TenantGovernmentCredentialMetadataDto(
      tenantId: json['tenantId']?.toString(),
      morSystemNumber: json['morSystemNumber']?.toString(),
      sellerTin: json['sellerTin']?.toString(),
      taxpayerRegistrationIdentity: json['taxpayerRegistrationIdentity']?.toString(),
      insaCertificateReference: json['insaCertificateReference']?.toString(),
      certificateSerial: json['certificateSerial']?.toString(),
      certificateExpiry: json['certificateExpiry'] != null
          ? DateTime.tryParse(json['certificateExpiry'].toString())
          : null,
      credentialStatus: CredentialValidationStatus.fromString(json['credentialStatus']?.toString() ?? 'MISSING'),
      lastValidatedAt: json['lastValidatedAt'] != null
          ? DateTime.tryParse(json['lastValidatedAt'].toString())
          : null,
      keyVaultVersion: (json['keyVaultVersion'] as num?)?.toInt() ?? 1,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      updatedAt: json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) : null,
    );
  }

  bool get hasMorSystemNumber => morSystemNumber != null && morSystemNumber!.isNotEmpty;
  bool get hasInsaCertificate => insaCertificateReference != null && insaCertificateReference!.isNotEmpty;
  bool get isCertificateExpired => certificateExpiry != null && DateTime.now().isAfter(certificateExpiry!);
  int get daysUntilExpiry => certificateExpiry != null ? certificateExpiry!.difference(DateTime.now()).inDays : 0;
}

class GovernmentCredentialsService {
  final ApiClient apiClient;

  GovernmentCredentialsService(this.apiClient);

  Future<TenantGovernmentCredentialMetadataDto> getMetadata() async {
    final response = await apiClient.get('/api/v1/government/credentials/metadata');
    return TenantGovernmentCredentialMetadataDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<TenantGovernmentCredentialMetadataDto> provisionCredentials({
    required String morSystemNumber,
    required String apiKey,
    required String clientSecret,
    String? insaCertificateReference,
    String? certificateSerial,
    DateTime? certificateExpiry,
  }) async {
    final response = await apiClient.post('/api/v1/government/credentials/provision', data: {
      'morSystemNumber': morSystemNumber,
      'apiKey': apiKey,
      'clientSecret': clientSecret,
      if (insaCertificateReference != null) 'insaCertificateReference': insaCertificateReference,
      if (certificateSerial != null) 'certificateSerial': certificateSerial,
      if (certificateExpiry != null) 'certificateExpiry': certificateExpiry.toIso8601String(),
    });
    return TenantGovernmentCredentialMetadataDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<TenantGovernmentCredentialMetadataDto> rotateApiKey(String newApiKey) async {
    final response = await apiClient.post('/api/v1/government/credentials/rotate-api-key', data: {
      'newApiKey': newApiKey,
    });
    return TenantGovernmentCredentialMetadataDto.fromJson(response.data as Map<String, dynamic>);
  }
}
