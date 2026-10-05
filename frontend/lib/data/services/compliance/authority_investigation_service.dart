import '../../../core/networking/api_client.dart';

class AuthorityCustomerDto {
  final String id;
  final String tin;
  final String legalName;
  final String? tradeName;
  final String address;
  final DateTime registeredAt;
  final bool active;
  final String? auditReference;

  AuthorityCustomerDto({
    required this.id,
    required this.tin,
    required this.legalName,
    this.tradeName,
    required this.address,
    required this.registeredAt,
    required this.active,
    this.auditReference,
  });

  factory AuthorityCustomerDto.fromJson(Map<String, dynamic> json) {
    return AuthorityCustomerDto(
      id: json['id']?.toString() ?? '',
      tin: json['tin']?.toString() ?? '',
      legalName: json['legalName']?.toString() ?? '',
      tradeName: json['tradeName']?.toString(),
      address: json['address']?.toString() ?? '',
      registeredAt: json['registeredAt'] != null
          ? DateTime.tryParse(json['registeredAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      active: json['active'] != false,
      auditReference: json['auditReference']?.toString(),
    );
  }
}

class AuthorityExportJobDto {
  final String id;
  final String caseReference;
  final String reason;
  final String status; // SUBMITTED, PROCESSING, COMPLETED, FAILED
  final String? encryptedArtifactSha256;
  final int recordCount;
  final String requestedBy;
  final DateTime createdAt;
  final DateTime? completedAt;

  AuthorityExportJobDto({
    required this.id,
    required this.caseReference,
    required this.reason,
    required this.status,
    this.encryptedArtifactSha256,
    required this.recordCount,
    required this.requestedBy,
    required this.createdAt,
    this.completedAt,
  });

  factory AuthorityExportJobDto.fromJson(Map<String, dynamic> json) {
    return AuthorityExportJobDto(
      id: json['id']?.toString() ?? '',
      caseReference: json['caseReference']?.toString() ?? '',
      reason: json['reason']?.toString() ?? '',
      status: json['status']?.toString() ?? 'PROCESSING',
      encryptedArtifactSha256: json['encryptedArtifactSha256']?.toString(),
      recordCount: (json['recordCount'] as num?)?.toInt() ?? 0,
      requestedBy: json['requestedBy']?.toString() ?? 'GOV_AUDITOR',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      completedAt: json['completedAt'] != null
          ? DateTime.tryParse(json['completedAt'].toString())
          : null,
    );
  }
}

class SystemChecksumDto {
  final String version;
  final String gitSha;
  final String backendSha256;
  final String frontendBuildSha256;
  final String containerDigest;
  final String databaseSchemaVersion;
  final String registeredChecksum;
  final String status; // MATCH, MISMATCH, NOT_REGISTERED
  final DateTime buildTimestamp;

  SystemChecksumDto({
    required this.version,
    required this.gitSha,
    required this.backendSha256,
    required this.frontendBuildSha256,
    required this.containerDigest,
    required this.databaseSchemaVersion,
    required this.registeredChecksum,
    required this.status,
    required this.buildTimestamp,
  });

  factory SystemChecksumDto.fromJson(Map<String, dynamic> json) {
    return SystemChecksumDto(
      version: json['version']?.toString() ?? '1.0.0-PROD',
      gitSha: json['gitSha']?.toString() ?? 'dev-build',
      backendSha256: json['backendSha256']?.toString() ?? '',
      frontendBuildSha256: json['frontendBuildSha256']?.toString() ?? '',
      containerDigest: json['containerDigest']?.toString() ?? '',
      databaseSchemaVersion: json['databaseSchemaVersion']?.toString() ?? 'V18',
      registeredChecksum: json['registeredChecksum']?.toString() ?? '',
      status: json['status']?.toString() ?? json['checksumStatus']?.toString() ?? 'MATCH',
      buildTimestamp: json['buildTimestamp'] != null
          ? DateTime.tryParse(json['buildTimestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  bool get isMatch => status == 'MATCH';
  bool get isMismatch => status == 'MISMATCH';
}

class AuthorityInvestigationService {
  final ApiClient apiClient;

  AuthorityInvestigationService(this.apiClient);

  Future<SystemChecksumDto> getSystemChecksum() async {
    final response = await apiClient.get('/api/v1/authority/system-checksum');
    return SystemChecksumDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<AuthorityCustomerDto>> searchCustomers({
    String? tin,
    String? legalName,
    required String caseReference,
    required String reason,
  }) async {
    final response = await apiClient.get('/api/v1/authority/customers', queryParameters: {
      if (tin != null && tin.isNotEmpty) 'tin': tin,
      if (legalName != null && legalName.isNotEmpty) 'legalName': legalName,
      'caseReference': caseReference,
      'reason': reason,
    });
    final dynamic data = response.data;
    List<dynamic> items;
    if (data is Map && data['content'] is List) {
      items = data['content'] as List<dynamic>;
    } else if (data is List) {
      items = data;
    } else {
      items = [];
    }
    return items.map((c) => AuthorityCustomerDto.fromJson(c as Map<String, dynamic>)).toList();
  }

  Future<AuthorityExportJobDto> createExportJob({
    required String caseReference,
    required String reason,
    String? taxpayerTin,
    DateTime? fromDate,
    DateTime? toDate,
  }) async {
    final response = await apiClient.post('/api/v1/authority/investigations/exports', data: {
      'caseReference': caseReference,
      'reason': reason,
      if (taxpayerTin != null) 'taxpayerTin': taxpayerTin,
      if (fromDate != null) 'fromDate': fromDate.toIso8601String(),
      if (toDate != null) 'toDate': toDate.toIso8601String(),
    });
    return AuthorityExportJobDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<AuthorityExportJobDto>> listExportJobs() async {
    final response = await apiClient.get('/api/v1/authority/investigations/exports');
    final dynamic data = response.data;
    List<dynamic> items;
    if (data is Map && data['content'] is List) {
      items = data['content'] as List<dynamic>;
    } else if (data is List) {
      items = data;
    } else {
      items = [];
    }
    return items.map((j) => AuthorityExportJobDto.fromJson(j as Map<String, dynamic>)).toList();
  }
}
