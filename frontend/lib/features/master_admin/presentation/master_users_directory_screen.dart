import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterUsersDirectoryScreen extends ConsumerStatefulWidget {
  const MasterUsersDirectoryScreen({super.key});

  @override
  ConsumerState<MasterUsersDirectoryScreen> createState() =>
      _MasterUsersDirectoryScreenState();
}

class _MasterUsersDirectoryScreenState
    extends ConsumerState<MasterUsersDirectoryScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  bool _isLoading = true;
  String? _error;
  List<dynamic> _users = [];
  List<dynamic> _invitations = [];

  String _searchQuery = '';
  String _selectedRoleFilter = 'ALL';
  String _selectedStatusFilter = 'ALL';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);

      String url = '/api/v1/master/users?';
      if (_searchQuery.isNotEmpty) url += 'search=$_searchQuery&';
      if (_selectedRoleFilter != 'ALL') url += 'role=$_selectedRoleFilter&';
      if (_selectedStatusFilter != 'ALL') url += 'status=$_selectedStatusFilter&';

      final usersRes = await client.get(url);
      final invRes = await client.get('/api/v1/master/users/invitations');

      setState(() {
        _users = usersRes.data is List ? usersRes.data as List : [];
        _invitations = invRes.data is List ? invRes.data as List : [];
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to load user directory: $e';
        _isLoading = false;
      });
    }
  }

  void _showInviteDialog() {
    final nameCtrl = TextEditingController();
    final emailCtrl = TextEditingController();
    final phoneCtrl = TextEditingController();
    final tenantCtrl = TextEditingController();
    String selectedRole = 'ROLE_SAAS_ADMIN';
    String? dialogError;
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.person_add_alt_1, color: AppColors.navy900, size: 22),
              const SizedBox(width: 8),
              Text('Invite Administrator', style: AppTypography.h3()),
            ],
          ),
          content: SizedBox(
            width: 480,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'An invitation token will be generated and dispatched with 72-hour validity.',
                    style: AppTypography.bodySmall(color: AppColors.inkMuted),
                  ),
                  const SizedBox(height: 16),
                  if (dialogError != null) ...[
                    Text(dialogError!, style: AppTypography.bodySmall(color: AppColors.red700)),
                    const SizedBox(height: 8),
                  ],
                  TextField(
                    controller: nameCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Full Name',
                      prefixIcon: Icon(Icons.person, size: 18),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: emailCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Official Email',
                      prefixIcon: Icon(Icons.email, size: 18),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: phoneCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Mobile Phone (+251...)',
                      prefixIcon: Icon(Icons.phone, size: 18),
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: selectedRole,
                    decoration: const InputDecoration(
                      labelText: 'Initial Role Assignment',
                      prefixIcon: Icon(Icons.shield, size: 18),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'ROLE_PLATFORM_ADMIN', child: Text('Platform Administrator')),
                      DropdownMenuItem(value: 'ROLE_SAAS_ADMIN', child: Text('SaaS Administrator')),
                      DropdownMenuItem(value: 'ROLE_TENANT_ADMIN', child: Text('Tenant Administrator')),
                      DropdownMenuItem(value: 'ROLE_SECURITY_ADMIN', child: Text('Security Administrator')),
                      DropdownMenuItem(value: 'ROLE_AUDIT_ADMIN', child: Text('Audit Administrator')),
                      DropdownMenuItem(value: 'ROLE_SUPPORT_ADMIN', child: Text('Support Administrator')),
                      DropdownMenuItem(value: 'ROLE_FINANCE_ADMIN', child: Text('Finance Administrator')),
                      DropdownMenuItem(value: 'ROLE_READ_ONLY', child: Text('Read-Only Auditor')),
                    ],
                    onChanged: (v) => setDialogState(() => selectedRole = v ?? 'ROLE_SAAS_ADMIN'),
                  ),
                  if (selectedRole == 'ROLE_TENANT_ADMIN') ...[
                    const SizedBox(height: 12),
                    TextField(
                      controller: tenantCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Assigned Tenant UUID',
                        hintText: 'e.g. 00000000-0000-0000-0000-000000000001',
                        prefixIcon: Icon(Icons.business, size: 18),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });
                      try {
                        final client = ref.read(masterAdminApiClientProvider);
                        await client.post(
                          '/api/v1/master/users/invite',
                          data: {
                            'fullName': nameCtrl.text.trim(),
                            'email': emailCtrl.text.trim(),
                            'phone': phoneCtrl.text.trim().isEmpty ? null : phoneCtrl.text.trim(),
                            'initialRoleCode': selectedRole,
                            'tenantId': tenantCtrl.text.trim().isEmpty ? null : tenantCtrl.text.trim(),
                          },
                        );
                        if (ctx.mounted) Navigator.pop(ctx);
                        _loadData();
                      } catch (e) {
                        setDialogState(() {
                          dialogError = 'Failed to invite administrator: $e';
                          isSubmitting = false;
                        });
                      }
                    },
              child: const Text('Send Invitation'),
            ),
          ],
        ),
      ),
    );
  }

  void _showChangeStatusDialog(dynamic user) {
    String selectedStatus = user['status'] ?? 'ACTIVE';
    final reasonCtrl = TextEditingController();
    String? dialogError;
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Text('Change Administrator Status', style: AppTypography.h3()),
          content: SizedBox(
            width: 400,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'User: ${user['fullName']} (${user['username']})',
                  style: AppTypography.uiLabelBold(),
                ),
                const SizedBox(height: 12),
                if (dialogError != null) ...[
                  Text(dialogError!, style: AppTypography.bodySmall(color: AppColors.red700)),
                  const SizedBox(height: 8),
                ],
                DropdownButtonFormField<String>(
                  value: selectedStatus,
                  decoration: const InputDecoration(labelText: 'Status'),
                  items: const [
                    DropdownMenuItem(value: 'ACTIVE', child: Text('ACTIVE')),
                    DropdownMenuItem(value: 'SUSPENDED', child: Text('SUSPENDED (Temporary Lock)')),
                    DropdownMenuItem(value: 'LOCKED', child: Text('LOCKED (Security Lockout)')),
                    DropdownMenuItem(value: 'DISABLED', child: Text('DISABLED (Permanent Deactivation)')),
                  ],
                  onChanged: (v) => setDialogState(() => selectedStatus = v ?? 'ACTIVE'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: reasonCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Operational Rationale / Ticket Ref',
                    hintText: 'Reason for status update',
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });
                      try {
                        final client = ref.read(masterAdminApiClientProvider);
                        await client.put(
                          '/api/v1/master/users/${user['id']}/status',
                          data: {
                            'status': selectedStatus,
                            'reason': reasonCtrl.text.trim(),
                          },
                        );
                        if (ctx.mounted) Navigator.pop(ctx);
                        _loadData();
                      } catch (e) {
                        setDialogState(() {
                          dialogError = 'Error: $e';
                          isSubmitting = false;
                        });
                      }
                    },
              child: const Text('Apply Status'),
            ),
          ],
        ),
      ),
    );
  }

  void _showEffectiveAccessModal(dynamic user) async {
    try {
      final client = ref.read(masterAdminApiClientProvider);
      final res = await client.get('/api/v1/master/rbac/effective-access/${user['id']}');
      final data = Map<String, dynamic>.from(res.data as Map);
      final assignedRoles = List<String>.from(data['assignedRoles'] ?? []);
      final effective = List<String>.from(data['effectivePermissions'] ?? [])..sort();
      final prohibited = List<String>.from(data['prohibitedPermissions'] ?? [])..sort();

      if (!mounted) return;
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          title: Row(
            children: [
              const Icon(Icons.calculate_outlined, color: AppColors.navy900, size: 22),
              const SizedBox(width: 8),
              Text('Effective Access Calculator', style: AppTypography.h3()),
            ],
          ),
          content: SizedBox(
            width: 580,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Subject: ${data['username']} (${user['fullName']})',
                    style: AppTypography.uiLabelBold(),
                  ),
                  const SizedBox(height: 8),
                  Text('Assigned Roles: ${assignedRoles.join(', ')}', style: AppTypography.bodySmall()),
                  const SizedBox(height: 16),
                  Text(
                    'Effective Permissions (${effective.length}):',
                    style: AppTypography.uiLabelBold(color: AppColors.green700),
                  ),
                  const SizedBox(height: 6),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.green700.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(color: AppColors.green700.withValues(alpha: 0.3)),
                    ),
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: effective
                          .map((p) => Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppColors.green700,
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: Text(p, style: AppTypography.monoSmall(color: Colors.white)),
                              ))
                          .toList(),
                    ),
                  ),
                  if (prohibited.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Text(
                      'Explicitly Prohibited Permissions (${prohibited.length}):',
                      style: AppTypography.uiLabelBold(color: AppColors.red700),
                    ),
                    const SizedBox(height: 6),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.red700.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: AppColors.red700.withValues(alpha: 0.3)),
                      ),
                      child: Wrap(
                        spacing: 6,
                        runSpacing: 6,
                        children: prohibited
                            .map((p) => Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: AppColors.red700,
                                    borderRadius: BorderRadius.circular(3),
                                  ),
                                  child: Text(p, style: AppTypography.monoSmall(color: Colors.white)),
                                ))
                            .toList(),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Close'),
            ),
          ],
        ),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to calculate effective access: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paperMuted,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Page Header
            Row(
              children: [
                const Icon(
                  Icons.people_alt_outlined,
                  size: 28,
                  color: AppColors.navy900,
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Platform & Tenant Administrator Directory', style: AppTypography.h1()),
                    Text(
                      'Lifecycle governance, identity status, credential resets, and effective authority oversight.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                const Spacer(),
                ElevatedButton.icon(
                  onPressed: _showInviteDialog,
                  icon: const Icon(Icons.person_add, size: 16),
                  label: const Text('Invite Administrator'),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Tab bar
            Container(
              decoration: const BoxDecoration(
                border: Border(bottom: BorderSide(color: AppColors.rule)),
              ),
              child: TabBar(
                controller: _tabController,
                isScrollable: true,
                tabs: [
                  Tab(text: 'Active Administrators (${_users.length})'),
                  Tab(text: 'Pending Invitations (${_invitations.length})'),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Filter bar
            Row(
              children: [
                Expanded(
                  flex: 3,
                  child: TextField(
                    decoration: const InputDecoration(
                      hintText: 'Search by full name, email, or username...',
                      prefixIcon: Icon(Icons.search, size: 18),
                    ),
                    onChanged: (val) {
                      _searchQuery = val;
                      _loadData();
                    },
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: DropdownButtonFormField<String>(
                    value: _selectedRoleFilter,
                    decoration: const InputDecoration(labelText: 'Filter Role'),
                    items: const [
                      DropdownMenuItem(value: 'ALL', child: Text('All Roles')),
                      DropdownMenuItem(value: 'ROLE_PLATFORM_ADMIN', child: Text('Platform Admin')),
                      DropdownMenuItem(value: 'ROLE_SAAS_ADMIN', child: Text('SaaS Admin')),
                      DropdownMenuItem(value: 'ROLE_TENANT_ADMIN', child: Text('Tenant Admin')),
                      DropdownMenuItem(value: 'ROLE_SECURITY_ADMIN', child: Text('Security Admin')),
                      DropdownMenuItem(value: 'ROLE_AUDIT_ADMIN', child: Text('Audit Admin')),
                      DropdownMenuItem(value: 'ROLE_SUPPORT_ADMIN', child: Text('Support Admin')),
                      DropdownMenuItem(value: 'ROLE_READ_ONLY', child: Text('Read-Only')),
                    ],
                    onChanged: (v) {
                      _selectedRoleFilter = v ?? 'ALL';
                      _loadData();
                    },
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: DropdownButtonFormField<String>(
                    value: _selectedStatusFilter,
                    decoration: const InputDecoration(labelText: 'Filter Status'),
                    items: const [
                      DropdownMenuItem(value: 'ALL', child: Text('All Statuses')),
                      DropdownMenuItem(value: 'ACTIVE', child: Text('Active')),
                      DropdownMenuItem(value: 'SUSPENDED', child: Text('Suspended')),
                      DropdownMenuItem(value: 'LOCKED', child: Text('Locked')),
                      DropdownMenuItem(value: 'DISABLED', child: Text('Disabled')),
                    ],
                    onChanged: (v) {
                      _selectedStatusFilter = v ?? 'ALL';
                      _loadData();
                    },
                  ),
                ),
                const SizedBox(width: 12),
                IconButton(
                  icon: const Icon(Icons.refresh),
                  tooltip: 'Reload directory',
                  onPressed: _loadData,
                ),
              ],
            ),
            const SizedBox(height: 16),

            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.red700.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.red700)),
              ),
              const SizedBox(height: 16),
            ],

            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : TabBarView(
                      controller: _tabController,
                      children: [
                        _buildUsersTable(),
                        _buildInvitationsTable(),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildUsersTable() {
    if (_users.isEmpty) {
      return Center(
        child: Text('No administrators match the search criteria.', style: AppTypography.bodySmall()),
      );
    }

    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: ListView.separated(
        itemCount: _users.length,
        separatorBuilder: (ctx, i) => const Divider(height: 1, color: AppColors.rule),
        itemBuilder: (ctx, i) {
          final u = _users[i];
          final status = u['status'] ?? 'ACTIVE';
          final mfa = u['mfaEnabled'] == true;
          final role = u['primaryRole'] ?? 'ROLE_PLATFORM_ADMIN';

          Color statusBg = AppColors.green700.withValues(alpha: 0.1);
          Color statusFg = AppColors.green700;
          if (status == 'SUSPENDED' || status == 'LOCKED') {
            statusBg = AppColors.amber700.withValues(alpha: 0.1);
            statusFg = AppColors.amber700;
          } else if (status == 'DISABLED') {
            statusBg = AppColors.red700.withValues(alpha: 0.1);
            statusFg = AppColors.red700;
          }

          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            leading: CircleAvatar(
              backgroundColor: AppColors.navy900.withValues(alpha: 0.1),
              child: Text(
                (u['fullName'] ?? 'A').substring(0, 1).toUpperCase(),
                style: AppTypography.uiLabelBold(color: AppColors.navy900),
              ),
            ),
            title: Row(
              children: [
                Text(u['fullName'] ?? '', style: AppTypography.uiLabelBold()),
                const SizedBox(width: 8),
                Text('(@${u['username']})', style: AppTypography.monoSmall(color: AppColors.inkMuted)),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: statusBg,
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Text(
                    status,
                    style: AppTypography.monoSmall(color: statusFg, weight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 8),
                Icon(
                  mfa ? Icons.verified_user : Icons.gpp_maybe_outlined,
                  size: 16,
                  color: mfa ? AppColors.green700 : AppColors.amber700,
                ),
              ],
            ),
            subtitle: Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Row(
                children: [
                  Text('Email: ${u['email'] ?? '—'}', style: AppTypography.monoSmall()),
                  const SizedBox(width: 16),
                  Text('Role: $role', style: AppTypography.monoSmall(color: AppColors.navy900)),
                  const SizedBox(width: 16),
                  Text(
                    'Last Login: ${u['lastLoginAt'] != null ? u['lastLoginAt'].toString().split('T').first : 'Never'}',
                    style: AppTypography.monoSmall(color: AppColors.inkMuted),
                  ),
                ],
              ),
            ),
            trailing: PopupMenuButton<String>(
              onSelected: (val) {
                if (val == 'STATUS') _showChangeStatusDialog(u);
                if (val == 'EFFECTIVE_ACCESS') _showEffectiveAccessModal(u);
                if (val == 'RESET_MFA') {
                  showDialog(
                    context: context,
                    builder: (c) => AlertDialog(
                      title: const Text('Reset MFA Protection'),
                      content: Text('Are you sure you want to reset MFA for ${u['username']}?'),
                      actions: [
                        TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
                        ElevatedButton(
                          onPressed: () async {
                            Navigator.pop(c);
                            final client = ref.read(masterAdminApiClientProvider);
                            await client.post('/api/v1/master/users/${u['id']}/reset-mfa');
                            _loadData();
                          },
                          child: const Text('Reset MFA'),
                        ),
                      ],
                    ),
                  );
                }
              },
              itemBuilder: (ctx) => [
                const PopupMenuItem(
                  value: 'EFFECTIVE_ACCESS',
                  child: Row(
                    children: [
                      Icon(Icons.calculate_outlined, size: 16),
                      SizedBox(width: 8),
                      Text('Inspect Effective Access'),
                    ],
                  ),
                ),
                const PopupMenuItem(
                  value: 'STATUS',
                  child: Row(
                    children: [
                      Icon(Icons.toggle_on_outlined, size: 16),
                      SizedBox(width: 8),
                      Text('Change Status'),
                    ],
                  ),
                ),
                const PopupMenuItem(
                  value: 'RESET_MFA',
                  child: Row(
                    children: [
                      Icon(Icons.lock_reset, size: 16),
                      SizedBox(width: 8),
                      Text('Reset MFA Token'),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildInvitationsTable() {
    if (_invitations.isEmpty) {
      return Center(
        child: Text('No invitations pending.', style: AppTypography.bodySmall()),
      );
    }

    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.rule),
      ),
      child: ListView.separated(
        itemCount: _invitations.length,
        separatorBuilder: (ctx, i) => const Divider(height: 1, color: AppColors.rule),
        itemBuilder: (ctx, i) {
          final inv = _invitations[i];
          final status = inv['status'] ?? 'PENDING';
          return ListTile(
            leading: const Icon(Icons.mark_email_unread_outlined, color: AppColors.navy900),
            title: Text('${inv['fullName']} (${inv['email']})', style: AppTypography.uiLabelBold()),
            subtitle: Text(
              'Role: ${inv['initialRoleCode']} | Status: $status | Expires: ${inv['expiresAt']}',
              style: AppTypography.monoSmall(color: AppColors.inkMuted),
            ),
            trailing: status == 'PENDING'
                ? OutlinedButton(
                    onPressed: () async {
                      final client = ref.read(masterAdminApiClientProvider);
                      await client.post('/api/v1/master/users/invitations/${inv['id']}/revoke');
                      _loadData();
                    },
                    style: OutlinedButton.styleFrom(foregroundColor: AppColors.red700),
                    child: const Text('Revoke'),
                  )
                : null,
          );
        },
      ),
    );
  }
}
