import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../../core/networking/api_client.dart';
import '../../domain/customer/models/customer_model.dart';
import '../local/database/app_database.dart';
import 'customer_repository.dart';

class CustomerRepositoryImpl implements CustomerRepository {
  final ApiClient apiClient;
  final AppDatabase db;
  static const Uuid _uuid = Uuid();

  CustomerRepositoryImpl({
    required this.apiClient,
    required this.db,
  });

  @override
  Future<List<CustomerModel>> searchCustomers({
    required String tenantId,
    String? query,
    int page = 0,
    int size = 20,
  }) async {
    apiClient.setScope(tenantId: tenantId, branchId: null);

    // 1. Fetch from local SQLite DB first for immediate offline/instant rendering
    final localRows = await db.searchScopedCustomers(
      tenantId: tenantId,
      query: query,
      limit: size,
    );
    final localModels = localRows.map<CustomerModel>(_fromLocalCustomer).toList();

    // 2. Fetch from backend REST API if online
    try {
      final response = await apiClient.get<Map<String, dynamic>>(
        '/api/v1/customers',
        queryParameters: {
          if (query != null && query.trim().isNotEmpty) 'query': query.trim(),
          'page': page,
          'size': size,
        },
      );

      if (response.data != null && response.data!['content'] is List) {
        final content = response.data!['content'] as List;
        final remoteModels = <CustomerModel>[];

        for (final item in content) {
          if (item is Map<String, dynamic>) {
            final model = CustomerModel.fromJson(item);
            remoteModels.add(model);
            // Upsert into local database
            await db.upsertLocalCustomer(_toLocalCompanion(model, syncStatus: 'synced'));
          }
        }
        return remoteModels;
      }
    } catch (e) {
      debugPrint('[CustomerRepository] Remote customer fetch failed: $e. Returning local DB fallback.');
    }

    return localModels;
  }

  @override
  Stream<List<CustomerModel>> watchCustomers({required String tenantId}) {
    return db.watchScopedCustomers(tenantId: tenantId).map(
          (rows) => rows.map<CustomerModel>(_fromLocalCustomer).toList(),
        );
  }

  @override
  Future<CustomerModel?> getCustomerById({
    required String tenantId,
    required String customerId,
  }) async {
    apiClient.setScope(tenantId: tenantId, branchId: null);
    final localRow = await db.getCustomerById(customerId, tenantId);
    if (localRow != null) {
      return _fromLocalCustomer(localRow);
    }

    try {
      final response = await apiClient.get<Map<String, dynamic>>(
        '/api/v1/customers/$customerId',
      );
      if (response.data != null) {
        final model = CustomerModel.fromJson(response.data!);
        await db.upsertLocalCustomer(_toLocalCompanion(model, syncStatus: 'synced'));
        return model;
      }
    } catch (e) {
      debugPrint('[CustomerRepository] Remote customer lookup failed: $e');
    }
    return null;
  }

  @override
  Future<CustomerModel?> lookupByTin({
    required String tenantId,
    required String tin,
  }) async {
    apiClient.setScope(tenantId: tenantId, branchId: null);
    final normalizedTin = tin.trim();
    final localRow = await db.getCustomerByTin(normalizedTin, tenantId);
    if (localRow != null) {
      return _fromLocalCustomer(localRow);
    }

    try {
      final response = await apiClient.get<Map<String, dynamic>>(
        '/api/v1/customers/lookup',
        queryParameters: {'tin': normalizedTin},
      );
      if (response.data != null) {
        final model = CustomerModel.fromJson(response.data!);
        await db.upsertLocalCustomer(_toLocalCompanion(model, syncStatus: 'synced'));
        return model;
      }
    } catch (e) {
      debugPrint('[CustomerRepository] Remote TIN lookup failed: $e');
    }
    return null;
  }

  @override
  Future<CustomerModel> createCustomer({
    required String tenantId,
    required CustomerModel customer,
  }) async {
    apiClient.setScope(tenantId: tenantId, branchId: null);
    final id = customer.id.isEmpty ? _uuid.v4() : customer.id;
    final modelToSave = customer.copyWith(
      id: id,
      tenantId: tenantId,
      createdAt: customer.createdAt,
      syncStatus: 'pending',
    );

    // Save locally immediately
    await db.upsertLocalCustomer(_toLocalCompanion(modelToSave, syncStatus: 'pending'));

    try {
      final response = await apiClient.post<Map<String, dynamic>>(
        '/api/v1/customers',
        idempotencyKey: _uuid.v4(),
        data: {
          'legalName': modelToSave.legalName,
          if (modelToSave.tradeName != null) 'tradeName': modelToSave.tradeName,
          if (modelToSave.tin != null && modelToSave.tin!.isNotEmpty) 'tin': modelToSave.tin,
          if (modelToSave.vatNumber != null) 'vatNumber': modelToSave.vatNumber,
          if (modelToSave.buyerIdType.isNotEmpty) 'buyerIdType': modelToSave.buyerIdType,
          if (modelToSave.buyerIdNumber != null) 'buyerIdNumber': modelToSave.buyerIdNumber,
          if (modelToSave.phone != null) 'phone': modelToSave.phone,
          if (modelToSave.email != null) 'email': modelToSave.email,
          'country': modelToSave.country,
          if (modelToSave.region != null) 'region': modelToSave.region,
          if (modelToSave.city != null) 'city': modelToSave.city,
          if (modelToSave.zone != null) 'zone': modelToSave.zone,
          if (modelToSave.woreda != null) 'woreda': modelToSave.woreda,
          if (modelToSave.kebele != null) 'kebele': modelToSave.kebele,
          if (modelToSave.houseNumber != null) 'houseNumber': modelToSave.houseNumber,
          'isVatRegistered': modelToSave.isVatRegistered,
        },
      );

      if (response.data != null) {
        final syncedModel = CustomerModel.fromJson(response.data!);
        if (syncedModel.id != modelToSave.id) {
          await (db.delete(db.localCustomers)..where((tbl) => tbl.id.equals(modelToSave.id))).go();
        }
        await db.upsertLocalCustomer(_toLocalCompanion(syncedModel, syncStatus: 'synced'));
        return syncedModel;
      }
    } catch (e) {
      debugPrint('[CustomerRepository] Remote customer create failed: $e. Retaining local draft.');
    }

    return modelToSave;
  }

