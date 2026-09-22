class BuyerModel {
  final String legalName;
  final String tin;
  final String? vatNumber;
  final String? idNumber;
  final String? idType;
  final String? phone;
  final String? email;
  final String country;
  final String? region;
  final String? city;
  final String? zone;
  final String? woreda;
  final String? kebele;
  final String? houseNumber;

  const BuyerModel({
    required this.legalName,
    required this.tin,
    this.vatNumber,
    this.idNumber,
    this.idType,
    this.phone,
    this.email,
    this.country = 'ET',
    this.region,
    this.city,
    this.zone,
    this.woreda,
    this.kebele,
    this.houseNumber,
  });

  Map<String, dynamic> toJson() => {
        'legalName': legalName,
        'tin': tin,
        if (vatNumber != null) 'vatNumber': vatNumber,
        if (idNumber != null) 'idNumber': idNumber,
        if (idType != null) 'idType': idType,
        if (phone != null) 'phone': phone,
        if (email != null) 'email': email,
        'country': country,
        if (region != null) 'region': region,
        if (city != null) 'city': city,
        if (zone != null) 'zone': zone,
        if (woreda != null) 'woreda': woreda,
        if (kebele != null) 'kebele': kebele,
        if (houseNumber != null) 'houseNo': houseNumber,
      };

  factory BuyerModel.fromJson(Map<String, dynamic> json) => BuyerModel(
        legalName: json['legalName']?.toString() ?? '',
        tin: json['tin']?.toString() ?? '',
        vatNumber: json['vatNumber']?.toString(),
        idNumber: json['idNumber']?.toString(),
        idType: json['idType']?.toString(),
        phone: json['phone']?.toString(),
        email: json['email']?.toString(),
        country: json['country']?.toString() ?? 'ET',
        region: json['region']?.toString(),
        city: json['city']?.toString(),
        zone: json['zone']?.toString(),
        woreda: json['woreda']?.toString(),
        kebele: json['kebele']?.toString(),
        houseNumber: json['houseNo']?.toString() ?? json['houseNumber']?.toString(),
      );
}

class InvoiceLineModel {
  final int lineNumber;
  final String itemCode;
  final String productDescription;
  final double quantity;
  final String unit;
  final double unitPrice;
  final double discount;
  final double preTaxValue;
  final String taxCode;
  final double taxAmount;
  final double totalLineAmount;

  const InvoiceLineModel({
    required this.lineNumber,
    required this.itemCode,
    required this.productDescription,
    required this.quantity,
    this.unit = 'PCS',
    required this.unitPrice,
    this.discount = 0.0,
    required this.preTaxValue,
    this.taxCode = 'VAT15',
    required this.taxAmount,
    required this.totalLineAmount,
  });

  Map<String, dynamic> toJson() => {
        'lineNumber': lineNumber,
        'itemCode': itemCode,
        'productDescription': productDescription,
        'quantity': quantity,
        'unit': unit,
        'unitPrice': unitPrice,
        'discount': discount,
        'taxCode': taxCode,
      };

  factory InvoiceLineModel.fromJson(Map<String, dynamic> json) => InvoiceLineModel(
        lineNumber: json['lineNumber'] is int ? json['lineNumber'] : 1,
        itemCode: json['itemCode']?.toString() ?? '',
        productDescription: json['productDescription']?.toString() ?? '',
        quantity: (json['quantity'] as num?)?.toDouble() ?? 1.0,
        unit: json['unit']?.toString() ?? 'PCS',
        unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
        discount: (json['discount'] as num?)?.toDouble() ?? 0.0,
        preTaxValue: (json['preTaxValue'] as num?)?.toDouble() ?? 0.0,
        taxCode: json['taxCode']?.toString() ?? 'VAT15',
        taxAmount: (json['taxAmount'] as num?)?.toDouble() ?? 0.0,
        totalLineAmount: (json['totalLineAmount'] as num?)?.toDouble() ?? 0.0,
      );
}

class InvoiceModel {
  final String id;
  final String tenantId;
  final String? branchId;
  final String documentNumber;
  final int? invoiceCounter;
  final DateTime invoiceDate;
  final String transactionType; // 'B2B', 'B2C'
  final String paymentMode;
  final String status;
  final double preTaxTotal;
  final double taxTotal;
  final double grandTotal;
  final String currency;
  final String? irn;
  final String? rrn;
  final String? signedQr;
  final int reprintCount;
  final BuyerModel? buyer;
  final List<InvoiceLineModel> lines;

  const InvoiceModel({
    required this.id,
    required this.tenantId,
    this.branchId,
    required this.documentNumber,
    this.invoiceCounter,
    required this.invoiceDate,
    required this.transactionType,
    required this.paymentMode,
    required this.status,
    required this.preTaxTotal,
    required this.taxTotal,
    required this.grandTotal,
    this.currency = 'ETB',
    this.irn,
    this.rrn,
    this.signedQr,
    this.reprintCount = 0,
    this.buyer,
    this.lines = const [],
  });

