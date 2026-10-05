import '../../../core/networking/api_client.dart';

enum CancellationReasonCode {
  incorrectCustomerDetails('INCORRECT_CUSTOMER_DETAILS', 'Incorrect Customer / Buyer Information'),
  wrongItemOrQuantity('WRONG_ITEM_OR_QUANTITY', 'Wrong Item, Unit Price, or Quantity'),
  duplicateIssuance('DUPLICATE_ISSUANCE', 'Duplicate Fiscal Invoice Issuance'),
  unfulfilledCommercialSale('UNFULFILLED_COMMERCIAL_SALE', 'Commercial Transaction Aborted / Unfulfilled'),
  systemRegistrationError('SYSTEM_REGISTRATION_ERROR', 'Fiscal Register Technical Malfunction / System Error'),
  otherStatutoryReason('OTHER_STATUTORY_REASON', 'Other Irreversible Statutory Reason');

  final String code;
  final String label;
  const CancellationReasonCode(this.code, this.label);

  static CancellationReasonCode fromString(String val) {
    return CancellationReasonCode.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => CancellationReasonCode.otherStatutoryReason,
    );
  }
}

enum CancellationStatus {
  requested('REQUESTED', 'Requested / በማረጋገጥ ላይ'),
  underReview('UNDER_REVIEW', 'Under Authority Review / በግምገማ ላይ'),
  evidenceRequested('EVIDENCE_REQUESTED', 'Evidence Demanded / ማስረጃ የተጠየቀበት'),
  evidenceSubmitted('EVIDENCE_SUBMITTED', 'Evidence Submitted / ማስረጃ የቀረበበት'),
  approved('APPROVED', 'Cancellation Approved / የተሰረዘ'),
  rejected('REJECTED', 'Cancellation Rejected / ውድቅ የተደረገ');

  final String code;
  final String label;
  const CancellationStatus(this.code, this.label);

  static CancellationStatus fromString(String val) {
    return CancellationStatus.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => CancellationStatus.requested,
    );
  }
}

class CancellationAttachmentDto {
  final String id;
  final String fileName;
  final String fileHash;
  final int fileSizeBytes;
  final String attachmentType;
  final DateTime uploadedAt;

  CancellationAttachmentDto({
    required this.id,
    required this.fileName,
    required this.fileHash,
    required this.fileSizeBytes,
    required this.attachmentType,
    required this.uploadedAt,
  });

  factory CancellationAttachmentDto.fromJson(Map<String, dynamic> json) {
    return CancellationAttachmentDto(
      id: json['id']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      fileHash: json['fileHash']?.toString() ?? '',
      fileSizeBytes: (json['fileSizeBytes'] as num?)?.toInt() ?? 0,
      attachmentType: json['attachmentType']?.toString() ?? 'DOCUMENT',
      uploadedAt: json['uploadedAt'] != null
          ? DateTime.tryParse(json['uploadedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class CancellationRequestDto {
  final String id;
  final String invoiceId;
  final String? invoiceNumber;
  final String? originalIrn;
  final CancellationReasonCode reasonCode;
  final String reasonDescription;
  final CancellationStatus status;
  final DateTime requestedAt;
  final DateTime? evidenceDeadline;
  final String? rejectionReason;
  final String? cancellationReference;
  final List<CancellationAttachmentDto> attachments;

  CancellationRequestDto({
    required this.id,
    required this.invoiceId,
    this.invoiceNumber,
    this.originalIrn,
    required this.reasonCode,
    required this.reasonDescription,
    required this.status,
    required this.requestedAt,
    this.evidenceDeadline,
    this.rejectionReason,
    this.cancellationReference,
    required this.attachments,
  });

  factory CancellationRequestDto.fromJson(Map<String, dynamic> json) {
    return CancellationRequestDto(
      id: json['id']?.toString() ?? '',
      invoiceId: json['invoiceId']?.toString() ?? '',
      invoiceNumber: json['invoiceNumber']?.toString(),
      originalIrn: json['originalIrn']?.toString(),
      reasonCode: CancellationReasonCode.fromString(json['reasonCode']?.toString() ?? 'OTHER_STATUTORY_REASON'),
      reasonDescription: json['reasonDescription']?.toString() ?? '',
      status: CancellationStatus.fromString(json['status']?.toString() ?? 'REQUESTED'),
      requestedAt: json['requestedAt'] != null
          ? DateTime.tryParse(json['requestedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      evidenceDeadline: json['evidenceDeadline'] != null
          ? DateTime.tryParse(json['evidenceDeadline'].toString())
          : null,
      rejectionReason: json['rejectionReason']?.toString(),
      cancellationReference: json['cancellationReference']?.toString(),
      attachments: (json['attachments'] as List<dynamic>? ?? [])
          .map((a) => CancellationAttachmentDto.fromJson(a as Map<String, dynamic>))
          .toList(),
    );
  }

  Duration? get remainingEvidenceTime {
    if (evidenceDeadline == null) return null;
    final diff = evidenceDeadline!.difference(DateTime.now());
    return diff.isNegative ? Duration.zero : diff;
  }
}

class CancellationService {
  final ApiClient apiClient;

  CancellationService(this.apiClient);

  Future<CancellationRequestDto> requestCancellation({
    required String invoiceId,
    required CancellationReasonCode reasonCode,
    required String reasonDescription,
  }) async {
    final response = await apiClient.post('/api/v1/cancellations', data: {
      'invoiceId': invoiceId,
      'reasonCode': reasonCode.code,
      'reasonDescription': reasonDescription,
    });
    return CancellationRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CancellationRequestDto> getCancellation(String id) async {
    final response = await apiClient.get('/api/v1/cancellations/$id');
    return CancellationRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<CancellationRequestDto>> listCancellations({int page = 0, int size = 20}) async {
    final response = await apiClient.get('/api/v1/cancellations', queryParameters: {
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
    return items.map((c) => CancellationRequestDto.fromJson(c as Map<String, dynamic>)).toList();
  }

  Future<CancellationRequestDto> submitEvidence({
    required String cancellationId,
    required String attachmentType,
    required String fileReference,
    required String fileHash,
    required String fileName,
    required int fileSizeBytes,
  }) async {
    final response = await apiClient.post('/api/v1/cancellations/evidence', data: {
      'cancellationId': cancellationId,
      'attachmentType': attachmentType,
      'fileReference': fileReference,
      'fileHash': fileHash,
      'fileName': fileName,
      'fileSizeBytes': fileSizeBytes,
    });
    return CancellationRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CancellationRequestDto> demandEvidence(String id, {int hours = 48}) async {
    final response = await apiClient.post(
      '/api/v1/cancellations/$id/demand-evidence',
      queryParameters: {'hours': hours},
    );
    return CancellationRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CancellationRequestDto> approveCancellation(String id, String cancellationRef) async {
    final response = await apiClient.post(
      '/api/v1/cancellations/$id/authority-approve',
      queryParameters: {'cancellationRef': cancellationRef},
    );
    return CancellationRequestDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CancellationRequestDto> rejectCancellation(String id, String reason) async {
    final response = await apiClient.post(
      '/api/v1/cancellations/$id/authority-reject',
      queryParameters: {'reason': reason},
    );
    return CancellationRequestDto.fromJson(response.data as Map<String, dynamic>);
  }
}
