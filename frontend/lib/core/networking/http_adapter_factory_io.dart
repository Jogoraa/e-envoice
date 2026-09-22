import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';

import 'network_profile.dart';

/// Builds a pooled, keep-alive HTTP adapter for the dart:io platforms
/// (Windows/macOS/Linux desktop, Android, iOS).
///
/// `IOHttpClientAdapter` caches the [HttpClient] it creates, so one client is
/// reused for every request through this gateway and TCP/TLS sessions stay
/// warm. Dio's built-in default sets `idleTimeout` to just 3 seconds, which
/// tears down keep-alive connections between two consecutive user actions — on
/// a high-latency link the re-handshake then costs more than the payload.
HttpClientAdapter buildAdapter(NetworkProfile profile) {
  return IOHttpClientAdapter(
    createHttpClient: () {
      return HttpClient()
        // Socket-level floor so a black-holed SYN cannot hang forever. Dio
        // overwrites this per request from the effective connectTimeout.
        ..connectionTimeout = profile.connectTimeout
        // Keep sockets warm across a user paging through invoices.
        ..idleTimeout = const Duration(seconds: 30)
        // Bounded pool: enough for the UI plus a background sync drain, low
        // enough that a stalled network cannot spawn unbounded sockets.
        ..maxConnectionsPerHost = 8
        ..autoUncompress = true
        ..userAgent = 'UT-EInvoice-Client/1.0';
    },
  );
}
