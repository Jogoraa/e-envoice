import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../errors/app_error.dart';
import '../errors/error_category.dart';
import 'network_profile.dart';
import 'retry_policy.dart';

/// Coarse health of the path between this device and the backend.
///
/// Deliberately NOT a boolean. "Wi-Fi is up but the receipt request timed out"
/// is [degraded], not [offline] — conflating the two is what produced the
/// false `NETWORK_UNAVAILABLE` this class exists to prevent.
enum NetworkState {
  /// Nothing has been observed yet. Never used to block anything.
  unknown,

  /// A request completed successfully recently.
  online,

  /// Requests are completing slowly, or failing intermittently, but the link
  /// is demonstrably alive.
  degraded,

  /// Requests are reaching the network but the backend is not answering.
  serverUnreachable,

  /// An attempt is in flight and the previous verdict was not healthy — the UI
  /// should say "still trying" rather than showing an error.
  requestInProgress,

  /// Sufficiently established inability to reach the network. Requires either
  /// the OS reporting no interface, or repeated link-level failures.
  offline,
}

/// What the UI is allowed to show while an operation is being retried.
class ResilienceProgress {
  final String operation;
  final int attempt;
  final int maxAttempts;
  final Duration? nextRetryIn;
  final ErrorCategory? lastCategory;
  final String? correlationId;

  const ResilienceProgress({
    required this.operation,
    required this.attempt,
    required this.maxAttempts,
    this.nextRetryIn,
    this.lastCategory,
    this.correlationId,
  });

  bool get isFirstAttempt => attempt == 1 && lastCategory == null;

  /// Escalating, non-alarming copy. Red is reserved for confirmed failure.
  String get message {
    if (isFirstAttempt) return 'Loading…';
    if (lastCategory == null) return 'Working…';
    if (attempt >= 3) return 'Still trying to reach the server…';
    return lastCategory!.userMessage;
  }
}

/// Immutable view of everything the UI or sync engine needs to know.
class NetworkStatusSnapshot {
  final NetworkState state;
  final int inFlightRequests;
  final int consecutiveLinkFailures;
  final DateTime? lastSuccessAt;
  final ErrorCategory? lastFailureCategory;

  const NetworkStatusSnapshot({
    required this.state,
    required this.inFlightRequests,
    required this.consecutiveLinkFailures,
    this.lastSuccessAt,
    this.lastFailureCategory,
  });

  /// True only for a genuinely established disconnection.
  bool get isOffline => state == NetworkState.offline;

  /// True when work is likely to succeed. Note that [unknown] and [degraded]
  /// both count as "worth trying" — the request itself is the real test.
  bool get isWorthAttempting => state != NetworkState.offline;
}

/// Per-attempt context handed to the action being executed.
class RequestAttemptContext {
  final String correlationId;
  final int attempt;
  final NetworkProfile profile;

  const RequestAttemptContext({
    required this.correlationId,
    required this.attempt,
    required this.profile,
  });
}

/// Rolling counters for diagnostics. Cheap, bounded, no timers.
class NetworkMetrics {
  int attempts = 0;
  int successes = 0;
  int retries = 0;
  int deduplicatedRequests = 0;
  int terminalFailures = 0;
  final Map<String, int> failuresByCategory = <String, int>{};

  void recordCategory(ErrorCategory category) {
    failuresByCategory.update(category.code, (v) => v + 1, ifAbsent: () => 1);
  }

  Map<String, Object> toJson() => {
        'attempts': attempts,
        'successes': successes,
        'retries': retries,
        'deduplicatedRequests': deduplicatedRequests,
        'terminalFailures': terminalFailures,
        'failuresByCategory': Map<String, int>.from(failuresByCategory),
      };
}

/// The single owner of request classification, retry scheduling, backoff,
/// deduplication, correlation IDs, connectivity state and recovery signalling.
///
/// Repositories and screens call [execute]; they never implement their own
/// retry loops, and they never consult connectivity before making a request.
class NetworkResilienceManager {
  static const Uuid _uuid = Uuid();

  /// How many consecutive link-level failures (DNS/connect/reset) are required
  /// before the app is willing to call itself offline. One is never enough.
  static const int offlineEvidenceThreshold = 3;

  /// How stale the last success must be before link failures are believed.
  static const Duration offlineEvidenceWindow = Duration(seconds: 45);

  final RetryPolicy retryPolicy;
  final NetworkMetrics metrics = NetworkMetrics();