  factory InvoiceModel.fromJson(Map<String, dynamic> json) {
    BuyerModel? buyer;
    if (json['buyer'] is Map<String, dynamic>) {
      buyer = BuyerModel.fromJson(json['buyer']);
    }

    List<InvoiceLineModel> lines = [];
    if (json['lines'] is List) {
      lines = (json['lines'] as List)
          .map((item) => InvoiceLineModel.fromJson(item as Map<String, dynamic>))
          .toList();
    }

    return InvoiceModel(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      branchId: json['branchId']?.toString(),
      documentNumber: json['documentNumber']?.toString() ?? '',
      invoiceCounter: json['invoiceCounter'] as int?,
      invoiceDate: json['invoiceDate'] != null
          ? DateTime.tryParse(json['invoiceDate'].toString()) ?? DateTime.now()
          : DateTime.now(),
      transactionType: json['transactionType']?.toString() ?? 'B2C',
      paymentMode: json['paymentMode']?.toString() ?? 'CASH',
      status: json['status']?.toString() ?? 'PENDING_REGISTRATION',
      preTaxTotal: (json['preTaxTotal'] as num?)?.toDouble() ?? 0.0,
      taxTotal: (json['taxTotal'] as num?)?.toDouble() ?? 0.0,
      grandTotal: (json['grandTotal'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency']?.toString() ?? 'ETB',
      irn: json['irn']?.toString(),
      rrn: json['rrn']?.toString(),
      signedQr: json['signedQr']?.toString(),
      reprintCount: json['reprintCount'] as int? ?? 0,
      buyer: buyer,
      lines: lines,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'tenantId': tenantId,
        if (branchId != null) 'branchId': branchId,
        'documentNumber': documentNumber,
        if (invoiceCounter != null) 'invoiceCounter': invoiceCounter,
        'invoiceDate': invoiceDate.toIso8601String(),
        'transactionType': transactionType,
        'paymentMode': paymentMode,
        'status': status,
        'preTaxTotal': preTaxTotal,
        'taxTotal': taxTotal,
        'grandTotal': grandTotal,
        'currency': currency,
        if (irn != null) 'irn': irn,
        if (rrn != null) 'rrn': rrn,
        if (signedQr != null) 'signedQr': signedQr,
        'reprintCount': reprintCount,
        if (buyer != null) 'buyer': buyer!.toJson(),
        'lines': lines.map((l) => l.toJson()).toList(),
      };
}

/// Client-side Tax Estimator.
/// Strictly non-authoritative — provides real-time UI previews before server calculation wins.
class ClientTaxEstimator {
  static const double standardVatRate = 0.15; // 15% Ethiopian VAT

  static CalculatedTotals estimateTotals(List<InvoiceLineDraft> items) {
    double preTaxSum = 0.0;
    double taxSum = 0.0;

    for (final item in items) {
      final lineBase = (item.quantity * item.unitPrice) - item.discount;
      final preTax = lineBase > 0 ? lineBase : 0.0;
      double tax = 0.0;
      if (item.taxCode == 'VAT15') {
        tax = (preTax * standardVatRate * 100).round() / 100.0; // Two decimal rounding
      }
      preTaxSum += preTax;
      taxSum += tax;
    }

    final grandTotal = preTaxSum + taxSum;
    return CalculatedTotals(
      preTaxTotal: (preTaxSum * 100).round() / 100.0,
      taxTotal: (taxSum * 100).round() / 100.0,
      grandTotal: (grandTotal * 100).round() / 100.0,
    );
  }
}

class CalculatedTotals {
  final double preTaxTotal;
  final double taxTotal;
  final double grandTotal;

  const CalculatedTotals({
    required this.preTaxTotal,
    required this.taxTotal,
    required this.grandTotal,
  });
}

class InvoiceLineDraft {
  String itemCode;
  String productDescription;
  double quantity;
  String unit;
  double unitPrice;
  double discount;
  String taxCode;
  String itemType; // 'PRODUCT' or 'SERVICE'
  String? catalogItemId;

  InvoiceLineDraft({
    required this.itemCode,
    required this.productDescription,
    this.quantity = 1.0,
    this.unit = 'PCS',
    this.unitPrice = 0.0,
    this.discount = 0.0,
    this.taxCode = 'VAT15',
    this.itemType = 'PRODUCT',
    this.catalogItemId,
  });
}

class InvoiceSummary {
  final int todayInvoicesCount;
  final double todayGrossSales;
  final double todayVatAmount;
  final int totalInvoicesCount;
  final double totalGrossSales;
  final double totalVatAmount;
  final int registeredCount;
  final int pendingCount;

  const InvoiceSummary({
    this.todayInvoicesCount = 0,
    this.todayGrossSales = 0.0,
    this.todayVatAmount = 0.0,
    this.totalInvoicesCount = 0,
    this.totalGrossSales = 0.0,
    this.totalVatAmount = 0.0,
    this.registeredCount = 0,
    this.pendingCount = 0,
  });

  factory InvoiceSummary.fromJson(Map<String, dynamic> json) {
    return InvoiceSummary(
      todayInvoicesCount: (json['todayInvoicesCount'] as num?)?.toInt() ?? 0,
      todayGrossSales: (json['todayGrossSales'] as num?)?.toDouble() ?? 0.0,
      todayVatAmount: (json['todayVatAmount'] as num?)?.toDouble() ?? 0.0,
      totalInvoicesCount: (json['totalInvoicesCount'] as num?)?.toInt() ?? 0,
      totalGrossSales: (json['totalGrossSales'] as num?)?.toDouble() ?? 0.0,
      totalVatAmount: (json['totalVatAmount'] as num?)?.toDouble() ?? 0.0,
      registeredCount: (json['registeredCount'] as num?)?.toInt() ?? 0,
      pendingCount: (json['pendingCount'] as num?)?.toInt() ?? 0,
    );
  }
}
