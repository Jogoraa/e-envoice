import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/core/utils/tin_validator.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';
import 'package:ut_einvoice_client/data/repositories/customer_repository_impl.dart';
import 'package:ut_einvoice_client/domain/customer/models/customer_model.dart';
import 'package:ut_einvoice_client/domain/invoice/invoice_type_registry.dart';

class _FastMockAdapter implements HttpClientAdapter {
  final Map<String, List<Map<String, dynamic>>> tenantStore = {};

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final tenantId = options.headers['X-Tenant-ID']?.toString() ?? 'default-tenant';

    if (options.method == 'POST' && options.path.contains('/api/v1/customers')) {
      final data = options.data is Map ? Map<String, dynamic>.from(options.data as Map) : <String, dynamic>{};
      final savedItem = {
        'id': 'remote-${DateTime.now().millisecondsSinceEpoch}',
        'tenantId': tenantId,
        'createdAt': DateTime.now().toIso8601String(),
        ...data,
      };
      tenantStore.putIfAbsent(tenantId, () => []).add(savedItem);
      return ResponseBody.fromString(
        jsonEncode(savedItem),
        201,
        headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
      );
    }

    if (options.method == 'GET' && options.path.contains('/api/v1/customers/lookup')) {
      final tin = options.queryParameters['tin']?.toString();
      final items = tenantStore[tenantId] ?? [];
      final match = items.where((i) => i['tin'] == tin).toList();
      if (match.isNotEmpty) {
        return ResponseBody.fromString(
          jsonEncode(match.first),
          200,
          headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
        );
      }
      return ResponseBody.fromString(
        jsonEncode({'error': 'Not found'}),
        404,
        headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
      );
    }

    if (options.method == 'GET' && options.path.contains('/api/v1/customers')) {
      final items = tenantStore[tenantId] ?? [];
      final query = options.queryParameters['query']?.toString().toLowerCase();
      final filtered = items.where((i) {
        if (query == null || query.isEmpty) return true;
        final name = (i['legalName'] ?? '').toString().toLowerCase();
        final tin = (i['tin'] ?? '').toString().toLowerCase();
        return name.contains(query) || tin.contains(query);
      }).toList();

      return ResponseBody.fromString(
        jsonEncode({
          'content': filtered,
          'totalElements': filtered.length,
          'totalPages': 1,
          'page': 0,
          'size': 20,
        }),
        200,
        headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
      );
    }

    return ResponseBody.fromString(
      jsonEncode({'message': 'OK'}),
      200,
      headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
    );
  }
}