  /// Injected so tests can run backoff without real delays.
  final Future<void> Function(Duration) _sleep;
  final DateTime Function() _now;

  final _stateController = StreamController<NetworkStatusSnapshot>.broadcast();
  final _recoveryController = StreamController<NetworkState>.broadcast();
  final Map<String, Future<Object?>> _inFlight = <String, Future<Object?>>{};

  NetworkState _state = NetworkState.unknown;
  int _inFlightCount = 0;
  int _consecutiveLinkFailures = 0;
  DateTime? _lastSuccessAt;
  ErrorCategory? _lastFailureCategory;
  bool _linkReportedDown = false;

  NetworkResilienceManager({
    RetryPolicy? retryPolicy,
    Future<void> Function(Duration)? sleep,
    DateTime Function()? now,
  })  : retryPolicy = retryPolicy ?? RetryPolicy(),
        _sleep = sleep ?? Future<void>.delayed,
        _now = now ?? DateTime.now;

  // ---------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------

  NetworkStatusSnapshot get snapshot => NetworkStatusSnapshot(
        state: _state,
        inFlightRequests: _inFlightCount,
        consecutiveLinkFailures: _consecutiveLinkFailures,
        lastSuccessAt: _lastSuccessAt,
        lastFailureCategory: _lastFailureCategory,
      );

  Stream<NetworkStatusSnapshot> get stateStream => _stateController.stream;

  /// Fires when the path becomes usable again after being unhealthy. The sync
  /// engine listens here instead of polling.
  Stream<NetworkState> get recoveryStream => _recoveryController.stream;

  /// Advisory signal from the OS link layer. The *only* input allowed to move
  /// the app straight to [NetworkState.offline], because "no interface at all"
  /// is established fact rather than one failed request.
  void reportLinkStatus({required bool hasLink}) {
    _linkReportedDown = !hasLink;
    if (!hasLink) {
      _transition(NetworkState.offline);
      return;
    }
    // Link came back. Do not claim online — a real request must prove it.
    if (_state == NetworkState.offline) {
      _consecutiveLinkFailures = 0;
      _transition(NetworkState.unknown);
      _recoveryController.add(NetworkState.unknown);
    }
  }

  /// Advisory result of a lightweight health probe. Never blocks a request.
  void reportProbeResult({required bool reachable, ErrorCategory? category}) {
    if (reachable) {
      recordSuccess();
    } else if (category != null) {
      recordFailure(category);
    }
  }

  void recordSuccess() {
    _consecutiveLinkFailures = 0;
    _lastFailureCategory = null;
    _lastSuccessAt = _now();
    final wasUnhealthy = _state != NetworkState.online;
    _transition(NetworkState.online);
    if (wasUnhealthy) _recoveryController.add(NetworkState.online);
  }

  /// Folds one observed failure into the state model.
  ///
  /// A timeout or a 503 proves the network is *working* (we reached something),
  /// so it can only ever produce [NetworkState.degraded] or
  /// [NetworkState.serverUnreachable] — never [NetworkState.offline].
  void recordFailure(ErrorCategory category) {
    _lastFailureCategory = category;
    metrics.recordCategory(category);

    if (!category.isTransport) {
      // The server answered; the path is healthy even though we did not like
      // the answer.
      _lastSuccessAt = _now();
      _transition(NetworkState.online);
      return;
    }

    if (category.isOfflineEvidence) {
      _consecutiveLinkFailures++;
      if (_isOfflineEstablished()) {
        _transition(NetworkState.offline);
        return;
      }
      _transition(NetworkState.degraded);
      return;
    }

    switch (category) {
      case ErrorCategory.serverUnreachable:
        _transition(NetworkState.serverUnreachable);
      case ErrorCategory.noNetwork:
        _consecutiveLinkFailures++;
        _transition(_isOfflineEstablished()
            ? NetworkState.offline
            : NetworkState.degraded);
      default:
        _transition(NetworkState.degraded);
    }
  }

  bool _isOfflineEstablished() {
    if (_linkReportedDown) return true;
    if (_consecutiveLinkFailures < offlineEvidenceThreshold) return false;
    final last = _lastSuccessAt;
    if (last == null) return true;
    return _now().difference(last) >= offlineEvidenceWindow;
  }

  void _transition(NetworkState next) {
    if (_state == next) {
      _emit();
      return;
    }
    _state = next;
    _emit();
  }

