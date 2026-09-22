import '../../domain/customer/models/customer_model.dart';

abstract class CustomerRepository {
  Future<List<CustomerModel>> searchCustomers({
    required String tenantId,
    String? query,
    int page = 0,
    int size = 20,
  });

  Stream<List<CustomerModel>> watchCustomers({required String tenantId});

  Future<CustomerModel?> getCustomerById({
    required String tenantId,
    required String customerId,
  });

  Future<CustomerModel?> lookupByTin({
    required String tenantId,
    required String tin,
  });

  Future<CustomerModel> createCustomer({
    required String tenantId,
    required CustomerModel customer,
  });

  Future<CustomerModel> updateCustomer({
    required String tenantId,
    required CustomerModel customer,
  });
}
