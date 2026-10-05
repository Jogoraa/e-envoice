import '../../../core/networking/api_client.dart';

class MarketplaceMerchantDto {
  final String id;
  final String tin;
  final String legalName;
  final String tradeName;
  final String address;
  final String contactPhone;
  final String status; // ACTIVE, SUSPENDED, TERMINATED
  final bool authoritySuspended;
  final String? suspensionReason;
  final String? authorityReference;
  final DateTime registeredAt;

  MarketplaceMerchantDto({
    required this.id,
    required this.tin,
    required this.legalName,
    required this.tradeName,
    required this.address,
    required this.contactPhone,
    required this.status,
    required this.authoritySuspended,
    this.suspensionReason,
    this.authorityReference,
    required this.registeredAt,
  });

  factory MarketplaceMerchantDto.fromJson(Map<String, dynamic> json) {
    return MarketplaceMerchantDto(
      id: json['id']?.toString() ?? '',
      tin: json['tin']?.toString() ?? '',
      legalName: json['legalName']?.toString() ?? '',
      tradeName: json['tradeName']?.toString() ?? '',
      address: json['address']?.toString() ?? '',
      contactPhone: json['contactPhone']?.toString() ?? '',
      status: json['status']?.toString() ?? 'ACTIVE',
      authoritySuspended: json['authoritySuspended'] == true,
      suspensionReason: json['suspensionReason']?.toString(),
      authorityReference: json['authorityReference']?.toString(),
      registeredAt: json['registeredAt'] != null
          ? DateTime.tryParse(json['registeredAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class MultiSellerInvoiceSplitDto {
  final String sellerTin;
  final String sellerName;
  final String invoiceId;
  final String? irn;
  final String? rrn;
  final double amount;
  final double taxAmount;
  final String status;

  MultiSellerInvoiceSplitDto({
    required this.sellerTin,
    required this.sellerName,
    required this.invoiceId,
    this.irn,
    this.rrn,
    required this.amount,
    required this.taxAmount,
    required this.status,
  });

  factory MultiSellerInvoiceSplitDto.fromJson(Map<String, dynamic> json) {
    return MultiSellerInvoiceSplitDto(
      sellerTin: json['sellerTin']?.toString() ?? '',
      sellerName: json['sellerName']?.toString() ?? '',
      invoiceId: json['invoiceId']?.toString() ?? '',
      irn: json['irn']?.toString(),
      rrn: json['rrn']?.toString(),
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      taxAmount: (json['taxAmount'] as num?)?.toDouble() ?? 0.0,
      status: json['status']?.toString() ?? 'REGISTERED',
    );
  }
}

class MultiSellerOrderResponseDto {
  final String orderId;
  final double totalAmount;
  final double commissionAmount;
  final String? commissionIrn;
  final List<MultiSellerInvoiceSplitDto> sellerInvoices;

  MultiSellerOrderResponseDto({
    required this.orderId,
    required this.totalAmount,
    required this.commissionAmount,
    this.commissionIrn,
    required this.sellerInvoices,
  });

  factory MultiSellerOrderResponseDto.fromJson(Map<String, dynamic> json) {
    return MultiSellerOrderResponseDto(
      orderId: json['orderId']?.toString() ?? '',
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      commissionAmount: (json['commissionAmount'] as num?)?.toDouble() ?? 0.0,
      commissionIrn: json['commissionIrn']?.toString(),
      sellerInvoices: (json['sellerInvoices'] as List<dynamic>? ?? [])
          .map((s) => MultiSellerInvoiceSplitDto.fromJson(s as Map<String, dynamic>))
          .toList(),
    );
  }
}

class MarketplaceService {
  final ApiClient apiClient;

  MarketplaceService(this.apiClient);

  Future<MarketplaceMerchantDto> registerMerchant(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/marketplace/merchants', data: payload);
    return MarketplaceMerchantDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<MarketplaceMerchantDto>> listMerchants() async {
    final response = await apiClient.get('/api/v1/marketplace/merchants');
    return (response.data as List<dynamic>? ?? [])
        .map((m) => MarketplaceMerchantDto.fromJson(m as Map<String, dynamic>))
        .toList();
  }

  Future<MarketplaceMerchantDto> suspendMerchant({
    required String merchantId,
    required String reason,
    required String authorityReference,
  }) async {
    final response = await apiClient.post('/api/v1/marketplace/merchants/$merchantId/suspend', data: {
      'reason': reason,
      'authorityReference': authorityReference,
    });
    return MarketplaceMerchantDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<MarketplaceMerchantDto> reinstateMerchant({
    required String merchantId,
    required String authorityReference,
  }) async {
    final response = await apiClient.post('/api/v1/marketplace/merchants/$merchantId/reinstate', data: {
      'authorityReference': authorityReference,
    });
    return MarketplaceMerchantDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<MultiSellerOrderResponseDto> processOrder(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/marketplace/orders/checkout', data: payload);
    return MultiSellerOrderResponseDto.fromJson(response.data as Map<String, dynamic>);
  }
}
