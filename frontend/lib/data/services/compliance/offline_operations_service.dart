import '../../../core/networking/api_client.dart';

class DeviceOfflineAllocationDto {
  final String id;
  final String deviceId;
  final String rangeStart;
  final String rangeEnd;
  final String currentSequence;
  final int blockSize;
  final int usedCount;
  final int remainingCount;
  final DateTime allocatedAt;
  final DateTime expiresAt;
  final String status; // ACTIVE, EXHAUSTED, REVOKED, EXPIRED
  final String? authorityReference;

  DeviceOfflineAllocationDto({
    required this.id,
    required this.deviceId,
    required this.rangeStart,
    required this.rangeEnd,
    required this.currentSequence,
    required this.blockSize,
    required this.usedCount,
    required this.remainingCount,
    required this.allocatedAt,
    required this.expiresAt,
    required this.status,
    this.authorityReference,
  });

  factory DeviceOfflineAllocationDto.fromJson(Map<String, dynamic> json) {
    return DeviceOfflineAllocationDto(
      id: json['id']?.toString() ?? '',
      deviceId: json['deviceId']?.toString() ?? '',
      rangeStart: json['rangeStart']?.toString() ?? '',
      rangeEnd: json['rangeEnd']?.toString() ?? '',
      currentSequence: json['currentSequence']?.toString() ?? '',
      blockSize: (json['blockSize'] as num?)?.toInt() ?? 100,
      usedCount: (json['usedCount'] as num?)?.toInt() ?? 0,
      remainingCount: (json['remainingCount'] as num?)?.toInt() ?? 100,
      allocatedAt: json['allocatedAt'] != null
          ? DateTime.tryParse(json['allocatedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      expiresAt: json['expiresAt'] != null
          ? DateTime.tryParse(json['expiresAt'].toString()) ?? DateTime.now().add(const Duration(days: 7))
          : DateTime.now().add(const Duration(days: 7)),
      status: json['status']?.toString() ?? 'ACTIVE',
      authorityReference: json['authorityReference']?.toString(),
    );
  }

  bool get isExpired => DateTime.now().isAfter(expiresAt);
  bool get isExhausted => remainingCount <= 0;
  bool get isWarningThreshold => remainingCount <= 20;
}

class ManualDocumentLineItemDto {
  final String itemDescription;
  final double quantity;
  final double unitPrice;
  final double taxAmount;
  final double totalAmount;

  ManualDocumentLineItemDto({
    required this.itemDescription,
    required this.quantity,
    required this.unitPrice,
    required this.taxAmount,
    required this.totalAmount,
  });

  Map<String, dynamic> toJson() => {
    'itemDescription': itemDescription,
    'quantity': quantity,
    'unitPrice': unitPrice,
    'taxAmount': taxAmount,
    'totalAmount': totalAmount,
  };

  factory ManualDocumentLineItemDto.fromJson(Map<String, dynamic> json) {
    return ManualDocumentLineItemDto(
      itemDescription: json['itemDescription']?.toString() ?? '',
      quantity: (json['quantity'] as num?)?.toDouble() ?? 1.0,
      unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
      taxAmount: (json['taxAmount'] as num?)?.toDouble() ?? 0.0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class ManualFiscalDocumentDto {
  final String id;
  final String manualBookNumber;
  final String manualDocumentNumber;
  final DateTime originalIssueDate;
  final String customerName;
  final String? customerTin;
  final double totalAmount;
  final double taxAmount;
  final String outageReference;
  final String status; // PENDING_RECONCILIATION, RECONCILED, FAILED
  final String? serverIrn;
  final int reprintCount;
  final DateTime reconciledAt;

  ManualFiscalDocumentDto({
    required this.id,
    required this.manualBookNumber,
    required this.manualDocumentNumber,
    required this.originalIssueDate,
    required this.customerName,
    this.customerTin,
    required this.totalAmount,
    required this.taxAmount,
    required this.outageReference,
    required this.status,
    this.serverIrn,
    required this.reprintCount,
    required this.reconciledAt,
  });

  factory ManualFiscalDocumentDto.fromJson(Map<String, dynamic> json) {
    return ManualFiscalDocumentDto(
      id: json['id']?.toString() ?? '',
      manualBookNumber: json['manualBookNumber']?.toString() ?? '',
      manualDocumentNumber: json['manualDocumentNumber']?.toString() ?? '',
      originalIssueDate: json['originalIssueDate'] != null
          ? DateTime.tryParse(json['originalIssueDate'].toString()) ?? DateTime.now()
          : DateTime.now(),
      customerName: json['customerName']?.toString() ?? '',
      customerTin: json['customerTin']?.toString(),
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      taxAmount: (json['taxAmount'] as num?)?.toDouble() ?? 0.0,
      outageReference: json['outageReference']?.toString() ?? '',
      status: json['status']?.toString() ?? 'PENDING_RECONCILIATION',
      serverIrn: json['serverIrn']?.toString(),
      reprintCount: (json['reprintCount'] as num?)?.toInt() ?? 0,
      reconciledAt: json['reconciledAt'] != null
          ? DateTime.tryParse(json['reconciledAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class OfflineOperationsService {
  final ApiClient apiClient;

  OfflineOperationsService(this.apiClient);

  // Offline Allocations (Art. 4(4) & Art. 21)
  Future<DeviceOfflineAllocationDto> requestOfflineAllocation({
    required String deviceId,
    int blockSize = 100,
    int validityDays = 7,
    String? authorityRef,
  }) async {
    final response = await apiClient.post('/api/v1/offline/allocations/request', data: {
      'deviceId': deviceId,
      'blockSize': blockSize,
      'validityDays': validityDays,
      if (authorityRef != null) 'authorityRef': authorityRef,
    });
    return DeviceOfflineAllocationDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<DeviceOfflineAllocationDto?> getActiveAllocation(String deviceId) async {
    final response = await apiClient.get('/api/v1/offline/allocations/active', queryParameters: {
      'deviceId': deviceId,
    });
    if (response.statusCode == 204 || response.data == null || response.data == '') {
      return null;
    }
    return DeviceOfflineAllocationDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> revokeAllocation(String allocationId, {String? reason}) async {
    await apiClient.post(
      '/api/v1/offline/allocations/$allocationId/revoke',
      queryParameters: {'reason': reason ?? 'Operator requested revocation'},
    );
  }

  // Manual Paper / QR Fallback Invoices (Art. 22)
  Future<List<ManualFiscalDocumentDto>> reconcileManualBatch({
    required String outageReference,
    required List<Map<String, dynamic>> documents,
  }) async {
    final response = await apiClient.post('/api/v1/invoices/manual/batch', data: {
      'outageReference': outageReference,
      'documents': documents,
    });
    return (response.data as List<dynamic>? ?? [])
        .map((m) => ManualFiscalDocumentDto.fromJson(m as Map<String, dynamic>))
        .toList();
  }

  Future<List<ManualFiscalDocumentDto>> listManualInvoices({int page = 0, int size = 20}) async {
    final response = await apiClient.get('/api/v1/invoices/manual', queryParameters: {
      'page': page,
      'size': size,
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
    return items.map((m) => ManualFiscalDocumentDto.fromJson(m as Map<String, dynamic>)).toList();
  }

  Future<Map<String, dynamic>> reprintManualInvoice(String id) async {
    final response = await apiClient.get('/api/v1/invoices/manual/$id/reprint');
    return response.data as Map<String, dynamic>;
  }
}
