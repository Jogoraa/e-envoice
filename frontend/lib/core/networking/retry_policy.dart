import 'dart:math';

import '../errors/app_error.dart';
import '../errors/error_category.dart';
import 'network_profile.dart';

/// Why an operation may or may not be replayed.
enum RetryDecision {
  /// Safe to replay after [RetryPlan.delay].
  retry,

  /// Replaying could duplicate a side effect (non-idempotent write with no
  /// idempotency key). Never retried automatically.
  unsafeToRetry,

  /// The server made a deliberate decision; replaying would get the same answer.
  terminal,

  /// Retryable in principle, but the budget (attempts or deadline) is spent.
  budgetExhausted,
}

class RetryPlan {
  final RetryDecision decision;
  final Duration delay;
  final String reason;

  const RetryPlan(this.decision, this.delay, this.reason);

  bool get shouldRetry => decision == RetryDecision.retry;
}

/// Centralized retry rules. The *only* place in the client that decides whether
/// an operation is replayed — repositories and screens must never hand-roll
/// their own loops.
class RetryPolicy {
  final Random _random;

  RetryPolicy({Random? random}) : _random = random ?? Random();

  /// HTTP verbs that are idempotent by definition (RFC 9110).
  static const Set<String> _idempotentMethods = {
    'GET',
    'HEAD',
    'OPTIONS',
    'PUT',
    'DELETE',
  };

  /// Status codes that always mean "come back later".
  static const Set<int> _retryableStatuses = {408, 425, 429, 500, 502, 503, 504};

  /// Backend business codes that are final regardless of transport health.
  /// Replaying these cannot change the answer and would spam the Ministry.
  static const Set<String> _terminalBusinessCodes = {
    'GOVERNMENT_REJECTED',
    'MOR_REJECTED',
    'EIRS_REJECTED',
    'REGISTRATION_REJECTED',
    'DEVICE_REVOKED',
    'OFFLINE_BATCH_EXPIRED',
    'DUPLICATE_OFFLINE_SEQUENCE',
    'INVOICE_NOT_FOUND',
    'TAXPAYER_PROFILE_NOT_FOUND',
    'VALIDATION_ERROR',
    'PDF_EXPORT_UNAVAILABLE',
  };

  /// Decides the fate of a failed attempt.
  ///
  /// [attempt] is 1-based: the attempt that just failed.
  /// [elapsed] is the time consumed by the whole operation so far.
  /// [hasIdempotencyKey] must be true only when the request actually carried an
  /// `Idempotency-Key` header, so a replayed POST cannot create a second invoice.
  RetryPlan evaluate({
    required AppError error,
    required String method,
    required int attempt,
    required Duration elapsed,
    required NetworkProfile profile,
    bool hasIdempotencyKey = false,
  }) {
    final upperMethod = method.toUpperCase();

    if (error.category == ErrorCategory.cancelled) {
      return const RetryPlan(
        RetryDecision.terminal,
        Duration.zero,
        'Request was cancelled by the caller.',
      );
    }

    // 1. Is this class of failure retryable at all?
    if (!_isTransientCategory(error)) {
      return RetryPlan(
        RetryDecision.terminal,
        Duration.zero,
        'Server returned a definitive result (${error.code}); replay would not change it.',
      );
    }

    // 2. Is replaying this verb safe? A POST without an idempotency key could
    //    create a duplicate invoice, which is worse than surfacing the error.
    final safeVerb = _idempotentMethods.contains(upperMethod) || hasIdempotencyKey;
    if (!safeVerb) {
      return RetryPlan(
        RetryDecision.unsafeToRetry,
        Duration.zero,
        '$upperMethod without an Idempotency-Key cannot be replayed safely.',
      );
    }

    // 3. Budget.
    if (attempt >= profile.maxAttempts) {
      return RetryPlan(
        RetryDecision.budgetExhausted,
        Duration.zero,
        'Retry budget of ${profile.maxAttempts} attempts is exhausted.',
      );
    }

    final delay = computeDelay(
      attempt: attempt,
      profile: profile,
      retryAfter: error.retryAfter,
    );

    if (elapsed + delay >= profile.overallDeadline) {
      return RetryPlan(
        RetryDecision.budgetExhausted,
        Duration.zero,
        'Overall deadline of ${profile.overallDeadline.inSeconds}s would be exceeded.',
      );
    }

    return RetryPlan(
      RetryDecision.retry,
      delay,
      'Transient ${error.code}; retrying attempt ${attempt + 1} of ${profile.maxAttempts}.',
    );
  }

  bool _isTransientCategory(AppError error) {
    if (_terminalBusinessCodes.contains(error.code.toUpperCase())) return false;

    switch (error.category) {
      // Transport conditions: the request never got a verdict.
      case ErrorCategory.networkDegraded:
      case ErrorCategory.dnsFailure:
      case ErrorCategory.connectionTimeout:
      case ErrorCategory.requestTimeout:
      case ErrorCategory.connectionReset:
      case ErrorCategory.serverUnreachable:
      case ErrorCategory.rateLimited:
        return true;

      // A disconnected link is retryable, but only so the caller can pick up a
      // brief interruption; the resilience manager gates how often we bother.
      case ErrorCategory.noNetwork:
        return true;

      // 5xx: the server broke, not the payload.
      case ErrorCategory.serverError:
        return error.status == null || _retryableStatuses.contains(error.status);

      // TLS failures are usually configuration, not weather. One retry buys
      // nothing and a handshake storm is expensive on a constrained link.
      case ErrorCategory.tlsFailure:
      case ErrorCategory.authenticationRequired:
      case ErrorCategory.authorizationDenied:
      case ErrorCategory.validationError:
      case ErrorCategory.notFound:
      case ErrorCategory.conflict:
      case ErrorCategory.governmentPending:
      case ErrorCategory.governmentRejected:
      case ErrorCategory.cancelled:
        return false;

      case ErrorCategory.unknown:
        // An unclassifiable transport hiccup with no status is worth one more
        // look; an unclassifiable *response* is not.
        return error.status == null;
    }
  }

  /// Exponential backoff with equal jitter, clamped to the profile ceiling.
  ///
  /// Equal jitter (`half + rand(0, half)`) keeps the sequence monotonically
  /// growing — unlike full jitter, which can schedule attempt 4 sooner than
  /// attempt 2 — while still de-synchronising a fleet of POS devices that all
  /// reconnect at once.
  Duration computeDelay({
    required int attempt,
    required NetworkProfile profile,
    Duration? retryAfter,
  }) {
    // An explicit server instruction always wins.
    if (retryAfter != null) {
      return retryAfter > profile.maxBackoff ? profile.maxBackoff : retryAfter;
    }

    final exponent = (attempt - 1).clamp(0, 10);
    final rawMs = profile.baseBackoff.inMilliseconds * pow(2, exponent);
    final cappedMs = min(rawMs.toDouble(), profile.maxBackoff.inMilliseconds.toDouble());
    final half = cappedMs / 2;
    final jittered = half + _random.nextDouble() * half;
    return Duration(milliseconds: jittered.round());
  }
}
