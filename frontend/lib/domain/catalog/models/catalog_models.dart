/// Product and Service Catalog Domain Models
/// Establishes clear separation between physical inventory products and non-physical services.
library;

enum TaxClassification {
  vat15('VAT15', 0.15, '15% Standard VAT (የተጨማሪ እሴት ታክስ 15%)'),
  exempt('EXEMPT', 0.0, 'Exempt from VAT (ከቫት ነፃ)'),
  zeroRated('ZERO_RATED', 0.0, 'Zero-Rated (0% የዜሮ ተመን)');

  final String code;
  final double rate;
  final String label;

  const TaxClassification(this.code, this.rate, this.label);

  static TaxClassification fromCode(String? code) {
    if (code == null) return TaxClassification.vat15;
    for (final val in TaxClassification.values) {
      if (val.code == code) return val;
    }
    return TaxClassification.vat15;
  }
}

class ProductModel {
  final String id;
  final String tenantId;
  final String branchId;
  final String itemCode;
  final String sku;
  final String? barcode;
  final String description;
  final String category;
  final String unit; // 'PCS', 'KG', 'LITER', 'BOX', etc.
  final double unitPrice;
  final TaxClassification taxClassification;
  final bool isActive;
  final bool trackStock;
  final double stockQuantity;
  final double minStockLevel;

  const ProductModel({
    required this.id,
    required this.tenantId,
    required this.branchId,
    required this.itemCode,
    required this.sku,
    this.barcode,
    required this.description,
    required this.category,
    this.unit = 'PCS',
    required this.unitPrice,
    this.taxClassification = TaxClassification.vat15,
    this.isActive = true,
    this.trackStock = true,
    this.stockQuantity = 0.0,
    this.minStockLevel = 5.0,
  });

  bool get isOutOfStock => trackStock && stockQuantity <= 0;
  bool get isLowStock => trackStock && stockQuantity > 0 && stockQuantity <= minStockLevel;

  ProductModel copyWith({
    String? id,
    String? tenantId,
    String? branchId,
    String? itemCode,
    String? sku,
    String? barcode,
    String? description,
    String? category,
    String? unit,
    double? unitPrice,
    TaxClassification? taxClassification,
    bool? isActive,
    bool? trackStock,
    double? stockQuantity,
    double? minStockLevel,
  }) {
    return ProductModel(
      id: id ?? this.id,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      itemCode: itemCode ?? this.itemCode,
      sku: sku ?? this.sku,
      barcode: barcode ?? this.barcode,
      description: description ?? this.description,
      category: category ?? this.category,
      unit: unit ?? this.unit,
      unitPrice: unitPrice ?? this.unitPrice,
      taxClassification: taxClassification ?? this.taxClassification,
      isActive: isActive ?? this.isActive,
      trackStock: trackStock ?? this.trackStock,
      stockQuantity: stockQuantity ?? this.stockQuantity,
      minStockLevel: minStockLevel ?? this.minStockLevel,
    );
  }
  factory ProductModel.fromJson(Map<String, dynamic> json) {
    return ProductModel(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      branchId: json['branchId']?.toString() ?? '',
      itemCode: json['itemCode']?.toString() ?? '',
      sku: json['sku']?.toString() ?? '',
      barcode: json['barcode']?.toString(),
      description: json['description']?.toString() ?? '',
      category: json['categoryCode']?.toString() ?? json['category']?.toString() ?? '',
      unit: json['unit']?.toString() ?? 'PCS',
      unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
      taxClassification: TaxClassification.fromCode(json['taxClassification']?.toString()),
      isActive: json['isActive'] as bool? ?? (json['active'] as bool? ?? true),
      trackStock: json['trackStock'] as bool? ?? true,
      stockQuantity: (json['stockQuantity'] as num?)?.toDouble() ?? 0.0,
      minStockLevel: (json['minStockLevel'] as num?)?.toDouble() ?? 5.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'tenantId': tenantId,
      'branchId': branchId,
      'itemCode': itemCode,
      'sku': sku,
      'barcode': barcode,
      'description': description,
      'categoryCode': category,
      'unit': unit,
      'unitPrice': unitPrice,
      'taxClassification': taxClassification.code,
      'isActive': isActive,
      'trackStock': trackStock,
      'stockQuantity': stockQuantity,
      'minStockLevel': minStockLevel,
    };
  }
}

class ServiceModel {
  final String id;
  final String tenantId;
  final String branchId;
  final String serviceCode;
  final String name;
  final String description;
  final String category;
  final String unit; // 'HOUR', 'SESSION', 'CONTRACT', 'SERVICE'
  final double unitPrice;
  final TaxClassification taxClassification;
  final bool isActive;

  const ServiceModel({
    required this.id,
    required this.tenantId,
    required this.branchId,
    required this.serviceCode,
    required this.name,
    required this.description,
    required this.category,
    this.unit = 'SERVICE',
    required this.unitPrice,
    this.taxClassification = TaxClassification.vat15,
    this.isActive = true,
  });

  ServiceModel copyWith({
    String? id,
    String? tenantId,
    String? branchId,
    String? serviceCode,
    String? name,
    String? description,
    String? category,
    String? unit,
    double? unitPrice,
    TaxClassification? taxClassification,
    bool? isActive,
  }) {
    return ServiceModel(
      id: id ?? this.id,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      serviceCode: serviceCode ?? this.serviceCode,
      name: name ?? this.name,
      description: description ?? this.description,
      category: category ?? this.category,
      unit: unit ?? this.unit,
      unitPrice: unitPrice ?? this.unitPrice,
      taxClassification: taxClassification ?? this.taxClassification,
      isActive: isActive ?? this.isActive,
    );
  }

  factory ServiceModel.fromJson(Map<String, dynamic> json) {
    return ServiceModel(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      branchId: json['branchId']?.toString() ?? '',
      serviceCode: json['serviceCode']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      category: json['categoryCode']?.toString() ?? json['category']?.toString() ?? '',
      unit: json['unit']?.toString() ?? 'SERVICE',
      unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
      taxClassification: TaxClassification.fromCode(json['taxClassification']?.toString()),
      isActive: json['isActive'] as bool? ?? (json['active'] as bool? ?? true),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'tenantId': tenantId,
      'branchId': branchId,
      'serviceCode': serviceCode,
      'name': name,
      'description': description,
      'categoryCode': category,
      'unit': unit,
      'unitPrice': unitPrice,
      'taxClassification': taxClassification.code,
      'isActive': isActive,
    };
  }
}

class CategoryModel {
  final String id;
  final String tenantId;
  final String code;
  final String name;
  final String type; // 'PRODUCT' or 'SERVICE' or 'ALL'
  final String? description;
  final String status;

  const CategoryModel({
    required this.id,
    required this.tenantId,
    required this.code,
    required this.name,
    required this.type,
    this.description,
    this.status = 'ACTIVE',
  });

  factory CategoryModel.fromJson(Map<String, dynamic> json) {
    return CategoryModel(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      code: json['code']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      type: json['categoryType']?.toString() ?? json['type']?.toString() ?? 'PRODUCT',
      description: json['description']?.toString(),
      status: json['status']?.toString() ?? 'ACTIVE',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'tenantId': tenantId,
      'code': code,
      'name': name,
      'categoryType': type,
      'description': description,
      'status': status,
    };
  }
}
