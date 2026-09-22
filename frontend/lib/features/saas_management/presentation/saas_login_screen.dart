import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';

class SaasLoginScreen extends ConsumerStatefulWidget {
  const SaasLoginScreen({super.key});

  @override
  ConsumerState<SaasLoginScreen> createState() => _SaasLoginScreenState();
}

class _SaasLoginScreenState extends ConsumerState<SaasLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _mfaController = TextEditingController();
  final _mfaFocusNode = FocusNode();
  bool _isLoading = false;
  bool _showMfaInput = false;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _mfaController.dispose();
    _mfaFocusNode.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      // Authenticate strictly against SaaS Management Gateway
      await ref
          .read(saasSessionProvider.notifier)
          .login(
            email: _emailController.text.trim(),
            password: _passwordController.text.trim(),
            mfaCode: _showMfaInput ? _mfaController.text.trim() : '',
          );

      if (mounted) {
        context.go('/saas/dashboard');
      }
    } catch (e) {
      final errStr = e.toString().replaceAll('Exception: ', '');
      final isMfaChallenge =
          errStr.toLowerCase().contains('mfa') ||
          errStr.toLowerCase().contains('totp');

      setState(() {
        _isLoading = false;
        if (isMfaChallenge && !_showMfaInput) {
          _showMfaInput = true;
          _errorMessage =
              'Two-Factor Authentication is enrolled on this account. Enter your 6-digit authenticator code below to complete login.';
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _mfaFocusNode.requestFocus();
          });
        } else {
          _errorMessage = errStr;
        }
      });
    }
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
                    color: AppColors.ink.withValues(alpha: 0.04),
                    blurRadius: 16,
                    offset: const Offset(0, 4),
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
                        color: AppColors.navy700.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(
                          color: AppColors.navy700.withValues(alpha: 0.3),
                        ),
                      ),
                      child: Text(
                        'COMMERCIAL SaaS MANAGEMENT PORTAL',
                        textAlign: TextAlign.center,
                        style: AppTypography.monoSmall(
                          color: AppColors.navy700,
                          weight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text('SaaS Operations Login', style: AppTypography.h1()),
                    const SizedBox(height: 6),
                    Text(
                      'Dedicated commercial operations gateway for tenant onboarding, subscription lifecycle, and billing management.',
                      style: AppTypography.bodySmall(),
                    ),
                    const SizedBox(height: 24),
                    if (_errorMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: _showMfaInput && _mfaController.text.isEmpty
                              ? AppColors.amber700.withValues(alpha: 0.1)
                              : AppColors.red600.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(3),
                          border: Border.all(
                            color: _showMfaInput && _mfaController.text.isEmpty
                                ? AppColors.amber700.withValues(alpha: 0.4)
                                : AppColors.red600.withValues(alpha: 0.3),
                          ),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              _showMfaInput && _mfaController.text.isEmpty
                                  ? Icons.shield_outlined
                                  : Icons.error_outline,
                              size: 16,
                              color:
                                  _showMfaInput && _mfaController.text.isEmpty
                                  ? AppColors.amber700
                                  : AppColors.red600,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                _errorMessage!,
                                style: AppTypography.bodySmall(
                                  color:
                                      _showMfaInput &&
                                          _mfaController.text.isEmpty
                                      ? AppColors.amber700
                                      : AppColors.red600,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],

                    TextFormField(
                      controller: _emailController,
                      decoration: const InputDecoration(
                        labelText: 'Operator Username or Email',
                        hintText: 'email@example.com',
                        prefixIcon: Icon(Icons.person_outline, size: 18),
                      ),
                      validator: (v) => v == null || v.trim().isEmpty
                          ? 'Operator identifier required'
                          : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: true,
                      decoration: const InputDecoration(
                        labelText: 'Password',
                        prefixIcon: Icon(Icons.lock_outline, size: 18),
                      ),
                      validator: (v) =>
                          v == null || v.isEmpty ? 'Password required' : null,
                    ),
                    const SizedBox(height: 12),

                    // TOTP MFA Field (shown only if account has registered TOTP, or toggled)
                    if (_showMfaInput) ...[
                      const SizedBox(height: 4),
                      TextFormField(
                        controller: _mfaController,
                        focusNode: _mfaFocusNode,
                        keyboardType: TextInputType.number,
                        maxLength: 6,
                        decoration: InputDecoration(
                          labelText: 'Hardware / TOTP MFA Token (6 Digits)',
                          hintText: 'Enter 6-digit authenticator code',
                          prefixIcon: const Icon(Icons.security, size: 18),
                          counterText: '',
                          suffixIcon: IconButton(
                            icon: const Icon(Icons.close, size: 16),
                            tooltip: 'Hide TOTP input',
                            onPressed: () {
                              setState(() {
                                _showMfaInput = false;
                                _mfaController.clear();
                              });
                            },
                          ),
                        ),
                        validator: (v) {
                          if (_showMfaInput &&
                              (v == null || v.trim().length != 6)) {
                            return '6-digit MFA token required';
                          }
                          return null;
                        },
                      ),
                    ] else ...[
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton.icon(
                          onPressed: () {
                            setState(() {
                              _showMfaInput = true;
                            });
                            WidgetsBinding.instance.addPostFrameCallback((_) {
                              _mfaFocusNode.requestFocus();
                            });
                          },
                          icon: const Icon(Icons.shield_outlined, size: 14),
                          label: Text(
                            'Using 2FA / Authenticator app? (Optional)',
                            style: AppTypography.monoSmall(
                              color: AppColors.navy700,
                            ),
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),

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
                                _showMfaInput
                                    ? 'Verify & Access SaaS Portal'
                                    : 'Access SaaS Portal',
                              ),
                      ),
                    ),
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
