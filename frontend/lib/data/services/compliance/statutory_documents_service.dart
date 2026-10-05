import '../../../core/networking/api_client.dart';

enum CashReceiptPurpose {
  advancePayment('ADVANCE_PAYMENT', 'Advance Payment / ቅድመ ክፍያ'),
  creditSaleSettlement('CREDIT_SALE_SETTLEMENT', 'Credit Sale Settlement / የብድር ሽያጭ ክፍያ'),
  loanRepayment('LOAN_REPAYMENT', 'Loan Repayment / የብድር መመለሻ'),
  customerAccountPayment('CUSTOMER_ACCOUNT_PAYMENT', 'Customer Account Payment / የደንበኛ ሂሳብ ክፍያ'),
  otherNonSale('OTHER_NON_SALE', 'Other Non-Sale Receipt / ሌላ ሽያጭ ያልሆነ ገቢ');

  final String code;
  final String label;
  const CashReceiptPurpose(this.code, this.label);

  static CashReceiptPurpose fromString(String val) {
    return CashReceiptPurpose.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => CashReceiptPurpose.otherNonSale,
    );
  }
}

enum CashReceiptPaymentMethod {
  cash('CASH', 'Cash / ጥሬ ገንዘብ'),
  bankTransfer('BANK_TRANSFER', 'Bank Transfer / የባንክ ማስተላለፍ'),
  check('CHECK', 'Check / ቼክ'),
  digitalWallet('DIGITAL_WALLET', 'Digital Wallet / ዲጂታል ቦርሳ');

  final String code;
  final String label;
  const CashReceiptPaymentMethod(this.code, this.label);

  static CashReceiptPaymentMethod fromString(String val) {
    return CashReceiptPaymentMethod.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => CashReceiptPaymentMethod.cash,
    );
  }
}

class CashReceiptDto {
  final String id;
  final String tenantId;
  final String receiptNumber;
  final String payerName;
  final String? payerTin;
  final double amount;
  final String currency;
  final CashReceiptPurpose purpose;
  final String? purposeDescription;
  final String? relatedInvoiceId;
  final String? relatedCreditAccountId;
  final CashReceiptPaymentMethod paymentMethod;
  final String? referenceNumber;
  final DateTime receivedAt;
  final String? irn;
  final String? qrCode;

  CashReceiptDto({
    required this.id,
    required this.tenantId,
    required this.receiptNumber,
    required this.payerName,
    this.payerTin,
    required this.amount,
    required this.currency,
    required this.purpose,
    this.purposeDescription,
    this.relatedInvoiceId,
    this.relatedCreditAccountId,
    required this.paymentMethod,
    this.referenceNumber,
    required this.receivedAt,
    this.irn,
    this.qrCode,
  });

  factory CashReceiptDto.fromJson(Map<String, dynamic> json) {
    return CashReceiptDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      receiptNumber: json['receiptNumber']?.toString() ?? '',
      payerName: json['payerName']?.toString() ?? '',
      payerTin: json['payerTin']?.toString(),
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency']?.toString() ?? 'ETB',
      purpose: CashReceiptPurpose.fromString(json['purpose']?.toString() ?? 'OTHER_NON_SALE'),
      purposeDescription: json['purposeDescription']?.toString(),
      relatedInvoiceId: json['relatedInvoiceId']?.toString(),
      relatedCreditAccountId: json['relatedCreditAccountId']?.toString(),
      paymentMethod: CashReceiptPaymentMethod.fromString(json['paymentMethod']?.toString() ?? 'CASH'),
      referenceNumber: json['referenceNumber']?.toString(),
      receivedAt: json['receivedAt'] != null
          ? DateTime.tryParse(json['receivedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      irn: json['irn']?.toString(),
      qrCode: json['qrCode']?.toString(),
    );
  }
}

enum UnavailableReceiptReason {
  farmerAgriculturalProduce('FARMER_AGRICULTURAL_PRODUCE', 'Farmer / Agricultural Produce (ገበሬ/ግብርና ምርት)'),
  informalSupplierBelowThreshold('INFORMAL_SUPPLIER_BELOW_THRESHOLD', 'Informal Supplier Below Threshold (ከጣሪያ በታች ያልተመዘገበ አቅራቢ)'),
  statutoryExemptSupplier('STATUTORY_EXEMPT_SUPPLIER', 'Statutory Exempt Supplier (በህግ የተፈቀደ ነፃ አቅራቢ)'),
  outOfOfficeEmergency('OUT_OF_OFFICE_EMERGENCY', 'Out of Office Emergency Procurement (ከቢሮ ውጭ ድንገተኛ ግዥ)'),
  otherLegalExemption('OTHER_LEGAL_EXEMPTION', 'Other Legal Exemption (ሌላ ህጋዊ ምክንያት)');

