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
      // Authenticate strictly against Master Admin API Gateway with MFA validation
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
      setState(() {
        _errorMessage =
            'Master Admin Gateway Authentication Failed: ${e.toString()}';
        _isLoading = false;
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
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _mfaController,
                      decoration: const InputDecoration(
                        labelText: 'Hardware / TOTP MFA Token (6 Digits)',
                        prefixIcon: Icon(Icons.security, size: 18),
                      ),
                      validator: (v) => v == null || v.length != 6
                          ? '6-digit MFA token required'
                          : null,
                    ),
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
                            : const Text('Authenticate with MFA'),
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Divider(height: 1, color: AppColors.rule),
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        TextButton.icon(
                          onPressed: () => context.go('/'),
                          icon: const Icon(Icons.arrow_back, size: 14),
                          label: const Text('Workspaces', style: TextStyle(fontSize: 12)),
                        ),
                        TextButton(
                          onPressed: () => context.go('/tenant/login'),
                          child: const Text('Tenant Client', style: TextStyle(fontSize: 12)),
                        ),
                      ],
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
