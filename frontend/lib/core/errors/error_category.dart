import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';

/// Precise classification of a *single* failed operation.
///
/// Deliberately separates "this device has no network interface" ([noNetwork])
/// from "this particular HTTP request did not succeed" (everything else).
/// A single failed request NEVER yields [noNetwork]; only the OS link layer
/// reporting no interface at all can produce that value.
enum ErrorCategory {
  noNetwork,
  networkDegraded,
  dnsFailure,
  connectionTimeout,
  tlsFailure,
  requestTimeout,
  connectionReset,
  serverUnreachable,
  serverError,
  authenticationRequired,
  authorizationDenied,
  validationError,
  notFound,
  conflict,
  rateLimited,
  governmentPending,
  governmentRejected,
  cancelled,
  unknown,
}

extension ErrorCategoryX on ErrorCategory {
  /// Stable machine code surfaced in [AppError.code] and in logs.
  String get code {
    switch (this) {
      case ErrorCategory.noNetwork:
        return 'NO_NETWORK';
      case ErrorCategory.networkDegraded:
        return 'NETWORK_DEGRADED';
      case ErrorCategory.dnsFailure:
        return 'DNS_FAILURE';
      case ErrorCategory.connectionTimeout:
        return 'CONNECTION_TIMEOUT';
      case ErrorCategory.tlsFailure:
        return 'TLS_FAILURE';
      case ErrorCategory.requestTimeout:
        return 'REQUEST_TIMEOUT';
      case ErrorCategory.connectionReset:
        return 'CONNECTION_RESET';
      case ErrorCategory.serverUnreachable:
        return 'SERVER_UNREACHABLE';
      case ErrorCategory.serverError:
        return 'SERVER_ERROR';
      case ErrorCategory.authenticationRequired:
        return 'AUTHENTICATION_REQUIRED';
      case ErrorCategory.authorizationDenied:
        return 'AUTHORIZATION_DENIED';
      case ErrorCategory.validationError:
        return 'VALIDATION_ERROR';
      case ErrorCategory.notFound:
        return 'NOT_FOUND';
      case ErrorCategory.conflict:
        return 'CONFLICT';
      case ErrorCategory.rateLimited:
        return 'RATE_LIMITED';
      case ErrorCategory.governmentPending:
        return 'GOVERNMENT_PENDING';
      case ErrorCategory.governmentRejected:
        return 'GOVERNMENT_REJECTED';
      case ErrorCategory.cancelled:
        return 'REQUEST_CANCELLED';
      case ErrorCategory.unknown:
        return 'UNKNOWN';
    }
  }

  /// True when the failure is a transport-level condition rather than a
  /// decision the server deliberately made about the payload.
  bool get isTransport {
    switch (this) {
      case ErrorCategory.noNetwork:
      case ErrorCategory.networkDegraded:
      case ErrorCategory.dnsFailure:
      case ErrorCategory.connectionTimeout:
      case ErrorCategory.tlsFailure:
      case ErrorCategory.requestTimeout:
      case ErrorCategory.connectionReset:
      case ErrorCategory.serverUnreachable:
        return true;
      default:
        return false;
    }
  }

  /// Whether observing this category is *evidence* that the device may be
  /// disconnected. Evidence is accumulated by NetworkResilienceManager — a
  /// single occurrence never flips the app into offline continuity mode.
  bool get isOfflineEvidence {
    switch (this) {
      case ErrorCategory.noNetwork:
      case ErrorCategory.dnsFailure:
      case ErrorCategory.connectionTimeout:
      case ErrorCategory.connectionReset:
        return true;
      // A receive timeout or a 503 means the server is slow or busy — the
      // network itself is demonstrably working, so it is NOT offline evidence.
      default:
        return false;
    }
  }

  /// Plain, non-alarming English copy. Progress-oriented while recovery is
  /// still possible; factual once it is not.
  String get userMessage {
    switch (this) {
      case ErrorCategory.noNetwork:
        return "You're offline. The saved invoice remains available.";
      case ErrorCategory.networkDegraded:
        return 'The connection is slow. Still working on it…';
      case ErrorCategory.dnsFailure:
        return 'Cannot look up the server address right now. Retrying…';
      case ErrorCategory.connectionTimeout:
        return 'Taking longer than usual to reach the server. Retrying…';
      case ErrorCategory.tlsFailure:
        return 'The secure connection to the server could not be established.';
      case ErrorCategory.requestTimeout:
        return 'The server is taking longer than expected. Retrying…';
      case ErrorCategory.connectionReset:
        return 'The connection dropped mid-request. Retrying…';
      case ErrorCategory.serverUnreachable:
        return 'The service is temporarily unavailable. Retrying…';
      case ErrorCategory.serverError:
        return 'The server could not complete this request.';
      case ErrorCategory.authenticationRequired:
        return 'Your session needs to be renewed.';
      case ErrorCategory.authorizationDenied:
        return 'You do not have permission for this operation.';
      case ErrorCategory.validationError:
        return 'The request was rejected because some details are invalid.';
      case ErrorCategory.notFound:
        return 'That record was not found on the server.';
      case ErrorCategory.conflict:
        return 'This operation conflicts with the current server state.';
      case ErrorCategory.rateLimited:
        return 'Too many requests. Waiting before trying again…';
      case ErrorCategory.governmentPending:
        return 'Awaiting Ministry of Revenues registration.';
      case ErrorCategory.governmentRejected:
        return 'The Ministry of Revenues rejected this document.';
      case ErrorCategory.cancelled:
        return 'The request was cancelled.';
      case ErrorCategory.unknown:
        return 'Something went wrong completing this request.';
    }
  }