void main() {
  group('Ethiopian 10-Digit TIN Validation Suite', () {
    test('Valid 10-digit numeric TIN with leading zeros is strictly valid', () {
      expect(TinValidator.isValid('0041746204'), isTrue);
      expect(TinValidator.validate('0041746204'), isNull);

      expect(TinValidator.isValid('0012345678'), isTrue);
      expect(TinValidator.validate('0012345678'), isNull);
    });

    test('Preserves leading zeros during normalization', () {
      expect(TinValidator.normalize('  0041746204  '), equals('0041746204'));
      expect(TinValidator.normalize('0001112223'), equals('0001112223'));
      expect(TinValidator.normalize('   '), isNull);
      expect(TinValidator.normalize(null), isNull);
    });

    test('Rejects invalid TINs with letters, symbols, or incorrect length', () {
      expect(TinValidator.isValid('004174620A'), isFalse);
      expect(TinValidator.validate('004174620A'), contains('numbers only'));

      expect(TinValidator.isValid('12345'), isFalse);
      expect(TinValidator.validate('12345'), contains('exactly 10 digits'));

      expect(TinValidator.isValid('12345678901'), isFalse);
      expect(TinValidator.validate('12345678901'), contains('exactly 10 digits'));

      expect(TinValidator.isValid('00-4174-6204'), isFalse);
    });

    test('Handles optional vs mandatory requirements correctly', () {
      expect(TinValidator.validate(null, isRequired: false), isNull);
      expect(TinValidator.validate('', isRequired: false), isNull);
      expect(TinValidator.validate(null, isRequired: true), contains('mandatory'));
      expect(TinValidator.validate('', isRequired: true), contains('mandatory'));
    });
  });

  group('Invoice Type Registry & Compliance Suite', () {
    test('B2B Commercial Tax Invoice strictly enforces Buyer TIN & Legal Name', () {
      final b2b = InvoiceTypeRegistry.getDefinition(InvoiceType.b2b);
      expect(b2b.type, equals(InvoiceType.b2b));
      expect(b2b.isBuyerTinRequired, isTrue);
      expect(b2b.isBuyerNameRequired, isTrue);
      expect(b2b.isBuyerAddressRequired, isTrue);
      expect(b2b.defaultTaxCode, equals('VAT15'));
    });

    test('B2C Standard Retail Invoice allows optional buyer details', () {
      final b2c = InvoiceTypeRegistry.getDefinition(InvoiceType.b2c);
      expect(b2c.type, equals(InvoiceType.b2c));
      expect(b2c.isBuyerTinRequired, isFalse);
      expect(b2c.isBuyerNameRequired, isFalse);
      expect(b2c.defaultTaxCode, equals('VAT15'));
    });

    test('Export Invoice specifies Zero-Rated tax and multi-currency support', () {
      final exp = InvoiceTypeRegistry.getDefinition(InvoiceType.export);
      expect(exp.type, equals(InvoiceType.export));
      expect(exp.isExport, isTrue);
      expect(exp.defaultTaxCode, equals('ZERO_RATED'));
      expect(exp.supportedCurrencies, containsAll(['USD', 'EUR', 'GBP', 'ETB']));
    });
  });

  group('Customer Master Data & Tenant Isolation Suite', () {
    late AppDatabase db;
    late CustomerRepositoryImpl repository;
    late _FastMockAdapter mockAdapter;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      FlutterSecureStorage.setMockInitialValues({});
      final secureStorage = SecureStorageService();
      mockAdapter = _FastMockAdapter();
      final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080'));
      dio.httpClientAdapter = mockAdapter;

      final apiClient = ApiClient(
        baseUrl: 'http://localhost:8080',
        secureStorage: secureStorage,
        customDio: dio,
      );
      repository = CustomerRepositoryImpl(apiClient: apiClient, db: db);
    });

    tearDown(() async {
      await db.close();
    });

    test('Tenant Isolation: Customer records are strictly isolated per tenant', () async {
      const tenantA = 'tenant-uuid-1111';
      const tenantB = 'tenant-uuid-2222';

      // Insert customer into Tenant A
      await repository.createCustomer(
        tenantId: tenantA,
        customer: CustomerModel(
          id: 'cust-a1',
          tenantId: tenantA,
          tin: '0041746204',
          legalName: 'Abyssinia Trading Enterprise PLC',
          phone: '+251911223344',
          country: 'ET',
          city: 'Addis Ababa',
          createdAt: DateTime.now(),
        ),
      );

      // Search within Tenant A
      final tenantACustomers = await repository.searchCustomers(tenantId: tenantA);
      expect(tenantACustomers.length, equals(1));
      expect(tenantACustomers.first.legalName, equals('Abyssinia Trading Enterprise PLC'));

      // Search within Tenant B must be EMPTY (no leakage)
      final tenantBCustomers = await repository.searchCustomers(tenantId: tenantB);
      expect(tenantBCustomers.isEmpty, isTrue);

      // Lookup by TIN scoped to Tenant B must return null
      final lookupTenantB = await repository.lookupByTin(tenantId: tenantB, tin: '0041746204');
      expect(lookupTenantB, isNull);

      // Lookup by TIN scoped to Tenant A returns correct record
      final lookupTenantA = await repository.lookupByTin(tenantId: tenantA, tin: '0041746204');
      expect(lookupTenantA, isNotNull);
      expect(lookupTenantA!.tin, equals('0041746204'));
    });
  });
}
