import '../../../core/networking/api_client.dart';

class TenantLifecycleEventDto {
  final String id;
  final String tenantId;
  final String eventType; // COMMENCEMENT, TERMINATION
  final String status; // PENDING, SUBMITTED, ACKNOWLEDGED, FAILED, RETRYING
  final String? governmentAckReference;
  final String? payload;
  final String? lastError;
  final DateTime createdAt;
  final DateTime? acknowledgedAt;

  TenantLifecycleEventDto({
    required this.id,
    required this.tenantId,
    required this.eventType,
    required this.status,
    this.governmentAckReference,
    this.payload,
    this.lastError,
    required this.createdAt,
    this.acknowledgedAt,
  });

  factory TenantLifecycleEventDto.fromJson(Map<String, dynamic> json) {
    return TenantLifecycleEventDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      eventType: json['eventType']?.toString() ?? 'COMMENCEMENT',
      status: json['status']?.toString() ?? 'SUBMITTED',
      governmentAckReference: json['governmentAckReference']?.toString(),
      payload: json['payload']?.toString(),
      lastError: json['lastError']?.toString(),
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      acknowledgedAt: json['acknowledgedAt'] != null
          ? DateTime.tryParse(json['acknowledgedAt'].toString())
          : null,
    );
  }

  bool get isCommencement => eventType == 'COMMENCEMENT';
  bool get isTermination => eventType == 'TERMINATION';
  bool get isAcknowledged => status == 'ACKNOWLEDGED';
  bool get isFailed => status == 'FAILED';
}

class TenantLifecycleService {
  final ApiClient apiClient;

  TenantLifecycleService(this.apiClient);

  Future<TenantLifecycleEventDto> triggerCommencement(String tenantId) async {
    final response = await apiClient.post('/api/v1/tenant-lifecycle/commence/$tenantId');
    return TenantLifecycleEventDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<TenantLifecycleEventDto> triggerTermination(String tenantId) async {
    final response = await apiClient.post('/api/v1/tenant-lifecycle/terminate/$tenantId');
    return TenantLifecycleEventDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<TenantLifecycleEventDto>> getEvents(String tenantId) async {
    final response = await apiClient.get('/api/v1/tenant-lifecycle/events/$tenantId');
    return (response.data as List<dynamic>? ?? [])
        .map((e) => TenantLifecycleEventDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<TenantLifecycleEventDto> recordAcknowledgement({
    required String eventId,
    required String ackReference,
    String? payload,
  }) async {
    final response = await apiClient.post('/api/v1/tenant-lifecycle/acknowledge/$eventId', data: {
      'ackReference': ackReference,
      if (payload != null) 'payload': payload,
    });
    return TenantLifecycleEventDto.fromJson(response.data as Map<String, dynamic>);
  }
}
