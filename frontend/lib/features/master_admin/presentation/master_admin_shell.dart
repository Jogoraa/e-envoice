import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';

final adminSidebarCollapsedProvider = StateProvider<bool>((ref) => false);

class MasterAdminShell extends ConsumerStatefulWidget {
  final Widget child;

  const MasterAdminShell({super.key, required this.child});

  @override
  ConsumerState<MasterAdminShell> createState() => _MasterAdminShellState();
}

class _MasterAdminShellState extends ConsumerState<MasterAdminShell>
    with WidgetsBindingObserver {
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
            ref.read(adminSidebarCollapsedProvider.notifier).state = true;
          }
        });
      }
    } else {
      final wasSmall = _lastWidth! < tabletBreakpoint;
      if (!wasSmall && isSmallTab) {
        // Automatically collapse when window is resized to small tab size
        Future.microtask(() {
          if (mounted) {
            ref.read(adminSidebarCollapsedProvider.notifier).state = true;
          }
        });
      } else if (wasSmall && !isSmallTab) {
        // Automatically expand when resized larger than small tab size
        Future.microtask(() {
          if (mounted) {
            ref.read(adminSidebarCollapsedProvider.notifier).state = false;
          }
        });
      }
    }
    _lastWidth = width;
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(masterAdminSessionProvider);
    final isCollapsed = ref.watch(adminSidebarCollapsedProvider);

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
                      color: AppColors.navy900,
                    ),
                    tooltip: isCollapsed
                        ? 'Expand sidebar'
                        : 'Collapse sidebar',
                    onPressed: () {
                      ref.read(adminSidebarCollapsedProvider.notifier).state =
                          !isCollapsed;
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
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.navy900.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(
                        color: AppColors.navy900.withValues(alpha: 0.3),
                      ),
                    ),
                    child: Text(
                      isNarrow ? 'ADMIN' : 'MASTER ADMIN (RESTRICTED)',
                      style: AppTypography.monoSmall(
                        color: AppColors.navy900,
                        weight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const Spacer(),

                  // Security Administrator Context
                  if (isMedium)
                    Row(
                      children: [
                        const Icon(
                          Icons.verified_user_outlined,
                          size: 18,
                          color: AppColors.green700,
                        ),
                        const SizedBox(width: 8),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              session?.name ?? 'Lead Platform Security Officer',
                              style: AppTypography.uiLabelBold(),
                            ),
                            Text(
                              session?.email ??
                                  'platform.admin@utsolutionsplc.com',
                              style: AppTypography.monoSmall(
                                color: AppColors.inkMuted,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(width: 16),
                        OutlinedButton.icon(
                          onPressed: () async {
                            await ref
                                .read(masterAdminSessionProvider.notifier)
                                .logout();
                            if (context.mounted) {
                              context.go('/admin/login');
                            }
                          },
                          icon: const Icon(Icons.logout, size: 14),
                          label: const Text('Sign Out'),
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 6,
                            ),
                          ),
                        ),
                      ],
                    )
                  else
                    IconButton(
                      icon: const Icon(
                        Icons.logout,
                        size: 20,
                        color: AppColors.inkMuted,
                      ),
                      tooltip: 'Sign Out',
                      onPressed: () async {
                        await ref
                            .read(masterAdminSessionProvider.notifier)
                            .logout();
                        if (context.mounted) {
                          context.go('/admin/login');
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
              border: Border(
                right: BorderSide(color: AppColors.rule, width: 1),
              ),
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
                        label: 'Platform Dashboard',
                        route: '/admin/dashboard',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.policy_outlined,
                        label: 'Tenant Oversight',
                        route: '/admin/tenants',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.api_outlined,
                        label: 'API Clients & Webhooks',
                        route: '/admin/api-management',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.fact_check_outlined,
                        label: 'System Readiness',
                        route: '/admin/readiness',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.router_outlined,
                        label: 'MoR Gateway Monitor',
                        route: '/admin/gateway',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.security_outlined,
                        label: 'Security & Audit Log',
                        route: '/admin/audit',
                        isCollapsed: isCollapsed,
                      ),
                      _buildNavItem(
                        context,
                        icon: Icons.tune_outlined,
                        label: 'Platform Configuration',
                        route: '/admin/config',
                        isCollapsed: isCollapsed,
                      ),
                    ],
                  ),
                ),
                const Divider(height: 1, color: AppColors.rule),
                InkWell(
                  onTap: () {
                    ref.read(adminSidebarCollapsedProvider.notifier).state =
                        !isCollapsed;
                  },
                  child: Container(
                    height: 48,
                    padding: EdgeInsets.symmetric(
                      horizontal: isCollapsed ? 0 : 16,
                    ),
                    alignment: isCollapsed
                        ? Alignment.center
                        : Alignment.centerLeft,
                    child: isCollapsed
                        ? const Center(
                            child: Icon(
                              Icons.keyboard_double_arrow_right,
                              size: 18,
                              color: AppColors.inkMuted,
                            ),
                          )
                        : Row(
                            children: [
                              const Icon(
                                Icons.keyboard_double_arrow_left,
                                size: 18,
                                color: AppColors.inkMuted,
                              ),
                              const SizedBox(width: 12),
                              Text(
                                'Collapse sidebar',
                                style: AppTypography.uiLabel(
                                  color: AppColors.inkMuted,
                                ),
                              ),
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
              color: isSelected
                  ? AppColors.navy900.withValues(alpha: 0.08)
                  : Colors.transparent,
              border: Border(
                left: BorderSide(
                  color: isSelected ? AppColors.navy900 : Colors.transparent,
                  width: 3,
                ),
              ),
            ),
            child: Icon(
              icon,
              size: 22,
              color: isSelected ? AppColors.navy900 : AppColors.inkMuted,
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
          color: isSelected
              ? AppColors.navy900.withValues(alpha: 0.06)
              : Colors.transparent,
          border: Border(
            left: BorderSide(
              color: isSelected ? AppColors.navy900 : Colors.transparent,
              width: 3,
            ),
          ),
        ),
        child: Row(
          children: [
            Icon(
              icon,
              size: 20,
              color: isSelected ? AppColors.navy900 : AppColors.inkMuted,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                style: isSelected
                    ? AppTypography.uiLabelBold(color: AppColors.navy900)
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
