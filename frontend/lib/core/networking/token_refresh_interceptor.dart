import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../errors/app_error.dart';
import '../errors/error_category.dart';
import '../storage/secure_storage_service.dart';

/// Single-Flight Token Refresh Interceptor.
/// Prevents thundering herd refresh loops when concurrent requests encounter HTTP 401.
///
/// Under poor network conditions:
/// - Retries transient connection/timeout blips during refresh.
/// - Preserves user credentials on network failures instead of wiping session.
/// - Only wipes session when the backend definitively rejects the refresh token (HTTP 401/403).
class TokenRefreshInterceptor extends Interceptor {
  final Dio dio;
  final SecureStorageService secureStorage;
  final String refreshUrl;
  final Dio? refreshClient;

  Dio get _dio => dio;
  SecureStorageService get _secureStorage => secureStorage;
  String get _refreshUrl => refreshUrl;

  Completer<String?>? _refreshCompleter;

  TokenRefreshInterceptor({
    required this.dio,
    required this.secureStorage,
    this.refreshUrl = '/api/v1/auth/refresh',
    this.refreshClient,
  });

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode != 401) {
      return handler.next(err);
    }

    final RequestOptions requestOptions = err.requestOptions;

    // Prevent recursive refresh loop on the refresh endpoint itself
    if (requestOptions.path.contains(_refreshUrl) ||
        requestOptions.extra['is_retry'] == true) {
      return handler.next(err);
    }

    try {
      final newToken = await _executeSingleFlightRefresh();
      if (newToken != null && newToken.isNotEmpty) {
        requestOptions.headers['Authorization'] = 'Bearer $newToken';
        requestOptions.extra['is_retry'] = true;

        // Safely replay original request with newly refreshed token
        final retryResponse = await _dio.fetch(requestOptions);
        return handler.resolve(retryResponse);
      }
    } catch (refreshError) {
      final appError = AppError.fromObject(refreshError);
      // Only wipe session if the server definitively rejected authentication
      if (appError.category == ErrorCategory.authenticationRequired ||
          appError.category == ErrorCategory.authorizationDenied ||
          appError.status == 401 ||
          appError.status == 403) {
        debugPrint('[TokenRefresh] Authentication definitively rejected. Purging session.');
        await _secureStorage.wipeSession();
      } else {
        debugPrint('[TokenRefresh] Refresh failed due to transient transport (${appError.code}). Preserving session.');
      }
      return handler.next(err);
    }

    return handler.next(err);
  }

  /// Ensures only ONE network call is executed for refreshing tokens across any number of concurrent 401s.
  Future<String?> _executeSingleFlightRefresh() async {
    if (_refreshCompleter != null) {
      return await _refreshCompleter!.future;
    }

    _refreshCompleter = Completer<String?>();

    try {
      final refreshToken = await _secureStorage.getRefreshToken();
      final apiKey = await _secureStorage.getApiKey();
      final clientSecret = await _secureStorage.getClientSecret();

      String? newToken;

      // 1. Try Refresh Token flow if available
      if (refreshToken != null && refreshToken.isNotEmpty) {
        final refreshDio = refreshClient ??
            Dio(BaseOptions(
              baseUrl: _dio.options.baseUrl,
              connectTimeout: const Duration(seconds: 15),
              receiveTimeout: const Duration(seconds: 20),
            ));

        var attempts = 0;
        const maxAttempts = 3;

        while (attempts < maxAttempts) {
          attempts++;
          try {
            final response = await refreshDio.post(
              _refreshUrl,
              data: {'refreshToken': refreshToken},
            );

            if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
              newToken = response.data['accessToken'] ?? response.data['token'];
              final newRefreshToken = response.data['refreshToken'];
              if (newToken != null) {
                await _secureStorage.saveToken(newToken);
              }
              if (newRefreshToken != null) {
                await _secureStorage.saveRefreshToken(newRefreshToken);
              }
              break;
            }
          } on DioException catch (dioErr) {
            final category = ErrorClassifier.classifyDio(dioErr);
            final isTransient = category == ErrorCategory.connectionTimeout ||
                category == ErrorCategory.requestTimeout ||
                category == ErrorCategory.connectionReset ||
                category == ErrorCategory.dnsFailure ||
                category == ErrorCategory.serverUnreachable;

            if (isTransient && attempts < maxAttempts) {
              await Future<void>.delayed(Duration(milliseconds: 400 * attempts));
              continue;
            }
            rethrow;
          }
        }
      }
      // 2. M2M client credentials fallback (re-authenticating API Client)
      else if (apiKey != null && clientSecret != null) {
        newToken = apiKey; // API Key header remains valid or re-checked
      }

      _refreshCompleter!.complete(newToken);
      return newToken;
    } catch (e) {
      _refreshCompleter!.completeError(e);
      rethrow;
    } finally {
      _refreshCompleter = null;
    }
  }
}

