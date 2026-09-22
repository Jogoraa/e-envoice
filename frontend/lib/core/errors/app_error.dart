import 'package:dio/dio.dart';

import 'error_category.dart';

/// Standardized Domain Error Model representing backend ErrorEnvelope or client failure.
/// Adheres to Brand Voice: plain, direct, no apologies, names what happened and next steps.
///
/// Every error carries an [ErrorCategory]. Nothing in the client may claim the
/// device is offline unless the category is [ErrorCategory.noNetwork] *and* the
/// NetworkResilienceManager has corroborating evidence — a single failed HTTP
/// request is never sufficient.
class AppError implements Exception {
  final int? status;
  final String code;
  final String message;
  final String? localizedMessage;
  final String? correlationId;
  final String? path;
  final List<ValidationErrorDetail> details;
  final ErrorCategory category;

  /// Server-supplied `Retry-After` delay, when present.
  final Duration? retryAfter;

  /// Number of attempts already spent on the operation that produced this error.
  final int attempts;

  const AppError({
    this.status,
    required this.code,
    required this.message,
    this.localizedMessage,
    this.correlationId,
    this.path,
    this.details = const [],
    this.category = ErrorCategory.unknown,
    this.retryAfter,
    this.attempts = 1,
  });

  /// Copy used by the retry layer to record how much effort was spent before
  /// giving up, without losing the original classification.
  AppError copyWith({
    int? attempts,
    String? correlationId,
    ErrorCategory? category,
    String? message,
  }) {
    return AppError(
      status: status,
      code: category?.code ?? code,
      message: message ?? this.message,
      localizedMessage: localizedMessage,
      correlationId: correlationId ?? this.correlationId,
      path: path,
      details: details,
      category: category ?? this.category,
      retryAfter: retryAfter,
      attempts: attempts ?? this.attempts,
    );
  }

  /// True when the failure describes the transport rather than a deliberate
  /// server decision about the payload.
  bool get isTransport => category.isTransport;

  /// True only for a genuinely disconnected device. Timeouts, resets, 503s and
  /// DNS hiccups are explicitly NOT offline.
  bool get isOffline => category == ErrorCategory.noNetwork;

  /// Non-alarming copy suitable for a snackbar or inline banner. Transport
  /// failures already carry category copy in [message]; server errors carry
  /// whatever the backend envelope said.
  String get userMessage => message.isNotEmpty ? message : category.userMessage;

  factory AppError.fromDioException(DioException dioEx, {int attempts = 1}) {
    final response = dioEx.response;
    final correlationId = _resolveCorrelationId(dioEx);
    final retryAfter = _parseRetryAfter(response);

    if (response != null && response.data is Map<String, dynamic>) {
      final data = response.data as Map<String, dynamic>;
      final rawDetails = data['details'];
      List<ValidationErrorDetail> parsedDetails = [];
      if (rawDetails is List) {
        parsedDetails = rawDetails.map((d) {
          if (d is Map<String, dynamic>) {
            return ValidationErrorDetail(
              field: d['field']?.toString() ?? '',
              issue: d['issue']?.toString() ?? '',
              rejectedValue: d['rejectedValue'],
            );
          }
          return ValidationErrorDetail(field: '', issue: d.toString());
        }).toList();
      }

      final bodyCode = data['code']?.toString() ?? data['error']?.toString();
      final category = ErrorClassifier.classifyResponse(
        response.statusCode,
        bodyCode: bodyCode,
      );

      return AppError(
        status: response.statusCode ?? data['status'] as int?,
        code: bodyCode ?? category.code,
        message: data['message']?.toString() ?? category.userMessage,
        localizedMessage: data['localizedMessage']?.toString(),
        correlationId: data['correlationId']?.toString() ?? correlationId,
        path: data['path']?.toString(),
        details: parsedDetails,
        category: category,
        retryAfter: retryAfter,
        attempts: attempts,
      );
    }

    final category = ErrorClassifier.classifyDio(dioEx);

    return AppError(
      status: response?.statusCode,
      code: category.code,
      message: category.userMessage,
      localizedMessage: category.localizedUserMessage,
      correlationId: correlationId,
      path: dioEx.requestOptions.path,
      category: category,
      retryAfter: retryAfter,
      attempts: attempts,
    );
  }

