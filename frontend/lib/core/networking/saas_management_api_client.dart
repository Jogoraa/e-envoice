import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';
import '../errors/app_error.dart';
import '../storage/secure_storage_service.dart';
import 'gateway_config.dart';

/// Dedicated SaaS Management API Client.
/// Completely isolated from both Tenant Client and Master Admin.
/// Governs commercial tenant lifecycle, onboarding, plans, and support.
class SaasManagementApiClient {
  final Dio _dio;
  final SecureStorageService secureStorage;
  static const Uuid _uuid = Uuid();

  SaasManagementApiClient({
    String? baseUrl,
    required this.secureStorage,
    Dio? customDio,
  }) : _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: baseUrl ?? GatewayConfig.saasManagementApiBaseUrl,
              connectTimeout: GatewayConfig.connectTimeout,
              receiveTimeout: GatewayConfig.receiveTimeout,
              headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'X-Gateway-Surface': 'UT-SAAS-MANAGEMENT',
              },
            )) {
    _initInterceptors();
  }

  void _initInterceptors() {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        options.headers['X-Gateway-Surface'] = 'UT-SAAS-MANAGEMENT';
        options.headers['X-Correlation-ID'] = _uuid.v4();
        options.headers['X-Request-ID'] = _uuid.v4();

        // Dual token fallback: check SaaS token first, then Master Admin token
        var token = await secureStorage.getSaasAdminToken();
        if (token == null || token.isEmpty) {
          token = await secureStorage.getMasterAdminToken();
        }

        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (err, handler) {
        handler.next(err);
      },
    ));
  }

  String _normalizePath(String path) {
    if (_dio.options.baseUrl.endsWith('/api/v1/saas') && path.startsWith('/api/v1/saas')) {
      final stripped = path.substring('/api/v1/saas'.length);
      return stripped.isEmpty ? '/' : stripped;
    }
    return path;
  }

  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.get<T>(
        _normalizePath(path),
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
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.post<T>(
        _normalizePath(path),
        data: data,
        queryParameters: queryParameters,
        options: options,
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
        _normalizePath(path),
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
        _normalizePath(path),
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
