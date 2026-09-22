/// Per-operation timeout and retry budgets.
///
/// One aggressive global timeout is wrong for Ethiopian mobile/VSAT/hotspot
/// links: a receipt render may legitimately take twenty seconds while a health
/// probe should give up in five. Profiles make the trade-off explicit and
/// configurable instead of scattering magic numbers through the app.
class NetworkProfile {
  /// Time allowed to establish the TCP connection.
  final Duration connectTimeout;

  /// Time allowed for the TLS handshake to complete once connected.
  /// Dart's HttpClient folds this into connection setup, so it is applied as a
  /// distinct budget by [ResilientHttpClient] rather than by Dio itself.
  final Duration tlsHandshakeTimeout;

  /// Time allowed to stream the request body out.
  final Duration sendTimeout;

  /// Maximum gap between two chunks of the response.
  final Duration receiveTimeout;

  /// Hard ceiling for one *attempt*, covering connect + TLS + send + receive.
  /// Prevents a request that trickles one byte per second from hanging forever.
  final Duration attemptDeadline;

  /// Hard ceiling for the whole operation including every retry. Bounded but
  /// generous — slow is not the same as broken.
  final Duration overallDeadline;

  /// Maximum number of attempts (1 = no retry).
  final int maxAttempts;

  /// First backoff step; subsequent steps grow exponentially.
  final Duration baseBackoff;

  /// Upper bound on any single backoff delay.
  final Duration maxBackoff;

  const NetworkProfile({
    required this.connectTimeout,
    required this.tlsHandshakeTimeout,
    required this.sendTimeout,
    required this.receiveTimeout,
    required this.attemptDeadline,
    required this.overallDeadline,
    required this.maxAttempts,
    this.baseBackoff = const Duration(milliseconds: 600),
    this.maxBackoff = const Duration(seconds: 20),
  });

  NetworkProfile copyWith({
    Duration? connectTimeout,
    Duration? tlsHandshakeTimeout,
    Duration? sendTimeout,
    Duration? receiveTimeout,
    Duration? attemptDeadline,
    Duration? overallDeadline,
    int? maxAttempts,
    Duration? baseBackoff,
    Duration? maxBackoff,
  }) {
    return NetworkProfile(
      connectTimeout: connectTimeout ?? this.connectTimeout,
      tlsHandshakeTimeout: tlsHandshakeTimeout ?? this.tlsHandshakeTimeout,
      sendTimeout: sendTimeout ?? this.sendTimeout,
      receiveTimeout: receiveTimeout ?? this.receiveTimeout,
      attemptDeadline: attemptDeadline ?? this.attemptDeadline,
      overallDeadline: overallDeadline ?? this.overallDeadline,
      maxAttempts: maxAttempts ?? this.maxAttempts,
      baseBackoff: baseBackoff ?? this.baseBackoff,
      maxBackoff: maxBackoff ?? this.maxBackoff,
    );
  }
}

/// The named profiles the app ships with.
///
/// Values are tuned for unstable, high-latency, bandwidth-constrained links:
/// connection setup gets a long leash because TCP+TLS over a congested mobile
/// link routinely needs 10–20s, and receive budgets are generous because the
/// server renders documents synchronously.
class NetworkProfiles {
  const NetworkProfiles._();

  /// Ordinary JSON business calls.
  static const NetworkProfile standard = NetworkProfile(
    connectTimeout: Duration(seconds: 20),
    tlsHandshakeTimeout: Duration(seconds: 20),
    sendTimeout: Duration(seconds: 30),
    receiveTimeout: Duration(seconds: 45),
    attemptDeadline: Duration(seconds: 75),
    overallDeadline: Duration(minutes: 3),
    maxAttempts: 4,
  );

  /// Receipt HTML / canonical receipt DTO. Server-side rendering plus a slow
  /// link means the response can legitimately trickle for a long time.
  static const NetworkProfile receipt = NetworkProfile(
    connectTimeout: Duration(seconds: 20),
    tlsHandshakeTimeout: Duration(seconds: 20),
    sendTimeout: Duration(seconds: 30),
    receiveTimeout: Duration(seconds: 90),
    attemptDeadline: Duration(seconds: 120),
    overallDeadline: Duration(minutes: 5),
    maxAttempts: 5,
    baseBackoff: Duration(milliseconds: 800),
  );

  /// PDF generation is the heaviest server operation (headless render).
  static const NetworkProfile document = NetworkProfile(
    connectTimeout: Duration(seconds: 20),
    tlsHandshakeTimeout: Duration(seconds: 20),
    sendTimeout: Duration(seconds: 30),
    receiveTimeout: Duration(seconds: 180),
    attemptDeadline: Duration(minutes: 4),
    overallDeadline: Duration(minutes: 10),
    maxAttempts: 4,
    baseBackoff: Duration(seconds: 1),
  );

  /// Interactive auth calls — the user is staring at a spinner, so the budget
  /// is tighter, but still far above a "fast network only" assumption.
  static const NetworkProfile authentication = NetworkProfile(
    connectTimeout: Duration(seconds: 15),
    tlsHandshakeTimeout: Duration(seconds: 15),
    sendTimeout: Duration(seconds: 20),
    receiveTimeout: Duration(seconds: 30),
    attemptDeadline: Duration(seconds: 45),
    overallDeadline: Duration(seconds: 120),
    maxAttempts: 3,
  );

  /// Background outbox drain — patient, because nobody is waiting.
  static const NetworkProfile background = NetworkProfile(
    connectTimeout: Duration(seconds: 25),
    tlsHandshakeTimeout: Duration(seconds: 25),
    sendTimeout: Duration(seconds: 60),
    receiveTimeout: Duration(seconds: 120),
    attemptDeadline: Duration(minutes: 3),
    overallDeadline: Duration(minutes: 10),
    maxAttempts: 5,
    baseBackoff: Duration(seconds: 2),
    maxBackoff: Duration(seconds: 60),
  );

  /// Health probe. Short on purpose — it is advisory telemetry, never a gate.
  static const NetworkProfile probe = NetworkProfile(
    connectTimeout: Duration(seconds: 8),
    tlsHandshakeTimeout: Duration(seconds: 8),
    sendTimeout: Duration(seconds: 8),
    receiveTimeout: Duration(seconds: 8),
    attemptDeadline: Duration(seconds: 12),
    overallDeadline: Duration(seconds: 12),
    maxAttempts: 1,
  );

  static const Map<String, NetworkProfile> byName = {
    'standard': standard,
    'receipt': receipt,
    'document': document,
    'authentication': authentication,
    'background': background,
    'probe': probe,
  };
}