  final String code;
  final String label;
  const UnavailableReceiptReason(this.code, this.label);

  static UnavailableReceiptReason fromString(String val) {
    return UnavailableReceiptReason.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => UnavailableReceiptReason.otherLegalExemption,
    );
  }
}

class PurchaseVoucherLineDto {
  final String itemDescription;
  final double quantity;
  final String unitOfMeasure;
  final double unitPrice;
  final double? taxRate;
  final double lineTotal;

  PurchaseVoucherLineDto({
    required this.itemDescription,
    required this.quantity,
    required this.unitOfMeasure,
    required this.unitPrice,
    this.taxRate,
    double? lineTotal,
  }) : lineTotal = lineTotal ?? (quantity * unitPrice);

  Map<String, dynamic> toJson() => {
    'itemDescription': itemDescription,
    'quantity': quantity,
    'unitOfMeasure': unitOfMeasure,
    'unitPrice': unitPrice,
    if (taxRate != null) 'taxRate': taxRate,
  };

  factory PurchaseVoucherLineDto.fromJson(Map<String, dynamic> json) {
    final qty = (json['quantity'] as num?)?.toDouble() ?? 1.0;
    final price = (json['unitPrice'] as num?)?.toDouble() ?? 0.0;
    return PurchaseVoucherLineDto(
      itemDescription: json['itemDescription']?.toString() ?? '',
      quantity: qty,
      unitOfMeasure: json['unitOfMeasure']?.toString() ?? 'PCS',
      unitPrice: price,
      taxRate: (json['taxRate'] as num?)?.toDouble(),
      lineTotal: (json['lineTotal'] as num?)?.toDouble() ?? (qty * price),
    );
  }
}

class PurchaseVoucherDto {
  final String id;
  final String tenantId;
  final String voucherNumber;
  final String supplierName;
  final String? supplierTin;
  final String? supplierIdNumber;
  final String? supplierIdType;
  final String? supplierPhone;
  final String? supplierAddress;
  final UnavailableReceiptReason unavailableReason;
  final String? reasonDescription;
  final DateTime transactionDate;
  final String currency;
  final double totalAmount;
  final double? withholdingAmount;
  final String? attachmentReference;
  final List<PurchaseVoucherLineDto> items;
  final String? irn;
  final String? qrCode;

  PurchaseVoucherDto({
    required this.id,
    required this.tenantId,
    required this.voucherNumber,
    required this.supplierName,
    this.supplierTin,
    this.supplierIdNumber,
    this.supplierIdType,
    this.supplierPhone,
    this.supplierAddress,
    required this.unavailableReason,
    this.reasonDescription,
    required this.transactionDate,
    required this.currency,
    required this.totalAmount,
    this.withholdingAmount,
    this.attachmentReference,
    required this.items,
    this.irn,
    this.qrCode,
  });

  factory PurchaseVoucherDto.fromJson(Map<String, dynamic> json) {
    return PurchaseVoucherDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      voucherNumber: json['voucherNumber']?.toString() ?? '',
      supplierName: json['supplierName']?.toString() ?? '',
      supplierTin: json['supplierTin']?.toString(),
      supplierIdNumber: json['supplierIdNumber']?.toString(),
      supplierIdType: json['supplierIdType']?.toString(),
      supplierPhone: json['supplierPhone']?.toString(),
      supplierAddress: json['supplierAddress']?.toString(),
      unavailableReason: UnavailableReceiptReason.fromString(json['unavailableReason']?.toString() ?? 'OTHER_LEGAL_EXEMPTION'),
      reasonDescription: json['reasonDescription']?.toString(),
      transactionDate: json['transactionDate'] != null
          ? DateTime.tryParse(json['transactionDate'].toString()) ?? DateTime.now()
          : DateTime.now(),
      currency: json['currency']?.toString() ?? 'ETB',
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      withholdingAmount: (json['withholdingAmount'] as num?)?.toDouble(),
      attachmentReference: json['attachmentReference']?.toString(),
      items: (json['items'] as List<dynamic>? ?? [])
          .map((i) => PurchaseVoucherLineDto.fromJson(i as Map<String, dynamic>))
          .toList(),
      irn: json['irn']?.toString(),
      qrCode: json['qrCode']?.toString(),
    );
  }
}

