import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/device_compliance_service.dart';

class DeviceComplianceScreen extends ConsumerStatefulWidget {
  const DeviceComplianceScreen({super.key});

  @override
  ConsumerState<DeviceComplianceScreen> createState() => _DeviceComplianceScreenState();
}

class _DeviceComplianceScreenState extends ConsumerState<DeviceComplianceScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  List<DeviceDto> _devices = [];
  List<GeofenceDto> _geofences = [];
  DeviceTelemetryLogDto? _lastTelemetry;
  bool _isSendingTelemetry = false;

  // Mock initial device reading that will be queried/updated
  final double _currentLat = 9.0305;
  final double _currentLng = 38.7410;
  final double _currentAccuracy = 4.2; // meters
  final int _batteryLevel = 92; // percent
  final bool _gpsPermissionGranted = true;

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm:ss');

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(deviceComplianceServiceProvider);
      final devices = await service.listDevices();
      final geofences = await service.listGeofences();

      if (mounted) {
        setState(() {
          _devices = devices;
          _geofences = geofences;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _sendHeartbeatTelemetry(String deviceId) async {
    setState(() => _isSendingTelemetry = true);

    try {
      final service = ref.read(deviceComplianceServiceProvider);
      final log = await service.submitTelemetry(
        deviceId: deviceId,
        latitude: _currentLat,
        longitude: _currentLng,
        accuracy: _currentAccuracy,
        batteryLevel: _batteryLevel,
      );

      if (mounted) {
        setState(() {
          _lastTelemetry = log;
          _isSendingTelemetry = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              log.insideGeofence
                  ? 'Telemetry verified: Inside authorized fiscal geofence'
                  : 'WARNING: Device reported outside authorized geofence!',
            ),
            backgroundColor: log.insideGeofence ? AppColors.success : AppColors.error,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSendingTelemetry = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to submit device telemetry: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  void _showRegisterDeviceDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _RegisterDeviceDialog(
        geofences: _geofences,
        onSuccess: () {
          _loadData();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('mPOS device registered with fiscal hardware key.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('mPOS & Device Compliance'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, size: 48, color: AppColors.error),
                      const SizedBox(height: 16),
                      Text(_errorMessage!, style: AppTypography.bodyMedium),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: _loadData,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _buildDirectiveNoticeCard(),
                      const SizedBox(height: 16),
                      _buildCurrentGpsCard(),
                      const SizedBox(height: 16),
                      _buildGeofenceCard(),
                      const SizedBox(height: 16),
                      _buildRegisteredDevicesSection(),
                    ],
                  ),
                ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showRegisterDeviceDialog,
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.add_to_home_screen),
        label: const Text('Register Device'),
      ),
    );
  }

  Widget _buildDirectiveNoticeCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.security, color: AppColors.primary, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 — mPOS Geolocation & Device Telemetry',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'Mobile POS devices must transmit signed telemetry and prove physical operation within '
                  'the taxpayer\'s registered jurisdiction/geofence before fiscal invoice issuance is authorized. '
                  'Transactions outside authorized coordinates will be rejected by the backend fiscal gateway.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCurrentGpsCard() {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(Icons.my_location, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Text('Hardware GPS & Telemetry Sensor', style: AppTypography.titleMedium),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: _gpsPermissionGranted
                        ? AppColors.success.withValues(alpha: 0.15)
                        : AppColors.error.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _gpsPermissionGranted ? Icons.check_circle : Icons.cancel,
                        size: 14,
                        color: _gpsPermissionGranted ? AppColors.success : AppColors.error,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        _gpsPermissionGranted ? 'GPS LOCKED' : 'PERMISSION DENIED',
                        style: TextStyle(
                          color: _gpsPermissionGranted ? AppColors.success : AppColors.error,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const Divider(height: 24),
            Row(
              children: [
                Expanded(
                  child: _sensorMetric(
                    'Current Latitude',
                    _currentLat.toStringAsFixed(6),
                    Icons.explore,
                  ),
                ),
                Expanded(
                  child: _sensorMetric(
                    'Current Longitude',
                    _currentLng.toStringAsFixed(6),
                    Icons.explore_outlined,
                  ),
                ),
                Expanded(
                  child: _sensorMetric(
                    'Accuracy',
                    '±${_currentAccuracy.toStringAsFixed(1)} m',
                    Icons.gps_fixed,
                  ),
                ),
                Expanded(
                  child: _sensorMetric(
                    'Battery Status',
                    '$_batteryLevel%',
                    Icons.battery_charging_full,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_lastTelemetry != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: _lastTelemetry!.insideGeofence
                      ? AppColors.success.withValues(alpha: 0.1)
                      : AppColors.error.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Row(
                  children: [
                    Icon(
                      _lastTelemetry!.insideGeofence ? Icons.verified : Icons.warning,
                      color: _lastTelemetry!.insideGeofence ? AppColors.success : AppColors.error,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _lastTelemetry!.insideGeofence
                            ? 'Server Confirmed: Device is INSIDE authorized sales geofence. Invoices allowed.'
                            : 'Server Blocked: Device is OUTSIDE registered zone! Fiscal issuance inhibited.',
                        style: TextStyle(
                          color: _lastTelemetry!.insideGeofence ? AppColors.success : AppColors.error,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    Text(
                      _dateFormat.format(_lastTelemetry!.capturedAt),
                      style: AppTypography.caption,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
            ],
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (_devices.isNotEmpty)
                  ElevatedButton.icon(
                    onPressed: _isSendingTelemetry
                        ? null
                        : () => _sendHeartbeatTelemetry(_devices.first.id),
                    icon: _isSendingTelemetry
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.send_outlined),
                    label: const Text('Send Heartbeat & Verify Geofence'),
                    style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _sensorMetric(String label, String value, IconData icon) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, size: 14, color: AppColors.textSecondary),
            const SizedBox(width: 4),
            Text(label, style: AppTypography.caption.copyWith(color: AppColors.textSecondary)),
          ],
        ),
        const SizedBox(height: 4),
        Text(value, style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold)),
      ],
    );
  }

  Widget _buildGeofenceCard() {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.map_outlined, color: AppColors.primary),
                const SizedBox(width: 8),
                Text('Assigned Authorized Fiscal Geofences', style: AppTypography.titleMedium),
              ],
            ),
            const SizedBox(height: 12),
            if (_geofences.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  'No geofences assigned yet. Devices will default to taxpayer principal place of business.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              )
            else
              ..._geofences.map(
                (g) => Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: AppColors.divider),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(g.name, style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold)),
                          Text(
                            'Center: (${g.centerLat.toStringAsFixed(4)}, ${g.centerLng.toStringAsFixed(4)}) • Radius: ${g.radiusMeters.toStringAsFixed(0)} m',
                            style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                          ),
                        ],
                      ),
                      Chip(
                        label: Text(g.active ? 'ACTIVE' : 'INACTIVE'),
                        backgroundColor: g.active
                            ? AppColors.success.withValues(alpha: 0.1)
                            : AppColors.error.withValues(alpha: 0.1),
                        labelStyle: TextStyle(
                          color: g.active ? AppColors.success : AppColors.error,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildRegisteredDevicesSection() {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(Icons.devices, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Text('Registered Taxpayer Devices (${_devices.length})', style: AppTypography.titleMedium),
                  ],
                ),
                TextButton.icon(
                  onPressed: _showRegisterDeviceDialog,
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('Add Device'),
                ),
              ],
            ),
            const Divider(height: 24),
            if (_devices.isEmpty)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      const Icon(Icons.point_of_sale, size: 48, color: AppColors.textTertiary),
                      const SizedBox(height: 8),
                      Text('No mPOS or fiscal devices registered yet', style: AppTypography.bodyMedium),
                      const SizedBox(height: 4),
                      Text(
                        'Register this device to bind its cryptographic key and obtain authorization for sales.',
                        style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
              )
            else
              ListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _devices.length,
                itemBuilder: (ctx, index) {
                  final dev = _devices[index];
                  final isActive = dev.status.toUpperCase() == 'ACTIVE';

                  return ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: CircleAvatar(
                      backgroundColor: AppColors.primary.withValues(alpha: 0.1),
                      child: Icon(
                        dev.deviceType == 'MPOS' ? Icons.phone_android : Icons.computer,
                        color: AppColors.primary,
                      ),
                    ),
                    title: Text(
                      'Serial: ${dev.deviceSerial}',
                      style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold),
                    ),
                    subtitle: Text(
                      'Type: ${dev.deviceType} • MoR Sys#: ${dev.systemNumber ?? 'Pending'} • Public Key: ${dev.publicKey != null ? 'Provisioned (Ed25519)' : 'None'}',
                      style: AppTypography.caption,
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Chip(
                          label: Text(dev.status),
                          backgroundColor: isActive
                              ? AppColors.success.withValues(alpha: 0.15)
                              : AppColors.error.withValues(alpha: 0.15),
                          labelStyle: TextStyle(
                            color: isActive ? AppColors.success : AppColors.error,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.send),
                          tooltip: 'Send Telemetry',
                          onPressed: () => _sendHeartbeatTelemetry(dev.id),
                        ),
                      ],
                    ),
                  );
                },
              ),
          ],
        ),
      ),
    );
  }
}

