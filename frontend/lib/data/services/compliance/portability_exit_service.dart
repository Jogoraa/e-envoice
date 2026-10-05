import '../../../core/networking/api_client.dart';

enum TenantExitStatus {
  initiated('INITIATED', '1. Exit Initiated / ተጀምሯል'),
  exportReady('EXPORT_READY', '2. Export Package Generated / የወረደ ጥቅል ተዘጋጅቷል'),
  exportConfirmed('EXPORT_CONFIRMED', '3. Export Checksum Verified / ማረጋገጫ ተረጋግጧል'),
  migrationInProgress('MIGRATION_IN_PROGRESS', '4. Migration in Progress / ዝውውር በመካሄድ ላይ'),
  retentionReview('RETENTION_REVIEW', '5. Statutory Retention Review / የህግ ማቆያ ግምገማ'),
  purgePending('PURGE_PENDING', '6. Purge Authorization Pending / ፈቃድ በመጠባበቅ ላይ'),
  authorizationPending('AUTHORIZATION_PENDING', '7. Dual-Authorization Required / የሁለትዮሽ ፈቃድ'),
  completed('COMPLETED', '8. Exit Completed / ተጠናቋል');

  final String code;
  final String label;
  const TenantExitStatus(this.code, this.label);

  static TenantExitStatus fromString(String val) {
    return TenantExitStatus.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => TenantExitStatus.initiated,
    );
  }
}

class TenantExitRequestDto {
  final String id;
  final String tenantId;
  final String destinationProvider;
  final String reason;
  final TenantExitStatus status;
  final String? archiveChecksum;
  final DateTime requestedAt;
  final DateTime? completedAt;

  TenantExitRequestDto({
    required this.id,
    required this.tenantId,
    required this.destinationProvider,
    required this.reason,
    required this.status,
    this.archiveChecksum,
    required this.requestedAt,
    this.completedAt,
  });

  factory TenantExitRequestDto.fromJson(Map<String, dynamic> json) {
    return TenantExitRequestDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      destinationProvider: json['destinationProvider']?.toString() ?? 'SELF_HOSTED',
      reason: json['reason']?.toString() ?? '',
      status: TenantExitStatus.fromString(json['status']?.toString() ?? 'INITIATED'),
      archiveChecksum: json['archiveChecksum']?.toString(),
      requestedAt: json['requestedAt'] != null
          ? DateTime.tryParse(json['requestedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      completedAt: json['completedAt'] != null
          ? DateTime.tryParse(json['completedAt'].toString())
          : null,
    );
  }
}

class RetentionClassificationSummaryDto {
  final String tenantId;
  final int retainByLawCount;
  final int purgeEligibleCount;
  final int legalHoldCount;
  final DateTime? oldestFiscalRecord;
  final DateTime? retentionExpiryDate;

  RetentionClassificationSummaryDto({
    required this.tenantId,
    required this.retainByLawCount,
    required this.purgeEligibleCount,
    required this.legalHoldCount,
    this.oldestFiscalRecord,
    this.retentionExpiryDate,
  });

  factory RetentionClassificationSummaryDto.fromJson(Map<String, dynamic> json) {
    return RetentionClassificationSummaryDto(
      tenantId: json['tenantId']?.toString() ?? '',
      retainByLawCount: (json['retainByLawCount'] as num?)?.toInt() ?? 0,
      purgeEligibleCount: (json['purgeEligibleCount'] as num?)?.toInt() ?? 0,
      legalHoldCount: (json['legalHoldCount'] as num?)?.toInt() ?? 0,
      oldestFiscalRecord: json['oldestFiscalRecord'] != null
          ? DateTime.tryParse(json['oldestFiscalRecord'].toString())
          : null,
      retentionExpiryDate: json['retentionExpiryDate'] != null
          ? DateTime.tryParse(json['retentionExpiryDate'].toString())
          : null,
    );
  }
}

class PurgeAuditCertificateDto {
  final String id;
  final String exitRequestId;
  final int purgedRecordCount;
  final String certificateHash;
  final DateTime executedAt;

  PurgeAuditCertificateDto({
    required this.id,
    required this.exitRequestId,
    required this.purgedRecordCount,
    required this.certificateHash,
    required this.executedAt,
  });

  factory PurgeAuditCertificateDto.fromJson(Map<String, dynamic> json) {
    return PurgeAuditCertificateDto(
      id: json['id']?.toString() ?? '',
      exitRequestId: json['exitRequestId']?.toString() ?? '',
      purgedRecordCount: (json['purgedRecordCount'] as num?)?.toInt() ?? 0,
      certificateHash: json['certificateHash']?.toString() ?? '',
      executedAt: json['executedAt'] != null
          ? DateTime.tryParse(json['executedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class PortabilityExitService {
  final ApiClient apiClient;

  PortabilityExitService(this.apiClient);

  Future<TenantExitRequestDto> requestExit({
    required String destinationProvider,
    required String reason,
  }) async {
    final response = await apiClient.post('/api/v1/portability/exit/request', data: {
      'destinationProvider': destinationProvider,
      'reason': reason,
    });
    return TenantExitRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<TenantExitRequestDto> verifyArchive({
    required String exitRequestId,
    required String archiveChecksum,
  }) async {
    final response = await apiClient.post('/api/v1/portability/exit/verify-archive/$exitRequestId', data: {
      'archiveChecksum': archiveChecksum,
    });
    return TenantExitRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<RetentionClassificationSummaryDto> getRetentionSummary(String tenantId) async {
    final response = await apiClient.get('/api/v1/portability/exit/retention-summary/$tenantId');
    return RetentionClassificationSummaryDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<PurgeAuditCertificateDto> executePurge({
    required String exitRequestId,
    required String tenantAdminApproval,
    required String platformAdminApproval,
  }) async {
    final response = await apiClient.post('/api/v1/portability/exit/execute-purge/$exitRequestId', data: {
      'tenantAdminApproval': tenantAdminApproval,
      'platformAdminApproval': platformAdminApproval,
    });
    return PurgeAuditCertificateDto.fromJson(response.data as Map<String, dynamic>);
  }
}
