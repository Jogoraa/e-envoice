import 'package:drift/drift.dart';
import '../../core/networking/api_client.dart';
import '../../domain/catalog/models/catalog_models.dart';
import '../local/database/app_database.dart';

abstract class CatalogRepository {
  Future<List<ProductModel>> getProducts({
    required String tenantId,
    required String branchId,
    String? query,
    String? category,
    bool? isActive,
  });

  Future<ProductModel?> getProductByBarcode({
    required String tenantId,
    required String branchId,
    required String barcode,
  });

  Future<ProductModel> registerProduct({
    required String tenantId,
    required String branchId,
    required ProductModel product,
  });

  Future<List<ServiceModel>> getServices({
    required String tenantId,
    required String branchId,
    String? query,
    String? category,
  });

  Future<ServiceModel> registerService({
    required String tenantId,
    required String branchId,
    required ServiceModel service,
  });

  Future<List<CategoryModel>> getCategories({required String tenantId});

  Future<CategoryModel> createCategory({
    required String tenantId,
    required String code,
    required String name,
    required String type,
    String? description,
  });
}

class CatalogRepositoryImpl implements CatalogRepository {
  final ApiClient apiClient;
  final AppDatabase db;

  // In-memory tenant/branch scoped mock cache if offline/development
  final List<ProductModel> _mockProducts = [];
  final List<ServiceModel> _mockServices = [];
  final List<CategoryModel> _mockCategories = [];

  CatalogRepositoryImpl({
    required this.apiClient,
    required this.db,
  }) {
    _initSeedData();
  }

