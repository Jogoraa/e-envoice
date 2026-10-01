import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';

class MasterAdminLoginScreen extends ConsumerStatefulWidget {
  const MasterAdminLoginScreen({super.key});

  @override
  ConsumerState<MasterAdminLoginScreen> createState() =>
      _MasterAdminLoginScreenState();
}

class _MasterAdminLoginScreenState
    extends ConsumerState<MasterAdminLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _mfaController = TextEditingController();
  bool _isLoading = false;
  bool _requiresMfa = false;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _mfaController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      // First-time platform accounts are not enrolled in MFA. Send the code only
      // after the server has explicitly required it for this account.
      await ref
          .read(masterAdminSessionProvider.notifier)
          .login(
            email: _emailController.text.trim(),
            password: _passwordController.text.trim(),
            mfaCode: _mfaController.text.trim(),
          );

      if (mounted) {
        context.go('/admin/dashboard');
      }
    } catch (e) {
      final message = e.toString();
      final requiresMfa = _isMfaRequired(message);
      setState(() {
        _requiresMfa = _requiresMfa || requiresMfa;
        _errorMessage = requiresMfa
            ? 'Multi-factor authentication is enabled for this account. Enter your current 6-digit authenticator code and try again.'
            : 'Master Admin Gateway Authentication Failed: $message';
        _isLoading = false;
      });
    }
  }

  bool _isMfaRequired(String message) {
    final normalized = message.toLowerCase();
    return normalized.contains('mfa is enabled') ||
        normalized.contains('mfa token is required') ||
        normalized.contains('totp') ||
        normalized.contains('multi-factor authentication');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 460),
            child: Container(
              padding: const EdgeInsets.all(32),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule, width: 1),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.ink.withValues(alpha: 0.06),
                    blurRadius: 20,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: UtInvoiceLogo(
                        size: 36,
                        showWordmark: true,
                        showParentCredit: true,
                      ),
                    ),
                    const SizedBox(height: 24),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.navy900.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(
                          color: AppColors.navy900.withValues(alpha: 0.4),
                        ),
                      ),
                      child: Text(
                        'INTERNAL PLATFORM MASTER ADMIN (RESTRICTED)',
                        textAlign: TextAlign.center,
                        style: AppTypography.monoSmall(
                          color: AppColors.navy900,
                          weight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text('Master Admin Login', style: AppTypography.h1()),
                    const SizedBox(height: 6),
                    Text(
                      'Internal platform operations, regulatory compliance monitoring, and security oversight.',
                      style: AppTypography.bodySmall(),
                    ),
                    const SizedBox(height: 24),

                    if (_errorMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.red600.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(3),
                          border: Border.all(
                            color: AppColors.red600.withValues(alpha: 0.3),
                          ),
                        ),
                        child: Text(
                          _errorMessage!,
                          style: AppTypography.bodySmall(
                            color: AppColors.red600,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],

                    TextFormField(
                      controller: _emailController,
                      decoration: const InputDecoration(
                        labelText: 'Administrator Email',
                        prefixIcon: Icon(Icons.shield_outlined, size: 18),
                      ),
                      validator: (v) => v == null || v.isEmpty
                          ? 'Admin email required'
                          : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: true,
                      decoration: const InputDecoration(
                        labelText: 'Master Password',
                        prefixIcon: Icon(Icons.password_outlined, size: 18),
                      ),
                      validator: (v) =>
                          v == null || v.isEmpty ? 'Password required' : null,
                    ),
                    if (_requiresMfa) ...[
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _mfaController,
                        keyboardType: TextInputType.number,
                        maxLength: 6,
                        decoration: const InputDecoration(
                          labelText: 'Authenticator / TOTP Code',
                          hintText: 'Enter your current 6-digit code',
                          prefixIcon: Icon(Icons.security, size: 18),
                        ),
                        validator: (v) => v == null || v.length != 6
                            ? '6-digit MFA token required'
                            : null,
                      ),
                    ],
                    const SizedBox(height: 24),

                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        onPressed: _isLoading ? null : _handleLogin,
                        child: _isLoading
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : Text(
                                _requiresMfa
                                    ? 'Authenticate with MFA'
                                    : 'Sign in',
                              ),
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