  /// Amharic counterpart used for the bilingual surfaces.
  String get localizedUserMessage {
    switch (this) {
      case ErrorCategory.noNetwork:
        return 'ከመስመር ውጪ ነዎት። የተቀመጠው ደረሰኝ አሁንም ይገኛል።';
      case ErrorCategory.networkDegraded:
        return 'ግንኙነቱ ዘገምተኛ ነው። በመሞከር ላይ…';
      case ErrorCategory.dnsFailure:
        return 'የአገልጋዩን አድራሻ ማግኘት አልተቻለም። እንደገና በመሞከር ላይ…';
      case ErrorCategory.connectionTimeout:
        return 'አገልጋዩን ለመድረስ ከወትሮው በላይ ጊዜ እየወሰደ ነው። እንደገና በመሞከር ላይ…';
      case ErrorCategory.tlsFailure:
        return 'ደህንነቱ የተጠበቀ ግንኙነት መፍጠር አልተቻለም።';
      case ErrorCategory.requestTimeout:
        return 'አገልጋዩ ከተጠበቀው በላይ ጊዜ እየወሰደ ነው። እንደገና በመሞከር ላይ…';
      case ErrorCategory.connectionReset:
        return 'ግንኙነቱ በመሃል ተቋርጧል። እንደገና በመሞከር ላይ…';
      case ErrorCategory.serverUnreachable:
        return 'አገልግሎቱ ለጊዜው አይገኝም። እንደገና በመሞከር ላይ…';
      case ErrorCategory.serverError:
        return 'አገልጋዩ ጥያቄውን ማጠናቀቅ አልቻለም።';
      case ErrorCategory.authenticationRequired:
        return 'የመግቢያ ክፍለ ጊዜዎ መታደስ አለበት።';
      case ErrorCategory.authorizationDenied:
        return 'ለዚህ ተግባር ፈቃድ የለዎትም።';
      case ErrorCategory.validationError:
        return 'አንዳንድ መረጃዎች ትክክል ስላልሆኑ ጥያቄው ተቀባይነት አላገኘም።';
      case ErrorCategory.notFound:
        return 'መዝገቡ በአገልጋዩ ላይ አልተገኘም።';
      case ErrorCategory.conflict:
        return 'ይህ ተግባር ከአሁኑ የአገልጋይ ሁኔታ ጋር ይጋጫል።';
      case ErrorCategory.rateLimited:
        return 'ብዙ ጥያቄዎች ቀርበዋል። ትንሽ ቆይቶ እንደገና ይሞከራል…';
      case ErrorCategory.governmentPending:
        return 'የገቢዎች ሚኒስቴር ምዝገባ በመጠባበቅ ላይ።';
      case ErrorCategory.governmentRejected:
        return 'የገቢዎች ሚኒስቴር ይህንን ሰነድ ውድቅ አድርጎታል።';
      case ErrorCategory.cancelled:
        return 'ጥያቄው ተሰርዟል።';
      case ErrorCategory.unknown:
        return 'ጥያቄውን ማጠናቀቅ ላይ ችግር ተፈጥሯል።';
    }
  }
}

/// Maps raw transport/HTTP failures onto [ErrorCategory].
///
/// This is the single place in the client where a low-level exception becomes
/// a named condition. Nothing else may invent a category.
class ErrorClassifier {
  const ErrorClassifier._();

  /// Backend `code` values that carry government lifecycle meaning.
  static const Set<String> _governmentPendingCodes = {
    'GOVERNMENT_PENDING',
    'PENDING_REGISTRATION',
    'MOR_PENDING',
    'REGISTRATION_PENDING',
    'GATEWAY_UNAVAILABLE',
  };

  static const Set<String> _governmentRejectedCodes = {
    'GOVERNMENT_REJECTED',
    'MOR_REJECTED',
    'EIRS_REJECTED',
    'REGISTRATION_REJECTED',
  };

  static ErrorCategory classify(Object error) {
    if (error is DioException) return classifyDio(error);
    if (error is TimeoutException) return ErrorCategory.requestTimeout;
    if (error is SocketException) return _classifySocket(error);
    if (error is HandshakeException) return ErrorCategory.tlsFailure;
    if (error is HttpException) return ErrorCategory.serverUnreachable;
    return ErrorCategory.unknown;
  }