  void _initSeedData() {
    const tenantId = '00000000-0000-0000-0000-000000000001';
    const branchHeadOffice = '00000000-0000-0000-0000-000000000010';
    const branchBole = '00000000-0000-0000-0000-000000000020';

    _mockCategories.addAll([
      const CategoryModel(id: 'cat-01', tenantId: tenantId, code: 'BEV', name: 'Beverages (መጠጦች)', type: 'PRODUCT'),
      const CategoryModel(id: 'cat-02', tenantId: tenantId, code: 'GRAIN', name: 'Grains & Cereals (እህሎች)', type: 'PRODUCT'),
      const CategoryModel(id: 'cat-03', tenantId: tenantId, code: 'IT_SERV', name: 'IT & Software Services', type: 'SERVICE'),
      const CategoryModel(id: 'cat-04', tenantId: tenantId, code: 'CONSULT', name: 'Consulting & Advisory', type: 'SERVICE'),
    ]);

    _mockProducts.addAll([
      const ProductModel(
        id: 'prod-001',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-TEFF-01',
        sku: 'SKU-TEFF-WHITE',
        barcode: '6001002003001',
        description: 'Magna White Teff 50kg (የማኛ ነጭ ጤፍ)',
        category: 'GRAIN',
        unit: 'BAG',
        unitPrice: 5800.00,
        taxClassification: TaxClassification.exempt,
        isActive: true,
        trackStock: true,
        stockQuantity: 120.0,
        minStockLevel: 20.0,
      ),
      const ProductModel(
        id: 'prod-002',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-OIL-01',
        sku: 'SKU-SUN-OIL-5L',
        barcode: '6001002003002',
        description: 'Pure Sunflower Cooking Oil 5L (የሱፍ ዘይት)',
        category: 'BEV',
        unit: 'BOTTLE',
        unitPrice: 1450.00,
        taxClassification: TaxClassification.vat15,
        isActive: true,
        trackStock: true,
        stockQuantity: 45.0,
        minStockLevel: 10.0,
      ),
      const ProductModel(
        id: 'prod-003',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-COFFEE-01',
        sku: 'SKU-YIRGA-COFFEE',
        barcode: '6001002003003',
        description: 'Yirgacheffe Roasted Coffee Beans 1kg (ይርጋጨፌ ቡና)',
        category: 'BEV',
        unit: 'KG',
        unitPrice: 850.00,
        taxClassification: TaxClassification.vat15,
        isActive: true,
        trackStock: true,
        stockQuantity: 4.0, // Low stock demo
        minStockLevel: 10.0,
      ),
      const ProductModel(
        id: 'prod-004',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        itemCode: 'ITM-SUGAR-DIS',
        sku: 'SKU-SUGAR-DISCONTINUED',
        barcode: '6001002003004',
        description: 'Imported White Cane Sugar (Discontinued)',
        category: 'GRAIN',
        unit: 'KG',
        unitPrice: 120.00,
        taxClassification: TaxClassification.vat15,
        isActive: false, // Inactive product demo
        trackStock: true,
        stockQuantity: 0.0,
        minStockLevel: 5.0,
      ),
      // Branch Bole Exclusive Product
      const ProductModel(
        id: 'prod-005',
        tenantId: tenantId,
        branchId: branchBole,
        itemCode: 'ITM-BOLE-HONEY',
        sku: 'SKU-TIGRAY-WHITE-HONEY',
        barcode: '6001002003005',
        description: 'Pure White Honey 1kg (የማር ማሰሮ)',
        category: 'BEV',
        unit: 'KG',
        unitPrice: 1200.00,
        taxClassification: TaxClassification.vat15,
        isActive: true,
        trackStock: true,
        stockQuantity: 28.0,
        minStockLevel: 5.0,
      ),
    ]);

    _mockServices.addAll([
      const ServiceModel(
        id: 'serv-001',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        serviceCode: 'SRV-POS-SETUP',
        name: 'POS System Deployment & Integration Service',
        description: 'Onsite hardware deployment and fiscal terminal setup',
        category: 'IT_SERV',
        unit: 'SERVICE',
        unitPrice: 4500.00,
        taxClassification: TaxClassification.vat15,
        isActive: true,
      ),
      const ServiceModel(
        id: 'serv-002',
        tenantId: tenantId,
        branchId: branchHeadOffice,
        serviceCode: 'SRV-TAX-CONSULT',
        name: 'Quarterly Tax Reconciliation Consulting',
        description: 'Certified Ethiopian VAT declaration & audit review',
        category: 'CONSULT',
        unit: 'HOUR',
        unitPrice: 1800.00,
        taxClassification: TaxClassification.vat15,
        isActive: true,
      ),
    ]);
  }

  @override
  Future<List<ProductModel>> getProducts({
    required String tenantId,
    required String branchId,
    String? query,
    String? category,
    bool? isActive,
  }) async {
    try {
      final params = <String, dynamic>{'size': 100};
      if (query != null && query.isNotEmpty) params['query'] = query;
      if (category != null && category.isNotEmpty) params['categoryCode'] = category;
      if (isActive != null) params['isActive'] = isActive;

      final response = await apiClient.get('/api/v1/catalog/products', queryParameters: params);
      if (response.statusCode == 200 && response.data != null) {
        final dynamic data = response.data;
        List<dynamic> items = [];
        if (data is Map && data['content'] is List) {
          items = data['content'] as List;
        } else if (data is List) {
          items = data;
        }
        if (items.isNotEmpty) {
          final list = items.map((e) => ProductModel.fromJson(e as Map<String, dynamic>)).toList();
          return list;
        }
      }
    } catch (_) {
      // Fallback to local memory cache
    }

    return _mockProducts.where((p) {
      if (p.tenantId != tenantId && p.tenantId != '00000000-0000-0000-0000-000000000001') return false;
      if (isActive != null && p.isActive != isActive) return false;
      if (category != null && category.isNotEmpty && p.category != category) return false;
      if (query != null && query.isNotEmpty) {
        final q = query.toLowerCase();
        final matchCode = p.itemCode.toLowerCase().contains(q);
        final matchDesc = p.description.toLowerCase().contains(q);
        final matchBarcode = p.barcode?.toLowerCase().contains(q) ?? false;
        final matchSku = p.sku.toLowerCase().contains(q);
        return matchCode || matchDesc || matchBarcode || matchSku;
      }
      return true;
    }).toList();
  }

