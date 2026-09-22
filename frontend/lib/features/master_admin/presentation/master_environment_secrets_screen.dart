import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterEnvironmentSecretsScreen extends ConsumerStatefulWidget {
  const MasterEnvironmentSecretsScreen({super.key});

  @override
  ConsumerState<MasterEnvironmentSecretsScreen> createState() =>
      _MasterEnvironmentSecretsScreenState();
}

class _MasterEnvironmentSecretsScreenState
    extends ConsumerState<MasterEnvironmentSecretsScreen> {
  bool _isLoading = false;
  String? _errorMessage;

  // Privileged Session State
  String? _privilegedToken;
  DateTime? _privilegedExpiresAt;
  Timer? _sessionCountdownTimer;
  int _secondsRemaining = 0;

  // Configuration Data
  List<Map<String, dynamic>> _configurations = [];
  Map<String, dynamic>? _healthData;
  List<Map<String, dynamic>> _revisions = [];
  int _activeRevisionNumber = 1;

  // Staged Edits
  final Map<String, String> _stagedChanges = {};
  String _selectedScope = 'ALL';
  String _searchQuery = '';

  // Messaging Delivery Diagnostics
  Map<String, dynamic>? _emailDiagStatus;
  Map<String, dynamic>? _smsDiagStatus;
  bool _isSendingTestEmail = false;
  bool _isSendingTestSms = false;
  String? _testEmailResult;
  String? _testSmsResult;

  // Step-Up Form Controllers
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _mfaCodeController = TextEditingController();
  bool _isStepUpLoading = false;
  String? _stepUpError;

  // Step-Up MFA Method Selection ('EMAIL_SMS' or 'AUTHENTICATOR_APP')
  String _selectedMfaMethod = 'EMAIL_SMS';
  bool _isSendingOtp = false;
  String? _otpSentSuccessMessage;
  Timer? _otpCooldownTimer;
  int _otpCooldownSeconds = 0;

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _sessionCountdownTimer?.cancel();
    _otpCooldownTimer?.cancel();
    _passwordController.dispose();
    _mfaCodeController.dispose();
    super.dispose();
  }

  Future<void> _requestOtpCode() async {
    if (_otpCooldownSeconds > 0 || _isSendingOtp) return;

    setState(() {
      _isSendingOtp = true;
      _stepUpError = null;
      _otpSentSuccessMessage = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.post('/api/v1/master/environment/send-otp');

      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        final cooldown = (data['cooldownSeconds'] as num?)?.toInt() ?? 60;
        final maskedEmail = data['maskedEmail']?.toString() ?? 'registered email';
        final maskedPhone = data['maskedPhone']?.toString() ?? 'registered phone';

        setState(() {
          _isSendingOtp = false;
          _otpSentSuccessMessage =
              'Verification code sent to $maskedEmail and $maskedPhone. Valid for 5 minutes.';
          _otpCooldownSeconds = cooldown;
        });

        _otpCooldownTimer?.cancel();
        _otpCooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
          if (!mounted) return;
          if (_otpCooldownSeconds <= 1) {
            timer.cancel();
            setState(() {
              _otpCooldownSeconds = 0;
            });
          } else {
            setState(() {
              _otpCooldownSeconds--;
            });
          }
        });
      }
    } catch (e) {
      setState(() {
        _isSendingOtp = false;
        _stepUpError =
            'Failed to dispatch verification code via SMS/Email. Please use Authenticator App or retry.';
      });
    }
  }

  void _startCountdown(int durationSeconds) {
    _sessionCountdownTimer?.cancel();
    setState(() {
      _secondsRemaining = durationSeconds;
    });
    _sessionCountdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      if (_secondsRemaining <= 1) {
        timer.cancel();
        setState(() {
          _privilegedToken = null;
          _secondsRemaining = 0;
          _errorMessage =
              'Privileged session expired. Please complete step-up authentication again.';
        });
      } else {
        setState(() {
          _secondsRemaining--;
        });
      }
    });
  }

  Future<void> _performStepUp() async {
    final password = _passwordController.text.trim();
    final mfaCode = _mfaCodeController.text.trim();

    if (password.isEmpty || mfaCode.isEmpty) {
      setState(() {
        _stepUpError = 'Both password and 6-digit MFA code are required.';
      });
      return;
    }

    setState(() {
      _isStepUpLoading = true;
      _stepUpError = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.post(
        '/api/v1/master/environment/step-up',
        data: {'password': password, 'mfaCode': mfaCode},
      );

      if (response.data is Map) {
        final data = response.data as Map<String, dynamic>;
        final token = data['privilegedToken']?.toString();
        final duration = (data['durationSeconds'] as num?)?.toInt() ?? 900;
        final expiresAtStr = data['expiresAt']?.toString();

        setState(() {
          _privilegedToken = token;
          _privilegedExpiresAt = expiresAtStr != null
              ? DateTime.tryParse(expiresAtStr)
              : DateTime.now().add(Duration(seconds: duration));
          _isStepUpLoading = false;
          _passwordController.clear();
          _mfaCodeController.clear();
        });

        _startCountdown(duration);
        await _fetchEnvironmentData();
      }
    } catch (e) {
      setState(() {
        _isStepUpLoading = false;
        _stepUpError =
            'Step-up verification failed. Please verify your credentials and MFA code.';
      });
    }
  }

  Future<void> _revokeSession() async {
    if (_privilegedToken == null) return;
    try {
      final client = ref.read(masterAdminApiClientProvider);
      await client.post(
        '/api/v1/master/environment/step-up/revoke',
        options: _buildDioOptions(),
      );
    } catch (_) {}

    _sessionCountdownTimer?.cancel();
    setState(() {
      _privilegedToken = null;
      _secondsRemaining = 0;
      _configurations.clear();
      _stagedChanges.clear();
    });
  }

  Map<String, String> _getPrivilegedHeaders() {
    final token = _privilegedToken;
    return token != null ? {'X-Privileged-Token': token} : {};
  }

  Options _buildDioOptions([Map<String, String>? extraHeaders]) {
    final headers = extraHeaders ?? _getPrivilegedHeaders();
    return Options(headers: headers);
  }

  Future<void> _fetchEnvironmentData() async {
    if (_privilegedToken == null) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);

      final configRes = await client.get(
        '/api/v1/master/environment',
        options: _buildDioOptions(),
      );
      final healthRes = await client.get(
        '/api/v1/master/environment/health',
        options: _buildDioOptions(),
      );
      final revRes = await client.get(
        '/api/v1/master/environment/revisions',
        options: _buildDioOptions(),
      );

      // Safe Diagnostics: Email & SMS delivery providers
      try {
        final emailStatusRes = await client.get(
          '/api/v1/master/environment/email/status',
          options: _buildDioOptions(),
        );
        if (emailStatusRes.data is Map) {
          _emailDiagStatus = Map<String, dynamic>.from(emailStatusRes.data);
        }
      } catch (_) {}

      try {
        final smsStatusRes = await client.get(
          '/api/v1/master/environment/sms/status',
          options: _buildDioOptions(),
        );
        if (smsStatusRes.data is Map) {
          _smsDiagStatus = Map<String, dynamic>.from(smsStatusRes.data);
        }
      } catch (_) {}

      setState(() {
        if (configRes.data is List) {
          _configurations = List<Map<String, dynamic>>.from(configRes.data);
        }
        if (healthRes.data is Map) {
          _healthData = Map<String, dynamic>.from(healthRes.data);
          _activeRevisionNumber =
              (_healthData?['currentRevision'] as num?)?.toInt() ?? 1;
        }
        if (revRes.data is List) {
          _revisions = List<Map<String, dynamic>>.from(revRes.data);
        }
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = 'Failed to load authoritative environment data: $e';
      });
    }
  }

  Future<void> _sendTestEmail() async {
    setState(() {
      _isSendingTestEmail = true;
      _testEmailResult = null;
    });
    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.post(
        '/api/v1/master/environment/email/test-send',
        options: _buildDioOptions(),
      );
      final data = res.data as Map<String, dynamic>;
      final success = data['success'] == true;
      final status = data['status']?.toString() ?? 'UNKNOWN';
      final msgId = data['messageId']?.toString() ?? 'N/A';
      final details = data['details']?.toString() ?? '';
      final recipient = data['recipientEmail']?.toString() ?? '';

      setState(() {
        _isSendingTestEmail = false;
        _testEmailResult = success
            ? 'Success: Accepted by mail server ($status). Message ID: $msgId to $recipient.'
            : 'Result: $status - $details';
      });
      _fetchEnvironmentData();
    } catch (e) {
      setState(() {
        _isSendingTestEmail = false;
        _testEmailResult = 'Test Send Failed: $e';
      });
    }
  }

  Future<void> _sendTestSms() async {
    setState(() {
      _isSendingTestSms = true;
      _testSmsResult = null;
    });
    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.post(
        '/api/v1/master/environment/sms/test-send',
        options: _buildDioOptions(),
      );
      final data = res.data as Map<String, dynamic>;
      final success = data['success'] == true;
      final status = data['status']?.toString() ?? 'UNKNOWN';
      final msgId = data['messageId']?.toString() ?? 'N/A';
      final details = data['details']?.toString() ?? '';
      final recipient = data['recipientPhone']?.toString() ?? '';

      setState(() {
        _isSendingTestSms = false;
        _testSmsResult = success
            ? 'Success: $status. Message ID: $msgId to $recipient.'
            : 'Result: $status - $details';
      });
      _fetchEnvironmentData();
    } catch (e) {
      setState(() {
        _isSendingTestSms = false;
        _testSmsResult = 'Test Send Failed: $e';
      });
    }
  }

  Future<void> _commitStagedChanges() async {
    if (_stagedChanges.isEmpty) return;

    final client = ref.read(masterAdminApiClientProvider);
    try {
      final response = await client.put(
        '/api/v1/master/environment/configuration',
        data: {
          'expectedRevisionNumber': _activeRevisionNumber,
          'configurations': _stagedChanges,
          'changeSummary': 'Master Admin configuration updates',
        },
        options: _buildDioOptions(),
      );

      if (response.statusCode != null &&
          response.statusCode! >= 200 &&
          response.statusCode! < 300) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Configuration committed successfully!'),
              backgroundColor: AppColors.green700,
            ),
          );
        }
        _stagedChanges.clear();
        await _fetchEnvironmentData();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to commit configuration: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  void _showRotateSecretDialog(String keyName) {
    final secretController = TextEditingController();
    final reasonController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Icon(Icons.key, color: AppColors.amber700, size: 22),
            const SizedBox(width: 8),
            Text('Rotate Secret: $keyName', style: AppTypography.h2()),
          ],
        ),
        content: SizedBox(
          width: 480,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.amber600.withValues(alpha: 0.1),
                  border: Border.all(color: AppColors.amber600),
                  borderRadius: BorderRadius.circular(3),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.warning_amber_rounded,
                      color: AppColors.amber700,
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'This will encrypt the new secret using AES-256-GCM and immediately rotate the runtime credential. The previous secret will be permanently archived.',
                        style: AppTypography.bodySmall(),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'New Plaintext Secret Value',
                style: AppTypography.uiLabelBold(),
              ),
              const SizedBox(height: 6),
              TextField(
                controller: secretController,
                obscureText: true,
                decoration: const InputDecoration(
                  hintText: 'Enter new high-entropy secret...',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.lock_outline, size: 18),
                ),
              ),
              const SizedBox(height: 12),
              Text('Rotation Reason', style: AppTypography.uiLabelBold()),
              const SizedBox(height: 6),
              TextField(
                controller: reasonController,
                decoration: const InputDecoration(
                  hintText: 'e.g. Scheduled quarterly key rotation',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.navy900,
              foregroundColor: Colors.white,
            ),
            onPressed: () async {
              final newSecret = secretController.text.trim();
              final reason = reasonController.text.trim();
              if (newSecret.isEmpty) return;

              Navigator.of(ctx).pop();
              try {
                final client = ref.read(masterAdminApiClientProvider);
                await client.post(
                  '/api/v1/master/environment/secrets/rotate',
                  data: {
                    'keyName': keyName,
                    'newSecret': newSecret,
                    'reason': reason,
                  },
                  options: _buildDioOptions(),
                );
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Secret for $keyName rotated successfully!'),
                      backgroundColor: AppColors.green700,
                    ),
                  );
                }
                await _fetchEnvironmentData();
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Secret rotation failed: $e'),
                      backgroundColor: AppColors.red600,
                    ),
                  );
                }
              }
            },
            child: const Text('Confirm Rotation'),
          ),
        ],
      ),
    );
  }

  void _showRollbackDialog(int targetRev) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Rollback to Revision #$targetRev', style: AppTypography.h2()),
        content: Text(
          'Are you sure you want to rollback non-secret configuration variables to revision #$targetRev? This action will generate a new audit revision.',
          style: AppTypography.body(),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.navy900,
              foregroundColor: Colors.white,
            ),
            onPressed: () async {
              Navigator.of(ctx).pop();
              try {
                final client = ref.read(masterAdminApiClientProvider);
                await client.post(
                  '/api/v1/master/environment/revisions/$targetRev/rollback',
                  options: _buildDioOptions(),
                );
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        'Rolled back configuration to Revision #$targetRev',
                      ),
                      backgroundColor: AppColors.green700,
                    ),
                  );
                }
                await _fetchEnvironmentData();
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Rollback failed: $e'),
                      backgroundColor: AppColors.red600,
                    ),
                  );
                }
              }
            },
            child: const Text('Execute Rollback'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_privilegedToken == null) {
      return _buildStepUpChallengeView();
    }

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Column(
        children: [
          _buildPrivilegedSessionHeader(),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : RefreshIndicator(
                    onRefresh: _fetchEnvironmentData,
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildHeaderActions(),
                          if (_errorMessage != null) ...[
                            const SizedBox(height: 16),
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: AppColors.red600.withValues(alpha: 0.1),
                                border: Border.all(color: AppColors.red600),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.error_outline, color: AppColors.red600, size: 18),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      _errorMessage!,
                                      style: AppTypography.bodySmall(color: AppColors.red700),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                          const SizedBox(height: 20),
                          _buildHealthCardsGrid(),
                          const SizedBox(height: 24),
                          _buildEmergencyKillSwitchesSection(),
                          const SizedBox(height: 24),
                          _buildMessagingDeliveryDiagnosticsSection(),
                          const SizedBox(height: 24),
                          _buildScopeFilterAndSearch(),
                          const SizedBox(height: 16),
                          _buildConfigurationEntriesList(),
                          const SizedBox(height: 24),
                          _buildRevisionsHistorySection(),
                        ],
                      ),
                    ),
                  ),
          ),
          if (_stagedChanges.isNotEmpty) _buildStickyActionBar(),
        ],
      ),
    );
  }

  Widget _buildStepUpChallengeView() {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Center(
        child: Container(
          constraints: const BoxConstraints(maxWidth: 480),
          margin: const EdgeInsets.all(24),
          padding: const EdgeInsets.all(32),
          decoration: BoxDecoration(
            color: AppColors.paperRaised,
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: AppColors.rule),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.05),
                blurRadius: 16,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.navy900.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Icon(
                      Icons.shield_outlined,
                      color: AppColors.navy900,
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('PRIVILEGED ACCESS REQUIRED', style: AppTypography.h2()),
                        Text(
                          'Step-Up Multi-Factor Authentication',
                          style: AppTypography.bodySmall(color: AppColors.inkMuted),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Text(
                'Managing platform environment parameters, database connections, and cryptographic secrets requires an elevated, short-lived privileged session.',
                style: AppTypography.body(color: AppColors.ink),
              ),
              if (_stepUpError != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.red600.withValues(alpha: 0.1),
                    border: Border.all(color: AppColors.red600),
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Text(
                    _stepUpError!,
                    style: AppTypography.bodySmall(color: AppColors.red700),
                  ),
                ),
              ],
              const SizedBox(height: 20),
              Text('Master Admin Password', style: AppTypography.uiLabelBold()),
              const SizedBox(height: 6),
              TextField(
                controller: _passwordController,
                obscureText: true,
                decoration: const InputDecoration(
                  hintText: 'Re-enter your master account password',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.password_outlined, size: 18),
                ),
              ),
              const SizedBox(height: 20),
              Text('Multi-Factor Authentication Method', style: AppTypography.uiLabelBold()),
              const SizedBox(height: 8),
              // Segmented Tab Selector: Email & SMS vs Authenticator App
              Container(
                decoration: BoxDecoration(
                  color: AppColors.paperMuted,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppColors.rule),
                ),
                padding: const EdgeInsets.all(3),
                child: Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: () {
                          setState(() {
                            _selectedMfaMethod = 'EMAIL_SMS';
                          });
                        },
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          decoration: BoxDecoration(
                            color: _selectedMfaMethod == 'EMAIL_SMS'
                                ? Colors.white
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(3),
                            boxShadow: _selectedMfaMethod == 'EMAIL_SMS'
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withValues(alpha: 0.05),
                                      blurRadius: 2,
                                      offset: const Offset(0, 1),
                                    )
                                  ]
                                : null,
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.mark_email_read_outlined,
                                size: 16,
                                color: _selectedMfaMethod == 'EMAIL_SMS'
                                    ? AppColors.navy900
                                    : AppColors.inkMuted,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'Email & SMS OTP',
                                style: AppTypography.bodySmall(
                                  color: _selectedMfaMethod == 'EMAIL_SMS'
                                      ? AppColors.navy900
                                      : AppColors.inkMuted,
                                ).copyWith(
                                  fontWeight: _selectedMfaMethod == 'EMAIL_SMS'
                                      ? FontWeight.w600
                                      : FontWeight.w400,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: InkWell(
                        onTap: () {
                          setState(() {
                            _selectedMfaMethod = 'AUTHENTICATOR_APP';
                          });
                        },
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          decoration: BoxDecoration(
                            color: _selectedMfaMethod == 'AUTHENTICATOR_APP'
                                ? Colors.white
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(3),
                            boxShadow: _selectedMfaMethod == 'AUTHENTICATOR_APP'
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withValues(alpha: 0.05),
                                      blurRadius: 2,
                                      offset: const Offset(0, 1),
                                    )
                                  ]
                                : null,
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.phonelink_lock_outlined,
                                size: 16,
                                color: _selectedMfaMethod == 'AUTHENTICATOR_APP'
                                    ? AppColors.navy900
                                    : AppColors.inkMuted,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'Authenticator App (TOTP)',
                                style: AppTypography.bodySmall(
                                  color: _selectedMfaMethod == 'AUTHENTICATOR_APP'
                                      ? AppColors.navy900
                                      : AppColors.inkMuted,
                                ).copyWith(
                                  fontWeight: _selectedMfaMethod == 'AUTHENTICATOR_APP'
                                      ? FontWeight.w600
                                      : FontWeight.w400,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              if (_selectedMfaMethod == 'EMAIL_SMS') ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.paperMuted,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.rule),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.send_to_mobile, size: 16, color: AppColors.navy900),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Out-of-band delivery to registered email & SMS',
                              style: AppTypography.bodySmall(color: AppColors.ink),
                            ),
                          ),
                          const SizedBox(width: 8),
                          OutlinedButton.icon(
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppColors.navy900,
                              side: const BorderSide(color: AppColors.navy900),
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            ),
                            onPressed: (_isSendingOtp || _otpCooldownSeconds > 0)
                                ? null
                                : _requestOtpCode,
                            icon: _isSendingOtp
                                ? const SizedBox(
                                    width: 12,
                                    height: 12,
                                    child: CircularProgressIndicator(strokeWidth: 2),
                                  )
                                : const Icon(Icons.send_rounded, size: 14),
                            label: Text(
                              _isSendingOtp
                                  ? 'Sending...'
                                  : _otpCooldownSeconds > 0
                                      ? 'Resend in ${_otpCooldownSeconds}s'
                                      : 'Send Code',
                              style: AppTypography.bodySmall().copyWith(fontWeight: FontWeight.w600),
                            ),
                          ),
                        ],
                      ),
                      if (_otpSentSuccessMessage != null) ...[
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            const Icon(Icons.check_circle_outline, size: 14, color: AppColors.green700),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                _otpSentSuccessMessage!,
                                style: AppTypography.bodySmall(color: AppColors.green700),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ] else ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.blue600.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppColors.blue600.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.shield_outlined, size: 18, color: AppColors.blue600),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Open your enrolled Authenticator App (Google Authenticator, Microsoft Authenticator, or hardware token) and enter your current 6-digit TOTP code below.',
                          style: AppTypography.bodySmall(color: AppColors.ink),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 16),
              Text(
                _selectedMfaMethod == 'EMAIL_SMS'
                    ? '6-Digit SMS / Email Verification Code'
                    : '6-Digit Authenticator (TOTP) Code',
                style: AppTypography.uiLabelBold(),
              ),
              const SizedBox(height: 6),
              TextField(
                controller: _mfaCodeController,
                keyboardType: TextInputType.number,
                maxLength: 6,
                decoration: InputDecoration(
                  hintText: _selectedMfaMethod == 'EMAIL_SMS'
                      ? 'e.g. 6-digit code received via SMS/Email'
                      : 'e.g. 123456 from Authenticator App',
                  counterText: '',
                  border: const OutlineInputBorder(),
                  prefixIcon: const Icon(Icons.security, size: 18),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy900,
                    foregroundColor: Colors.white,
                  ),
                  onPressed: _isStepUpLoading ? null : _performStepUp,
                  icon: _isStepUpLoading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.lock_open_outlined, size: 18),
                  label: Text(
                    _isStepUpLoading
                        ? 'Verifying Ceremony...'
                        : 'Unlock Environment & Secrets',
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPrivilegedSessionHeader() {
    final minutes = _secondsRemaining ~/ 60;
    final seconds = _secondsRemaining % 60;
    final formattedTime =
        '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
      decoration: const BoxDecoration(
        color: AppColors.navy900,
        border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
      ),
      child: Row(
        children: [
          const Icon(Icons.lock_clock, color: Colors.white, size: 18),
          const SizedBox(width: 8),
          Text(
            'PRIVILEGED CONFIGURATION SESSION ACTIVE',
            style: AppTypography.monoSmall(color: Colors.white, weight: FontWeight.w700),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(3),
            ),
            child: Row(
              children: [
                const Icon(Icons.timer_outlined, color: Colors.white, size: 14),
                const SizedBox(width: 6),
                Text(
                  _privilegedExpiresAt != null
                      ? 'Expires in $formattedTime (Until ${_privilegedExpiresAt!.toIso8601String().substring(11, 19)}Z)'
                      : 'Expires in $formattedTime',
                  style: AppTypography.monoSmall(
                    color: Colors.white,
                    weight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.white,
              side: const BorderSide(color: Colors.white54),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            ),
            onPressed: _revokeSession,
            icon: const Icon(Icons.lock, size: 14),
            label: const Text('Lock Session'),
          ),
        ],
      ),
    );
  }

  Widget _buildHeaderActions() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('ENVIRONMENT & SECRETS MANAGEMENT', style: AppTypography.h1()),
            const SizedBox(height: 4),
            Text(
              'Authoritative production configuration registry and hardware-encrypted secret store',
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
          ],
        ),
        Row(
          children: [
            ElevatedButton.icon(
              onPressed: _fetchEnvironmentData,
              icon: const Icon(Icons.refresh, size: 16),
              label: const Text('Refresh Status'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildHealthCardsGrid() {
    final health = _healthData;
    if (health == null) return const SizedBox.shrink();

    return LayoutBuilder(
      builder: (context, constraints) {
        final crossAxisCount = constraints.maxWidth > 1100
            ? 4
            : (constraints.maxWidth > 650 ? 2 : 1);

        return GridView.count(
          crossAxisCount: crossAxisCount,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 2.2,
          children: [
            _buildHealthTile(
              'Application Core',
              health['applicationStatus']?.toString() ?? 'Valid',
              Icons.computer,
              AppColors.green700,
            ),
            _buildHealthTile(
              'Database Cluster',
              health['databaseStatus']?.toString() ?? 'Connected',
              Icons.storage,
              AppColors.green700,
            ),
            _buildHealthTile(
              'Ministry of Revenues EIRS',
              health['eirsStatus']?.toString() ?? 'Configured',
              Icons.account_balance,
              (health['eirsKillSwitchActive'] == true)
                  ? AppColors.red600
                  : AppColors.green700,
            ),
            _buildHealthTile(
              'SMS Gateway (GeezSMS)',
              '🔒 MOCK ONLY (Live Blocked)',
              Icons.sms,
              AppColors.amber700,
              badge: 'STATUTORY LOCK',
            ),
          ],
        );
      },
    );
  }

  Widget _buildHealthTile(
    String title,
    String status,
    IconData icon,
    Color statusColor, {
    String? badge,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Icon(icon, color: statusColor, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  title,
                  style: AppTypography.uiLabelBold(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  status,
                  style: AppTypography.monoSmall(color: statusColor),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                if (badge != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    badge,
                    style: AppTypography.monoSmall(
                      color: AppColors.amber700,
                      weight: FontWeight.w700,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmergencyKillSwitchesSection() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.flash_on, color: AppColors.red600, size: 18),
              const SizedBox(width: 8),
              Text(
                'EMERGENCY INTEGRATION KILL SWITCHES',
                style: AppTypography.h2(color: AppColors.navy900),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 24,
            runSpacing: 12,
            children: [
              _buildKillSwitchItem(
                'SMS Outbox Dispatch',
                'SMS_ENABLED',
                'Instantly queues SMS notifications without dispatching',
              ),
              _buildKillSwitchItem(
                'MoR EIRS Ingress Dispatch',
                'MOR_INTEGRATION_ENABLED',
                'Defers live clearance to local immutable buffer',
              ),
              _buildKillSwitchItem(
                'Email Delivery Dispatch',
                'EMAIL_DELIVERY_ENABLED',
                'Suspends transactional receipt email dispatches',
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildKillSwitchItem(String label, String key, String subtitle) {
    final entry = _configurations.firstWhere(
      (c) => c['keyName'] == key,
      orElse: () => {'currentValue': 'true'},
    );
    final isEnabled =
        _stagedChanges.containsKey(key)
            ? _stagedChanges[key] == 'true'
            : (entry['currentValue'] == 'true');

    return Container(
      width: 320,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.paper,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: AppTypography.uiLabelBold()),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
          Switch(
            value: isEnabled,
            activeColor: AppColors.green700,
            inactiveThumbColor: AppColors.red600,
            onChanged: (val) {
              setState(() {
                _stagedChanges[key] = val.toString();
              });
            },
          ),
        ],
      ),
    );
  }

  Widget _buildMessagingDeliveryDiagnosticsSection() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.mark_email_read_outlined,
                color: AppColors.navy900,
                size: 20,
              ),
              const SizedBox(width: 8),
              Text(
                'MESSAGING & NOTIFICATION DELIVERY DIAGNOSTICS',
                style: AppTypography.h2(color: AppColors.navy900),
              ),
              const Spacer(),
              Text(
                'RFC 5321 Pure SMTP Engine & Authoritative SMS Gateway',
                style: AppTypography.monoSmall(color: AppColors.inkMuted),
              ),
            ],
          ),
          const SizedBox(height: 16),
          LayoutBuilder(
            builder: (context, constraints) {
              final isWide = constraints.maxWidth > 900;
              final emailCard = _buildEmailProviderCard();
              final smsCard = _buildSmsProviderCard();

              if (isWide) {
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: emailCard),
                    const SizedBox(width: 16),
                    Expanded(child: smsCard),
                  ],
                );
              } else {
                return Column(
                  children: [
                    emailCard,
                    const SizedBox(height: 16),
                    smsCard,
                  ],
                );
              }
            },
          ),
        ],
      ),
    );
  }

  Widget _buildEmailProviderCard() {
    final status = _emailDiagStatus?['status']?.toString() ?? 'NOT_CONFIGURED';
    final host = _emailDiagStatus?['host']?.toString() ?? 'Not set';
    final port = _emailDiagStatus?['port']?.toString() ?? '587';
    final username = _emailDiagStatus?['username']?.toString() ?? 'Not set';
    final fromAddress =
        _emailDiagStatus?['fromAddress']?.toString() ?? 'Not set';
    final probe = _emailDiagStatus?['probeMessage']?.toString() ?? 'No probe run';

    Color statusColor = AppColors.amber700;
    if (status == 'CONFIGURED' || status == 'ACCEPTED') {
      statusColor = AppColors.green700;
    } else if (status.contains('FAILED') || status.contains('REJECTED')) {
      statusColor = AppColors.red600;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paper,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.email_outlined, color: AppColors.navy900, size: 18),
              const SizedBox(width: 8),
              Text('Email Delivery Provider (SMTP)', style: AppTypography.uiLabelBold()),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                ),
                child: Text(
                  status,
                  style: AppTypography.monoSmall(
                    color: statusColor,
                    weight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            'Host: $host | Port: $port | User: $username',
            style: AppTypography.monoSmall(color: AppColors.ink),
          ),
          const SizedBox(height: 4),
          Text(
            'From: $fromAddress | Probe: $probe',
            style: AppTypography.monoSmall(color: AppColors.inkMuted),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.paperRaised,
              borderRadius: BorderRadius.circular(3),
            ),
            child: Text(
              'Required keys: SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_FROM. Configure in server-side secret store or Docker environment. Passwords/credentials are never exposed to the frontend.',
              style: AppTypography.monoSmall(color: AppColors.inkMuted),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              ElevatedButton.icon(
                onPressed: _isSendingTestEmail ? null : _sendTestEmail,
                icon: const Icon(Icons.send_outlined, size: 14),
                label: Text(
                  _isSendingTestEmail ? 'Testing SMTP...' : 'Send Test Email',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Destination: Authenticated Master Admin verified email only (prevents open relay).',
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ),
            ],
          ),
          if (_testEmailResult != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: _testEmailResult!.startsWith('Success')
                    ? AppColors.green700.withValues(alpha: 0.1)
                    : AppColors.red600.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(3),
                border: Border.all(
                  color: _testEmailResult!.startsWith('Success')
                      ? AppColors.green700
                      : AppColors.red600,
                ),
              ),
              child: Text(
                _testEmailResult!,
                style: AppTypography.monoSmall(
                  color: _testEmailResult!.startsWith('Success')
                      ? AppColors.green700
                      : AppColors.red700,
                  weight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildSmsProviderCard() {
    final status = _smsDiagStatus?['status']?.toString() ?? 'BLOCKED_BY_POLICY';
    final providerName =
        _smsDiagStatus?['providerName']?.toString() ?? 'MockGeezSmsProvider';
    final statusReason =
        _smsDiagStatus?['statusReason']?.toString() ??
        'Statutory safety policy active';

    Color statusColor = AppColors.amber700;
    if (status == 'CONFIGURED' || status == 'SUBMITTED') {
      statusColor = AppColors.green700;
    } else if (status.contains('FAILED') || status.contains('REJECTED')) {
      statusColor = AppColors.red600;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paper,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.sms_outlined, color: AppColors.navy900, size: 18),
              const SizedBox(width: 8),
              Text('SMS Gateway Provider', style: AppTypography.uiLabelBold()),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                ),
                child: Text(
                  status,
                  style: AppTypography.monoSmall(
                    color: statusColor,
                    weight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            'Provider: $providerName | Statutory Invariant: Active',
            style: AppTypography.monoSmall(color: AppColors.ink),
          ),
          const SizedBox(height: 4),
          Text(
            'Policy: $statusReason',
            style: AppTypography.monoSmall(color: AppColors.inkMuted),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.paperRaised,
              borderRadius: BorderRadius.circular(3),
            ),
            child: Text(
              'Required keys: SMS_PROVIDER, SMS_ENABLED, ETHIO_TELECOM_SMS_URL, ETHIO_TELECOM_SMS_KEY, ETHIO_TELECOM_SMS_SENDER_ID. Live GeezSMS third-party egress is blocked until statutory certification.',
              style: AppTypography.monoSmall(color: AppColors.inkMuted),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              ElevatedButton.icon(
                onPressed: _isSendingTestSms ? null : _sendTestSms,
                icon: const Icon(Icons.send_outlined, size: 14),
                label: Text(_isSendingTestSms ? 'Testing SMS...' : 'Send Test SMS'),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Destination: Authenticated Master Admin verified phone only.',
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ),
            ],
          ),
          if (_testSmsResult != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: _testSmsResult!.startsWith('Success')
                    ? AppColors.green700.withValues(alpha: 0.1)
                    : AppColors.red600.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(3),
                border: Border.all(
                  color: _testSmsResult!.startsWith('Success')
                      ? AppColors.green700
                      : AppColors.red600,
                ),
              ),
              child: Text(
                _testSmsResult!,
                style: AppTypography.monoSmall(
                  color: _testSmsResult!.startsWith('Success')
                      ? AppColors.green700
                      : AppColors.red700,
                  weight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildScopeFilterAndSearch() {
    final scopes = [
      'ALL',
      'APPLICATION',
      'DATABASE',
      'REDIS',
      'SECURITY',
      'AUTHENTICATION',
      'MOEIRS',
      'SMS',
      'EMAIL',
      'STORAGE',
      'OBSERVABILITY',
      'RATE_LIMITING',
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextField(
                decoration: const InputDecoration(
                  hintText: 'Search configuration keys and descriptions...',
                  prefixIcon: Icon(Icons.search, size: 18),
                  border: OutlineInputBorder(),
                  isDense: true,
                ),
                onChanged: (val) {
                  setState(() {
                    _searchQuery = val.trim().toLowerCase();
                  });
                },
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: scopes.map((s) {
              final isSelected = _selectedScope == s;
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: FilterChip(
                  label: Text(s),
                  selected: isSelected,
                  selectedColor: AppColors.navy900.withValues(alpha: 0.15),
                  checkmarkColor: AppColors.navy900,
                  onSelected: (_) {
                    setState(() {
                      _selectedScope = s;
                    });
                  },
                ),
              );
            }).toList(),
          ),
        ),
      ],
    );
  }

  Widget _buildConfigurationEntriesList() {
    final filtered = _configurations.where((item) {
      final scopeMatch =
          _selectedScope == 'ALL' ||
          item['scope']?.toString().toUpperCase() == _selectedScope;
      final key = item['keyName']?.toString().toLowerCase() ?? '';
      final desc = item['description']?.toString().toLowerCase() ?? '';
      final searchMatch =
          _searchQuery.isEmpty ||
          key.contains(_searchQuery) ||
          desc.contains(_searchQuery);
      return scopeMatch && searchMatch;
    }).toList();

    if (filtered.isEmpty) {
      return Container(
        padding: const EdgeInsets.all(32),
        alignment: Alignment.center,
        child: Text(
          'No configuration items matched the active scope and search query.',
          style: AppTypography.body(color: AppColors.inkMuted),
        ),
      );
    }

    return ListView.separated(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: filtered.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final item = filtered[index];
        return _buildConfigItemCard(item);
      },
    );
  }

  Widget _buildConfigItemCard(Map<String, dynamic> item) {
    final keyName = item['keyName']?.toString() ?? '';
    final isSecret = item['isSecret'] == true;
    final valueType = item['valueType']?.toString() ?? 'STRING';
    final classification = item['classification']?.toString() ?? 'LOW';
    final desc = item['description']?.toString() ?? '';
    final currentValue = item['currentValue']?.toString();
    final maskedValue = item['maskedValue']?.toString() ?? '••••••••••••••••';
    final fingerprint = item['secretFingerprint']?.toString();
    final isStaged = _stagedChanges.containsKey(keyName);
    final displayedValue = isStaged ? _stagedChanges[keyName] : currentValue;

    Color classColor = AppColors.navy900;
    if (classification == 'CRITICAL') classColor = AppColors.red600;
    if (classification == 'HIGH') classColor = AppColors.amber700;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(
          color: isStaged ? AppColors.navy900 : AppColors.rule,
          width: isStaged ? 1.5 : 1.0,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(keyName, style: AppTypography.uiLabelBold()),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: classColor.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(2),
                            border: Border.all(color: classColor),
                          ),
                          child: Text(
                            classification,
                            style: AppTypography.monoSmall(color: classColor),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.paper,
                            borderRadius: BorderRadius.circular(2),
                            border: Border.all(color: AppColors.rule),
                          ),
                          child: Text(
                            valueType,
                            style: AppTypography.monoSmall(color: AppColors.inkMuted),
                          ),
                        ),
                        if (isSecret) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.navy900.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(2),
                            ),
                            child: Row(
                              children: [
                                const Icon(
                                  Icons.lock,
                                  size: 10,
                                  color: AppColors.navy900,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  'AES-256-GCM',
                                  style: AppTypography.monoSmall(
                                    color: AppColors.navy900,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      desc,
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
              ),
              if (isSecret)
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy900,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                  ),
                  onPressed: () => _showRotateSecretDialog(keyName),
                  icon: const Icon(Icons.sync_lock, size: 14),
                  label: const Text('Rotate Secret'),
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (isSecret)
            Row(
              children: [
                Text(
                  'Current Credential: ',
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
                Text(maskedValue, style: AppTypography.mono(weight: FontWeight.w700)),
                const SizedBox(width: 12),
                if (fingerprint != null)
                  Text(
                    '[$fingerprint]',
                    style: AppTypography.monoSmall(color: AppColors.inkMuted),
                  ),
              ],
            )
          else
            _buildTypedControl(item, displayedValue),
        ],
      ),
    );
  }

  Widget _buildTypedControl(Map<String, dynamic> item, String? activeValue) {
    final keyName = item['keyName']?.toString() ?? '';
    final valueType = item['valueType']?.toString() ?? 'STRING';
    final allowedValues = (item['allowedValues'] as List?)?.cast<String>();

    if (valueType == 'BOOLEAN') {
      final isTrue = (activeValue == 'true');
      return Row(
        children: [
          Text(
            isTrue ? 'ACTIVE / ENABLED' : 'DISABLED',
            style: AppTypography.mono(weight: FontWeight.w600),
          ),
          const SizedBox(width: 12),
          Switch(
            value: isTrue,
            activeColor: AppColors.navy900,
            onChanged: (val) {
              setState(() {
                _stagedChanges[keyName] = val.toString();
              });
            },
          ),
        ],
      );
    }

    if (valueType == 'ENUM' && allowedValues != null) {
      return DropdownButton<String>(
        value: allowedValues.contains(activeValue) ? activeValue : allowedValues.first,
        items: allowedValues
            .map((v) => DropdownMenuItem(value: v, child: Text(v)))
            .toList(),
        onChanged: (newVal) {
          if (newVal != null) {
            setState(() {
              _stagedChanges[keyName] = newVal;
            });
          }
        },
      );
    }

    return Row(
      children: [
        Expanded(
          child: TextFormField(
            initialValue: activeValue ?? '',
            decoration: InputDecoration(
              isDense: true,
              border: const OutlineInputBorder(),
              suffixIcon: _stagedChanges.containsKey(keyName)
                  ? IconButton(
                      icon: const Icon(Icons.undo, size: 16),
                      onPressed: () {
                        setState(() {
                          _stagedChanges.remove(keyName);
                        });
                      },
                    )
                  : null,
            ),
            onChanged: (val) {
              setState(() {
                _stagedChanges[keyName] = val.trim();
              });
            },
          ),
        ),
      ],
    );
  }

  Widget _buildStickyActionBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        border: const Border(top: BorderSide(color: AppColors.rule, width: 1)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 10,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        children: [
          const Icon(Icons.edit_note, color: AppColors.navy900, size: 20),
          const SizedBox(width: 8),
          Text(
            '${_stagedChanges.length} unsaved configuration change(s) staged',
            style: AppTypography.uiLabelBold(),
          ),
          const Spacer(),
          TextButton(
            onPressed: () {
              setState(() {
                _stagedChanges.clear();
              });
            },
            child: const Text('Discard All'),
          ),
          const SizedBox(width: 12),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.navy900,
              foregroundColor: Colors.white,
            ),
            onPressed: _commitStagedChanges,
            icon: const Icon(Icons.check, size: 16),
            label: const Text('Review & Commit Changes'),
          ),
        ],
      ),
    );
  }

  Widget _buildRevisionsHistorySection() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.history, color: AppColors.navy900, size: 20),
              const SizedBox(width: 8),
              Text('IMMUTABLE CONFIGURATION REVISION CHAIN', style: AppTypography.h2()),
            ],
          ),
          const SizedBox(height: 12),
          if (_revisions.isEmpty)
            Text('No previous revisions recorded.', style: AppTypography.bodySmall())
          else
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _revisions.length,
              separatorBuilder: (_, _) => const Divider(height: 16),
              itemBuilder: (context, idx) {
                final rev = _revisions[idx];
                final revNum = (rev['revisionNumber'] as num?)?.toInt() ?? 1;
                final summary = rev['changeSummary']?.toString() ?? '';
                final author = rev['createdBy']?.toString() ?? 'SYSTEM';
                final isCurrent = (revNum == _activeRevisionNumber);

                return Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: isCurrent
                            ? AppColors.green700.withValues(alpha: 0.15)
                            : AppColors.navy900.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(3),
                      ),
                      child: Text(
                        'Rev #$revNum',
                        style: AppTypography.monoSmall(
                          color: isCurrent
                              ? AppColors.green700
                              : AppColors.navy900,
                          weight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(summary, style: AppTypography.bodySmall()),
                          Text(
                            'Author: $author',
                            style: AppTypography.monoSmall(color: AppColors.inkMuted),
                          ),
                        ],
                      ),
                    ),
                    if (!isCurrent)
                      OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                        ),
                        onPressed: () => _showRollbackDialog(revNum),
                        icon: const Icon(Icons.restore, size: 14),
                        label: const Text('Rollback'),
                      ),
                  ],
                );
              },
            ),
        ],
      ),
    );
  }
}
