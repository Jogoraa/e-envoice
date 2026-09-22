import '../../core/networking/api_client.dart';

class AuditEventModel {
  final String id;
  final String tenantId;
  final String streamId;
  final int sequenceNumber;
  final String action;
  final String actor;
  final String targetType;
  final String targetId;
  final String payloadSummary;
  final String eventHash;
  final DateTime createdAt;

  const AuditEventModel({
    required this.id,
    required this.tenantId,
    required this.streamId,
    required this.sequenceNumber,
    required this.action,
    required this.actor,
    required this.targetType,
    required this.targetId,
    required this.payloadSummary,
    required this.eventHash,
    required this.createdAt,
  });

  factory AuditEventModel.fromJson(Map<String, dynamic> json) {
    return AuditEventModel(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      streamId: json['streamId']?.toString() ?? 'SYSTEM_STREAM',
      sequenceNumber: json['sequenceNumber'] as int? ?? 1,
      action: json['action']?.toString() ?? '',
      actor: json['actor']?.toString() ?? 'SYSTEM',
      targetType: json['targetType']?.toString() ?? '',
      targetId: json['targetId']?.toString() ?? '',
      payloadSummary: json['payloadSummary']?.toString() ?? '',
      eventHash: json['eventHash']?.toString() ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class StreamVerificationResult {
  final String streamId;
  final bool isValid;
  final int totalEventsVerified;
  final String? failedSequence;

  const StreamVerificationResult({
    required this.streamId,
    required this.isValid,
    required this.totalEventsVerified,
    this.failedSequence,
  });

  factory StreamVerificationResult.fromJson(Map<String, dynamic> json) {
    return StreamVerificationResult(
      streamId: json['streamId']?.toString() ?? '',
      isValid: json['valid'] == true || json['isValid'] == true,
      totalEventsVerified: (json['eventsVerified'] ?? json['totalEventsVerified']) as int? ?? 0,
      failedSequence: json['failedSequence']?.toString(),
    );
  }
}

class AuditRepositoryImpl {
  final ApiClient _apiClient;

  AuditRepositoryImpl(this._apiClient);

  Future<List<AuditEventModel>> getAuditLogs({
    String? tenantId,
    int page = 0,
    int size = 50,
  }) async {
    final response = await _apiClient.get(
      '/api/v1/authority/audit-logs',
      queryParameters: {
        if (tenantId != null && tenantId.isNotEmpty) 'tenantId': tenantId,
        'page': page,
        'size': size,
      },
    );

    if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
      final content = response.data['content'];
      if (content is List) {
        return content
            .map((item) => AuditEventModel.fromJson(item as Map<String, dynamic>))
            .toList();
      }
    }
    return [];
  }

  Future<StreamVerificationResult> verifyStream(String streamId) async {
    final response = await _apiClient.get('/api/v1/audit/verification/stream/$streamId');
    if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
      return StreamVerificationResult.fromJson(response.data);
    }
    return StreamVerificationResult(
      streamId: streamId,
      isValid: false,
      totalEventsVerified: 0,
    );
  }

  Future<Map<String, dynamic>> generateCheckpoint(String streamId) async {
    final response = await _apiClient.post('/api/v1/audit/verification/checkpoint/$streamId');
    return (response.data is Map<String, dynamic>) ? response.data : {};
  }
}