  @override
  Future<ProductModel?> getProductByBarcode({
    required String tenantId,
    required String branchId,
    required String barcode,
  }) async {
    try {
      final response = await apiClient.get('/api/v1/catalog/products/barcode/$barcode');
      if (response.statusCode == 200 && response.data != null) {
        return ProductModel.fromJson(response.data as Map<String, dynamic>);
      }
    } catch (_) {
      // Fallback
    }

    try {
      return _mockProducts.firstWhere(
        (p) => (p.tenantId == tenantId || p.tenantId == '00000000-0000-0000-0000-000000000001') &&
               p.barcode == barcode &&
               p.isActive,
      );
    } catch (_) {
      return null;
    }
  }

  @override
  Future<ProductModel> registerProduct({
    required String tenantId,
    required String branchId,
    required ProductModel product,
  }) async {
    try {
      final response = await apiClient.post('/api/v1/catalog/products', data: {
        'itemCode': product.itemCode,
        'sku': product.sku,
        'barcode': product.barcode,
        'description': product.description,
        'categoryCode': product.category,
        'unit': product.unit,
        'unitPrice': product.unitPrice,
        'taxClassification': product.taxClassification.code,
        'trackStock': product.trackStock,
        'stockQuantity': product.stockQuantity,
        'minStockLevel': product.minStockLevel,
      });
      if (response.statusCode == 200 || response.statusCode == 201) {
        final registered = ProductModel.fromJson(response.data as Map<String, dynamic>);
        _mockProducts.add(registered);
        await db.into(db.localProducts).insertOnConflictUpdate(
              LocalProductsCompanion.insert(
                id: registered.id,
                tenantId: registered.tenantId,
                branchId: registered.branchId,
                itemCode: registered.itemCode,
                description: registered.description,
                unitPrice: registered.unitPrice,
                taxCode: Value(registered.taxClassification.code),
                unit: Value(registered.unit),
              ),
            );
        return registered;
      }
    } catch (_) {
      // Fallback
    }

    final registered = product.copyWith(
      id: 'prod-${DateTime.now().millisecondsSinceEpoch}',
      tenantId: tenantId,
      branchId: branchId,
    );
    _mockProducts.add(registered);

    // Cache locally in Drift
    await db.into(db.localProducts).insertOnConflictUpdate(
          LocalProductsCompanion.insert(
            id: registered.id,
            tenantId: registered.tenantId,
            branchId: registered.branchId,
            itemCode: registered.itemCode,
            description: registered.description,
            unitPrice: registered.unitPrice,
            taxCode: Value(registered.taxClassification.code),
            unit: Value(registered.unit),
          ),
        );

    return registered;
  }

