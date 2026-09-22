import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/connectivity/connectivity_service.dart';
import '../../../core/synchronization/sync_state_notifier.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../core/authentication/multi_gateway_session.dart';
import '../../../core/di/providers.dart';
import '../../../shared/widgets/brand/ut_invoice_logo.dart';
import '../../../shared/widgets/status/status_pill.dart';

final appLocaleProvider = StateProvider<Locale>((ref) => const Locale('en'));

final sidebarCollapsedProvider = StateProvider<bool>((ref) => false);

class EnterpriseShell extends ConsumerStatefulWidget {
  final Widget child;

  const EnterpriseShell({super.key, required this.child});

  @override
  ConsumerState<EnterpriseShell> createState() => _EnterpriseShellState();
}

class _EnterpriseShellState extends ConsumerState<EnterpriseShell>
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
            ref.read(sidebarCollapsedProvider.notifier).state = true;
          }
        });
      }
    } else {
      final wasSmall = _lastWidth! < tabletBreakpoint;
      if (!wasSmall && isSmallTab) {
        // Automatically collapse when window is resized to small tab size
        Future.microtask(() {
          if (mounted) {
            ref.read(sidebarCollapsedProvider.notifier).state = true;
          }
        });
      } else if (wasSmall && !isSmallTab) {
        // Automatically expand when resized larger than small tab size
        Future.microtask(() {
          if (mounted) {
            ref.read(sidebarCollapsedProvider.notifier).state = false;
          }
        });
      }
    }
    _lastWidth = width;
  }

  @override
  Widget build(BuildContext context) {
    final tenantState = ref.watch(tenantContextProvider);
    final syncState = ref.watch(syncStateProvider);
    final connectionStatus =
        ref.watch(connectionStatusProvider).value ??
        ConnectionStatus.serverAvailable;
    final loc = AppLocalizations.of(context);
    final currentLocale = ref.watch(appLocaleProvider);
    final session = ref.watch(authSessionProvider);
    final isCollapsed = ref.watch(sidebarCollapsedProvider);
    final delegatedSession = ref.watch(delegatedTenantSessionProvider);

    return Scaffold(
      appBar: PreferredSize(
          preferredSize: const Size.fromHeight(64),
          child: Container(
            decoration: const BoxDecoration(
              color: AppColors.paperRaised,
              border: Border(
                bottom: BorderSide(color: AppColors.rule, width: 1),
              ),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final isWide = constraints.maxWidth >= 1200;
                final isMedium = constraints.maxWidth >= 960;
                final isCompact = constraints.maxWidth < 780;
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
                        ref.read(sidebarCollapsedProvider.notifier).state =
                            !isCollapsed;
                      },
                    ),
                    const SizedBox(width: 4),
                    UtInvoiceLogo(
                      size: 28,
                      showWordmark: isMedium,
                      showParentCredit: isWide,
                    ),
                    SizedBox(width: isCompact ? 8 : 16),

                    // Active Tenant Selector
                    if (tenantState.activeTenant != null) ...[
                      Flexible(
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            maxWidth: isWide
                                ? 240
                                : (isMedium ? 180 : (isNarrow ? 120 : 150)),
                          ),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.paper,
                              borderRadius: BorderRadius.circular(3),
                              border: Border.all(color: AppColors.rule),
                            ),
                            child: DropdownButtonHideUnderline(
                              child: DropdownButton<String>(
                                value: tenantState.activeTenant!.id,
                                isDense: true,
                                isExpanded: true,
                                icon: const Icon(
                                  Icons.keyboard_arrow_down,
                                  size: 18,
                                  color: AppColors.inkMuted,
                                ),
                                style: AppTypography.uiLabelBold(
                                  color: AppColors.ink,
                                ),
                                items: tenantState.authorizedTenants.map((t) {
                                  return DropdownMenuItem(
                                    value: t.id,
                                    child: Text(
                                      isWide
                                          ? '${t.name} [TIN: ${t.tin}]'
                                          : t.name,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  );
                                }).toList(),
                                onChanged: (newId) {
                                  if (newId != null) {
                                    final selected = tenantState
                                        .authorizedTenants
                                        .firstWhere((t) => t.id == newId);
                                    ref
                                        .read(tenantContextProvider.notifier)
                                        .switchTenant(
                                          selected,
                                          tenantState.authorizedBranches,
                                        );
                                  }
                                },
                              ),
                            ),
                          ),
                        ),
                      ),

                      // Active Branch Selector (Only show if not in narrow mode)
                      if (tenantState.authorizedBranches.isNotEmpty &&
                          !isNarrow) ...[
                        const SizedBox(width: 6),
                        Flexible(
                          child: ConstrainedBox(
                            constraints: BoxConstraints(
                              maxWidth: isWide ? 180 : (isMedium ? 140 : 110),
                            ),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: AppColors.paper,
                                borderRadius: BorderRadius.circular(3),
                                border: Border.all(color: AppColors.rule),
                              ),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: tenantState.activeBranch?.id,
                                  isDense: true,
                                  isExpanded: true,
                                  icon: const Icon(
                                    Icons.storefront,
                                    size: 16,
                                    color: AppColors.navy700,
                                  ),
                                  style: AppTypography.uiLabelBold(
                                    color: AppColors.navy700,
                                  ),
                                  items: tenantState.authorizedBranches.map((
                                    b,
                                  ) {
                                    return DropdownMenuItem(
                                      value: b.id,
                                      child: Text(
                                        '${b.name} (${b.code})',
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    );
                                  }).toList(),
                                  onChanged: (newBranchId) {
                                    if (newBranchId != null) {
                                      final b = tenantState.authorizedBranches
                                          .firstWhere(
                                            (item) => item.id == newBranchId,
                                          );
                                      ref
                                          .read(tenantContextProvider.notifier)
                                          .switchBranch(b);
                                    }
                                  },
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ],

                    const Spacer(),

                    // Realtime Brand Status Pill
                    StatusPill(
                      status: syncState.status,
                      errorMessage: syncState.lastError,
                    ),
                    SizedBox(width: isCompact ? 6 : 12),

                    // Network Indicator Dot
                    Tooltip(
                      message:
                          connectionStatus == ConnectionStatus.serverAvailable
                          ? 'Backend connected'
                          : 'Offline continuity active',
                      child: Container(
                        padding: EdgeInsets.symmetric(
                          horizontal: isWide ? 8 : 6,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color:
                              connectionStatus ==
                                  ConnectionStatus.serverAvailable
                              ? AppColors.green700.withValues(alpha: 0.08)
                              : AppColors.amber600.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(3),
                          border: Border.all(
                            color:
                                connectionStatus ==
                                    ConnectionStatus.serverAvailable
                                ? AppColors.green700.withValues(alpha: 0.3)
                                : AppColors.amber600.withValues(alpha: 0.3),
                          ),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              connectionStatus ==
                                      ConnectionStatus.serverAvailable
                                  ? Icons.wifi
                                  : Icons.wifi_off,
                              size: 14,
                              color:
                                  connectionStatus ==
                                      ConnectionStatus.serverAvailable
                                  ? AppColors.green700
                                  : AppColors.amber600,
                            ),
                            if (isWide) ...[
                              const SizedBox(width: 6),
                              Text(
                                connectionStatus ==
                                        ConnectionStatus.serverAvailable
                                    ? 'Online'
                                    : 'Offline',
                                style: AppTypography.uiLabel(
                                  color:
                                      connectionStatus ==
                                          ConnectionStatus.serverAvailable
                                      ? AppColors.green700
                                      : AppColors.amber600,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                    SizedBox(width: isCompact ? 6 : 10),

                    // Language Switcher
                    TextButton(
                      onPressed: () {
                        final newLocale = currentLocale.languageCode == 'en'
                            ? const Locale('am')
                            : const Locale('en');
                        ref.read(appLocaleProvider.notifier).state = newLocale;
                      },
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 4,
                        ),
                        minimumSize: const Size(36, 32),
                      ),
                      child: Text(
                        isCompact
                            ? (currentLocale.languageCode == 'en' ? 'አማ' : 'EN')
                            : (currentLocale.languageCode == 'en'
                                  ? 'አማርኛ'
                                  : 'English'),
                        style: AppTypography.uiLabelBold(
                          color: AppColors.navy700,
                        ),
                      ),
                    ),
                    SizedBox(width: isCompact ? 2 : 4),

                    // Operator Profile
                    if (session != null)
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (!isNarrow)
                            const CircleAvatar(
                              radius: 14,
                              backgroundColor: AppColors.navy900,
                              child: Icon(
                                Icons.person,
                                size: 16,
                                color: Colors.white,
                              ),
                            ),
                          if (isMedium) ...[
                            const SizedBox(width: 8),
                            Text(
                              session.username,
                              style: AppTypography.uiLabel(
                                color: AppColors.ink,
                              ),
                            ),
                          ],
                          const SizedBox(width: 4),
                          IconButton(
                            icon: const Icon(
                              Icons.logout,
                              size: 18,
                              color: AppColors.inkMuted,
                            ),
                            tooltip: 'Sign out',
                            onPressed: () async {
                              await ref
                                  .read(authSessionProvider.notifier)
                                  .logout();
                              ref.read(tenantContextProvider.notifier).clear();
                              if (context.mounted) {
                                context.go('/login');
                              }
                            },
                          ),
                        ],
                      ),
                  ],
                );
              },
            ),
          ),
        ),
        body: Column(
          children: [
            if (delegatedSession != null)
              _buildDelegatedTenantBanner(context, delegatedSession),
            Expanded(
              child: Row(
                children: [
                  // Collapsible Side Navigation Bar
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    curve: Curves.easeInOutCubic,
                    width: isCollapsed ? 64 : 220,
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
                                label: loc.get('dashboard'),
                                route: '/dashboard',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.receipt_long_outlined,
                                label: loc.get('invoices'),
                                route: '/invoices',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.price_change_outlined,
                                label: 'Tax Adjustments',
                                route: '/adjustments',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.inventory_2_outlined,
                                label: 'Product Catalog',
                                route: '/catalog/products',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.design_services_outlined,
                                label: 'Service Catalog',
                                route: '/catalog/services',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.category_outlined,
                                label: 'Item Categories',
                                route: '/catalog/categories',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.warehouse_outlined,
                                label: 'Branch Inventory',
                                route: '/inventory',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.people_outline,
                                label: 'Customer Registry',
                                route: '/customers',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.cloud_sync_outlined,
                                label: loc.get('offline_queue'),
                                route: '/offline/queue',
                                badgeCount: syncState.pendingCount,
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.account_balance_outlined,
                                label: 'Government EIRS',
                                route: '/government/status',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.bar_chart_outlined,
                                label: 'Fiscal Reports',
                                route: '/reports',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.verified_user_outlined,
                                label: loc.get('audit_trail'),
                                route: '/audit',
                                isCollapsed: isCollapsed,
                              ),
                              _buildNavItem(
                                context,
                                icon: Icons.settings_outlined,
                                label: 'Settings',
                                route: '/settings',
                                isCollapsed: isCollapsed,
                              ),
                            ],
                          ),
                        ),
                        // Bottom collapse / expand toggle footer
                        const Divider(height: 1, color: AppColors.rule),
                        InkWell(
                          onTap: () {
                            ref.read(sidebarCollapsedProvider.notifier).state =
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
                                ? const Tooltip(
                                    message: 'Expand sidebar',
                                    waitDuration: Duration(milliseconds: 150),
                                    child: Center(
                                      child: Icon(
                                        Icons.keyboard_double_arrow_right,
                                        size: 18,
                                        color: AppColors.inkMuted,
                                      ),
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
                                      Expanded(
                                        child: Text(
                                          'Collapse sidebar',
                                          style: AppTypography.uiLabel(
                                            color: AppColors.inkMuted,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    ],
                                  ),
                          ),
                        ),
                        if (!isCollapsed)
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 8,
                            ),
                            alignment: Alignment.centerLeft,
                            child: Text(
                              'UT Invoice Frontend - Build: v2-resilient\nNetwork Resilience: Active',
                              style: const TextStyle(
                                fontSize: 10,
                                color: AppColors.inkMuted,
                                height: 1.3,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  // Content View Area
                  Expanded(child: widget.child),
                ],
              ),
            ),
          ],
        ),
    );
  }

  Widget _buildDelegatedTenantBanner(
    BuildContext context,
    DelegatedTenantSession delegatedSession,
  ) {
    final isReadOnly = delegatedSession.accessType == 'READ_ONLY_SUPPORT';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: isReadOnly ? const Color(0xFF1E293B) : const Color(0xFF78350F),
        border: const Border(
          bottom: BorderSide(color: Color(0xFFF59E0B), width: 2),
        ),
      ),
      child: Row(
        children: [
          Icon(
            isReadOnly ? Icons.visibility_outlined : Icons.science_outlined,
            color: const Color(0xFFFDE68A),
            size: 20,
          ),
          const SizedBox(width: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: const Color(0xFFF59E0B),
              borderRadius: BorderRadius.circular(3),
            ),
            child: Text(
              isReadOnly
                  ? 'READ-ONLY SUPPORT SESSION'
                  : 'TENANT TEST MODE (MUTABLE)',
              style: const TextStyle(
                color: Colors.black,
                fontWeight: FontWeight.bold,
                fontSize: 10,
                letterSpacing: 0.5,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Testing as: ${delegatedSession.targetTenantName} [TIN: ${delegatedSession.targetTenantTin}]  •  Operator: ${delegatedSession.delegatedBy}  •  Expires: ${delegatedSession.expiresAt.toLocal().toString().substring(11, 16)}',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(width: 12),
          ElevatedButton.icon(
            onPressed: () async {
              try {
                final client = ref.read(saasManagementApiClientProvider);
                await client.post(
                  '/api/v1/saas/tenants/${delegatedSession.targetTenantId}/terminate-session',
                  queryParameters: {'sessionId': delegatedSession.sessionId},
                );
              } catch (_) {
                // Ignore network errors on cleanup
              }
              ref.read(delegatedTenantSessionProvider.notifier).clearSession();
              await ref
                  .read(secureStorageProvider)
                  .wipeDelegatedTenantSession();
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Delegated tenant session terminated.'),
                    backgroundColor: AppColors.navy900,
                  ),
                );
                final hasMaster =
                    ref.read(saasSessionProvider) != null ||
                    ref.read(masterAdminSessionProvider) != null;
                if (hasMaster) {
                  context.go('/saas/tenants');
                } else {
                  context.go('/');
                }
              }
            },
            icon: const Icon(Icons.exit_to_app, size: 14, color: Colors.white),
            label: const Text(
              'Exit Tenant Environment',
              style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFDC2626),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              minimumSize: const Size(0, 30),
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
    int badgeCount = 0,
    required bool isCollapsed,
  }) {
    final location = GoRouterState.of(context).matchedLocation;
    final isSelected = location.startsWith(route);

    if (isCollapsed) {
      return Tooltip(
        message: badgeCount > 0 ? '$label ($badgeCount)' : label,
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
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Icon(
                  icon,
                  size: 22,
                  color: isSelected ? AppColors.navy900 : AppColors.inkMuted,
                ),
                if (badgeCount > 0)
                  Positioned(
                    top: -2,
                    right: -4,
                    child: Container(
                      width: 8,
                      height: 8,
                      decoration: const BoxDecoration(
                        color: AppColors.amber600,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
              ],
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
            if (badgeCount > 0)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.amber600,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  '$badgeCount',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
