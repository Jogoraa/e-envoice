import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';

enum NetworkSimulationType {
  none,
  latency,
  packetLoss,
  dnsFailure,
  connectionReset,
  connectionTimeout,
  receiveTimeout,
  http500,
  http502,
  http503,
  http504,
  failNAttemptsThenSucceed,
}

/// Development and testing network simulation interceptor.
/// Allows unit and integration tests to verify resilience under harsh network environments.
class PoorNetworkSimulator extends Interceptor {
  static NetworkSimulationType activeSimulation = NetworkSimulationType.none;
  static Duration simulatedLatency = Duration.zero;
  static int failAttemptCount = 0;
  static int _currentAttempt = 0;
  static double packetLossRate = 0.0; // 0.0 to 1.0

  static void reset() {
    activeSimulation = NetworkSimulationType.none;
    simulatedLatency = Duration.zero;
    failAttemptCount = 0;
    _currentAttempt = 0;
    packetLossRate = 0.0;
  }

  static void simulateLatency(Duration latency) {
    activeSimulation = NetworkSimulationType.latency;
    simulatedLatency = latency;
  }

  static void simulateDnsFailure() {
    activeSimulation = NetworkSimulationType.dnsFailure;
  }

  static void simulateConnectionReset() {
    activeSimulation = NetworkSimulationType.connectionReset;
  }

  static void simulateConnectionTimeout() {
    activeSimulation = NetworkSimulationType.connectionTimeout;
  }

  static void simulateReceiveTimeout() {
    activeSimulation = NetworkSimulationType.receiveTimeout;
  }

  static void simulateHttpError(int statusCode) {
    if (statusCode == 500) activeSimulation = NetworkSimulationType.http500;
    if (statusCode == 502) activeSimulation = NetworkSimulationType.http502;
    if (statusCode == 503) activeSimulation = NetworkSimulationType.http503;
    if (statusCode == 504) activeSimulation = NetworkSimulationType.http504;
  }

  static void simulateFailThenSucceed(int failCount, NetworkSimulationType failType) {
    activeSimulation = NetworkSimulationType.failNAttemptsThenSucceed;
    failAttemptCount = failCount;
    _currentAttempt = 0;
  }

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    if (activeSimulation == NetworkSimulationType.none) {
      return handler.next(options);
    }

    if (simulatedLatency > Duration.zero) {
      await Future<void>.delayed(simulatedLatency);
    }

    if (activeSimulation == NetworkSimulationType.latency) {
      return handler.next(options);
    }

    if (activeSimulation == NetworkSimulationType.failNAttemptsThenSucceed) {
      _currentAttempt++;
      if (_currentAttempt > failAttemptCount) {
        // Recovery after N attempts
        return handler.next(options);
      }
      // Inject timeout failure during the initial N attempts
      return handler.reject(DioException(
        requestOptions: options,
        type: DioExceptionType.receiveTimeout,
        message: 'Simulated temporary timeout on attempt $_currentAttempt',
      ));
    }

    switch (activeSimulation) {
      case NetworkSimulationType.dnsFailure:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.connectionError,
          error: const SocketException('Failed host lookup: api.ut.gov.et'),
          message: 'Failed host lookup: api.ut.gov.et',
        ));

      case NetworkSimulationType.connectionReset:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.connectionError,
          error: const SocketException('Connection reset by peer'),
          message: 'Connection reset by peer',
        ));

      case NetworkSimulationType.connectionTimeout:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.connectionTimeout,
          message: 'Connection timed out',
        ));

      case NetworkSimulationType.receiveTimeout:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.receiveTimeout,
          message: 'Receive timed out',
        ));

      case NetworkSimulationType.http500:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.badResponse,
          response: Response(
            requestOptions: options,
            statusCode: 500,
            data: {'code': 'INTERNAL_SERVER_ERROR', 'message': 'Internal Server Error'},
          ),
        ));

      case NetworkSimulationType.http502:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.badResponse,
          response: Response(
            requestOptions: options,
            statusCode: 502,
            data: {'code': 'BAD_GATEWAY', 'message': 'Bad Gateway'},
          ),
        ));

      case NetworkSimulationType.http503:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.badResponse,
          response: Response(
            requestOptions: options,
            statusCode: 503,
            data: {'code': 'SERVICE_UNAVAILABLE', 'message': 'Service Unavailable'},
          ),
        ));

      case NetworkSimulationType.http504:
        return handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.badResponse,
          response: Response(
            requestOptions: options,
            statusCode: 504,
            data: {'code': 'GATEWAY_TIMEOUT', 'message': 'Gateway Timeout'},
          ),
        ));

      default:
        return handler.next(options);
    }
  }
}