enum WithholdingType {
  incomeTaxWithholding('INCOME_TAX_WITHHOLDING', 'Income Tax Withholding (2% / የገቢ ግብር ቅነሳ)'),
  vatWithholding('VAT_WITHHOLDING', 'VAT Withholding (50% or 100% / የተጨማሪ እሴት ታክስ ቅነሳ)');

  final String code;
  final String label;
  const WithholdingType(this.code, this.label);

  static WithholdingType fromString(String val) {
    return WithholdingType.values.firstWhere(
      (e) => e.code == val || e.name == val,
      orElse: () => WithholdingType.incomeTaxWithholding,
    );
  }
}

class WithholdingReceiptDto {
  final String id;
  final String receiptNumber;
  final WithholdingType withholdingType;
  final String? relatedInvoiceId;
  final String? relatedInvoiceIrn;
  final String withholdingAgentTin;
  final String withholdingAgentName;
  final String taxpayerTin;
  final String taxpayerName;
  final double taxBaseAmount;
  final double withheldTaxRate;
  final double withheldAmount;
  final String? paymentReference;
  final DateTime issueDate;
  final String? irn;
  final String? qrCode;

  WithholdingReceiptDto({
    required this.id,
    required this.receiptNumber,
    required this.withholdingType,
    this.relatedInvoiceId,
    this.relatedInvoiceIrn,
    required this.withholdingAgentTin,
    required this.withholdingAgentName,
    required this.taxpayerTin,
    required this.taxpayerName,
    required this.taxBaseAmount,
    required this.withheldTaxRate,
    required this.withheldAmount,
    this.paymentReference,
    required this.issueDate,
    this.irn,
    this.qrCode,
  });

  factory WithholdingReceiptDto.fromJson(Map<String, dynamic> json) {
    return WithholdingReceiptDto(
      id: json['id']?.toString() ?? '',
      receiptNumber: json['receiptNumber']?.toString() ?? '',
      withholdingType: WithholdingType.fromString(json['withholdingType']?.toString() ?? 'INCOME_TAX_WITHHOLDING'),
      relatedInvoiceId: json['relatedInvoiceId']?.toString(),
      relatedInvoiceIrn: json['relatedInvoiceIrn']?.toString(),
      withholdingAgentTin: json['withholdingAgentTin']?.toString() ?? '',
      withholdingAgentName: json['withholdingAgentName']?.toString() ?? '',
      taxpayerTin: json['taxpayerTin']?.toString() ?? '',
      taxpayerName: json['taxpayerName']?.toString() ?? '',
      taxBaseAmount: (json['taxBaseAmount'] as num?)?.toDouble() ?? 0.0,
      withheldTaxRate: (json['withheldTaxRate'] as num?)?.toDouble() ?? 0.0,
      withheldAmount: (json['withheldAmount'] as num?)?.toDouble() ?? 0.0,
      paymentReference: json['paymentReference']?.toString(),
      issueDate: json['issueDate'] != null
          ? DateTime.tryParse(json['issueDate'].toString()) ?? DateTime.now()
          : DateTime.now(),
      irn: json['irn']?.toString(),
      qrCode: json['qrCode']?.toString(),
    );
  }
}

class CreditAccountSummaryDto {
  final String invoiceId;
  final String invoiceNumber;
  final String? irn;
  final double originalTotal;
  final double settledAmount;
  final double remainingBalance;
  final bool isFullySettled;
  final List<CreditSettlementItemDto> settlements;

  CreditAccountSummaryDto({
    required this.invoiceId,
    required this.invoiceNumber,
    this.irn,
    required this.originalTotal,
    required this.settledAmount,
    required this.remainingBalance,
    required this.isFullySettled,
    required this.settlements,
  });

  factory CreditAccountSummaryDto.fromJson(Map<String, dynamic> json) {
    return CreditAccountSummaryDto(
      invoiceId: json['invoiceId']?.toString() ?? '',
      invoiceNumber: json['invoiceNumber']?.toString() ?? '',
      irn: json['irn']?.toString(),
      originalTotal: (json['originalTotal'] as num?)?.toDouble() ?? 0.0,
      settledAmount: (json['settledAmount'] as num?)?.toDouble() ?? 0.0,
      remainingBalance: (json['remainingBalance'] as num?)?.toDouble() ?? 0.0,
      isFullySettled: json['fullySettled'] == true,
      settlements: (json['settlements'] as List<dynamic>? ?? [])
          .map((s) => CreditSettlementItemDto.fromJson(s as Map<String, dynamic>))
          .toList(),
    );
  }
}