  static ErrorCategory classifyDio(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
        return ErrorCategory.connectionTimeout;
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
        return ErrorCategory.requestTimeout;
      case DioExceptionType.badCertificate:
        return ErrorCategory.tlsFailure;
      case DioExceptionType.cancel:
        return ErrorCategory.cancelled;
      case DioExceptionType.badResponse:
        return classifyResponse(
          e.response?.statusCode,
          bodyCode: _bodyCode(e.response?.data),
        );
      case DioExceptionType.connectionError:
      case DioExceptionType.unknown:
        return _classifyTransport(e);
    }
  }

  /// Classifies a completed HTTP exchange by status code, honouring any
  /// business `code` the backend put in the error envelope.
  static ErrorCategory classifyResponse(int? status, {String? bodyCode}) {
    final normalized = bodyCode?.toUpperCase();
    if (normalized != null) {
      if (_governmentPendingCodes.contains(normalized)) {
        return ErrorCategory.governmentPending;
      }
      if (_governmentRejectedCodes.contains(normalized)) {
        return ErrorCategory.governmentRejected;
      }
    }

    if (status == null) return ErrorCategory.unknown;
    if (status >= 200 && status < 300) return ErrorCategory.unknown;

    switch (status) {
      case 400:
      case 422:
        return ErrorCategory.validationError;
      case 401:
        return ErrorCategory.authenticationRequired;
      case 403:
        return ErrorCategory.authorizationDenied;
      case 404:
      case 410:
        return ErrorCategory.notFound;
      case 408:
        return ErrorCategory.requestTimeout;
      case 409:
        return ErrorCategory.conflict;
      case 425:
        // "Too Early" — the server asked us to come back; treat as degraded so
        // the retry policy picks it up without alarming the user.
        return ErrorCategory.networkDegraded;
      case 429:
        return ErrorCategory.rateLimited;
      case 502:
      case 503:
      case 504:
        return ErrorCategory.serverUnreachable;
    }

    if (status >= 500) return ErrorCategory.serverError;
    return ErrorCategory.validationError;
  }

  /// Transport failures carry no HTTP status. Dio collapses DNS failures,
  /// refused connections, resets and TLS handshake errors into a single
  /// [DioExceptionType.connectionError], so the underlying OS error is the
  /// only thing that can tell them apart.
  static ErrorCategory _classifyTransport(DioException e) {
    final inner = e.error;
    if (inner is HandshakeException || inner is TlsException) {
      return ErrorCategory.tlsFailure;
    }
    if (inner is SocketException) return _classifySocket(inner);
    if (inner is TimeoutException) return ErrorCategory.requestTimeout;

    return _classifyMessage(
      '${inner ?? ''} ${e.message ?? ''}',
      fallback: ErrorCategory.serverUnreachable,
    );
  }

  static ErrorCategory _classifySocket(SocketException e) {
    // A failed host lookup surfaces with errno 0 / -2 / -5 depending on the
    // platform, so the message is the only portable discriminator.
    return _classifyMessage(
      '${e.message} ${e.osError?.message ?? ''}',
      fallback: ErrorCategory.serverUnreachable,
    );
  }

  static ErrorCategory _classifyMessage(
    String raw, {
    required ErrorCategory fallback,
  }) {
    final text = raw.toLowerCase();

    if (text.contains('failed host lookup') ||
        text.contains('nodename nor servname') ||
        text.contains('name or service not known') ||
        text.contains('temporary failure in name resolution') ||
        text.contains('no such host') ||
        text.contains('getaddrinfo')) {
      return ErrorCategory.dnsFailure;
    }

    if (text.contains('handshake') ||
        text.contains('certificate') ||
        text.contains('tlsv1') ||
        text.contains('ssl')) {
      return ErrorCategory.tlsFailure;
    }

    if (text.contains('connection reset') ||
        text.contains('connection closed') ||
        text.contains('connection terminated') ||
        text.contains('software caused connection abort') ||
        text.contains('broken pipe') ||
        text.contains('econnreset')) {
      return ErrorCategory.connectionReset;
    }

    if (text.contains('timed out') || text.contains('timeout')) {
      return ErrorCategory.connectionTimeout;
    }

    if (text.contains('connection refused') ||
        text.contains('econnrefused') ||
        text.contains('no route to host') ||
        text.contains('host is unreachable')) {
      return ErrorCategory.serverUnreachable;
    }

    // "Network is unreachable" / "Network is down" are the only OS errors that
    // genuinely describe the local link being gone. Even so this is only ONE
    // observation: NetworkResilienceManager decides whether it becomes offline.
    if (text.contains('network is unreachable') ||
        text.contains('network is down') ||
        text.contains('enetunreach') ||
        text.contains('enetdown')) {
      return ErrorCategory.noNetwork;
    }

    return fallback;
  }

  static String? _bodyCode(dynamic data) {
    if (data is Map) {
      final code = data['code'] ?? data['error'];
      return code?.toString();
    }
    return null;
  }
}
