import '../../../core/networking/api_client.dart';

class ExemptSectorAuthorizationDto {
  final String id;
  final String tenantId;
  final String sectorCode;
  final String authorizedBy;
  final String authorizationReference;
  final String reportingFrequency; // DAILY, WEEKLY, MONTHLY
  final DateTime effectiveFrom;
  final DateTime? effectiveTo;
  final bool active;

  ExemptSectorAuthorizationDto({
    required this.id,
    required this.tenantId,
    required this.sectorCode,
    required this.authorizedBy,
    required this.authorizationReference,
    required this.reportingFrequency,
    required this.effectiveFrom,
    this.effectiveTo,
    required this.active,
  });

  factory ExemptSectorAuthorizationDto.fromJson(Map<String, dynamic> json) {
    return ExemptSectorAuthorizationDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      sectorCode: json['sectorCode']?.toString() ?? '',
      authorizedBy: json['authorizedBy']?.toString() ?? '',
      authorizationReference: json['authorizationReference']?.toString() ?? '',
      reportingFrequency: json['reportingFrequency']?.toString() ?? 'MONTHLY',
      effectiveFrom: json['effectiveFrom'] != null
          ? DateTime.tryParse(json['effectiveFrom'].toString()) ?? DateTime.now()
          : DateTime.now(),
      effectiveTo: json['effectiveTo'] != null
          ? DateTime.tryParse(json['effectiveTo'].toString())
          : null,
      active: json['active'] != false,
    );
  }
}

class ExemptSectorSummaryReportDto {
  final String id;
  final String tenantId;
  final String reportingPeriod;
  final int transactionCount;
  final double grossAmount;
  final double excludedB2bAmount;
  final double summaryAmount;
  final String status; // DRAFT, READY, SUBMITTED, ACKNOWLEDGED, FAILED
  final String? morAcknowledgementNumber;
  final DateTime generatedAt;
  final DateTime? submittedAt;

  ExemptSectorSummaryReportDto({
    required this.id,
    required this.tenantId,
    required this.reportingPeriod,
    required this.transactionCount,
    required this.grossAmount,
    required this.excludedB2bAmount,
    required this.summaryAmount,
    required this.status,
    this.morAcknowledgementNumber,
    required this.generatedAt,
    this.submittedAt,
  });

  factory ExemptSectorSummaryReportDto.fromJson(Map<String, dynamic> json) {
    return ExemptSectorSummaryReportDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      reportingPeriod: json['reportingPeriod']?.toString() ?? '',
      transactionCount: (json['transactionCount'] as num?)?.toInt() ?? 0,
      grossAmount: (json['grossAmount'] as num?)?.toDouble() ?? 0.0,
      excludedB2bAmount: (json['excludedB2bAmount'] as num?)?.toDouble() ?? 0.0,
      summaryAmount: (json['summaryAmount'] as num?)?.toDouble() ?? 0.0,
      status: json['status']?.toString() ?? 'DRAFT',
      morAcknowledgementNumber: json['morAcknowledgementNumber']?.toString(),
      generatedAt: json['generatedAt'] != null
          ? DateTime.tryParse(json['generatedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      submittedAt: json['submittedAt'] != null
          ? DateTime.tryParse(json['submittedAt'].toString())
          : null,
    );
  }
}

class ExemptSectorService {
  final ApiClient apiClient;

  ExemptSectorService(this.apiClient);

  Future<ExemptSectorAuthorizationDto?> getAuthorization(String tenantId) async {
    try {
      final response = await apiClient.get('/api/v1/compliance/exempt-sectors/authorizations/$tenantId');
      if (response.statusCode == 404 || response.data == null) return null;
      return ExemptSectorAuthorizationDto.fromJson(response.data as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  Future<ExemptSectorAuthorizationDto> grantAuthorization(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/compliance/exempt-sectors/authorizations', data: payload);
    return ExemptSectorAuthorizationDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ExemptSectorSummaryReportDto> generateDraftReport(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/compliance/exempt-sectors/reports/generate', data: payload);
    return ExemptSectorSummaryReportDto.fromJson(response.data as Map<String, dynamic>);
  }
}
