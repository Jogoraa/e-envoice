import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterRolesPermissionsScreen extends ConsumerStatefulWidget {
  const MasterRolesPermissionsScreen({super.key});

  @override
  ConsumerState<MasterRolesPermissionsScreen> createState() =>
      _MasterRolesPermissionsScreenState();
}

class _MasterRolesPermissionsScreenState
    extends ConsumerState<MasterRolesPermissionsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  bool _isLoading = true;
  String? _error;
  List<dynamic> _roles = [];
  List<dynamic> _permissions = [];
  List<dynamic> _users = [];

  // Active role selected in Permission Matrix
  Map<String, dynamic>? _selectedRoleForMatrix;
  Set<String> _stagedPermissions = {};
  Set<String> _initialRolePermissions = {};

  // Search and Filters
  final TextEditingController _searchController = TextEditingController();
  String _selectedCategoryFilter = 'ALL';
  String _selectedRiskFilter = 'ALL';

  // User access simulation
  String? _selectedUserId;
  Map<String, dynamic>? _effectiveAccess;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _loadData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final rolesRes = await client.get('/api/v1/master/rbac/roles');
      final permsRes = await client.get('/api/v1/master/rbac/permissions');
      final usersRes = await client.get('/api/v1/master/users');

      final rolesList = rolesRes.data is List ? rolesRes.data as List : [];
      final permsList = permsRes.data is List ? permsRes.data as List : [];
      final usersList = usersRes.data is List ? usersRes.data as List : [];

      setState(() {
        _roles = rolesList;
        _permissions = permsList;
        _users = usersList;
        _isLoading = false;

        // Default matrix to first role if available
        if (_roles.isNotEmpty && _selectedRoleForMatrix == null) {
          _selectRoleForMatrix(_roles.first as Map<String, dynamic>);
        }

        // Default user for user access view if available
        if (_users.isNotEmpty && _selectedUserId == null) {
          final firstUser = _users.first as Map<String, dynamic>;
          final id = firstUser['id']?.toString();
          if (id != null) {
            _selectedUserId = id;
            _fetchEffectiveAccess(id);
          }
        }
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to load RBAC governance catalog: $e';
        _isLoading = false;
      });
    }
  }

  void _selectRoleForMatrix(Map<String, dynamic> role) {
    final perms = role['permissions'] as List? ?? [];
    final currentCodes = perms
        .map((p) => p['code']?.toString() ?? '')
        .where((c) => c.isNotEmpty)
        .toSet();

    setState(() {
      _selectedRoleForMatrix = role;
      _initialRolePermissions = Set.from(currentCodes);
      _stagedPermissions = Set.from(currentCodes);
    });
  }

  Future<void> _fetchEffectiveAccess(String userId) async {
    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.get(
        '/api/v1/master/rbac/effective-access/$userId',
      );
      setState(() {
        _effectiveAccess = Map<String, dynamic>.from(res.data as Map);
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to calculate effective access: $e'),
            backgroundColor: AppColors.red700,
          ),
        );
      }
    }
  }

  int _getUserCountForRole(String roleCode) {
    int count = 0;
    for (final u in _users) {
      final roles = u['roles'] as List? ?? [];
      final hasRole = roles.any((r) => r.toString() == roleCode);
      final primaryRole = u['role']?.toString();
      if (hasRole || primaryRole == roleCode) {
        count++;
      }
    }
    return count;
  }

  double _calculateCoverage(List<dynamic>? rolePerms) {
    if (_permissions.isEmpty) return 0.0;
    final granted = rolePerms?.length ?? 0;
    return (granted / _permissions.length).clamp(0.0, 1.0);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _buildErrorState()
          : _buildMainWorkspace(),
    );
  }

  Widget _buildErrorState() {
    return Center(
      child: Container(
        constraints: const Duration(milliseconds: 300) == Duration.zero
            ? null
            : const BoxConstraints(maxWidth: 500),
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: AppColors.paperRaised,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.rule),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, color: AppColors.red700, size: 48),
            const SizedBox(height: 16),
            Text('Authorization Error', style: AppTypography.h3()),
            const SizedBox(height: 8),
            Text(
              _error!,
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: _loadData,
              icon: const Icon(Icons.refresh, size: 16),
              label: const Text('Retry Authorization Synchronization'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMainWorkspace() {
    return Column(
      children: [
        _buildWorkspaceHeader(),
        _buildSummaryCards(),
        _buildTabBar(),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _buildRolesOverviewTab(),
              _buildPermissionMatrixTab(),
              _buildUserAccessViewTab(),
              _buildAccessMatrixComparisonTab(),
            ],
          ),
        ),
        if (_hasStagedChanges && _tabController.index == 1)
          _buildStickyActionBar(),
      ],
    );
  }

  Widget _buildWorkspaceHeader() {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 16),
      decoration: const BoxDecoration(
        color: AppColors.paperRaised,
        border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final isNarrow = constraints.maxWidth < 750;
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Roles & Permissions',
                          style: AppTypography.h2(color: AppColors.navy900),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Manage administrator access, roles, authority scopes and fine-grained permissions.',
                          style: AppTypography.bodySmall(
                            color: AppColors.inkMuted,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (!isNarrow)
                    Row(
                      children: [
                        OutlinedButton.icon(
                          onPressed: () => context.go('/admin/access-reviews'),
                          icon: const Icon(
                            Icons.assignment_turned_in_outlined,
                            size: 16,
                          ),
                          label: const Text('Access Reviews'),
                        ),
                        const SizedBox(width: 12),
                        ElevatedButton.icon(
                          onPressed: () => _showCreateRoleWizard(),
                          icon: const Icon(
                            Icons.add_moderator_outlined,
                            size: 16,
                          ),
                          label: const Text('Create Role'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.navy900,
                            foregroundColor: Colors.white,
                          ),
                        ),
                      ],
                    ),
                ],
              ),
              if (isNarrow) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => context.go('/admin/access-reviews'),
                        icon: const Icon(
                          Icons.assignment_turned_in_outlined,
                          size: 16,
                        ),
                        label: const Text('Reviews'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: () => _showCreateRoleWizard(),
                        icon: const Icon(
                          Icons.add_moderator_outlined,
                          size: 16,
                        ),
                        label: const Text('Create Role'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.navy900,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  Widget _buildSummaryCards() {
    final totalRoles = _roles.length;
    final activeRoles = _roles.where((r) => r['status'] == 'ACTIVE').length;
    final totalPerms = _permissions.length;
    final totalAdmins = _users.length;
    final highRiskCount = _permissions
        .where((p) => p['riskLevel'] == 'HIGH')
        .length;
    final criticalCount = _permissions
        .where((p) => p['riskLevel'] == 'CRITICAL')
        .length;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      color: AppColors.paper,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final isWide = constraints.maxWidth >= 1000;
          final isMedium = constraints.maxWidth >= 650;

          if (isWide) {
            return Row(
              children: [
                Expanded(
                  child: _buildMetricCard(
                    'Total Roles',
                    '$totalRoles',
                    Icons.shield_outlined,
                    AppColors.navy900,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildMetricCard(
                    'Active Roles',
                    '$activeRoles',
                    Icons.check_circle_outline,
                    AppColors.green700,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildMetricCard(
                    'Total Permissions',
                    '$totalPerms',
                    Icons.lock_outline,
                    AppColors.ink,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildMetricCard(
                    'Administrators',
                    '$totalAdmins',
                    Icons.people_outline,
                    AppColors.navy900,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildMetricCard(
                    'High-Risk Perms',
                    '$highRiskCount',
                    Icons.warning_amber_outlined,
                    AppColors.amber700,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildMetricCard(
                    'Critical Perms',
                    '$criticalCount',
                    Icons.gpp_bad_outlined,
                    AppColors.red700,
                  ),
                ),
              ],
            );
          } else if (isMedium) {
            return Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: _buildMetricCard(
                        'Total Roles',
                        '$totalRoles',
                        Icons.shield_outlined,
                        AppColors.navy900,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildMetricCard(
                        'Active Roles',
                        '$activeRoles',
                        Icons.check_circle_outline,
                        AppColors.green700,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildMetricCard(
                        'Total Perms',
                        '$totalPerms',
                        Icons.lock_outline,
                        AppColors.ink,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: _buildMetricCard(
                        'Administrators',
                        '$totalAdmins',
                        Icons.people_outline,
                        AppColors.navy900,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildMetricCard(
                        'High-Risk',
                        '$highRiskCount',
                        Icons.warning_amber_outlined,
                        AppColors.amber700,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildMetricCard(
                        'Critical',
                        '$criticalCount',
                        Icons.gpp_bad_outlined,
                        AppColors.red700,
                      ),
                    ),
                  ],
                ),
              ],
            );
          } else {
            return SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildMetricCard(
                    'Total Roles',
                    '$totalRoles',
                    Icons.shield_outlined,
                    AppColors.navy900,
                  ),
                  const SizedBox(width: 8),
                  _buildMetricCard(
                    'Active',
                    '$activeRoles',
                    Icons.check_circle_outline,
                    AppColors.green700,
                  ),
                  const SizedBox(width: 8),
                  _buildMetricCard(
                    'Permissions',
                    '$totalPerms',
                    Icons.lock_outline,
                    AppColors.ink,
                  ),
                  const SizedBox(width: 8),
                  _buildMetricCard(
                    'Admins',
                    '$totalAdmins',
                    Icons.people_outline,
                    AppColors.navy900,
                  ),
                  const SizedBox(width: 8),
                  _buildMetricCard(
                    'Critical',
                    '$criticalCount',
                    Icons.gpp_bad_outlined,
                    AppColors.red700,
                  ),
                ],
              ),
            );
          }
        },
      ),
    );
  }

  Widget _buildMetricCard(
    String title,
    String value,
    IconData icon,
    Color color,
  ) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(width: 10),
          Flexible(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  value,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.mono(
                    color: AppColors.ink,
                    weight: FontWeight.w700,
                  ),
                ),
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTabBar() {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.paperRaised,
        border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
      ),
      child: TabBar(
        controller: _tabController,
        labelColor: AppColors.navy900,
        unselectedLabelColor: AppColors.inkMuted,
        indicatorColor: AppColors.navy900,
        indicatorWeight: 3,
        tabs: const [
          Tab(
            icon: Icon(Icons.table_chart_outlined, size: 18),
            text: 'Roles Overview',
          ),
          Tab(
            icon: Icon(Icons.grid_view_outlined, size: 18),
            text: 'Permission Matrix',
          ),
          Tab(
            icon: Icon(Icons.person_search_outlined, size: 18),
            text: 'User Access View',
          ),
          Tab(
            icon: Icon(Icons.compare_arrows_outlined, size: 18),
            text: 'Access Comparison',
          ),
        ],
      ),
    );
  }

  // ===========================================================================
  // TAB 1: ROLES OVERVIEW
  // ===========================================================================

  Widget _buildRolesOverviewTab() {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        for (final r in _roles) ...[
          _buildRoleManagementCard(r as Map<String, dynamic>),
          const SizedBox(height: 12),
        ],
      ],
    );
  }

  Widget _buildRoleManagementCard(Map<String, dynamic> role) {
    final code = role['code']?.toString() ?? '';
    final name = role['name']?.toString() ?? code;
    final description =
        role['description']?.toString() ?? 'No description defined.';
    final scope = role['scope']?.toString() ?? 'PLATFORM';
    final isSystem = role['isSystem'] == true;
    final status = role['status']?.toString() ?? 'ACTIVE';
    final perms = role['permissions'] as List? ?? [];
    final userCount = _getUserCountForRole(code);
    final coverage = _calculateCoverage(perms);
    final coveragePercent = (coverage * 100).toInt();

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          name,
                          style: AppTypography.uiLabelBold(
                            color: AppColors.navy900,
                          ).copyWith(fontSize: 16),
                        ),
                        const SizedBox(width: 8),
                        _buildScopeBadge(scope),
                        const SizedBox(width: 8),
                        if (isSystem)
                          _buildBadge(
                            'SYSTEM ROLE (PROTECTED)',
                            AppColors.navy900,
                            Colors.white,
                          )
                        else
                          _buildBadge(
                            'CUSTOM ROLE',
                            AppColors.inkMuted,
                            Colors.white,
                          ),
                        const SizedBox(width: 8),
                        _buildStatusBadge(status),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      code,
                      style: AppTypography.monoSmall(color: AppColors.inkMuted),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      description,
                      style: AppTypography.bodySmall(color: AppColors.ink),
                    ),
                  ],
                ),
              ),
              Row(
                children: [
                  OutlinedButton.icon(
                    onPressed: () {
                      _selectRoleForMatrix(role);
                      _tabController.animateTo(1);
                    },
                    icon: const Icon(Icons.tune, size: 14),
                    label: const Text('Permission Matrix'),
                  ),
                  const SizedBox(width: 8),
                  PopupMenuButton<String>(
                    tooltip: 'Role Actions',
                    onSelected: (val) {
                      if (val == 'duplicate') {
                        _showDuplicateRoleWizard(role);
                      } else if (val == 'edit') {
                        _showEditRoleDialog(role);
                      } else if (val == 'retire') {
                        _confirmRetireRole(role);
                      }
                    },
                    itemBuilder: (ctx) => [
                      const PopupMenuItem(
                        value: 'duplicate',
                        child: Text('Duplicate Role'),
                      ),
                      if (!isSystem)
                        const PopupMenuItem(
                          value: 'edit',
                          child: Text('Edit Role Details'),
                        ),
                      if (!isSystem && status == 'ACTIVE')
                        const PopupMenuItem(
                          value: 'retire',
                          child: Text(
                            'Retire Role',
                            style: TextStyle(color: AppColors.red700),
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Divider(height: 1, color: AppColors.rule),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Permission Coverage',
                          style: AppTypography.monoSmall(
                            color: AppColors.inkMuted,
                          ),
                        ),
                        Text(
                          '$coveragePercent% (${perms.length} / ${_permissions.length} applicable)',
                          style: AppTypography.monoSmall(
                            color: AppColors.ink,
                            weight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: coverage,
                        backgroundColor: AppColors.paper,
                        color: coveragePercent > 80
                            ? AppColors.navy900
                            : coveragePercent > 40
                            ? AppColors.green700
                            : AppColors.amber700,
                        minHeight: 8,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 32),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Assigned Users',
                    style: AppTypography.monoSmall(color: AppColors.inkMuted),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '$userCount platform administrators',
                    style: AppTypography.uiLabelBold(color: AppColors.ink),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ===========================================================================
  // TAB 2: PERMISSION MATRIX WORKSPACE
  // ===========================================================================

  bool get _hasStagedChanges {
    if (_selectedRoleForMatrix == null) return false;
    return !_setEquals(_stagedPermissions, _initialRolePermissions);
  }

  bool _setEquals(Set<String> a, Set<String> b) {
    if (a.length != b.length) return false;
    return a.containsAll(b);
  }

  Widget _buildPermissionMatrixTab() {
    if (_selectedRoleForMatrix == null) {
      return const Center(child: Text('Select a role to inspect permissions.'));
    }

    final roleCode = _selectedRoleForMatrix!['code']?.toString() ?? '';
    final isSystem = _selectedRoleForMatrix!['isSystem'] == true;
    final isPlatformAdmin = roleCode == 'ROLE_PLATFORM_ADMIN';

    // Group filtered permissions by category
    final search = _searchController.text.trim().toLowerCase();
    final filteredPerms = _permissions.where((p) {
      final code = p['code']?.toString().toLowerCase() ?? '';
      final name = p['name']?.toString().toLowerCase() ?? '';
      final desc = p['description']?.toString().toLowerCase() ?? '';
      final cat = p['category']?.toString() ?? '';
      final risk = p['riskLevel']?.toString() ?? '';

      if (_selectedCategoryFilter != 'ALL' && cat != _selectedCategoryFilter) {
        return false;
      }
      if (_selectedRiskFilter != 'ALL' && risk != _selectedRiskFilter) {
        return false;
      }
      if (search.isNotEmpty &&
          !code.contains(search) &&
          !name.contains(search) &&
          !desc.contains(search)) {
        return false;
      }
      return true;
    }).toList();

    final Map<String, List<dynamic>> grouped = {};
    for (final p in filteredPerms) {
      final cat = p['category']?.toString() ?? 'OTHER';
      grouped.putIfAbsent(cat, () => []).add(p);
    }

    return Column(
      children: [
        // Role Selector and Matrix Controls
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          decoration: const BoxDecoration(
            color: AppColors.paperRaised,
            border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
          ),
          child: Column(
            children: [
              Row(
                children: [
                  const Text(
                    'Active Role: ',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(width: 8),
                  DropdownButton<String>(
                    value: roleCode,
                    items: _roles.map<DropdownMenuItem<String>>((r) {
                      final c = r['code']?.toString() ?? '';
                      final n = r['name']?.toString() ?? c;
                      return DropdownMenuItem(value: c, child: Text('$n ($c)'));
                    }).toList(),
                    onChanged: (newCode) {
                      if (newCode != null) {
                        final found = _roles.firstWhere(
                          (r) => r['code'] == newCode,
                        );
                        _selectRoleForMatrix(found as Map<String, dynamic>);
                      }
                    },
                  ),
                  const Spacer(),
                  if (isPlatformAdmin)
                    _buildBadge(
                      'PERMISSIONS IMMUTABLE (CANONICAL)',
                      AppColors.amber700,
                      Colors.black,
                    )
                  else if (isSystem)
                    _buildBadge('SYSTEM ROLE', AppColors.navy900, Colors.white),
                ],
              ),
              const SizedBox(height: 12),
              // Search & Filter Bar
              Row(
                children: [
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: _searchController,
                      decoration: const InputDecoration(
                        hintText:
                            'Search permissions by name, code or description...',
                        prefixIcon: Icon(Icons.search, size: 18),
                        isDense: true,
                      ),
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                  const SizedBox(width: 12),
                  DropdownButton<String>(
                    value: _selectedCategoryFilter,
                    items: const [
                      DropdownMenuItem(
                        value: 'ALL',
                        child: Text('All Categories'),
                      ),
                      DropdownMenuItem(
                        value: 'IDENTITY',
                        child: Text('Identity'),
                      ),
                      DropdownMenuItem(
                        value: 'SECURITY',
                        child: Text('Security'),
                      ),
                      DropdownMenuItem(
                        value: 'TENANCY',
                        child: Text('Tenancy'),
                      ),
                      DropdownMenuItem(value: 'FISCAL', child: Text('Fiscal')),
                      DropdownMenuItem(
                        value: 'CONFIGURATION',
                        child: Text('Configuration'),
                      ),
                      DropdownMenuItem(value: 'AUDIT', child: Text('Audit')),
                    ],
                    onChanged: (v) =>
                        setState(() => _selectedCategoryFilter = v ?? 'ALL'),
                  ),
                  const SizedBox(width: 12),
                  DropdownButton<String>(
                    value: _selectedRiskFilter,
                    items: const [
                      DropdownMenuItem(
                        value: 'ALL',
                        child: Text('All Risk Levels'),
                      ),
                      DropdownMenuItem(
                        value: 'NORMAL',
                        child: Text('Normal (Low)'),
                      ),
                      DropdownMenuItem(
                        value: 'SENSITIVE',
                        child: Text('Sensitive (Med)'),
                      ),
                      DropdownMenuItem(value: 'HIGH', child: Text('High')),
                      DropdownMenuItem(
                        value: 'CRITICAL',
                        child: Text('Critical'),
                      ),
                    ],
                    onChanged: (v) =>
                        setState(() => _selectedRiskFilter = v ?? 'ALL'),
                  ),
                  const SizedBox(width: 12),
                  TextButton.icon(
                    onPressed: isPlatformAdmin
                        ? null
                        : () {
                            setState(() {
                              for (final p in filteredPerms) {
                                final c = p['code']?.toString();
                                if (c != null) _stagedPermissions.add(c);
                              }
                            });
                          },
                    icon: const Icon(Icons.select_all, size: 16),
                    label: const Text('Select Visible'),
                  ),
                  TextButton.icon(
                    onPressed: isPlatformAdmin
                        ? null
                        : () {
                            setState(() {
                              for (final p in filteredPerms) {
                                final c = p['code']?.toString();
                                if (c != null) _stagedPermissions.remove(c);
                              }
                            });
                          },
                    icon: const Icon(Icons.deselect, size: 16),
                    label: const Text('Clear Visible'),
                  ),
                ],
              ),
            ],
          ),
        ),

        // Permission Groups Matrix List
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              for (final entry in grouped.entries) ...[
                _buildCategoryPermissionGroup(
                  entry.key,
                  entry.value,
                  isPlatformAdmin,
                ),
                const SizedBox(height: 16),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildCategoryPermissionGroup(
    String categoryName,
    List<dynamic> perms,
    bool isReadOnly,
  ) {
    int selectedCount = 0;
    for (final p in perms) {
      if (_stagedPermissions.contains(p['code'])) {
        selectedCount++;
      }
    }

    bool? groupChecked;
    if (selectedCount == perms.length && perms.isNotEmpty) {
      groupChecked = true;
    } else if (selectedCount > 0) {
      groupChecked = null; // Indeterminate
    } else {
      groupChecked = false;
    }

    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Group Header with Tri-state Checkbox
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: const BoxDecoration(
              color: AppColors.paper,
              border: Border(
                bottom: BorderSide(color: AppColors.rule, width: 1),
              ),
            ),
            child: Row(
              children: [
                Checkbox(
                  tristate: true,
                  value: groupChecked,
                  onChanged: isReadOnly
                      ? null
                      : (val) {
                          setState(() {
                            final willSelect = val == true;
                            for (final p in perms) {
                              final code = p['code']?.toString();
                              if (code != null) {
                                if (willSelect) {
                                  _stagedPermissions.add(code);
                                } else {
                                  _stagedPermissions.remove(code);
                                }
                              }
                            }
                          });
                        },
                ),
                const SizedBox(width: 8),
                Text(
                  categoryName,
                  style: AppTypography.uiLabelBold(
                    color: AppColors.navy900,
                  ).copyWith(fontSize: 14),
                ),
                const Spacer(),
                Text(
                  '$selectedCount / ${perms.length} selected',
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
          // Individual Permissions in Category
          for (final p in perms) ...[
            _buildIndividualPermissionTile(
              p as Map<String, dynamic>,
              isReadOnly,
            ),
            if (p != perms.last)
              const Divider(height: 1, color: AppColors.rule),
          ],
        ],
      ),
    );
  }

  Widget _buildIndividualPermissionTile(
    Map<String, dynamic> perm,
    bool isReadOnly,
  ) {
    final code = perm['code']?.toString() ?? '';
    final name = perm['name']?.toString() ?? code;
    final description = perm['description']?.toString() ?? 'No description.';
    final riskLevel = perm['riskLevel']?.toString() ?? 'NORMAL';
    final isSelected = _stagedPermissions.contains(code);

    return InkWell(
      onTap: isReadOnly
          ? null
          : () {
              setState(() {
                if (isSelected) {
                  _stagedPermissions.remove(code);
                } else {
                  _stagedPermissions.add(code);
                }
              });
            },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Checkbox(
              value: isSelected,
              onChanged: isReadOnly
                  ? null
                  : (val) {
                      setState(() {
                        if (val == true) {
                          _stagedPermissions.add(code);
                        } else {
                          _stagedPermissions.remove(code);
                        }
                      });
                    },
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        name,
                        style: AppTypography.uiLabelBold(color: AppColors.ink),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '($code)',
                        style: AppTypography.monoSmall(
                          color: AppColors.inkMuted,
                        ),
                      ),
                      const Spacer(),
                      _buildRiskBadge(riskLevel),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStickyActionBar() {
    final additions = _stagedPermissions
        .difference(_initialRolePermissions)
        .length;
    final removals = _initialRolePermissions
        .difference(_stagedPermissions)
        .length;
    final totalSelected = _stagedPermissions.length;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
      decoration: const BoxDecoration(
        color: AppColors.navy900,
        boxShadow: [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 8,
            offset: Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        children: [
          const Icon(Icons.edit_note, color: Colors.white, size: 24),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Permission Changes Staged for ${_selectedRoleForMatrix?['name']}',
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                ),
              ),
              Text(
                '+$additions grants, -$removals revokes ($totalSelected total permissions)',
                style: const TextStyle(color: Colors.white70, fontSize: 12),
              ),
            ],
          ),
          const Spacer(),
          OutlinedButton(
            onPressed: () {
              setState(() {
                _stagedPermissions = Set.from(_initialRolePermissions);
              });
            },
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.white,
              side: const BorderSide(color: Colors.white54),
            ),
            child: const Text('Discard'),
          ),
          const SizedBox(width: 12),
          ElevatedButton(
            onPressed: _showReviewAndApplyDialog,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.green700,
              foregroundColor: Colors.white,
            ),
            child: const Text('Review & Apply Changes'),
          ),
        ],
      ),
    );
  }

  void _showReviewAndApplyDialog() {
    final additions = _stagedPermissions.difference(_initialRolePermissions);
    final removals = _initialRolePermissions.difference(_stagedPermissions);

    // Check if any added permissions are CRITICAL
    final criticalAdded = _permissions
        .where(
          (p) => additions.contains(p['code']) && p['riskLevel'] == 'CRITICAL',
        )
        .toList();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.paperRaised,
        title: Row(
          children: [
            const Icon(Icons.security, color: AppColors.navy900, size: 24),
            const SizedBox(width: 8),
            const Text('Confirm Permission Changes'),
          ],
        ),
        content: SizedBox(
          width: 550,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (criticalAdded.isNotEmpty) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.red700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: AppColors.red700),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(
                              Icons.warning,
                              color: AppColors.red700,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Text(
                              'Critical Access Modification',
                              style: AppTypography.uiLabelBold(
                                color: AppColors.red700,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'You are granting ${criticalAdded.length} CRITICAL permissions (${criticalAdded.map((c) => c['name']).join(', ')}). Elevated privileges are strictly audited.',
                          style: AppTypography.bodySmall(color: AppColors.ink),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                Text(
                  'Additions (${additions.length}):',
                  style: AppTypography.uiLabelBold(color: AppColors.green700),
                ),
                for (final a in additions)
                  Text(
                    ' • $a',
                    style: AppTypography.monoSmall(color: AppColors.ink),
                  ),
                const SizedBox(height: 12),
                Text(
                  'Removals (${removals.length}):',
                  style: AppTypography.uiLabelBold(color: AppColors.red700),
                ),
                for (final r in removals)
                  Text(
                    ' • $r',
                    style: AppTypography.monoSmall(color: AppColors.ink),
                  ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              _saveRolePermissions();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.navy900,
              foregroundColor: Colors.white,
            ),
            child: const Text('Confirm & Apply to Role'),
          ),
        ],
      ),
    );
  }

  Future<void> _saveRolePermissions() async {
    final code = _selectedRoleForMatrix?['code']?.toString();
    if (code == null) return;

    try {
      final client = ref.read(masterAdminApiClientProvider);
      await client.put(
        '/api/v1/master/rbac/roles/$code',
        data: {
          'name': _selectedRoleForMatrix?['name'],
          'description': _selectedRoleForMatrix?['description'],
          'permissionCodes': _stagedPermissions.toList(),
        },
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Permissions successfully updated and audited.'),
            backgroundColor: AppColors.green700,
          ),
        );
      }
      _loadData();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to update role permissions: $e'),
            backgroundColor: AppColors.red700,
          ),
        );
      }
    }
  }

  // ===========================================================================
  // TAB 3: USER ACCESS VIEW
  // ===========================================================================

  Widget _buildUserAccessViewTab() {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                'Administrator Account: ',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(width: 8),
              DropdownButton<String>(
                value: _selectedUserId,
                items: _users.map<DropdownMenuItem<String>>((u) {
                  final id = u['id']?.toString() ?? '';
                  final name =
                      u['fullName']?.toString() ??
                      u['username']?.toString() ??
                      id;
                  return DropdownMenuItem(
                    value: id,
                    child: Text('$name (${u['username']})'),
                  );
                }).toList(),
                onChanged: (newId) {
                  if (newId != null) {
                    setState(() => _selectedUserId = newId);
                    _fetchEffectiveAccess(newId);
                  }
                },
              ),
            ],
          ),

          const SizedBox(height: 16),
          if (_effectiveAccess != null) ...[
            _buildEffectiveAccessSummaryCard(),
            const SizedBox(height: 16),
            Expanded(child: _buildEffectivePermissionsList()),
          ] else
            const Expanded(child: Center(child: CircularProgressIndicator())),
        ],
      ),
    );
  }

  Widget _buildEffectiveAccessSummaryCard() {
    final roles = _effectiveAccess!['assignedRoles'] as List? ?? [];
    final effPerms = _effectiveAccess!['effectivePermissions'] as List? ?? [];
    final prohibited =
        _effectiveAccess!['prohibitedPermissions'] as List? ?? [];
    final coverage = (_permissions.isEmpty)
        ? 0.0
        : (effPerms.length / _permissions.length);
    final coveragePercent = (coverage * 100).toInt();

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'Assigned Roles: ',
                style: AppTypography.uiLabelBold(color: AppColors.navy900),
              ),
              for (final r in roles) ...[
                _buildBadge(r.toString(), AppColors.navy900, Colors.white),
                const SizedBox(width: 6),
              ],
              const Spacer(),
              Text(
                'Coverage: $coveragePercent%',
                style: AppTypography.monoSmall(
                  color: AppColors.ink,
                  weight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: coverage,
              minHeight: 6,
              backgroundColor: AppColors.paper,
              color: AppColors.navy900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '${effPerms.length} effective permissions granted, ${prohibited.length} restricted/prohibited by tenant scope or account status.',
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
        ],
      ),
    );
  }

  Widget _buildEffectivePermissionsList() {
    final effPerms = (_effectiveAccess!['effectivePermissions'] as List? ?? [])
        .toSet();
    final prohibited =
        (_effectiveAccess!['prohibitedPermissions'] as List? ?? []).toSet();

    return ListView(
      children: [
        for (final p in _permissions) ...[
          _buildEffectivePermissionTile(
            p as Map<String, dynamic>,
            effPerms.contains(p['code']),
            prohibited.contains(p['code']),
          ),
          const Divider(height: 1, color: AppColors.rule),
        ],
      ],
    );
  }

  Widget _buildEffectivePermissionTile(
    Map<String, dynamic> perm,
    bool isGranted,
    bool isProhibited,
  ) {
    final code = perm['code']?.toString() ?? '';
    final name = perm['name']?.toString() ?? code;
    final risk = perm['riskLevel']?.toString() ?? 'NORMAL';

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          Icon(
            isGranted
                ? Icons.check_circle
                : isProhibited
                ? Icons.block
                : Icons.radio_button_unchecked,
            size: 18,
            color: isGranted
                ? AppColors.green700
                : isProhibited
                ? AppColors.red700
                : AppColors.inkMuted,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: AppTypography.uiLabelBold(color: AppColors.ink),
                ),
                Text(
                  code,
                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                ),
              ],
            ),
          ),
          if (isGranted)
            _buildBadge('✓ Granted by Role', AppColors.green700, Colors.white)
          else if (isProhibited)
            _buildBadge('⚠ Restricted', AppColors.red700, Colors.white)
          else
            _buildBadge('Not Granted', AppColors.paper, AppColors.inkMuted),
          const SizedBox(width: 12),
          _buildRiskBadge(risk),
        ],
      ),
    );
  }

  // ===========================================================================
  // TAB 4: ACCESS COMPARISON MATRIX
  // ===========================================================================

  Widget _buildAccessMatrixComparisonTab() {
    final categories = [
      'IDENTITY',
      'SECURITY',
      'TENANCY',
      'FISCAL',
      'CONFIGURATION',
      'AUDIT',
    ];

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      scrollDirection: Axis.horizontal,
      child: SingleChildScrollView(
        child: DataTable(
          headingRowColor: WidgetStateProperty.all(AppColors.navy900),
          headingTextStyle: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
          ),
          columns: [
            const DataColumn(label: Text('Role')),
            const DataColumn(label: Text('Scope')),
            for (final cat in categories) DataColumn(label: Text(cat)),
            const DataColumn(label: Text('Total Perms')),
          ],
          rows: [
            for (final r in _roles) ...[
              DataRow(
                cells: [
                  DataCell(
                    Text(
                      r['name']?.toString() ?? '',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                  DataCell(
                    _buildScopeBadge(r['scope']?.toString() ?? 'PLATFORM'),
                  ),
                  for (final cat in categories)
                    DataCell(
                      _buildCategoryCountCell(
                        r['permissions'] as List? ?? [],
                        cat,
                      ),
                    ),
                  DataCell(
                    Text(
                      '${(r['permissions'] as List? ?? []).length} / ${_permissions.length}',
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildCategoryCountCell(List<dynamic> rolePerms, String category) {
    int count = 0;
    for (final p in rolePerms) {
      if (p['category'] == category) count++;
    }
    final totalInCat = _permissions
        .where((p) => p['category'] == category)
        .length;
    final isFull = count == totalInCat && totalInCat > 0;

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          count > 0 ? Icons.check : Icons.remove,
          size: 14,
          color: isFull
              ? AppColors.green700
              : (count > 0 ? AppColors.navy900 : AppColors.inkMuted),
        ),
        const SizedBox(width: 4),
        Text(
          '$count/$totalInCat',
          style: AppTypography.monoSmall(
            color: count > 0 ? AppColors.ink : AppColors.inkMuted,
          ),
        ),
      ],
    );
  }

  // ===========================================================================
  // WIZARDS & DIALOGS
  // ===========================================================================

  void _showCreateRoleWizard() {
    final codeCtrl = TextEditingController(text: 'ROLE_');
    final nameCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    String scope = 'PLATFORM';
    final Set<String> selectedPerms = {};
    String? dialogError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.add_moderator, color: AppColors.navy900),
              const SizedBox(width: 8),
              const Text('Create Custom Role'),
            ],
          ),
          content: SizedBox(
            width: 600,
            height: 500,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (dialogError != null) ...[
                    Text(
                      dialogError!,
                      style: const TextStyle(color: AppColors.red700),
                    ),
                    const SizedBox(height: 8),
                  ],
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: codeCtrl,
                          decoration: const InputDecoration(
                            labelText: 'Role Code (must start with ROLE_)',
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: scope,
                          decoration: const InputDecoration(labelText: 'Scope'),
                          items: const [
                            DropdownMenuItem(
                              value: 'PLATFORM',
                              child: Text('PLATFORM'),
                            ),
                            DropdownMenuItem(
                              value: 'TENANT',
                              child: Text('TENANT'),
                            ),
                            DropdownMenuItem(
                              value: 'GLOBAL',
                              child: Text('GLOBAL'),
                            ),
                          ],
                          onChanged: (v) =>
                              setDlgState(() => scope = v ?? 'PLATFORM'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: nameCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Display Name',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: descCtrl,
                    decoration: const InputDecoration(labelText: 'Description'),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Select Initial Permissions (${selectedPerms.length} selected):',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  for (final p in _permissions)
                    CheckboxListTile(
                      dense: true,
                      title: Text(p['name']?.toString() ?? ''),
                      subtitle: Text(p['code']?.toString() ?? ''),
                      value: selectedPerms.contains(p['code']),
                      onChanged: (val) {
                        setDlgState(() {
                          if (val == true) {
                            selectedPerms.add(p['code']);
                          } else {
                            selectedPerms.remove(p['code']);
                          }
                        });
                      },
                    ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                final code = codeCtrl.text.trim();
                final name = nameCtrl.text.trim();
                if (!code.startsWith('ROLE_')) {
                  setDlgState(
                    () => dialogError = 'Role code must begin with ROLE_',
                  );
                  return;
                }
                if (name.isEmpty) {
                  setDlgState(() => dialogError = 'Name is required');
                  return;
                }

                try {
                  final client = ref.read(masterAdminApiClientProvider);
                  await client.post(
                    '/api/v1/master/rbac/roles',
                    data: {
                      'code': code,
                      'name': name,
                      'description': descCtrl.text.trim(),
                      'scope': scope,
                      'permissionCodes': selectedPerms.toList(),
                    },
                  );
                  if (ctx.mounted) {
                    Navigator.of(ctx).pop();
                  }
                  _loadData();
                } catch (e) {
                  setDlgState(() => dialogError = 'Failed: $e');
                }
              },
              child: const Text('Create Role'),
            ),
          ],
        ),
      ),
    );
  }

  void _showDuplicateRoleWizard(Map<String, dynamic> role) {
    final originalCode = role['code']?.toString() ?? '';
    final codeCtrl = TextEditingController(text: '${originalCode}_COPY');
    final nameCtrl = TextEditingController(text: '${role['name']} (Copy)');
    final descCtrl = TextEditingController(
      text: role['description']?.toString() ?? '',
    );
    String scope = role['scope']?.toString() ?? 'PLATFORM';

    final perms = role['permissions'] as List? ?? [];
    final Set<String> selectedPerms = perms
        .map((p) => p['code']?.toString() ?? '')
        .toSet();
    String? dialogError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.copy_outlined, color: AppColors.navy900),
              const SizedBox(width: 8),
              Text('Duplicate Role: ${role['name']}'),
            ],
          ),
          content: SizedBox(
            width: 600,
            height: 500,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (dialogError != null) ...[
                    Text(
                      dialogError!,
                      style: const TextStyle(color: AppColors.red700),
                    ),
                    const SizedBox(height: 8),
                  ],
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: codeCtrl,
                          decoration: const InputDecoration(
                            labelText: 'New Unique Role Code',
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: scope,
                          decoration: const InputDecoration(labelText: 'Scope'),
                          items: const [
                            DropdownMenuItem(
                              value: 'PLATFORM',
                              child: Text('PLATFORM'),
                            ),
                            DropdownMenuItem(
                              value: 'TENANT',
                              child: Text('TENANT'),
                            ),
                            DropdownMenuItem(
                              value: 'GLOBAL',
                              child: Text('GLOBAL'),
                            ),
                          ],
                          onChanged: (v) =>
                              setDlgState(() => scope = v ?? 'PLATFORM'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: nameCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Display Name',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: descCtrl,
                    decoration: const InputDecoration(labelText: 'Description'),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Copied Permissions (${selectedPerms.length} selected):',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  for (final p in _permissions)
                    CheckboxListTile(
                      dense: true,
                      title: Text(p['name']?.toString() ?? ''),
                      subtitle: Text(p['code']?.toString() ?? ''),
                      value: selectedPerms.contains(p['code']),
                      onChanged: (val) {
                        setDlgState(() {
                          if (val == true) {
                            selectedPerms.add(p['code']);
                          } else {
                            selectedPerms.remove(p['code']);
                          }
                        });
                      },
                    ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                final code = codeCtrl.text.trim();
                final name = nameCtrl.text.trim();
                if (!code.startsWith('ROLE_')) {
                  setDlgState(
                    () => dialogError = 'Role code must begin with ROLE_',
                  );
                  return;
                }
                if (name.isEmpty) {
                  setDlgState(() => dialogError = 'Name is required');
                  return;
                }

                try {
                  final client = ref.read(masterAdminApiClientProvider);
                  await client.post(
                    '/api/v1/master/rbac/roles',
                    data: {
                      'code': code,
                      'name': name,
                      'description': descCtrl.text.trim(),
                      'scope': scope,
                      'permissionCodes': selectedPerms.toList(),
                    },
                  );
                  if (ctx.mounted) {
                    Navigator.of(ctx).pop();
                  }
                  _loadData();
                } catch (e) {
                  setDlgState(() => dialogError = 'Failed: $e');
                }
              },
              child: const Text('Duplicate Role'),
            ),
          ],
        ),
      ),
    );
  }

  void _showEditRoleDialog(Map<String, dynamic> role) {
    final code = role['code']?.toString() ?? '';
    final nameCtrl = TextEditingController(
      text: role['name']?.toString() ?? '',
    );
    final descCtrl = TextEditingController(
      text: role['description']?.toString() ?? '',
    );
    String? dialogError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Text('Edit Role Details ($code)'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (dialogError != null)
                Text(
                  dialogError!,
                  style: const TextStyle(color: AppColors.red700),
                ),
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: 'Display Name'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: descCtrl,
                decoration: const InputDecoration(labelText: 'Description'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                try {
                  final client = ref.read(masterAdminApiClientProvider);
                  await client.put(
                    '/api/v1/master/rbac/roles/$code',
                    data: {
                      'name': nameCtrl.text.trim(),
                      'description': descCtrl.text.trim(),
                    },
                  );
                  if (ctx.mounted) {
                    Navigator.of(ctx).pop();
                  }
                  _loadData();
                } catch (e) {
                  setDlgState(() => dialogError = 'Failed: $e');
                }
              },
              child: const Text('Save Details'),
            ),
          ],
        ),
      ),
    );
  }

  void _confirmRetireRole(Map<String, dynamic> role) {
    final code = role['code']?.toString() ?? '';
    final name = role['name']?.toString() ?? code;
    final userCount = _getUserCountForRole(code);

    if (userCount > 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Cannot retire role: Currently assigned to $userCount administrators.',
          ),
          backgroundColor: AppColors.red700,
        ),
      );
      return;
    }

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.paperRaised,
        title: Text('Retire Role: $name?'),
        content: Text(
          'Are you sure you want to retire role $code? It will no longer be assignable to administrators.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              try {
                final client = ref.read(masterAdminApiClientProvider);
                await client.delete('/api/v1/master/rbac/roles/$code');
                if (ctx.mounted) {
                  Navigator.of(ctx).pop();
                }
                _loadData();
              } catch (e) {
                if (ctx.mounted) {
                  Navigator.of(ctx).pop();
                }
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Failed to retire role: $e'),
                      backgroundColor: AppColors.red700,
                    ),
                  );
                }
              }
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.red700,
              foregroundColor: Colors.white,
            ),
            child: const Text('Confirm Retirement'),
          ),
        ],
      ),
    );
  }

  // ===========================================================================
  // BADGE HELPERS
  // ===========================================================================

  Widget _buildScopeBadge(String scope) {
    Color bg = AppColors.navy900.withValues(alpha: 0.1);
    Color fg = AppColors.navy900;
    if (scope == 'TENANT') {
      bg = AppColors.green700.withValues(alpha: 0.1);
      fg = AppColors.green700;
    } else if (scope == 'GLOBAL') {
      bg = Colors.purple.withValues(alpha: 0.1);
      fg = Colors.purple;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Text(
        scope,
        style: AppTypography.monoSmall(color: fg, weight: FontWeight.w700),
      ),
    );
  }

  Widget _buildStatusBadge(String status) {
    final isActive = status == 'ACTIVE';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: isActive
            ? AppColors.green700.withValues(alpha: 0.1)
            : Colors.grey.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(3),
      ),
      child: Text(
        status,
        style: AppTypography.monoSmall(
          color: isActive ? AppColors.green700 : AppColors.inkMuted,
          weight: FontWeight.w700,
        ),
      ),
    );
  }

  Widget _buildRiskBadge(String risk) {
    Color bg;
    Color fg;
    switch (risk.toUpperCase()) {
      case 'CRITICAL':
        bg = AppColors.red700.withValues(alpha: 0.15);
        fg = AppColors.red700;
        break;
      case 'HIGH':
        bg = AppColors.amber700.withValues(alpha: 0.15);
        fg = AppColors.amber700;
        break;
      case 'SENSITIVE':
      case 'MEDIUM':
        bg = Colors.orange.withValues(alpha: 0.12);
        fg = Colors.orange.shade900;
        break;
      default:
        bg = AppColors.navy900.withValues(alpha: 0.08);
        fg = AppColors.navy900;
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Text(
        risk,
        style: AppTypography.monoSmall(color: fg, weight: FontWeight.w700),
      ),
    );
  }

  Widget _buildBadge(String label, Color bg, Color fg) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Text(
        label,
        style: AppTypography.monoSmall(color: fg, weight: FontWeight.w700),
      ),
    );
  }
}
