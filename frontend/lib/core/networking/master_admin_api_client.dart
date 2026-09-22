import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';
import '../errors/app_error.dart';
import '../storage/secure_storage_service.dart';
import 'gateway_config.dart';

/// Dedicated Master Admin API Client.
/// Completely isolated from Tenant Client. Uses separate Master Admin Gateway and separate tokens.
class MasterAdminApiClient {
  final Dio _dio;
  final SecureStorageService secureStorage;
  static const Uuid _uuid = Uuid();

  MasterAdminApiClient({
    String? baseUrl,
    required this.secureStorage,
    Dio? customDio,
  }) : _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: baseUrl ?? GatewayConfig.masterAdminApiBaseUrl,
              connectTimeout: GatewayConfig.connectTimeout,
              receiveTimeout: GatewayConfig.receiveTimeout,
              headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'X-Gateway-Surface': 'UT-MASTER-ADMIN',
              },
            )) {
    _initInterceptors();
  }

  void _initInterceptors() {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        options.headers['X-Gateway-Surface'] = 'UT-MASTER-ADMIN';
        options.headers['X-Correlation-ID'] = _uuid.v4();
        options.headers['X-Request-ID'] = _uuid.v4();

        // Dual token fallback: check Master Admin token first, then SaaS token
        var token = await secureStorage.getMasterAdminToken();
        if (token == null || token.isEmpty) {
          token = await secureStorage.getSaasAdminToken();
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
    if (_dio.options.baseUrl.endsWith('/api/v1/master')) {
      if (path.startsWith('/api/v1/master')) {
        final stripped = path.substring('/api/v1/master'.length);
        return stripped.isEmpty ? '/' : stripped;
      }
      if (path.startsWith('/api/v1/admin')) {
        final stripped = path.substring('/api/v1/admin'.length);
        return stripped.isEmpty ? '/' : stripped;
      }
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