  void _emit() {
    if (!_stateController.isClosed) _stateController.add(snapshot);
  }

  // ---------------------------------------------------------------------
  // Execution
  // ---------------------------------------------------------------------

  /// Runs [action] with centralized classification, retry, backoff and
  /// deduplication.
  ///
  /// Never pre-checks connectivity: the request itself is the authority on
  /// whether the backend is reachable. Even when the manager believes the
  /// device is offline, the attempt is still made — a stale offline belief must
  /// not be able to lock the user out.
  Future<T> execute<T>({
    required String operation,
    required NetworkProfile profile,
    required Future<T> Function(RequestAttemptContext context) action,
    String? dedupeKey,
    String method = 'GET',
    bool hasIdempotencyKey = false,
    void Function(ResilienceProgress progress)? onProgress,
  }) {
    if (dedupeKey == null || dedupeKey.isEmpty) {
      return _run<T>(
        operation: operation,
        profile: profile,
        action: action,
        method: method,
        hasIdempotencyKey: hasIdempotencyKey,
        onProgress: onProgress,
      );
    }

    final existing = _inFlight[dedupeKey];
    if (existing != null) {
      metrics.deduplicatedRequests++;
      return existing.then((value) => value as T);
    }

    final future = _run<T>(
      operation: operation,
      profile: profile,
      action: action,
      method: method,
      hasIdempotencyKey: hasIdempotencyKey,
      onProgress: onProgress,
    );

    // Store a future that never rejects into the map so a failure does not
    // produce an unhandled error for the dedup bookkeeping itself; callers
    // still receive the original (rejecting) future.
    _inFlight[dedupeKey] = future;
    future.whenComplete(() {
      if (identical(_inFlight[dedupeKey], future)) _inFlight.remove(dedupeKey);
    }).ignore();

    return future;
  }

  /// True when an identical operation is already running.
  bool isInFlight(String dedupeKey) => _inFlight.containsKey(dedupeKey);

  Future<T> _run<T>({
    required String operation,
    required NetworkProfile profile,
    required Future<T> Function(RequestAttemptContext context) action,
    required String method,
    required bool hasIdempotencyKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final correlationId = _uuid.v4();
    final startedAt = _now();
    var attempt = 0;
    AppError? lastError;

    _inFlightCount++;
    if (_state != NetworkState.online) _transition(NetworkState.requestInProgress);

    try {
      while (true) {
        attempt++;
        metrics.attempts++;

        onProgress?.call(ResilienceProgress(
          operation: operation,
          attempt: attempt,
          maxAttempts: profile.maxAttempts,
          lastCategory: lastError?.category,
          correlationId: correlationId,
        ));

        try {
          final result = await action(RequestAttemptContext(
            correlationId: correlationId,
            attempt: attempt,
            profile: profile,
          )).timeout(profile.attemptDeadline);

          metrics.successes++;
          recordSuccess();
          return result;
        } catch (raw) {
          final error = AppError.fromObject(
            raw,
            correlationId: correlationId,
            attempts: attempt,
          );
          lastError = error;
          recordFailure(error.category);

          final elapsed = _now().difference(startedAt);
          final plan = retryPolicy.evaluate(
            error: error,
            method: method,
            attempt: attempt,
            elapsed: elapsed,
            profile: profile,
            hasIdempotencyKey: hasIdempotencyKey,
          );

          if (!plan.shouldRetry) {
            metrics.terminalFailures++;
            if (kDebugMode) {
              debugPrint(
                '[NetworkResilience] $operation gave up after $attempt attempt(s): '
                '${error.code} — ${plan.reason} (correlationId: $correlationId)',
              );
            }
            throw error.copyWith(attempts: attempt, correlationId: correlationId);
          }

          metrics.retries++;
          onProgress?.call(ResilienceProgress(
            operation: operation,
            attempt: attempt,
            maxAttempts: profile.maxAttempts,
            nextRetryIn: plan.delay,
            lastCategory: error.category,
            correlationId: correlationId,
          ));

          await _sleep(plan.delay);
        }
      }
    } finally {
      _inFlightCount = _inFlightCount > 0 ? _inFlightCount - 1 : 0;
      if (_state == NetworkState.requestInProgress) {
        _transition(
          lastError == null ? NetworkState.online : NetworkState.degraded,
        );
      } else {
        _emit();
      }
    }
  }

  void dispose() {
    _stateController.close();
    _recoveryController.close();
    _inFlight.clear();
  }
}
