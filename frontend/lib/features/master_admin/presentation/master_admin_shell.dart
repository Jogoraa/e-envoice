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
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
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
        Future.microtask(() {
          if (mounted) {
            ref.read(adminSidebarCollapsedProvider.notifier).state = true;
          }
        });
      } else if (wasSmall && !isSmallTab) {
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
    final screenWidth = MediaQuery.sizeOf(context).width;
    final isMobile = screenWidth < 750;

    return Scaffold(
      key: _scaffoldKey,
      drawer: isMobile
          ? Drawer(
              backgroundColor: AppColors.paperRaised,
              child: SafeArea(
                child: _buildSidebarContent(
                  context,
                  isCollapsed: false,
                  inDrawer: true,
                  session: session,
                ),
              ),
            )
          : null,
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
              final isNarrow = constraints.maxWidth < 650;

              return Row(
                children: [
                  IconButton(
                    icon: Icon(
                      isMobile
                          ? Icons.menu
                          : (isCollapsed ? Icons.menu : Icons.menu_open),
                      size: 20,
                      color: AppColors.navy900,
                    ),
                    tooltip: isMobile
                        ? 'Open navigation menu'
                        : (isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'),
                    onPressed: () {
                      if (isMobile) {
                        _scaffoldKey.currentState?.openDrawer();
                      } else {
                        ref.read(adminSidebarCollapsedProvider.notifier).state =
                            !isCollapsed;
                      }
                    },
                  ),
                  const SizedBox(width: 8),
                  UtInvoiceLogo(
                    size: 28,
                    showWordmark: isMedium,
                    showParentCredit: isWide,
                  ),
                  const SizedBox(width: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.navy900.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(
                        color: AppColors.navy900.withValues(alpha: 0.25),
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

                  // Security Administrator Quick Profile & Logout
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
                              session?.name ?? 'Dave',
                              style: AppTypography.uiLabelBold(),
                            ),
                            Text(
                              session?.email ?? 'platform.admin@utsolutionsplc.com',
                              style: AppTypography.monoSmall(
                                color: AppColors.inkMuted,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(width: 16),
                        OutlinedButton.icon(
                          onPressed: () => _handleLogout(context),
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
                      onPressed: () => _handleLogout(context),
                    ),
                ],
              );
            },
          ),
        ),
      ),
      body: Row(
        children: [
          // Sidebar (desktop/tablet only)
          if (!isMobile)
            AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeInOutCubic,
              width: isCollapsed ? 64 : 250,
              decoration: const BoxDecoration(
                color: AppColors.paperRaised,
                border: Border(
                  right: BorderSide(color: AppColors.rule, width: 1),
                ),
              ),
              child: _buildSidebarContent(
                context,
                isCollapsed: isCollapsed,
                inDrawer: false,
                session: session,
              ),
            ),
          // Main Work Area
          Expanded(child: widget.child),
        ],
      ),
    );
  }

  Widget _buildSidebarContent(
    BuildContext context, {
    required bool isCollapsed,
    required bool inDrawer,
    required dynamic session,
  }) {
    return Column(
      children: [
        // Navigation List
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(vertical: 8),
            children: [
              // Top-level Dashboard
              _buildNavItem(
                context,
                icon: Icons.dashboard_outlined,
                label: 'Dashboard',
                route: '/admin/dashboard',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),

              // SECTION: PLATFORM
              _buildSectionHeader('PLATFORM', isCollapsed),
              _buildNavItem(
                context,
                icon: Icons.policy_outlined,
                label: 'Tenants',
                route: '/admin/tenants',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.people_alt_outlined,
                label: 'Administrators',
                route: '/admin/users',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.admin_panel_settings_outlined,
                label: 'Roles & Permissions',
                route: '/admin/roles',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),

              // SECTION: OPERATIONS
              _buildSectionHeader('OPERATIONS', isCollapsed),
              _buildNavItem(
                context,
                icon: Icons.fact_check_outlined,
                label: 'System Health',
                route: '/admin/readiness',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.lock_clock_outlined,
                label: 'Environment & Secrets',
                route: '/admin/system/environment',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.router_outlined,
                label: 'Gateway Monitor',
                route: '/admin/gateway',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.api_outlined,
                label: 'API & Messaging',
                route: '/admin/api-management',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),

              // SECTION: SECURITY & GOVERNANCE
              _buildSectionHeader('SECURITY & GOVERNANCE', isCollapsed),
              _buildNavItem(
                context,
                icon: Icons.tune_outlined,
                label: 'Security Center',
                route: '/admin/config',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.assignment_turned_in_outlined,
                label: 'Access Reviews',
                route: '/admin/access-reviews',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.devices_outlined,
                label: 'Sessions & Devices',
                route: '/admin/sessions',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
              _buildNavItem(
                context,
                icon: Icons.security_outlined,
                label: 'Audit & Security Events',
                route: '/admin/audit',
                isCollapsed: isCollapsed,
                inDrawer: inDrawer,
              ),
            ],
          ),
        ),

        // SECTION: ACCOUNT (STRICTLY AT BOTTOM)
        const Divider(height: 1, color: AppColors.rule),
        _buildBottomAccountArea(context, isCollapsed, session, inDrawer),

        // Collapse / Expand toggle button (desktop only)
        if (!inDrawer) ...[
          const Divider(height: 1, color: AppColors.rule),
          InkWell(
            onTap: () {
              ref.read(adminSidebarCollapsedProvider.notifier).state =
                  !isCollapsed;
            },
            child: Container(
              height: 44,
              padding: EdgeInsets.symmetric(
                horizontal: isCollapsed ? 0 : 16,
              ),
              alignment: isCollapsed ? Alignment.center : Alignment.centerLeft,
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
                        Flexible(
                          child: Text(
                            'Collapse navigation',
                            overflow: TextOverflow.ellipsis,
                            style: AppTypography.uiLabel(
                              color: AppColors.inkMuted,
                            ),
                          ),
                        ),
                      ],
                    ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildSectionHeader(String title, bool isCollapsed) {
    if (isCollapsed) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Container(
          height: 1,
          margin: const EdgeInsets.symmetric(horizontal: 16),
          color: AppColors.rule,
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(left: 16, right: 16, top: 18, bottom: 6),
      child: Text(
        title,
        style: AppTypography.monoSmall(
          color: AppColors.inkMuted,
          weight: FontWeight.w700,
        ).copyWith(letterSpacing: 0.8, fontSize: 10.5),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  Widget _buildBottomAccountArea(
    BuildContext context,
    bool isCollapsed,
    dynamic session,
    bool inDrawer,
  ) {
    String location = '';
    try {
      location = GoRouterState.of(context).matchedLocation;
    } catch (_) {
      location = ModalRoute.of(context)?.settings.name ?? '';
    }
    final isSettingsSelected = location.startsWith('/admin/account/settings');
    final adminName = session?.name ?? 'Dave';
    final adminInitials = _extractInitials(adminName);

    if (isCollapsed) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          children: [
            Tooltip(
              message: '$adminName (Platform Admin)',
              child: CircleAvatar(
                radius: 16,
                backgroundColor: AppColors.navy900,
                child: Text(
                  adminInitials,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Tooltip(
              message: 'Account Settings',
              child: IconButton(
                icon: Icon(
                  Icons.manage_accounts_outlined,
                  size: 20,
                  color: isSettingsSelected
                      ? AppColors.navy900
                      : AppColors.inkMuted,
                ),
                onPressed: () {
                  context.go('/admin/account/settings');
                },
              ),
            ),
            Tooltip(
              message: 'Sign Out',
              child: IconButton(
                icon: const Icon(
                  Icons.logout,
                  size: 20,
                  color: AppColors.inkMuted,
                ),
                onPressed: () => _handleLogout(context),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      color: AppColors.paper,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            child: Text(
              'ACCOUNT',
              style: AppTypography.monoSmall(
                color: AppColors.inkMuted,
                weight: FontWeight.w700,
              ).copyWith(letterSpacing: 0.8, fontSize: 10),
            ),
          ),
          const SizedBox(height: 8),
          // User Card
          Row(
            children: [
              CircleAvatar(
                radius: 18,
                backgroundColor: AppColors.navy900,
                child: Text(
                  adminInitials,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      adminName,
                      style: AppTypography.uiLabelBold(color: AppColors.ink),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Row(
                      children: [
                        Container(
                          width: 6,
                          height: 6,
                          decoration: const BoxDecoration(
                            color: AppColors.green700,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 4),
                        Flexible(
                          child: Text(
                            'Platform Admin',
                            style: AppTypography.monoSmall(
                              color: AppColors.inkMuted,
                            ).copyWith(fontSize: 10.5),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          // Account Settings Item
          InkWell(
            onTap: () {
              if (inDrawer) Navigator.of(context).pop();
              context.go('/admin/account/settings');
            },
            borderRadius: BorderRadius.circular(4),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              decoration: BoxDecoration(
                color: isSettingsSelected
                    ? AppColors.navy900.withValues(alpha: 0.08)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.manage_accounts_outlined,
                    size: 18,
                    color: isSettingsSelected
                        ? AppColors.navy900
                        : AppColors.inkMuted,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Account Settings',
                      style: isSettingsSelected
                          ? AppTypography.uiLabelBold(color: AppColors.navy900)
                          : AppTypography.uiLabel(color: AppColors.ink),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 4),
          // Sign Out Action
          InkWell(
            onTap: () => _handleLogout(context),
            borderRadius: BorderRadius.circular(4),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              child: Row(
                children: [
                  const Icon(
                    Icons.logout,
                    size: 18,
                    color: AppColors.inkMuted,
                  ),
                  const SizedBox(width: 10),
                  Text(
                    'Sign Out',
                    style: AppTypography.uiLabel(color: AppColors.inkMuted),
                  ),
                ],
              ),
            ),
          ),
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
    required bool inDrawer,
  }) {
    String location = '';
    try {
      location = GoRouterState.of(context).matchedLocation;
    } catch (_) {
      location = ModalRoute.of(context)?.settings.name ?? '';
    }
    // Support matching both /admin/roles and /admin/roles-permissions
    final isSelected = location.startsWith(route) ||
        (route == '/admin/roles' && location.startsWith('/admin/roles-permissions'));

    if (isCollapsed) {
      return Tooltip(
        message: label,
        waitDuration: const Duration(milliseconds: 150),
        child: InkWell(
          onTap: () {
            if (inDrawer) Navigator.of(context).pop();
            context.go(route);
          },
          child: Container(
            height: 44,
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
              size: 20,
              color: isSelected ? AppColors.navy900 : AppColors.inkMuted,
            ),
          ),
        ),
      );
    }

    return InkWell(
      onTap: () {
        if (inDrawer) Navigator.of(context).pop();
        context.go(route);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 9),
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
              size: 19,
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

  String _extractInitials(String name) {
    if (name.trim().isEmpty) return 'AD';
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return (name.length >= 2 ? name.substring(0, 2) : name).toUpperCase();
  }

  Future<void> _handleLogout(BuildContext context) async {
    await ref.read(masterAdminSessionProvider.notifier).logout();
    if (context.mounted) {
      context.go('/admin/login');
    }
  }
}