class CreditSettlementItemDto {
  final String id;
  final double amount;
  final String? paymentMethod;
  final String? paymentReference;
  final DateTime paymentDate;
  final String? cashReceiptNumber;

  CreditSettlementItemDto({
    required this.id,
    required this.amount,
    this.paymentMethod,
    this.paymentReference,
    required this.paymentDate,
    this.cashReceiptNumber,
  });

  factory CreditSettlementItemDto.fromJson(Map<String, dynamic> json) {
    return CreditSettlementItemDto(
      id: json['id']?.toString() ?? '',
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      paymentMethod: json['paymentMethod']?.toString(),
      paymentReference: json['paymentReference']?.toString(),
      paymentDate: json['paymentDate'] != null
          ? DateTime.tryParse(json['paymentDate'].toString()) ?? DateTime.now()
          : DateTime.now(),
      cashReceiptNumber: json['cashReceiptNumber']?.toString(),
    );
  }
}

class StatutoryDocumentsService {
  final ApiClient apiClient;

  StatutoryDocumentsService(this.apiClient);

  // Cash Receipts (Art. 2(16))
  Future<CashReceiptDto> issueCashReceipt(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/cash-receipts', data: payload);
    return CashReceiptDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<CashReceiptDto>> listCashReceipts({CashReceiptPurpose? purpose}) async {
    final query = <String, dynamic>{};
    if (purpose != null) query['purpose'] = purpose.code;
    final response = await apiClient.get('/api/v1/cash-receipts', queryParameters: query);
    return (response.data as List<dynamic>? ?? [])
        .map((r) => CashReceiptDto.fromJson(r as Map<String, dynamic>))
        .toList();
  }

  Future<CashReceiptDto> getCashReceipt(String id) async {
    final response = await apiClient.get('/api/v1/cash-receipts/$id');
    return CashReceiptDto.fromJson(response.data as Map<String, dynamic>);
  }

  // Purchase Vouchers (Art. 2(18))
  Future<PurchaseVoucherDto> createPurchaseVoucher(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/purchase-vouchers', data: payload);
    return PurchaseVoucherDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<PurchaseVoucherDto>> listPurchaseVouchers({String? supplierTin}) async {
    final query = <String, dynamic>{};
    if (supplierTin != null && supplierTin.isNotEmpty) query['supplierTin'] = supplierTin;
    final response = await apiClient.get('/api/v1/purchase-vouchers', queryParameters: query);
    return (response.data as List<dynamic>? ?? [])
        .map((v) => PurchaseVoucherDto.fromJson(v as Map<String, dynamic>))
        .toList();
  }

  Future<PurchaseVoucherDto> getPurchaseVoucher(String id) async {
    final response = await apiClient.get('/api/v1/purchase-vouchers/$id');
    return PurchaseVoucherDto.fromJson(response.data as Map<String, dynamic>);
  }

  // Withholding Receipts (Art. 2(17, 19))
  Future<WithholdingReceiptDto> issueWithholdingReceipt(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/withholding-receipts', data: payload);
    return WithholdingReceiptDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<WithholdingReceiptDto>> listWithholdingReceipts({WithholdingType? type}) async {
    final query = <String, dynamic>{};
    if (type != null) query['type'] = type.code;
    final response = await apiClient.get('/api/v1/withholding-receipts', queryParameters: query);
    return (response.data as List<dynamic>? ?? [])
        .map((w) => WithholdingReceiptDto.fromJson(w as Map<String, dynamic>))
        .toList();
  }

  Future<WithholdingReceiptDto> getWithholdingReceipt(String id) async {
    final response = await apiClient.get('/api/v1/withholding-receipts/$id');
    return WithholdingReceiptDto.fromJson(response.data as Map<String, dynamic>);
  }

  // Credit Settlements (Art. 2(14), Art. 24)
  Future<CreditAccountSummaryDto> getCreditAccountSummary(String invoiceId) async {
    final response = await apiClient.get('/api/v1/credit-settlements/summary/$invoiceId');
    return CreditAccountSummaryDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CreditSettlementItemDto> recordCreditSettlement(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/credit-settlements', data: payload);
    return CreditSettlementItemDto.fromJson(response.data as Map<String, dynamic>);
  }
}
