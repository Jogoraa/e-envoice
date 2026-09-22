import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../core/di/providers.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';

// Re-export authSessionProvider from multi_gateway_session for backward compatibility
export '../../../core/authentication/multi_gateway_session.dart'
    show authSessionProvider;

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _keyController = TextEditingController();
  final _secretController = TextEditingController();
  final _tinController = TextEditingController();
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _keyController.dispose();
    _secretController.dispose();
    _tinController.dispose();
    super.dispose();
  }

  void _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final tin = _tinController.text.trim();
      final username = _keyController.text.trim();
      final password = _secretController.text.trim();

      // Real database-driven authentication against Tenant API Gateway
      final authResult = await ref
          .read(authSessionProvider.notifier)
          .login(tin: tin, username: username, password: password);

      final tenantId = authResult['tenantId']?.toString() ?? '';
      final legalName =
          authResult['legalName']?.toString() ?? 'Taxpayer Business';
      final tradeName = authResult['tradeName']?.toString() ?? legalName;

      // Establish real authorized context
      ref
          .read(tenantContextProvider.notifier)
          .setAuthorizedContext(
            tenants: [
              TenantInfo(
                id: tenantId,
                organizationId: 'ORG-$tin',
                name: legalName,
                tradeName: tradeName,
                tin: tin,
                status: 'ACTIVE',
              ),
            ],
            activeTenant: TenantInfo(
              id: tenantId,
              organizationId: 'ORG-$tin',
              name: legalName,
              tradeName: tradeName,
              tin: tin,
              status: 'ACTIVE',
            ),
            branches: [
              BranchInfo(
                id: '00000000-0000-0000-0000-000000000010',
                tenantId: tenantId,
                name: 'Head Office & Main Branch',
                code: 'MAIN_HQ',
                isHeadOffice: true,
              ),
            ],
            activeBranch: BranchInfo(
              id: '00000000-0000-0000-0000-000000000010',
              tenantId: tenantId,
              name: 'Head Office & Main Branch',
              code: 'MAIN_HQ',
              isHeadOffice: true,
            ),
          );

      final storage = ref.read(secureStorageProvider);
      await storage.saveActiveTenantId(tenantId);
      await storage.saveActiveBranchId('00000000-0000-0000-0000-000000000010');

      final apiClient = ref.read(apiClientProvider);
      apiClient.setScope(
        tenantId: tenantId,
        branchId: '00000000-0000-0000-0000-000000000010',
      );

      if (!mounted) return;
      context.go('/dashboard');
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.toString().replaceAll('Exception: ', '');
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
          child: Container(
            constraints: const BoxConstraints(maxWidth: 420),
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
                  Text('Operator Sign In', style: AppTypography.h1()),
                  const SizedBox(height: 6),
                  Text(
                    'Access your organization\'s electronic invoicing and sales registration system.',
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
                        style: AppTypography.bodySmall(color: AppColors.red600),
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                  TextFormField(
                    controller: _tinController,
                    decoration: const InputDecoration(
                      labelText: 'Taxpayer TIN',
                      prefixIcon: Icon(Icons.badge_outlined, size: 18),
                    ),
                    validator: (v) =>
                        v == null || v.isEmpty ? 'TIN required' : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _keyController,
                    decoration: const InputDecoration(
                      labelText: 'Username / Operator ID',
                      prefixIcon: Icon(Icons.person_outline, size: 18),
                    ),
                    validator: (v) =>
                        v == null || v.isEmpty ? 'Username required' : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _secretController,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: 'Password',
                      prefixIcon: Icon(Icons.lock_outline, size: 18),
                    ),
                    validator: (v) =>
                        v == null || v.isEmpty ? 'Password required' : null,
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
                          : const Text('Sign In to Tenant Portal'),
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
                        onPressed: () => context.go('/saas/login'),
                        child: const Text('Master Portal', style: TextStyle(fontSize: 12)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Center(
                    child: Text(
                      'Directive No. 1142/2026 Compliant Terminal',
                      style: AppTypography.uiLabel(color: AppColors.inkMuted),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
