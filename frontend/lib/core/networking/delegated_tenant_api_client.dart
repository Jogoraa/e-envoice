import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';
import '../errors/app_error.dart';
import '../storage/secure_storage_service.dart';
import 'gateway_config.dart';

/// Dedicated Delegated Tenant API Client.
/// Completely isolated from normal Tenant Client and Master Admin.
/// Operates strictly within the boundary of an authorized "Test as Tenant" session.
/// Never mutates, reads, or overwrites normal tenant credentials.
class DelegatedTenantApiClient {
  final Dio _dio;
  final SecureStorageService secureStorage;
  static const Uuid _uuid = Uuid();

  String? _targetTenantId;
  String? _targetBranchId;

  DelegatedTenantApiClient({
    String? baseUrl,
    required this.secureStorage,
    Dio? customDio,
  }) : _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: baseUrl ?? GatewayConfig.tenantApiBaseUrl,
              connectTimeout: GatewayConfig.connectTimeout,
              receiveTimeout: GatewayConfig.receiveTimeout,
              headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'X-Gateway-Surface': 'UT-DELEGATED-TESTING',
              },
            )) {
    _initInterceptors();
  }

  void setDelegatedScope({required String targetTenantId, String? targetBranchId}) {
    _targetTenantId = targetTenantId;
    _targetBranchId = targetBranchId;
  }

  void clearDelegatedScope() {
    _targetTenantId = null;
    _targetBranchId = null;
  }

  void _initInterceptors() {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        options.headers['X-Gateway-Surface'] = 'UT-DELEGATED-TESTING';
        options.headers['X-Correlation-ID'] = _uuid.v4();
        options.headers['X-Request-ID'] = _uuid.v4();
        options.headers['X-Delegated-Testing'] = 'true';

        if (_targetTenantId != null && _targetTenantId!.isNotEmpty) {
          options.headers['X-Tenant-ID'] = _targetTenantId;
        }
        if (_targetBranchId != null && _targetBranchId!.isNotEmpty) {
          options.headers['X-Branch-ID'] = _targetBranchId;
        }

        // Attach strictly Delegated Token (Never reads normal tenant token or master token)
        final delegatedToken = await secureStorage.getDelegatedTenantToken();
        if (delegatedToken != null && delegatedToken.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $delegatedToken';
        }

        handler.next(options);
      },
      onError: (err, handler) {
        handler.next(err);
      },
    ));
  }

  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.get<T>(
        path,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw AppError.fromDioException(e);
    }
  }

  Future<Response<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    String? idempotencyKey,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      final requestOptions = options ?? Options();
      if (idempotencyKey != null && idempotencyKey.isNotEmpty) {
        requestOptions.headers = {
          ...?requestOptions.headers,
          'Idempotency-Key': idempotencyKey,
        };
      }
      return await _dio.post<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: requestOptions,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw AppError.fromDioException(e);
    }
  }

  Future<Response<T>> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.put<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw AppError.fromDioException(e);
    }
  }

  Future<Response<T>> patch<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.patch<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw AppError.fromDioException(e);
    }
  }

  Future<Response<T>> delete<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.delete<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw AppError.fromDioException(e);
    }
  }
}
