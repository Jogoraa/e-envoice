import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterAccountSettingsScreen extends ConsumerStatefulWidget {
  const MasterAccountSettingsScreen({super.key});

  @override
  ConsumerState<MasterAccountSettingsScreen> createState() =>
      _MasterAccountSettingsScreenState();
}

class _MasterAccountSettingsScreenState
    extends ConsumerState<MasterAccountSettingsScreen> {
  bool _isLoading = true;
  String? _error;
  String? _successMessage;

  Map<String, dynamic>? _profile;

  // Profile fields
  final _fullNameController = TextEditingController();
  String _selectedTimezone = 'Africa/Addis_Ababa';
  String _selectedDateFormat = 'YYYY-MM-DD';

  // Password fields
  final _currentPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;

  @override
  void initState() {
    super.initState();
    _loadProfile();
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _currentPasswordController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _loadProfile() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.get('/api/v1/master/account/profile');
      if (res.data is Map) {
        final data = Map<String, dynamic>.from(res.data as Map);
        setState(() {
          _profile = data;
          _fullNameController.text = data['fullName'] ?? '';
          _selectedTimezone = data['timezone'] ?? 'Africa/Addis_Ababa';
          _selectedDateFormat = data['dateFormat'] ?? 'YYYY-MM-DD';
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _error = 'Failed to load account profile: $e';
        _isLoading = false;
      });
    }
  }

  Future<void> _saveProfile() async {
    setState(() {
      _error = null;
      _successMessage = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.put(
        '/api/v1/master/account/profile',
        data: {
          'fullName': _fullNameController.text.trim(),
          'timezone': _selectedTimezone,
          'dateFormat': _selectedDateFormat,
        },
      );
      if (res.data is Map) {
        setState(() {
          _profile = Map<String, dynamic>.from(res.data as Map);
          _successMessage = 'Profile information saved successfully.';
        });
      }
    } catch (e) {
      setState(() {
        _error = 'Failed to save profile: $e';
      });
    }
  }

  Future<void> _changePassword() async {
    final cur = _currentPasswordController.text;
    final nw = _newPasswordController.text;
    final conf = _confirmPasswordController.text;

    if (cur.isEmpty || nw.isEmpty || conf.isEmpty) {
      setState(() => _error = 'All password fields are required.');
      return;
    }
    if (nw != conf) {
      setState(() => _error = 'New passwords do not match.');
      return;
    }

    setState(() {
      _error = null;
      _successMessage = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      await client.post(
        '/api/v1/master/account/password/change',
        data: {
          'currentPassword': cur,
          'newPassword': nw,
          'confirmPassword': conf,
        },
      );
      _currentPasswordController.clear();
      _newPasswordController.clear();
      _confirmPasswordController.clear();
      setState(() {
        _successMessage =
            'Password changed successfully. All previous sessions have been invalidated.';
      });
    } catch (e) {
      setState(() => _error = 'Password update failed: $e');
    }
  }

  void _showEmailChangeDialog() {
    final emailCtrl = TextEditingController();
    final codeCtrl = TextEditingController();
    bool codeSent = false;
    String? dialogError;
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.email_outlined, color: AppColors.navy900, size: 22),
              const SizedBox(width: 8),
              Text('Update Primary Email', style: AppTypography.h3()),
            ],
          ),
          content: SizedBox(
            width: 440,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Changing your administrative email requires out-of-band cryptographic verification.',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
                const SizedBox(height: 16),
                if (dialogError != null) ...[
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.red700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      dialogError!,
                      style: AppTypography.bodySmall(color: AppColors.red700),
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                TextField(
                  controller: emailCtrl,
                  enabled: !codeSent && !isSubmitting,
                  decoration: const InputDecoration(
                    labelText: 'New Email Address',
                    hintText: 'admin@utsolutionsplc.com',
                    prefixIcon: Icon(Icons.alternate_email, size: 18),
                  ),
                ),
                if (codeSent) ...[
                  const SizedBox(height: 16),
                  TextField(
                    controller: codeCtrl,
                    enabled: !isSubmitting,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    decoration: const InputDecoration(
                      labelText: '6-Digit Verification Code',
                      hintText: '123456',
                      prefixIcon: Icon(Icons.password, size: 18),
                    ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });
                      try {
                        final client = ref.read(masterAdminApiClientProvider);
                        if (!codeSent) {
                          await client.post(
                            '/api/v1/master/account/email/initiate',
                            data: {'newEmail': emailCtrl.text.trim()},
                          );
                          setDialogState(() {
                            codeSent = true;
                            isSubmitting = false;
                          });
                        } else {
                          await client.post(
                            '/api/v1/master/account/email/confirm',
                            data: {
                              'newEmail': emailCtrl.text.trim(),
                              'verificationCode': codeCtrl.text.trim(),
                            },
                          );
                          if (ctx.mounted) Navigator.pop(ctx);
                          _loadProfile();
                        }
                      } catch (e) {
                        setDialogState(() {
                          dialogError = e.toString();
                          isSubmitting = false;
                        });
                      }
                    },
              child: Text(codeSent ? 'Verify & Save' : 'Send Code'),
            ),
          ],
        ),
      ),
    );
  }

  void _showPhoneChangeDialog() {
    final phoneCtrl = TextEditingController(text: _profile?['phone'] ?? '');
    final codeCtrl = TextEditingController();
    bool codeSent = false;
    String? dialogError;
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.phone_outlined, color: AppColors.navy900, size: 22),
              const SizedBox(width: 8),
              Text('Verify Phone Number', style: AppTypography.h3()),
            ],
          ),
          content: SizedBox(
            width: 440,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'A 6-digit SMS verification code will be dispatched to your phone via the SMS Gateway.',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
                const SizedBox(height: 16),
                if (dialogError != null) ...[
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.red700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      dialogError!,
                      style: AppTypography.bodySmall(color: AppColors.red700),
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                TextField(
                  controller: phoneCtrl,
                  enabled: !codeSent && !isSubmitting,
                  decoration: const InputDecoration(
                    labelText: 'Phone Number (E.164)',
                    hintText: '+251911234567',
                    prefixIcon: Icon(Icons.smartphone, size: 18),
                  ),
                ),
                if (codeSent) ...[
                  const SizedBox(height: 16),
                  TextField(
                    controller: codeCtrl,
                    enabled: !isSubmitting,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    decoration: const InputDecoration(
                      labelText: '6-Digit SMS Verification Code',
                      hintText: '123456',
                      prefixIcon: Icon(Icons.sms_outlined, size: 18),
                    ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });
                      try {
                        final client = ref.read(masterAdminApiClientProvider);
                        if (!codeSent) {
                          await client.post(
                            '/api/v1/master/account/phone/initiate',
                            data: {'phone': phoneCtrl.text.trim()},
                          );
                          setDialogState(() {
                            codeSent = true;
                            isSubmitting = false;
                          });
                        } else {
                          await client.post(
                            '/api/v1/master/account/phone/confirm',
                            data: {
                              'phone': phoneCtrl.text.trim(),
                              'verificationCode': codeCtrl.text.trim(),
                            },
                          );
                          if (ctx.mounted) Navigator.pop(ctx);
                          _loadProfile();
                        }
                      } catch (e) {
                        setDialogState(() {
                          dialogError = e.toString();
                          isSubmitting = false;
                        });
                      }
                    },
              child: Text(codeSent ? 'Verify & Confirm' : 'Send SMS Code'),
            ),
          ],
        ),
      ),
    );
  }

  void _showMfaEnrollmentDialog() async {
    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.post('/api/v1/master/account/mfa/setup');
      if (!mounted) return;

      final data = Map<String, dynamic>.from(res.data as Map);
      final secret = data['secret'] as String;
      final otpAuthUri = (data['otpAuthUri'] as String?) ??
          'otpauth://totp/UT%20Electronic%20Invoice:admin?secret=$secret&issuer=UT%20Electronic%20Invoice&algorithm=SHA1&digits=6&period=30';
      final backupCodes = List<String>.from(data['backupCodes'] ?? []);

      final codeCtrl = TextEditingController();
      String? dialogError;
      bool isSubmitting = false;

      if (!mounted) return;
      showDialog(
        context: context,
        builder: (ctx) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            backgroundColor: AppColors.paperRaised,
            title: Row(
              children: [
                const Icon(Icons.security, color: AppColors.navy900, size: 24),
                const SizedBox(width: 8),
                Text('Enroll Authenticator App (TOTP)', style: AppTypography.h3()),
              ],
            ),
            content: SizedBox(
              width: 500,
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: AppColors.rule),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.04),
                              blurRadius: 8,
                              offset: const Offset(0, 2),
                            ),
                          ],
                        ),
                        child: QrImageView(
                          data: otpAuthUri,
                          version: QrVersions.auto,
                          size: 180.0,
                          gapless: true,
                          errorCorrectionLevel: QrErrorCorrectLevel.M,
                          errorStateBuilder: (cxt, err) => Container(
                            width: 180,
                            height: 180,
                            alignment: Alignment.center,
                            child: const Text(
                              'Failed to generate QR code',
                              style: TextStyle(fontSize: 11, color: AppColors.red700),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Center(
                      child: Text(
                        'Scan this QR code with Google Authenticator, Microsoft Authenticator, or 1Password',
                        textAlign: TextAlign.center,
                        style: AppTypography.bodySmall(color: AppColors.inkMuted),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Manual entry secret (if camera scan is unavailable):',
                      style: AppTypography.uiLabel(),
                    ),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.paperMuted,
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: SelectableText(
                              secret,
                              style: AppTypography.monoSmall(weight: FontWeight.w700),
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.copy, size: 16),
                            tooltip: 'Copy secret key',
                            onPressed: () {
                              Clipboard.setData(ClipboardData(text: secret));
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Secret copied to clipboard')),
                              );
                            },
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Emergency Recovery Codes (Save these now):',
                      style: AppTypography.uiLabelBold(),
                    ),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.paperMuted,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Wrap(
                        spacing: 12,
                        runSpacing: 4,
                        children: backupCodes
                            .map((c) => Text(c, style: AppTypography.monoSmall()))
                            .toList(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    if (dialogError != null) ...[
                      Text(
                        dialogError!,
                        style: AppTypography.bodySmall(color: AppColors.red700),
                      ),
                      const SizedBox(height: 8),
                    ],
                    TextField(
                      controller: codeCtrl,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      decoration: const InputDecoration(
                        labelText: 'Enter 6-digit TOTP code to verify',
                        hintText: '123456',
                        prefixIcon: Icon(Icons.pin, size: 18),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
                child: const Text('Cancel'),
              ),
              ElevatedButton(
                onPressed: isSubmitting
                    ? null
                    : () async {
                        setDialogState(() {
                          isSubmitting = true;
                          dialogError = null;
                        });
                        try {
                          await client.post(
                            '/api/v1/master/account/mfa/verify',
                            data: {'code': codeCtrl.text.trim()},
                          );
                          if (ctx.mounted) Navigator.pop(ctx);
                          _loadProfile();
                        } catch (e) {
                          setDialogState(() {
                            dialogError = 'MFA verification failed: $e';
                            isSubmitting = false;
                          });
                        }
                      },
                child: const Text('Activate MFA'),
              ),
            ],
          ),
        ),
      );
    } catch (e) {
      setState(() => _error = 'Could not initiate MFA enrollment: $e');
    }
  }

  void _showDisableMfaDialog() {
    final passCtrl = TextEditingController();
    final codeCtrl = TextEditingController();
    String? dialogError;
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.warning_amber_rounded, color: AppColors.amber700, size: 24),
              const SizedBox(width: 8),
              Text('Disable MFA Protection', style: AppTypography.h3()),
            ],
          ),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Warning: Disabling MFA reduces account security and may violate platform compliance policies.',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
                const SizedBox(height: 16),
                if (dialogError != null) ...[
                  Text(dialogError!, style: AppTypography.bodySmall(color: AppColors.red700)),
                  const SizedBox(height: 8),
                ],
                TextField(
                  controller: passCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Current Password',
                    prefixIcon: Icon(Icons.lock_outline, size: 18),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: codeCtrl,
                  keyboardType: TextInputType.number,
                  maxLength: 6,
                  decoration: const InputDecoration(
                    labelText: '6-digit OTP or TOTP code',
                    prefixIcon: Icon(Icons.pin, size: 18),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: AppColors.red700),
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });
                      try {
                        final client = ref.read(masterAdminApiClientProvider);
                        await client.post(
                          '/api/v1/master/account/mfa/disable',
                          data: {
                            'password': passCtrl.text,
                            'code': codeCtrl.text.trim(),
                          },
                        );
                        if (ctx.mounted) Navigator.pop(ctx);
                        _loadProfile();
                      } catch (e) {
                        setDialogState(() {
                          dialogError = 'Failed to disable MFA: $e';
                          isSubmitting = false;
                        });
                      }
                    },
              child: const Text('Disable MFA'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: AppColors.paperMuted,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    final emailVerified = _profile?['emailVerified'] == true;
    final phoneVerified = _profile?['phoneVerified'] == true;
    final mfaEnabled = _profile?['mfaEnabled'] == true;

    return Scaffold(
      backgroundColor: AppColors.paperMuted,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Page Header
            Row(
              children: [
                const Icon(
                  Icons.manage_accounts_outlined,
                  size: 28,
                  color: AppColors.navy900,
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Master Administrator Account', style: AppTypography.h1()),
                    Text(
                      'Manage operator profile, authentication credentials, MFA, and active sessions.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                const Spacer(),
                OutlinedButton.icon(
                  onPressed: () => context.go('/admin/sessions'),
                  icon: const Icon(Icons.devices, size: 16),
                  label: const Text('View Active Sessions'),
                ),
              ],
            ),
            const SizedBox(height: 20),

            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.red700.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppColors.red700.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline, color: AppColors.red700, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.red700)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            if (_successMessage != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.green700.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppColors.green700.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.check_circle_outline, color: AppColors.green700, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _successMessage!,
                        style: AppTypography.bodySmall(color: AppColors.green700),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Section 1: Identity & Profile
            _buildCard(
              title: 'Administrator Profile & Regional Preferences',
              subtitle: 'Identity attributes recognized across cryptographic audit logs and signing certificates.',
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
                            Text('Username (Canonical)', style: AppTypography.uiLabelBold()),
                            const SizedBox(height: 4),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AppColors.paperMuted,
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(color: AppColors.rule),
                              ),
                              child: Text(
                                _profile?['username'] ?? '',
                                style: AppTypography.monoSmall(color: AppColors.inkMuted),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Primary Administrative Role', style: AppTypography.uiLabelBold()),
                            const SizedBox(height: 4),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AppColors.navy900.withValues(alpha: 0.05),
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(color: AppColors.navy900.withValues(alpha: 0.2)),
                              ),
                              child: Text(
                                _profile?['primaryRole'] ?? 'ROLE_PLATFORM_ADMIN',
                                style: AppTypography.monoSmall(
                                  color: AppColors.navy900,
                                  weight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _fullNameController,
                          decoration: const InputDecoration(
                            labelText: 'Full Operator Legal Name',
                            prefixIcon: Icon(Icons.person_outline, size: 18),
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: _selectedTimezone,
                          decoration: const InputDecoration(
                            labelText: 'Operating Timezone',
                            prefixIcon: Icon(Icons.schedule, size: 18),
                          ),
                          items: const [
                            DropdownMenuItem(value: 'Africa/Addis_Ababa', child: Text('Africa/Addis_Ababa (EAT, UTC+3)')),
                            DropdownMenuItem(value: 'UTC', child: Text('UTC (Universal Coordinated Time)')),
                            DropdownMenuItem(value: 'Europe/London', child: Text('Europe/London (GMT/BST)')),
                          ],
                          onChanged: (v) => setState(() => _selectedTimezone = v ?? 'Africa/Addis_Ababa'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Align(
                    alignment: Alignment.centerRight,
                    child: ElevatedButton.icon(
                      onPressed: _saveProfile,
                      icon: const Icon(Icons.save, size: 16),
                      label: const Text('Save Profile'),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Section 2: Contact Channels & Verification
            _buildCard(
              title: 'Verified Contact Channels (MFA Dispatch Destinations)',
              subtitle: 'Out-of-band channels used for step-up verification and critical security alerts.',
              child: Row(
                children: [
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.email_outlined, size: 20, color: AppColors.navy900),
                              const SizedBox(width: 8),
                              Text('Official Email', style: AppTypography.uiLabelBold()),
                              const Spacer(),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: emailVerified
                                      ? AppColors.green700.withValues(alpha: 0.1)
                                      : AppColors.amber700.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: Text(
                                  emailVerified ? 'VERIFIED' : 'UNVERIFIED',
                                  style: AppTypography.monoSmall(
                                    color: emailVerified ? AppColors.green700 : AppColors.amber700,
                                    weight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(_profile?['email'] ?? '', style: AppTypography.monoSmall()),
                          const SizedBox(height: 12),
                          OutlinedButton.icon(
                            onPressed: _showEmailChangeDialog,
                            icon: const Icon(Icons.edit, size: 14),
                            label: const Text('Change Email'),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppColors.paperRaised,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.phone_outlined, size: 20, color: AppColors.navy900),
                              const SizedBox(width: 8),
                              Text('SMS Security Phone', style: AppTypography.uiLabelBold()),
                              const Spacer(),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: phoneVerified
                                      ? AppColors.green700.withValues(alpha: 0.1)
                                      : AppColors.amber700.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: Text(
                                  phoneVerified ? 'VERIFIED' : 'UNVERIFIED',
                                  style: AppTypography.monoSmall(
                                    color: phoneVerified ? AppColors.green700 : AppColors.amber700,
                                    weight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            _profile?['phone'] ?? '+251 911 000000 (Default)',
                            style: AppTypography.monoSmall(),
                          ),
                          const SizedBox(height: 12),
                          OutlinedButton.icon(
                            onPressed: _showPhoneChangeDialog,
                            icon: const Icon(Icons.verified, size: 14),
                            label: const Text('Verify / Change Phone'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Section 3: Password & Multi-Factor Authentication
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Change Password
                Expanded(
                  child: _buildCard(
                    title: 'Password Management',
                    subtitle: 'Directive No. 1142/2026 Art. 4 & NIST SP 800-63B standards (12+ characters).',
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        TextField(
                          controller: _currentPasswordController,
                          obscureText: _obscureCurrent,
                          decoration: InputDecoration(
                            labelText: 'Current Password',
                            prefixIcon: const Icon(Icons.lock_outline, size: 18),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscureCurrent ? Icons.visibility : Icons.visibility_off,
                                size: 18,
                              ),
                              onPressed: () => setState(() => _obscureCurrent = !_obscureCurrent),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _newPasswordController,
                          obscureText: _obscureNew,
                          decoration: InputDecoration(
                            labelText: 'New Password',
                            prefixIcon: const Icon(Icons.vpn_key_outlined, size: 18),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscureNew ? Icons.visibility : Icons.visibility_off,
                                size: 18,
                              ),
                              onPressed: () => setState(() => _obscureNew = !_obscureNew),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _confirmPasswordController,
                          obscureText: _obscureConfirm,
                          decoration: InputDecoration(
                            labelText: 'Confirm New Password',
                            prefixIcon: const Icon(Icons.check_circle_outline, size: 18),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscureConfirm ? Icons.visibility : Icons.visibility_off,
                                size: 18,
                              ),
                              onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        Align(
                          alignment: Alignment.centerRight,
                          child: ElevatedButton.icon(
                            onPressed: _changePassword,
                            icon: const Icon(Icons.lock_reset, size: 16),
                            label: const Text('Update Password'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 20),

                // Multi-Factor Authentication (MFA)
                Expanded(
                  child: _buildCard(
                    title: 'Multi-Factor Authentication (MFA)',
                    subtitle: 'Hardware or software-based authenticator (RFC 6238 TOTP).',
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: mfaEnabled
                                ? AppColors.green700.withValues(alpha: 0.1)
                                : AppColors.amber700.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                mfaEnabled ? Icons.shield : Icons.shield_outlined,
                                color: mfaEnabled ? AppColors.green700 : AppColors.amber700,
                                size: 24,
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      mfaEnabled
                                          ? 'MFA Protection Active'
                                          : 'MFA Protection Inactive',
                                      style: AppTypography.uiLabelBold(
                                        color: mfaEnabled ? AppColors.green700 : AppColors.amber700,
                                      ),
                                    ),
                                    Text(
                                      mfaEnabled
                                          ? 'Authenticator App (TOTP) and Out-of-Band SMS/Email OTP enabled.'
                                          : 'Enroll your authenticator app to protect elevated administrative sessions.',
                                      style: AppTypography.bodySmall(
                                        color: mfaEnabled ? AppColors.green700 : AppColors.amber700,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),
                        if (!mfaEnabled)
                          ElevatedButton.icon(
                            onPressed: _showMfaEnrollmentDialog,
                            icon: const Icon(Icons.qr_code, size: 16),
                            label: const Text('Enroll Authenticator App (TOTP)'),
                          )
                        else
                          Row(
                            children: [
                              OutlinedButton.icon(
                                onPressed: () async {
                                  try {
                                    final client = ref.read(masterAdminApiClientProvider);
                                    final res = await client.post(
                                      '/api/v1/master/account/mfa/recovery-codes/regenerate',
                                    );
                                    final codes = List<String>.from(res.data['recoveryCodes'] ?? []);
                                    if (context.mounted) {
                                      showDialog(
                                        context: context,
                                        builder: (ctx) => AlertDialog(
                                          title: const Text('Regenerated Backup Codes'),
                                          content: SizedBox(
                                            width: 320,
                                            child: Column(
                                              mainAxisSize: MainAxisSize.min,
                                              children: codes
                                                  .map((c) => Text(c, style: AppTypography.monoSmall()))
                                                  .toList(),
                                            ),
                                          ),
                                          actions: [
                                            TextButton(
                                              onPressed: () => Navigator.pop(ctx),
                                              child: const Text('Close'),
                                            ),
                                          ],
                                        ),
                                      );
                                    }
                                  } catch (e) {
                                    setState(() => _error = 'Failed to regenerate codes: $e');
                                  }
                                },
                                icon: const Icon(Icons.vpn_key, size: 14),
                                label: const Text('Regenerate Backup Codes'),
                              ),
                              const SizedBox(width: 8),
                              OutlinedButton.icon(
                                onPressed: _showDisableMfaDialog,
                                style: OutlinedButton.styleFrom(foregroundColor: AppColors.red700),
                                icon: const Icon(Icons.cancel, size: 14),
                                label: const Text('Disable MFA'),
                              ),
                            ],
                          ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCard({
    required String title,
    required String subtitle,
    required Widget child,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppTypography.h3()),
          const SizedBox(height: 2),
          Text(subtitle, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
          const SizedBox(height: 16),
          child,
        ],
      ),
    );
  }
}
