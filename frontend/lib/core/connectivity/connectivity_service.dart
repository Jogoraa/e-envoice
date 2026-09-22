import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../networking/gateway_config.dart';

enum ConnectionStatus {
  noNetwork,
  networkAvailable,
  serverUnreachable,
  authenticationRequired,
  serverAvailable,
}

/// Robust connectivity service distinguishing raw link status from true backend availability.
class ConnectivityService {
  final Connectivity _connectivity;
  final Dio _probeDio;
  final String healthEndpoint;

  String get _healthEndpoint => healthEndpoint;

  final _controller = StreamController<ConnectionStatus>.broadcast();
  ConnectionStatus _currentStatus = ConnectionStatus.networkAvailable;
  StreamSubscription? _subscription;
  Timer? _periodicProbe;

  ConnectivityService({
    Connectivity? connectivity,
    Dio? probeDio,
    this.healthEndpoint = '${GatewayConfig.activeBackendHost}/actuator/health',
    bool autoStart = true,
  })  : _connectivity = connectivity ?? Connectivity(),
        _probeDio = probeDio ??
            Dio(BaseOptions(
              connectTimeout: const Duration(seconds: 4),
              receiveTimeout: const Duration(seconds: 4),
            )) {
    if (autoStart) {
      _init();
    }
  }

  ConnectionStatus get currentStatus => _currentStatus;
  Stream<ConnectionStatus> get statusStream => _controller.stream;
  bool get isOffline =>
      _currentStatus == ConnectionStatus.noNetwork ||
      _currentStatus == ConnectionStatus.serverUnreachable;

  void _init() {
    _subscription = _connectivity.onConnectivityChanged.listen((results) {
      _checkHealth();
    });

    // Probe periodically every 30 seconds when idle
    _periodicProbe = Timer.periodic(const Duration(seconds: 30), (_) {
      _checkHealth();
    });

    _checkHealth();
  }

  Future<ConnectionStatus> checkHealth() async {
    return await _checkHealth();
  }

  Future<ConnectionStatus> _checkHealth() async {
    final connectivityResults = await _connectivity.checkConnectivity();
    if (connectivityResults.contains(ConnectivityResult.none)) {
      _updateStatus(ConnectionStatus.noNetwork);
      return ConnectionStatus.noNetwork;
    }

    try {
      final response = await _probeDio.get(_healthEndpoint);
      if (response.statusCode == 200) {
        _updateStatus(ConnectionStatus.serverAvailable);
        return ConnectionStatus.serverAvailable;
      } else if (response.statusCode == 401 || response.statusCode == 403) {
        _updateStatus(ConnectionStatus.authenticationRequired);
        return ConnectionStatus.authenticationRequired;
      } else {
        _updateStatus(ConnectionStatus.serverUnreachable);
        return ConnectionStatus.serverUnreachable;
      }
    } catch (_) {
      _updateStatus(ConnectionStatus.serverUnreachable);
      return ConnectionStatus.serverUnreachable;
    }
  }

  void _updateStatus(ConnectionStatus newStatus) {
    if (_currentStatus != newStatus) {
      _currentStatus = newStatus;
      _controller.add(newStatus);
    }
  }

  void dispose() {
    _subscription?.cancel();
    _periodicProbe?.cancel();
    _controller.close();
  }
}

final connectivityServiceProvider = Provider<ConnectivityService>((ref) {
  final service = ConnectivityService();
  ref.onDispose(() => service.dispose());
  return service;
});

final connectionStatusProvider = StreamProvider<ConnectionStatus>((ref) {
  final service = ref.watch(connectivityServiceProvider);
  return service.statusStream;
});