  @override
  Future<List<ServiceModel>> getServices({
    required String tenantId,
    required String branchId,
    String? query,
    String? category,
  }) async {
    try {
      final params = <String, dynamic>{'size': 100};
      if (query != null && query.isNotEmpty) params['query'] = query;
      if (category != null && category.isNotEmpty) params['categoryCode'] = category;

      final response = await apiClient.get('/api/v1/catalog/services', queryParameters: params);
      if (response.statusCode == 200 && response.data != null) {
        final dynamic data = response.data;
        List<dynamic> items = [];
        if (data is Map && data['content'] is List) {
          items = data['content'] as List;
        } else if (data is List) {
          items = data;
        }
        if (items.isNotEmpty) {
          return items.map((e) => ServiceModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {
      // Fallback
    }

    return _mockServices.where((s) {
      if (s.tenantId != tenantId && s.tenantId != '00000000-0000-0000-0000-000000000001') return false;
      if (category != null && category.isNotEmpty && s.category != category) return false;
      if (query != null && query.isNotEmpty) {
        final q = query.toLowerCase();
        return s.serviceCode.toLowerCase().contains(q) || s.name.toLowerCase().contains(q);
      }
      return true;
    }).toList();
  }

  @override
  Future<ServiceModel> registerService({
    required String tenantId,
    required String branchId,
    required ServiceModel service,
  }) async {
    try {
      final response = await apiClient.post('/api/v1/catalog/services', data: {
        'serviceCode': service.serviceCode,
        'name': service.name,
        'description': service.description,
        'categoryCode': service.category,
        'unit': service.unit,
        'unitPrice': service.unitPrice,
        'taxClassification': service.taxClassification.code,
      });
      if (response.statusCode == 200 || response.statusCode == 201) {
        final registered = ServiceModel.fromJson(response.data as Map<String, dynamic>);
        _mockServices.add(registered);
        return registered;
      }
    } catch (_) {
      // Fallback
    }

    final registered = service.copyWith(
      id: 'serv-${DateTime.now().millisecondsSinceEpoch}',
      tenantId: tenantId,
      branchId: branchId,
    );
    _mockServices.add(registered);
    return registered;
  }

  @override
  Future<List<CategoryModel>> getCategories({required String tenantId}) async {
    try {
      final response = await apiClient.get('/api/v1/categories', queryParameters: {'size': 100});
      if (response.statusCode == 200 && response.data != null) {
        final dynamic data = response.data;
        List<dynamic> items = [];
        if (data is Map && data['content'] is List) {
          items = data['content'] as List;
        } else if (data is List) {
          items = data;
        }
        if (items.isNotEmpty) {
          final list = items.map((e) => CategoryModel.fromJson(e as Map<String, dynamic>)).toList();
          for (final c in list) {
            if (!_mockCategories.any((m) => m.code == c.code && m.tenantId == c.tenantId)) {
              _mockCategories.add(c);
            }
          }
          return list;
        }
      }
    } catch (_) {
      // Fallback
    }

    final cached = _mockCategories.where((c) => c.tenantId == tenantId || c.tenantId == '00000000-0000-0000-0000-000000000001').toList();
    if (cached.isNotEmpty) {
      return cached;
    }

    // Default baseline so dropdowns never assert on missing initial value
    return [
      CategoryModel(id: 'cat-01', tenantId: tenantId, code: 'BEV', name: 'Beverages (መጠጦች)', type: 'PRODUCT'),
      CategoryModel(id: 'cat-02', tenantId: tenantId, code: 'GRAIN', name: 'Grains & Cereals (እህሎች)', type: 'PRODUCT'),
      CategoryModel(id: 'cat-03', tenantId: tenantId, code: 'IT_SERV', name: 'IT & Software Services', type: 'SERVICE'),
      CategoryModel(id: 'cat-04', tenantId: tenantId, code: 'CONSULT', name: 'Consulting & Advisory', type: 'SERVICE'),
    ];
  }

  @override
  Future<CategoryModel> createCategory({
    required String tenantId,
    required String code,
    required String name,
    required String type,
    String? description,
  }) async {
    final payload = {
      'code': code.trim().toUpperCase(),
      'name': name.trim(),
      'categoryType': type,
      'description': description,
    };
    try {
      final response = await apiClient.post('/api/v1/categories', data: payload);
      if (response.statusCode == 200 || response.statusCode == 201) {
        final created = CategoryModel.fromJson(response.data as Map<String, dynamic>);
        _mockCategories.add(created);
        return created;
      }
    } catch (_) {
      // Fallback
    }
    final fallback = CategoryModel(
      id: 'cat-${DateTime.now().millisecondsSinceEpoch}',
      tenantId: tenantId,
      code: code.trim().toUpperCase(),
      name: name.trim(),
      type: type,
      description: description,
    );
    _mockCategories.add(fallback);
    return fallback;
  }
}
