import '../../../core/networking/api_client.dart';

class DeviceDto {
  final String id;
  final String tenantId;
  final String deviceSerial;
  final String deviceType; // MPOS, DESKTOP_POS, SERVER_GATEWAY
  final String? systemNumber;
  final String? publicKey;
  final String? authorizedGeofenceId;
  final String status; // ACTIVE, REVOKED, EXPIRED

  DeviceDto({
    required this.id,
    required this.tenantId,
    required this.deviceSerial,
    required this.deviceType,
    this.systemNumber,
    this.publicKey,
    this.authorizedGeofenceId,
    this.status = 'ACTIVE',
  });

  factory DeviceDto.fromJson(Map<String, dynamic> json) {
    return DeviceDto(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      deviceSerial: json['deviceSerial']?.toString() ?? '',
      deviceType: json['deviceType']?.toString() ?? 'MPOS',
      systemNumber: json['systemNumber']?.toString(),
      publicKey: json['publicKey']?.toString(),
      authorizedGeofenceId: json['authorizedGeofenceId']?.toString(),
      status: json['status']?.toString() ?? 'ACTIVE',
    );
  }
}

class GeofenceDto {
  final String id;
  final String name;
  final String description;
  final double centerLat;
  final double centerLng;
  final double radiusMeters;
  final bool active;

  GeofenceDto({
    required this.id,
    required this.name,
    required this.description,
    required this.centerLat,
    required this.centerLng,
    required this.radiusMeters,
    required this.active,
  });

  factory GeofenceDto.fromJson(Map<String, dynamic> json) {
    return GeofenceDto(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Default Authorized Zone',
      description: json['description']?.toString() ?? '',
      centerLat: (json['centerLat'] as num?)?.toDouble() ?? 9.0300,
      centerLng: (json['centerLng'] as num?)?.toDouble() ?? 38.7400,
      radiusMeters: (json['radiusMeters'] as num?)?.toDouble() ?? 5000.0,
      active: json['active'] != false,
    );
  }
}

class DeviceTelemetryLogDto {
  final String id;
  final String deviceId;
  final double latitude;
  final double longitude;
  final double accuracy;
  final int batteryLevel;
  final bool insideGeofence;
  final DateTime capturedAt;

  DeviceTelemetryLogDto({
    required this.id,
    required this.deviceId,
    required this.latitude,
    required this.longitude,
    required this.accuracy,
    required this.batteryLevel,
    required this.insideGeofence,
    required this.capturedAt,
  });

  factory DeviceTelemetryLogDto.fromJson(Map<String, dynamic> json) {
    return DeviceTelemetryLogDto(
      id: json['id']?.toString() ?? '',
      deviceId: json['deviceId']?.toString() ?? '',
      latitude: (json['latitude'] as num?)?.toDouble() ?? 0.0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 0.0,
      accuracy: (json['accuracy'] as num?)?.toDouble() ?? 0.0,
      batteryLevel: (json['batteryLevel'] as num?)?.toInt() ?? 100,
      insideGeofence: json['insideGeofence'] != false,
      capturedAt: json['capturedAt'] != null
          ? DateTime.tryParse(json['capturedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class DeviceComplianceService {
  final ApiClient apiClient;

  DeviceComplianceService(this.apiClient);

  Future<DeviceDto> registerDevice({
    required String deviceSerial,
    String deviceType = 'MPOS',
    String? systemNumber,
    String? publicKey,
    String? authorizedGeofenceId,
  }) async {
    final response = await apiClient.post('/api/v1/devices', data: {
      'deviceSerial': deviceSerial,
      'deviceType': deviceType,
      if (systemNumber != null) 'systemNumber': systemNumber,
      if (publicKey != null) 'publicKey': publicKey,
      if (authorizedGeofenceId != null) 'authorizedGeofenceId': authorizedGeofenceId,
    });
    return DeviceDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<DeviceDto>> listDevices() async {
    final response = await apiClient.get('/api/v1/devices');
    return (response.data as List<dynamic>? ?? [])
        .map((d) => DeviceDto.fromJson(d as Map<String, dynamic>))
        .toList();
  }

  Future<DeviceTelemetryLogDto> submitTelemetry({
    required String deviceId,
    required double latitude,
    required double longitude,
    required double accuracy,
    required int batteryLevel,
  }) async {
    final response = await apiClient.post('/api/v1/devices/$deviceId/telemetry', data: {
      'latitude': latitude,
      'longitude': longitude,
      'accuracy': accuracy,
      'batteryLevel': batteryLevel,
      'capturedAt': DateTime.now().toIso8601String(),
    });
    return DeviceTelemetryLogDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<GeofenceDto>> listGeofences() async {
    final response = await apiClient.get('/api/v1/devices/geofences');
    return (response.data as List<dynamic>? ?? [])
        .map((g) => GeofenceDto.fromJson(g as Map<String, dynamic>))
        .toList();
  }
}
