import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../authentication/multi_gateway_session.dart';
import '../app_surface.dart';
import '../route_recovery_service.dart';
import '../route_recovery_state.dart';

/// Premium Enterprise Route Recovery Screen for UT Electronic Invoicing.
/// Replaces raw GoException and generic 404 pages with an app-aware, role-aware,
/// secure recovery experience conforming to Directive No. 1142/2026 platform standards.
class RouteRecoveryScreen extends ConsumerWidget {
  final GoRouterState state;
  final AppSurface? defaultSurface;

  const RouteRecoveryScreen({
    super.key,
    required this.state,
    this.defaultSurface,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tenantSession = ref.watch(authSessionProvider);
    final delegatedSession = ref.watch(delegatedTenantSessionProvider);
    final saasSession = ref.watch(saasSessionProvider);
    final masterAdminSession = ref.watch(masterAdminSessionProvider);

    final service = ref.watch(routeRecoveryServiceProvider);
    final recoveryState = service.resolveRecoveryState(
      rawAttemptedPath: state.uri.toString(),
      tenantSession: tenantSession,
      delegatedSession: delegatedSession,
      saasSession: saasSession,
      masterAdminSession: masterAdminSession,
      surfaceHint: defaultSurface,
      canGoBack: context.canPop(),
    );

    final isNarrow = MediaQuery.of(context).size.width < 600;
    final loc = AppLocalizations.of(context);

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: EdgeInsets.symmetric(
              horizontal: isNarrow ? 20 : 32,
              vertical: 24,
            ),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 620),
              child: _buildRecoveryCard(context, ref, recoveryState, isNarrow, loc),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRecoveryCard(
    BuildContext context,
    WidgetRef ref,
    RouteRecoveryState recoveryState,
    bool isNarrow,
    AppLocalizations loc,
  ) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.paper,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.rule, width: 1.5),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0A0F172A),
            blurRadius: 24,
            offset: Offset(0, 12),
          ),
        ],
      ),
      padding: EdgeInsets.all(isNarrow ? 24 : 40),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // 1. Top UT Shield Emblem
          _buildBrandEmblem(recoveryState),
          const SizedBox(height: 24),

          // 2. Status Badge / Surface Pill
          Wrap(
            spacing: 8,
            runSpacing: 8,
            alignment: WrapAlignment.center,
            children: [
              _buildPill(
                label: recoveryState.appSurface.badgeLabel,
                color: recoveryState.appSurface.badgeColor,
                icon: Icons.layers_outlined,
              ),
              _buildPill(
                label: '${recoveryState.failureType.statusCode} — ${recoveryState.failureType.name.toUpperCase()}',
                color: recoveryState.failureType == RouteFailureType.accessDenied
                    ? AppColors.error
                    : AppColors.inkMuted,
                icon: recoveryState.failureType == RouteFailureType.accessDenied
                    ? Icons.lock_outline
                    : Icons.error_outline,
              ),
            ],
          ),
          const SizedBox(height: 20),

          // 3. Primary Title
          Text(
            recoveryState.displayTitle,
            style: AppTypography.titleLarge.copyWith(
              color: AppColors.ink,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.5,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 12),

          // 4. Descriptive Explanation
          Text(
            recoveryState.displayDescription,
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.inkMuted,
              height: 1.5,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 20),

          // 5. Sanitized Attempted Destination (Safe Display)
          if (!recoveryState.shouldHideAttemptedPath)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: AppColors.rule),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.link_off, size: 16, color: AppColors.inkMuted),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      recoveryState.attemptedPath,
                      style: AppTypography.monospace(
                        size: 13,
                        color: AppColors.ink,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 32),

          // 6. Action CTAs
          Wrap(
            alignment: WrapAlignment.center,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 14,
            runSpacing: 12,
            children: [
              _buildSecondaryButton(context, recoveryState, loc),
              _buildPrimaryButton(context, recoveryState),
            ],
          ),
          const SizedBox(height: 32),

          // 7. Statutory Platform Footer
          const Divider(height: 1, color: AppColors.rule),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.verified_user_outlined, size: 14, color: AppColors.inkMuted),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  'UT Electronic Invoicing • Directive No. 1142/2026 Aligned Platform',
                  style: AppTypography.caption.copyWith(color: AppColors.inkMuted),
                  textAlign: TextAlign.center,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildBrandEmblem(RouteRecoveryState state) {
    Color emblemColor;
    IconData emblemIcon;

    switch (state.failureType) {
      case RouteFailureType.accessDenied:
        emblemColor = AppColors.error;
        emblemIcon = Icons.shield_outlined;
        break;
      case RouteFailureType.sessionRequired:
        emblemColor = AppColors.warning;
        emblemIcon = Icons.lock_clock_outlined;
        break;
      case RouteFailureType.notFound:
        emblemColor = state.appSurface.badgeColor;
        emblemIcon = Icons.explore_off_outlined;
        break;
    }

    return Container(
      width: 68,
      height: 68,
      decoration: BoxDecoration(
        color: emblemColor.withValues(alpha: 0.1),
        shape: BoxShape.circle,
        border: Border.all(color: emblemColor.withValues(alpha: 0.25), width: 2),
      ),
      child: Center(
        child: Icon(emblemIcon, size: 34, color: emblemColor),
      ),
    );
  }

  Widget _buildPill({
    required String label,
    required Color color,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.2)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: color),
          const SizedBox(width: 5),
          Text(
            label,
            style: AppTypography.caption.copyWith(
              color: color,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPrimaryButton(BuildContext context, RouteRecoveryState state) {
    return ElevatedButton.icon(
      onPressed: () {
        context.go(state.safeHomeRoute);
      },
      icon: Icon(
        state.authenticated ? Icons.dashboard_outlined : Icons.login_outlined,
        size: 18,
      ),
      label: Text(state.safeHomeLabel),
      style: ElevatedButton.styleFrom(
        backgroundColor: state.appSurface.badgeColor,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        elevation: 0,
      ),
    );
  }

  Widget _buildSecondaryButton(
    BuildContext context,
    RouteRecoveryState state,
    AppLocalizations loc,
  ) {
    return OutlinedButton.icon(
      onPressed: () {
        if (context.canPop()) {
          context.pop();
        } else {
          context.go(state.safeHomeRoute);
        }
      },
      icon: const Icon(Icons.arrow_back, size: 18),
      label: Text(loc.get('goBack')),
      style: OutlinedButton.styleFrom(
        foregroundColor: AppColors.ink,
        side: const BorderSide(color: AppColors.rule),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }
}
