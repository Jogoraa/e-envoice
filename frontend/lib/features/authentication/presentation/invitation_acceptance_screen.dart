import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/networking/gateway_config.dart';

enum InvitationPageState {
  validating,
  valid,
  invalid,
  expired,
  alreadyUsed,
  revoked,
  activating,
  success,
  failed,
}

/// Public, deep-linkable account activation screen. It deliberately validates the
/// one-time token before rendering any password fields.
class InvitationAcceptanceScreen extends StatefulWidget {
  final String? token;
  final Dio? dio;

  const InvitationAcceptanceScreen({super.key, required this.token, this.dio});

  @override
  State<InvitationAcceptanceScreen> createState() =>
      _InvitationAcceptanceScreenState();
}

class _InvitationAcceptanceScreenState
    extends State<InvitationAcceptanceScreen> {
  late final Dio _dio;
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  InvitationPageState _state = InvitationPageState.validating;
  Map<String, dynamic>? _invitation;
  String? _error;
  String _loginPath = '/saas/login';

  @override
  void initState() {
    super.initState();
    _dio =
        widget.dio ??
        Dio(
          BaseOptions(
            baseUrl: GatewayConfig.activeBackendHost,
            connectTimeout: GatewayConfig.connectTimeout,
            receiveTimeout: GatewayConfig.receiveTimeout,
            headers: const {
              'Accept': 'application/json',
              'Content-Type': 'application/json',
            },
          ),
        );
    _validate();
  }

  @override
  void dispose() {
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _validate() async {
    final token = widget.token;
    if (token == null || token.isEmpty) {
      setState(() => _state = InvitationPageState.invalid);
      return;
    }

    setState(() {
      _state = InvitationPageState.validating;
      _error = null;
    });
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/v1/auth/invitations/validate',
        queryParameters: {'token': token},
      );
      final data = response.data ?? const <String, dynamic>{};
      if (data['valid'] == true) {
        setState(() {
          _invitation = data;
          _state = InvitationPageState.valid;
        });
        return;
      }
      setState(() => _state = _stateFromServer(data['state']?.toString()));
    } on DioException {
      setState(() {
        _state = InvitationPageState.failed;
        _error =
            'We could not verify this invitation. Check your connection and try again.';
      });
    }
  }

  Future<void> _activate() async {
    final token = widget.token;
    if (token == null || token.isEmpty) return;
    if (_passwordController.text != _confirmPasswordController.text) {
      setState(() => _error = 'Passwords do not match.');
      return;
    }
    final password = _passwordController.text;
    if (!RegExp(
      r'^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{12,128}$',
    ).hasMatch(password)) {
      setState(
        () => _error =
            'Use at least 12 characters with uppercase, lowercase, a number, and a special character.',
      );
      return;
    }
    setState(() {
      _state = InvitationPageState.activating;
      _error = null;
    });
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/v1/auth/invitations/accept',
        data: {
          'token': token,
          'password': _passwordController.text,
          'confirmPassword': _confirmPasswordController.text,
        },
      );
      final data = response.data ?? const <String, dynamic>{};
      setState(() {
        _loginPath = data['loginPath']?.toString() ?? '/saas/login';
        _state = InvitationPageState.success;
      });
    } on DioException catch (error) {
      final responseData = error.response?.data;
      final serverMessage = responseData is Map
          ? responseData['message']?.toString()
          : null;
      setState(() {
        _state = error.response == null
            ? InvitationPageState.failed
            : InvitationPageState.valid;
        _error =
            serverMessage ??
            (error.response == null
                ? 'We could not activate your account. Check your connection and try again.'
                : 'We could not activate your account. Correct the details and try again.');
      });
    }
  }

  InvitationPageState _stateFromServer(String? state) {
    switch (state) {
      case 'EXPIRED':
        return InvitationPageState.expired;
      case 'ALREADY_USED':
        return InvitationPageState.alreadyUsed;
      case 'REVOKED':
        return InvitationPageState.revoked;
      default:
        return InvitationPageState.invalid;
    }
  }

  String _messageForState() {
    switch (_state) {
      case InvitationPageState.invalid:
        return 'This invitation link is invalid.';
      case InvitationPageState.expired:
        return 'This invitation has expired. Ask an administrator to resend it.';
      case InvitationPageState.alreadyUsed:
        return 'This invitation has already been used. You can sign in with the password you created.';
      case InvitationPageState.revoked:
        return 'This invitation has been revoked. Contact Platform Administration if you need a new one.';
      case InvitationPageState.failed:
        return _error ??
            'Something went wrong while processing this invitation.';
      default:
        return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final blocked = {
      InvitationPageState.invalid,
      InvitationPageState.expired,
      InvitationPageState.alreadyUsed,
      InvitationPageState.revoked,
      InvitationPageState.failed,
    }.contains(_state);
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Card(
              color: AppColors.paperRaised,
              elevation: 2,
              clipBehavior: Clip.antiAlias,
              child: Padding(
                padding: const EdgeInsets.all(32),
                child:
                    _state == InvitationPageState.validating ||
                        _state == InvitationPageState.activating
                    ? _loadingContent()
                    : _state == InvitationPageState.success
                    ? _successContent(context)
                    : blocked
                    ? _blockedContent()
                    : _activationForm(),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _loadingContent() => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      const CircularProgressIndicator(),
      const SizedBox(height: 20),
      Text(
        _state == InvitationPageState.activating
            ? 'Activating your account…'
            : 'Validating your invitation…',
        style: AppTypography.h3(),
      ),
      const SizedBox(height: 8),
      Text(
        'This secure link can only be used once.',
        style: AppTypography.bodySmall(),
        textAlign: TextAlign.center,
      ),
    ],
  );

  Widget _blockedContent() => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      const Icon(Icons.error_outline, color: AppColors.red700, size: 42),
      const SizedBox(height: 16),
      Text('Invitation unavailable', style: AppTypography.h2()),
      const SizedBox(height: 10),
      Text(
        _messageForState(),
        style: AppTypography.body(),
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 20),
      OutlinedButton(onPressed: _validate, child: const Text('Try again')),
    ],
  );

  Widget _activationForm() {
    final expiresAt = DateTime.tryParse(
      _invitation?['expiresAt']?.toString() ?? '',
    );
    final expiryText = expiresAt == null
        ? 'the time shown in your invitation email'
        : DateFormat('MMMM d, y, h:mm a z').format(expiresAt.toLocal());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'UT INVOICE',
          style: AppTypography.uiLabelBold(color: AppColors.navy900),
        ),
        const SizedBox(height: 10),
        Text('Activate your account', style: AppTypography.h2()),
        const SizedBox(height: 12),
        Text(
          'Welcome ${_invitation?['displayName'] ?? ''}. Set a password to activate your access.',
          style: AppTypography.body(),
        ),
        const SizedBox(height: 18),
        _detail('Email', _invitation?['email']?.toString() ?? ''),
        _detail('Role', _invitation?['role']?.toString() ?? ''),
        _detail(
          'Organization',
          _invitation?['organization']?.toString() ?? 'UT Invoice Platform',
        ),
        _detail('Expires', expiryText),
        const SizedBox(height: 22),
        TextField(
          controller: _passwordController,
          obscureText: true,
          enableSuggestions: false,
          autocorrect: false,
          decoration: const InputDecoration(
            labelText: 'New password',
            helperText:
                '12+ characters with upper, lower, number, and special character',
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _confirmPasswordController,
          obscureText: true,
          enableSuggestions: false,
          autocorrect: false,
          onSubmitted: (_) => _activate(),
          decoration: const InputDecoration(labelText: 'Confirm password'),
        ),
        if (_error != null) ...[
          const SizedBox(height: 12),
          Text(
            _error!,
            style: AppTypography.bodySmall(color: AppColors.red700),
          ),
        ],
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _activate,
            child: const Text('Activate Account'),
          ),
        ),
        const SizedBox(height: 14),
        Text(
          'For security, this link can only be used once.',
          style: AppTypography.bodySmall(),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _detail(String label, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 6),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 108,
          child: Text(
            label,
            style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
          ),
        ),
        Expanded(child: Text(value, style: AppTypography.uiLabel())),
      ],
    ),
  );

  Widget _successContent(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      const Icon(
        Icons.check_circle_outline,
        color: AppColors.green700,
        size: 48,
      ),
      const SizedBox(height: 16),
      Text('Account activated', style: AppTypography.h2()),
      const SizedBox(height: 10),
      Text(
        'Your password is set and this invitation has been permanently consumed.',
        style: AppTypography.body(),
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 22),
      ElevatedButton(
        onPressed: () => context.go(_loginPath),
        child: const Text('Continue to sign in'),
      ),
    ],
  );
}