class _RegisterDeviceDialog extends ConsumerStatefulWidget {
  final List<GeofenceDto> geofences;
  final VoidCallback onSuccess;

  const _RegisterDeviceDialog({
    required this.geofences,
    required this.onSuccess,
  });

  @override
  ConsumerState<_RegisterDeviceDialog> createState() => _RegisterDeviceDialogState();
}

class _RegisterDeviceDialogState extends ConsumerState<_RegisterDeviceDialog> {
  final _formKey = GlobalKey<FormState>();
  final _serialController = TextEditingController();
  final _systemNumberController = TextEditingController();
  String _deviceType = 'MPOS';
  String? _selectedGeofenceId;
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _serialController.text = 'MPOS-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}';
    if (widget.geofences.isNotEmpty) {
      _selectedGeofenceId = widget.geofences.first.id;
    }
  }

  @override
  void dispose() {
    _serialController.dispose();
    _systemNumberController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(deviceComplianceServiceProvider);
      // Simulate hardware-backed public key enrollment
      final dummyPublicKey = 'MCowBQYDK2VwAyEA${DateTime.now().millisecondsSinceEpoch}hwkey==';

      await service.registerDevice(
        deviceSerial: _serialController.text.trim(),
        deviceType: _deviceType,
        systemNumber: _systemNumberController.text.trim().isNotEmpty
            ? _systemNumberController.text.trim()
            : null,
        publicKey: dummyPublicKey,
        authorizedGeofenceId: _selectedGeofenceId,
      );

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Registration failed: $e'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Register Fiscal Device / mPOS'),
      content: SizedBox(
        width: 450,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Enroll this point-of-sale device with a cryptographic key and geofence mapping under Art. 4(4).',
                style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _serialController,
                decoration: const InputDecoration(
                  labelText: 'Hardware Serial Number *',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: _deviceType,
                decoration: const InputDecoration(
                  labelText: 'Device Form Factor',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'MPOS', child: Text('mPOS (Mobile Android/iOS/Handheld)')),
                  DropdownMenuItem(value: 'DESKTOP_POS', child: Text('Desktop POS (Windows/Linux)')),
                  DropdownMenuItem(value: 'SERVER_GATEWAY', child: Text('Backend Server Gateway')),
                ],
                onChanged: (val) {
                  if (val != null) setState(() => _deviceType = val);
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _systemNumberController,
                decoration: const InputDecoration(
                  labelText: 'Ministry System Number (Optional)',
                  hintText: 'e.g. MOR-SYS-00912',
                  border: OutlineInputBorder(),
                ),
              ),
              if (widget.geofences.isNotEmpty) ...[
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: _selectedGeofenceId,
                  decoration: const InputDecoration(
                    labelText: 'Authorized Sales Geofence',
                    border: OutlineInputBorder(),
                  ),
                  items: widget.geofences
                      .map((g) => DropdownMenuItem(value: g.id, child: Text(g.name)))
                      .toList(),
                  onChanged: (val) => setState(() => _selectedGeofenceId = val),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Enroll & Register'),
        ),
      ],
    );
  }
}
