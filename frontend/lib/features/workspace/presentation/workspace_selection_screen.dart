import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';

/// Desktop Workspace Selection Screen
/// Allows operators and tenant users to choose their application domain
/// while maintaining strict session and authentication isolation.
class WorkspaceSelectionScreen extends ConsumerWidget {
  const WorkspaceSelectionScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tenantSession = ref.watch(authSessionProvider);
    final saasSession = ref.watch(saasSessionProvider);
    final masterAdminSession = ref.watch(masterAdminSessionProvider);
    final delegatedSession = ref.watch(delegatedTenantSessionProvider);

    return Scaffold(
      backgroundColor: AppColors.paper,
        body: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1100),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const UtInvoiceLogo(
                    size: 40,
                    showWordmark: true,
                    showParentCredit: true,
                  ),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.navy900.withValues(alpha: 0.06),
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(color: AppColors.navy900.withValues(alpha: 0.15)),
                    ),
                    child: Text(
                      'ELECTRONIC INVOICING & FISCAL PLATFORM',
                      style: AppTypography.monoSmall(
                        color: AppColors.navy900,
                        weight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Choose Application Workspace',
                    style: AppTypography.h1(color: AppColors.navy900),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Select the authorized operating environment for your role. Security contexts and credentials remain strictly isolated.',
                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),

                  // Three Workspace Chooser Cards
                  LayoutBuilder(
                    builder: (context, constraints) {
                      final isWide = constraints.maxWidth >= 960;
                      return Flex(
                        direction: isWide ? Axis.horizontal : Axis.vertical,
                        crossAxisAlignment: isWide ? CrossAxisAlignment.start : CrossAxisAlignment.stretch,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          // Workspace 1: Tenant Client
                          Expanded(
                            flex: isWide ? 1 : 0,
                            child: _buildWorkspaceCard(
                              context: context,
                              title: 'Tenant Client',
                              badgeText: 'BUSINESS OPERATIONS',
                              badgeColor: AppColors.navy700,
                              icon: Icons.storefront_outlined,
                              description:
                                  'Access registered taxpayer business portal for fiscal invoicing, customer registry, catalog, inventory, and MoR compliance reports.',
                              buttonLabel: tenantSession != null
                                  ? 'Resume Tenant Session'
                                  : 'Launch Tenant Client',
                              buttonIcon: Icons.arrow_forward,
                              isPrimary: true,
                              activeStatus: tenantSession != null
                                  ? 'Authenticated (${tenantSession.username})'
                                  : null,
                              onTap: () {
                                if (delegatedSession != null || tenantSession != null) {
                                  context.go('/dashboard');
                                } else {
                                  context.go('/tenant/login');
                                }
                              },
                            ),
                          ),
                          SizedBox(width: isWide ? 16 : 0, height: isWide ? 0 : 16),

                          // Workspace 2: SaaS Commercial Operations
                          Expanded(
                            flex: isWide ? 1 : 0,
                            child: _buildWorkspaceCard(
                              context: context,
                              title: 'SaaS Management',
                              badgeText: 'COMMERCIAL OPERATIONS',
                              badgeColor: const Color(0xFF7C3AED),
                              icon: Icons.business_outlined,
                              description:
                                  'Commercial portal for managing registered tenants, subscriptions, usage quotas, onboarding wizard, and Report Templates.',
                              buttonLabel: saasSession != null
                                  ? 'Resume SaaS Portal'
                                  : 'Launch SaaS Portal',
                              buttonIcon: Icons.card_membership_outlined,
                              isPrimary: false,
                              activeStatus: saasSession != null
                                  ? 'Authenticated (${saasSession.email})'
                                  : null,
                              onTap: () {
                                if (saasSession != null) {
                                  context.go('/saas/dashboard');
                                } else {
                                  context.go('/saas/login');
                                }
                              },
                            ),
                          ),
                          SizedBox(width: isWide ? 16 : 0, height: isWide ? 0 : 16),

                          // Workspace 3: Master Platform Admin
                          Expanded(
                            flex: isWide ? 1 : 0,
                            child: _buildWorkspaceCard(
                              context: context,
                              title: 'Master Platform Admin',
                              badgeText: 'PLATFORM OPERATIONS',
                              badgeColor: const Color(0xFF0F766E),
                              icon: Icons.admin_panel_settings_outlined,
                              description:
                                  'High-security administrative portal for MoR gateway telemetry, cryptographic Cloud HSM status, and SHA-256 HMAC audit log verification.',
                              buttonLabel: masterAdminSession != null
                                  ? 'Resume Master Admin'
                                  : 'Launch Master Admin',
                              buttonIcon: Icons.shield_outlined,
                              isPrimary: false,
                              activeStatus: masterAdminSession != null
                                  ? 'Authenticated (${masterAdminSession.email})'
                                  : null,
                              onTap: () {
                                if (masterAdminSession != null) {
                                  context.go('/admin/dashboard');
                                } else {
                                  context.go('/admin/login');
                                }
                              },
                            ),
                          ),
                        ],
                      );
                    },
                  ),

                  const SizedBox(height: 36),
                  const Divider(height: 1, color: AppColors.rule),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.verified_outlined, size: 16, color: AppColors.green700),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          'FDRE Ministry of Revenue Directive No. 1142/2026 Compliant Architecture',
                          style: AppTypography.uiLabel(color: AppColors.inkMuted),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
    );
  }

  Widget _buildWorkspaceCard({
    required BuildContext context,
    required String title,
    required String badgeText,
    required Color badgeColor,
    required IconData icon,
    required String description,
    required String buttonLabel,
    required IconData buttonIcon,
    required bool isPrimary,
    required VoidCallback onTap,
    String? activeStatus,
  }) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(
          color: activeStatus != null ? badgeColor.withValues(alpha: 0.6) : AppColors.rule,
          width: activeStatus != null ? 1.5 : 1,
        ),
        boxShadow: [
          BoxShadow(
            color: AppColors.ink.withValues(alpha: 0.04),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 12,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            alignment: WrapAlignment.spaceBetween,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: badgeColor.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Icon(icon, color: badgeColor, size: 22),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: badgeColor.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: badgeColor.withValues(alpha: 0.3)),
                ),
                child: Text(
                  badgeText,
                  style: AppTypography.monoSmall(
                    color: badgeColor,
                    weight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            title,
            style: AppTypography.h2(color: AppColors.navy900),
          ),
          const SizedBox(height: 8),
          Text(
            description,
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
            maxLines: 4,
          ),
          const SizedBox(height: 20),
          if (activeStatus != null) ...[
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: AppColors.green700.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.green700.withValues(alpha: 0.3)),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 6,
                    height: 6,
                    decoration: const BoxDecoration(
                      color: AppColors.green700,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      activeStatus,
                      style: AppTypography.monoSmall(color: AppColors.green700, weight: FontWeight.w600),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: onTap,
              icon: Icon(buttonIcon, size: 18),
              label: Text(buttonLabel),
              style: ElevatedButton.styleFrom(
                backgroundColor: isPrimary ? AppColors.navy900 : badgeColor,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(3)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