  @override
  Future<CustomerModel> updateCustomer({
    required String tenantId,
    required CustomerModel customer,
  }) async {
    apiClient.setScope(tenantId: tenantId, branchId: null);
    final modelToSave = customer.copyWith(
      tenantId: tenantId,
      updatedAt: DateTime.now(),
      syncStatus: 'pending',
    );

    await db.upsertLocalCustomer(_toLocalCompanion(modelToSave, syncStatus: 'pending'));

    try {
      final response = await apiClient.put<Map<String, dynamic>>(
        '/api/v1/customers/${customer.id}',
        idempotencyKey: _uuid.v4(),
        data: {
          'legalName': modelToSave.legalName,
          if (modelToSave.tradeName != null) 'tradeName': modelToSave.tradeName,
          if (modelToSave.tin != null && modelToSave.tin!.isNotEmpty) 'tin': modelToSave.tin,
          if (modelToSave.vatNumber != null) 'vatNumber': modelToSave.vatNumber,
          if (modelToSave.buyerIdType.isNotEmpty) 'buyerIdType': modelToSave.buyerIdType,
          if (modelToSave.buyerIdNumber != null) 'buyerIdNumber': modelToSave.buyerIdNumber,
          if (modelToSave.phone != null) 'phone': modelToSave.phone,
          if (modelToSave.email != null) 'email': modelToSave.email,
          'country': modelToSave.country,
          if (modelToSave.region != null) 'region': modelToSave.region,
          if (modelToSave.city != null) 'city': modelToSave.city,
          if (modelToSave.zone != null) 'zone': modelToSave.zone,
          if (modelToSave.woreda != null) 'woreda': modelToSave.woreda,
          if (modelToSave.kebele != null) 'kebele': modelToSave.kebele,
          if (modelToSave.houseNumber != null) 'houseNumber': modelToSave.houseNumber,
          'isVatRegistered': modelToSave.isVatRegistered,
        },
      );

      if (response.data != null) {
        final syncedModel = CustomerModel.fromJson(response.data!);
        await db.upsertLocalCustomer(_toLocalCompanion(syncedModel, syncStatus: 'synced'));
        return syncedModel;
      }
    } catch (e) {
      debugPrint('[CustomerRepository] Remote customer update failed: $e. Retaining local update.');
    }

    return modelToSave;
  }

  CustomerModel _fromLocalCustomer(LocalCustomerRecord row) {
    return CustomerModel(
      id: row.id,
      tenantId: row.tenantId,
      branchId: row.branchId,
      tin: row.tin,
      vatNumber: row.vatNumber,
      legalName: row.legalName,
      tradeName: row.tradeName,
      phone: row.phone,
      email: row.email,
      country: row.country,
      region: row.region,
      city: row.city,
      zone: row.zone,
      woreda: row.woreda,
      kebele: row.kebele,
      houseNumber: row.houseNumber,
      buyerIdType: row.buyerIdType,
      buyerIdNumber: row.buyerIdNumber,
      isVatRegistered: row.isVatRegistered,
      status: row.status,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
      syncStatus: row.syncStatus,
    );
  }

  LocalCustomersCompanion _toLocalCompanion(CustomerModel model, {String syncStatus = 'synced'}) {
    return LocalCustomersCompanion(
      id: Value(model.id),
      tenantId: Value(model.tenantId),
      branchId: Value(model.branchId),
      tin: Value(model.tin),
      vatNumber: Value(model.vatNumber),
      legalName: Value(model.legalName),
      tradeName: Value(model.tradeName),
      phone: Value(model.phone),
      email: Value(model.email),
      country: Value(model.country),
      region: Value(model.region),
      city: Value(model.city),
      zone: Value(model.zone),
      woreda: Value(model.woreda),
      kebele: Value(model.kebele),
      houseNumber: Value(model.houseNumber),
      buyerIdType: Value(model.buyerIdType),
      buyerIdNumber: Value(model.buyerIdNumber),
      isVatRegistered: Value(model.isVatRegistered),
      status: Value(model.status),
      createdAt: Value(model.createdAt),
      updatedAt: Value(model.updatedAt),
      syncStatus: Value(syncStatus),
    );
  }
}
