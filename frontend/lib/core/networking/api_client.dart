import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../errors/app_error.dart';
import '../errors/error_category.dart';
import '../storage/secure_storage_service.dart';
import 'http_adapter_factory_io.dart';
import 'network_profile.dart';
import 'network_resilience_manager.dart';
import 'poor_network_simulator.dart';
import 'token_refresh_interceptor.dart';

/// Centralized, hardened enterprise API client.
/// Strictly enforces correlation ID, tenant scoping, idempotency, single-flight refresh,
/// connection pooling, and resilient retry with exponential backoff and jitter.
class ApiClient {
  final Dio _dio;
  final SecureStorageService secureStorage;
  final NetworkResilienceManager resilienceManager;
  static const Uuid _uuid = Uuid();

  SecureStorageService get _secureStorage => secureStorage;

  String? _activeTenantId;
  String? _activeBranchId;

  ApiClient({
    required String baseUrl,
    required this.secureStorage,
    NetworkResilienceManager? resilienceManager,
    Dio? customDio,
  })  : resilienceManager = resilienceManager ?? NetworkResilienceManager(),
        _dio = customDio ??
            Dio(BaseOptions(
              baseUrl: baseUrl,
              connectTimeout: NetworkProfiles.standard.connectTimeout,
              receiveTimeout: NetworkProfiles.standard.receiveTimeout,
              sendTimeout: NetworkProfiles.standard.sendTimeout,
              headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
              },
            )) {
    if (customDio == null && !kIsWeb) {
      _dio.httpClientAdapter = buildAdapter(NetworkProfiles.standard);
    }
    _initInterceptors();
  }

  Dio get rawDio => _dio;
  String? get activeTenantId => _activeTenantId;
  String? get activeBranchId => _activeBranchId;

  void setScope({required String? tenantId, required String? branchId}) {
    _activeTenantId = tenantId;
    _activeBranchId = branchId;
  }

  void _initInterceptors() {
    // 0. Poor Network Simulation Interceptor (test & debug simulation)
    _dio.interceptors.add(PoorNetworkSimulator());

    // 1. Single-Flight Token Refresh Interceptor
    _dio.interceptors.add(TokenRefreshInterceptor(
      dio: _dio,
      secureStorage: _secureStorage,
    ));

    // 2. Correlation & Security Header Interceptor
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        // Enforce Correlation ID on every request if not already assigned
        final existingCorrelationId = options.headers['X-Correlation-ID'] ??
            options.extra['correlationId'];
        final correlationId = existingCorrelationId?.toString() ?? _uuid.v4();
        options.headers['X-Correlation-ID'] = correlationId;
        options.extra['correlationId'] = correlationId;

        if (!options.headers.containsKey('X-Request-ID')) {
          options.headers['X-Request-ID'] = _uuid.v4();
        }

        // Enforce Tenant ID header if scoped
        String? activeTenant = _activeTenantId;
        if (activeTenant == null || activeTenant.isEmpty) {
          activeTenant = await _secureStorage.getActiveTenantId();
        }
        if (activeTenant != null && activeTenant.isNotEmpty) {
          options.headers['X-Tenant-ID'] = activeTenant;
        }

        // Enforce Branch ID header if scoped
        String? activeBranch = _activeBranchId;
        if (activeBranch == null || activeBranch.isEmpty) {
          activeBranch = await _secureStorage.getActiveBranchId();
        }
        if (activeBranch != null && activeBranch.isNotEmpty) {
          options.headers['X-Branch-ID'] = activeBranch;
        }

        // Attach Authorization strictly from Tenant Token or M2M Credentials
        final token = await _secureStorage.getToken();

        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        } else {
          final apiKey = await _secureStorage.getApiKey();
          final clientSecret = await _secureStorage.getClientSecret();
          if (apiKey != null && clientSecret != null) {
            options.headers['X-API-Key'] = apiKey;
            options.headers['X-Client-Secret'] = clientSecret;
          }
        }

        handler.next(options);
      },
      onError: (err, handler) {
        handler.next(err);
      },
    ));
  }

  /// Resilient GET operation with centralized classification, retry, and deduplication.
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    NetworkProfile? profile,
    String? dedupeKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveProfile = profile ?? NetworkProfiles.standard;

    return await resilienceManager.execute<Response<T>>(
      operation: 'GET $path',
      profile: effectiveProfile,
      method: 'GET',
      dedupeKey: dedupeKey,
      onProgress: onProgress,
      action: (context) async {
        final requestOptions = options ?? Options();
        requestOptions.headers = {
          ...?requestOptions.headers,
          'X-Correlation-ID': context.correlationId,
        };
        requestOptions.extra = {
          ...?requestOptions.extra,
          'correlationId': context.correlationId,
        };
        requestOptions.sendTimeout = effectiveProfile.sendTimeout;
        requestOptions.receiveTimeout = effectiveProfile.receiveTimeout;

        try {
          return await _dio.get<T>(
            path,
            queryParameters: queryParameters,
            options: requestOptions,
            cancelToken: cancelToken,
          );
        } on DioException catch (e) {
          throw AppError.fromDioException(e, attempts: context.attempt);
        }
      },
    );
  }

  /// Resilient POST operation with idempotency safety and centralized retry.
  Future<Response<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    String? idempotencyKey,
    Options? options,
    CancelToken? cancelToken,
    NetworkProfile? profile,
    String? dedupeKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveProfile = profile ?? NetworkProfiles.standard;
    final hasIdempotency = idempotencyKey != null && idempotencyKey.isNotEmpty;

    return await resilienceManager.execute<Response<T>>(
      operation: 'POST $path',
      profile: effectiveProfile,
      method: 'POST',
      hasIdempotencyKey: hasIdempotency,
      dedupeKey: dedupeKey,
      onProgress: onProgress,
      action: (context) async {
        final requestOptions = options ?? Options();
        requestOptions.headers = {
          ...?requestOptions.headers,
          'X-Correlation-ID': context.correlationId,
          if (hasIdempotency) 'Idempotency-Key': idempotencyKey,
        };
        requestOptions.extra = {
          ...?requestOptions.extra,
          'correlationId': context.correlationId,
        };
        requestOptions.sendTimeout = effectiveProfile.sendTimeout;
        requestOptions.receiveTimeout = effectiveProfile.receiveTimeout;

        try {
          return await _dio.post<T>(
            path,
            data: data,
            queryParameters: queryParameters,
            options: requestOptions,
            cancelToken: cancelToken,
          );
        } on DioException catch (e) {
          throw AppError.fromDioException(e, attempts: context.attempt);
        }
      },
    );
  }

  /// Resilient PUT operation.
  Future<Response<T>> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    String? idempotencyKey,
    Options? options,
    CancelToken? cancelToken,
    NetworkProfile? profile,
    String? dedupeKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveProfile = profile ?? NetworkProfiles.standard;
    final hasIdempotency = idempotencyKey != null && idempotencyKey.isNotEmpty;

    return await resilienceManager.execute<Response<T>>(
      operation: 'PUT $path',
      profile: effectiveProfile,
      method: 'PUT',
      hasIdempotencyKey: hasIdempotency,
      dedupeKey: dedupeKey,
      onProgress: onProgress,
      action: (context) async {
        final requestOptions = options ?? Options();
        requestOptions.headers = {
          ...?requestOptions.headers,
          'X-Correlation-ID': context.correlationId,
          if (hasIdempotency) 'Idempotency-Key': idempotencyKey,
        };
        requestOptions.extra = {
          ...?requestOptions.extra,
          'correlationId': context.correlationId,
        };
        requestOptions.sendTimeout = effectiveProfile.sendTimeout;
        requestOptions.receiveTimeout = effectiveProfile.receiveTimeout;

        try {
          return await _dio.put<T>(
            path,
            data: data,
            queryParameters: queryParameters,
            options: requestOptions,
            cancelToken: cancelToken,
          );
        } on DioException catch (e) {
          throw AppError.fromDioException(e, attempts: context.attempt);
        }
      },
    );
  }

  /// Resilient PATCH operation.
  Future<Response<T>> patch<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    NetworkProfile? profile,
    String? dedupeKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveProfile = profile ?? NetworkProfiles.standard;

    return await resilienceManager.execute<Response<T>>(
      operation: 'PATCH $path',
      profile: effectiveProfile,
      method: 'PATCH',
      dedupeKey: dedupeKey,
      onProgress: onProgress,
      action: (context) async {
        final requestOptions = options ?? Options();
        requestOptions.headers = {
          ...?requestOptions.headers,
          'X-Correlation-ID': context.correlationId,
        };
        requestOptions.extra = {
          ...?requestOptions.extra,
          'correlationId': context.correlationId,
        };
        requestOptions.sendTimeout = effectiveProfile.sendTimeout;
        requestOptions.receiveTimeout = effectiveProfile.receiveTimeout;

        try {
          return await _dio.patch<T>(
            path,
            data: data,
            queryParameters: queryParameters,
            options: requestOptions,
            cancelToken: cancelToken,
          );
        } on DioException catch (e) {
          throw AppError.fromDioException(e, attempts: context.attempt);
        }
      },
    );
  }

  /// Resilient file or binary bytes downloader.
  Future<List<int>> downloadBytes(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    NetworkProfile? profile,
    String? dedupeKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveProfile = profile ?? NetworkProfiles.document;
    final requestOptions = options ?? Options();
    requestOptions.responseType = ResponseType.bytes;
    requestOptions.headers = {
      ...?requestOptions.headers,
      'Accept': requestOptions.headers?['Accept'] ?? 'application/pdf, application/octet-stream, */*',
    };

    final response = await get<List<dynamic>>(
      path,
      queryParameters: queryParameters,
      options: requestOptions,
      cancelToken: cancelToken,
      profile: effectiveProfile,
      dedupeKey: dedupeKey,
      onProgress: onProgress,
    );

    final data = response.data;
    if (data is List<int>) {
      return data;
    } else if (data is Uint8List) {
      return data.toList();
    } else if (data is List) {
      return data.map((e) => (e as num).toInt()).toList();
    }
    throw const AppError(
      code: 'INVALID_BINARY_RESPONSE',
      message: 'Server did not return a valid binary byte payload.',
      category: ErrorCategory.serverError,
    );
  }
}