  /// Wraps any non-Dio throwable without ever guessing "offline".
  factory AppError.fromObject(Object error, {String? correlationId, int attempts = 1}) {
    if (error is AppError) return error;
    if (error is DioException) {
      return AppError.fromDioException(error, attempts: attempts);
    }
    final category = ErrorClassifier.classify(error);
    return AppError(
      code: category.code,
      message: category.userMessage,
      localizedMessage: category.localizedUserMessage,
      correlationId: correlationId,
      category: category,
      attempts: attempts,
    );
  }

  /// The device link layer reports no interface at all.
  factory AppError.noNetwork({String? correlationId}) {
    return AppError(
      code: ErrorCategory.noNetwork.code,
      message: ErrorCategory.noNetwork.userMessage,
      localizedMessage: ErrorCategory.noNetwork.localizedUserMessage,
      correlationId: correlationId,
      category: ErrorCategory.noNetwork,
    );
  }

  factory AppError.validation(String message, {List<ValidationErrorDetail> details = const []}) {
    return AppError(
      code: 'VALIDATION_ERROR',
      message: message,
      details: details,
      category: ErrorCategory.validationError,
    );
  }

  factory AppError.duplicateSequence(Object longSequence) {
    return AppError(
      status: 409,
      code: 'DUPLICATE_OFFLINE_SEQUENCE',
      message: 'Transaction sequence $longSequence was already registered on the server.',
      localizedMessage: 'ይህ የግብይት ተራ ቁጥር አስቀድሞ በማዕከላዊ ስርዓቱ ተመዝግቧል።',
      category: ErrorCategory.conflict,
    );
  }

  factory AppError.deviceRevoked() {
    return const AppError(
      status: 403,
      code: 'DEVICE_REVOKED',
      message: 'This POS device has been revoked and cannot synchronize with the Ministry system.',
      localizedMessage: 'መሣሪያው አገልግሎት እንዳይሰጥ ስለታገደ ግብይቶችን ወደ ማዕከላዊ ስርዓቱ መላክ አይችልም።',
      category: ErrorCategory.authorizationDenied,
    );
  }

  factory AppError.batchExpired() {
    return const AppError(
      status: 400,
      code: 'OFFLINE_BATCH_EXPIRED',
      message: 'Offline transaction exceeds the statutory 72-hour limit per Directive No. 1142/2026 Art. 4(4).',
      localizedMessage: 'የመስመር ውጪ ግብይቱ የ72 ሰዓታት የህግ ገደብ አልፎበታል።',
      category: ErrorCategory.validationError,
    );
  }

  /// Correlation IDs are stamped on every outbound request by the API client,
  /// so a client-side transport failure can still name the request it belongs
  /// to. Preference order: server echo, then the ID we sent.
  static String? _resolveCorrelationId(DioException dioEx) {
    final response = dioEx.response;
    final fromResponse = response?.headers.value('X-Correlation-ID');
    if (fromResponse != null && fromResponse.isNotEmpty) return fromResponse;

    final fromRequest = dioEx.requestOptions.headers['X-Correlation-ID'];
    if (fromRequest != null && fromRequest.toString().isNotEmpty) {
      return fromRequest.toString();
    }

    final fromExtra = dioEx.requestOptions.extra['correlationId'];
    if (fromExtra != null && fromExtra.toString().isNotEmpty) {
      return fromExtra.toString();
    }
    return null;
  }

  static Duration? _parseRetryAfter(Response? response) {
    final raw = response?.headers.value('Retry-After');
    if (raw == null || raw.isEmpty) return null;

    final seconds = int.tryParse(raw.trim());
    if (seconds != null) return Duration(seconds: seconds.clamp(0, 300));

    final httpDate = DateTime.tryParse(raw.trim());
    if (httpDate != null) {
      final delta = httpDate.difference(DateTime.now());
      if (delta.isNegative) return Duration.zero;
      return delta > const Duration(minutes: 5) ? const Duration(minutes: 5) : delta;
    }
    return null;
  }

  @override
  String toString() {
    final buffer = StringBuffer('AppError[$code]: $message');
    if (correlationId != null && correlationId!.isNotEmpty) {
      buffer.write(' (correlationId: $correlationId)');
    }
    return buffer.toString();
  }
}

class ValidationErrorDetail {
  final String field;
  final String issue;
  final dynamic rejectedValue;

  const ValidationErrorDetail({
    required this.field,
    required this.issue,
    this.rejectedValue,
  });
}
