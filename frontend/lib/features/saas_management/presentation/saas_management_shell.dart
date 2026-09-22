import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';

final saasSidebarCollapsedProvider = StateProvider<bool>((ref) => false);

class SaasManagementShell extends ConsumerStatefulWidget {
  final Widget child;

  const SaasManagementShell({super.key, required this.child});

  @override
  ConsumerState<SaasManagementShell> createState() => _SaasManagementShellState();
}

class _SaasManagementShellState extends ConsumerState<SaasManagementShell> with WidgetsBindingObserver {
  double? _lastWidth;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeMetrics() {
    super.didChangeMetrics();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _syncResponsiveSidebar(MediaQuery.sizeOf(context).width);
      }
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncResponsiveSidebar(MediaQuery.sizeOf(context).width);
  }

  void _syncResponsiveSidebar(double width) {
    const double tabletBreakpoint = 1050.0;
    final isSmallTab = width < tabletBreakpoint;

    if (_lastWidth == null) {
      if (isSmallTab) {
        Future.microtask(() {
          if (mounted) {
            ref.read(saasSidebarCollapsedProvider.notifier).state = true;
          }
        });
      }
    } else {
      final wasSmall = _lastWidth! < tabletBreakpoint;
      if (!wasSmall && isSmallTab) {
        // Automatically collapse when window is resized to small tab size
        Future.microtask(() {
          if (mounted) {
            ref.read(saasSidebarCollapsedProvider.notifier).state = true;
          }
        });
      } else if (wasSmall && !isSmallTab) {
        // Automatically expand when resized larger than small tab size
        Future.microtask(() {
          if (mounted) {
            ref.read(saasSidebarCollapsedProvider.notifier).state = false;
          }
        });
      }
    }
    _lastWidth = width;
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(saasSessionProvider);
    final isCollapsed = ref.watch(saasSidebarCollapsedProvider);

    return Scaffold(
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: Container(
          decoration: const BoxDecoration(
            color: AppColors.paperRaised,
            border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final isWide = constraints.maxWidth >= 1200;
              final isMedium = constraints.maxWidth >= 950;
              final isCompact = constraints.maxWidth < 750;
              final isNarrow = constraints.maxWidth < 650;

              return Row(
                children: [
                  IconButton(
                    icon: Icon(
                      isCollapsed ? Icons.menu : Icons.menu_open,
                      size: 20,
                      color: AppColors.navy700,
                    ),
                    tooltip: isCollapsed ? 'Expand sidebar' : 'Collapse sidebar',
                    onPressed: () {
                      ref.read(saasSidebarCollapsedProvider.notifier).state = !isCollapsed;
                    },
                  ),
                  const SizedBox(width: 8),
                  UtInvoiceLogo(
                    size: 28,
                    showWordmark: isMedium,
                    showParentCredit: isWide,
                  ),
                  SizedBox(width: isCompact ? 8 : 16),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.navy700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: AppColors.navy700.withValues(alpha: 0.3)),
                    ),
                    child: Text(
                      isNarrow ? 'SaaS' : 'COMMERCIAL SaaS PORTAL',
                      style: AppTypography.monoSmall(
                        color: AppColors.navy700,
                        weight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const Spacer(),

                  // Operator Context
                  if (isMedium)
                    Row(
                      children: [
                        const Icon(Icons.support_agent_outlined, size: 18, color: AppColors.inkMuted),
                        const SizedBox(width: 8),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(session?.name ?? 'SaaS Commercial Lead', style: AppTypography.uiLabelBold()),
                            Text(session?.email ?? 'operations@utsolutionsplc.com', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                          ],
                        ),
                        const SizedBox(width: 16),
                        OutlinedButton.icon(
                          onPressed: () async {
                            await ref.read(saasSessionProvider.notifier).logout();
                            if (context.mounted) {
                              context.go('/saas/login');
                            }
                          },
                          icon: const Icon(Icons.logout, size: 14),
                          label: const Text('Sign Out'),
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                          ),
                        ),
                      ],
                    )
                  else
                    IconButton(
                      icon: const Icon(Icons.logout, size: 20, color: AppColors.inkMuted),
                      tooltip: 'Sign Out',
                      onPressed: () async {
                        await ref.read(saasSessionProvider.notifier).logout();
                        if (context.mounted) {
                          context.go('/saas/login');
                        }
                      },
                    ),
                ],
              );
            },
          ),
        ),
      ),
      body: Row(
        children: [
          // Sidebar
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeInOutCubic,
            width: isCollapsed ? 64 : 240,
            decoration: const BoxDecoration(
              color: AppColors.paperRaised,
              border: Border(right: BorderSide(color: AppColors.rule, width: 1)),
            ),
            child: Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    children: [
                      _buildNavItem(
                        context,
                        icon: Icons.dashboard_outlined,
                        label: 'SaaS Dashboard',
                        route: '/saas/dashboard',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.person_add_alt_1_outlined,
                        label: 'Tenant Onboarding',
                        route: '/saas/onboarding',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.domain_outlined,
                        label: 'Tenant Lifecycle',
                        route: '/saas/tenants',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.card_membership_outlined,
                        label: 'Plans & Subscriptions',
                        route: '/saas/subscriptions',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.data_usage_outlined,
                        label: 'Usage & Quotas',
                        route: '/saas/usage',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.assessment_outlined,
                        label: 'Report Templates',
                        route: '/saas/reports',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.headset_mic_outlined,
                        label: 'Support & Diagnostics',
                        route: '/saas/support',
                        isCollapsed: isCollapsed,
                      ),
                    ],
                  ),
                ),
                const Divider(height: 1, color: AppColors.rule),
                InkWell(
                  onTap: () {
                    ref.read(saasSidebarCollapsedProvider.notifier).state = !isCollapsed;
                  },
                  child: Container(
                    height: 48,
                    padding: EdgeInsets.symmetric(horizontal: isCollapsed ? 0 : 16),
                    alignment: isCollapsed ? Alignment.center : Alignment.centerLeft,
                    child: isCollapsed
                        ? const Center(
                            child: Icon(Icons.keyboard_double_arrow_right, size: 18, color: AppColors.inkMuted),
                          )
                        : Row(
                            children: [
                              const Icon(Icons.keyboard_double_arrow_left, size: 18, color: AppColors.inkMuted),
                              const SizedBox(width: 12),
                              Text('Collapse sidebar', style: AppTypography.uiLabel(color: AppColors.inkMuted)),
                            ],
                          ),
                  ),
                ),
              ],
            ),
          ),
          // Content
          Expanded(child: widget.child),
        ],
      ),
    );
  }

  Widget _buildNavItem(
    BuildContext context, {
    required IconData icon,
    required String label,
    required String route,
    required bool isCollapsed,
  }) {
    final location = GoRouterState.of(context).matchedLocation;
    final isSelected = location.startsWith(route);

    if (isCollapsed) {
      return Tooltip(
        message: label,
        waitDuration: const Duration(milliseconds: 150),
        child: InkWell(
          onTap: () => context.go(route),
          child: Container(
            height: 48,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: isSelected ? AppColors.navy700.withValues(alpha: 0.08) : Colors.transparent,
              border: Border(
                left: BorderSide(
                  color: isSelected ? AppColors.navy700 : Colors.transparent,
                  width: 3,
                ),
              ),
            ),
            child: Icon(
              icon,
              size: 22,
              color: isSelected ? AppColors.navy700 : AppColors.inkMuted,
            ),
          ),
        ),
      );
    }

    return InkWell(
      onTap: () => context.go(route),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.navy700.withValues(alpha: 0.06) : Colors.transparent,
          border: Border(
            left: BorderSide(
              color: isSelected ? AppColors.navy700 : Colors.transparent,
              width: 3,
            ),
          ),
        ),
        child: Row(
          children: [
            Icon(
              icon,
              size: 20,
              color: isSelected ? AppColors.navy700 : AppColors.inkMuted,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                style: isSelected
                    ? AppTypography.uiLabelBold(color: AppColors.navy700)
                    : AppTypography.uiLabel(color: AppColors.ink),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
