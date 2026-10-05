import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterApiManagementScreen extends ConsumerStatefulWidget {
  const MasterApiManagementScreen({super.key});

  @override
  ConsumerState<MasterApiManagementScreen> createState() =>
      _MasterApiManagementScreenState();
}

class _MasterApiManagementScreenState
    extends ConsumerState<MasterApiManagementScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = false;
  List<dynamic> _apiClients = [];
  List<dynamic> _webhooks = [];
  List<dynamic> _deliveries = [];
  List<dynamic> _tenants = [];
  Map<String, dynamic>? _apiVersionOverview;
  bool _apiVersionCatalogIsLive = false;
  String _clientSearchQuery = '';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _loadAllData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadAllData() async {
    setState(() => _isLoading = true);
    // This is a platform-admin screen. The tenant client only carries tenant
    // credentials, which caused master API calls to be sent without the admin JWT.
    final apiClient = ref.read(masterAdminApiClientProvider);

    try {
      // 1. Load API Clients
      final clientsRes = await apiClient.get('/api/v1/master/api-clients');
      if (clientsRes.statusCode == 200 && clientsRes.data != null) {
        _apiClients = clientsRes.data as List<dynamic>;
      }

      // 2. Load Webhooks
      final webhooksRes = await apiClient.get('/api/v1/master/webhooks');
      if (webhooksRes.statusCode == 200 && webhooksRes.data != null) {
        _webhooks = webhooksRes.data as List<dynamic>;
      }

      // 3. Load Deliveries
      final delivRes = await apiClient.get(
        '/api/v1/master/webhooks/deliveries?size=50',
      );
      if (delivRes.statusCode == 200 && delivRes.data != null) {
        _deliveries = delivRes.data as List<dynamic>;
      }

      // 4. Load Tenants for dropdown
      final tenantsRes = await apiClient.get('/api/v1/saas/tenants?size=100');
      if (tenantsRes.statusCode == 200 && tenantsRes.data != null) {
        final data = tenantsRes.data;
        if (data is Map && data['content'] is List) {
          _tenants = data['content'] as List<dynamic>;
        } else if (data is List) {
          _tenants = data;
        }
      }

      // 5. Load the deployment-owned public API version catalog.
      final versionsRes = await apiClient.get('/api/v1/master/api-versions');
      if (versionsRes.statusCode == 200 && versionsRes.data is Map) {
        _apiVersionOverview = Map<String, dynamic>.from(
          versionsRes.data as Map,
        );
        _apiVersionCatalogIsLive = true;
      }
    } catch (_) {
      // Fallback baseline for development simulation
      if (_apiClients.isEmpty) {
        _apiClients = [
          {
            'id': 'ac-01',
            'tenantId': '00000000-0000-0000-0000-000000000001',
            'tenantName': 'Abyssinia Trading & Distribution PLC',
            'clientId': 'CLIENT_ABYSSINIA_ERP',
            'clientName': 'Abyssinia ERP System',
            'scopes': 'invoice:read invoice:create tenant:admin',
            'status': 'ACTIVE',
            'lastUsedAt': DateTime.now()
                .subtract(const Duration(minutes: 14))
                .toIso8601String(),
            'createdAt': DateTime.now()
                .subtract(const Duration(days: 10))
                .toIso8601String(),
          },
        ];
      }
      _apiVersionOverview ??= _defaultVersionOverview();
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _showCreateClientDialog() {
    final formKey = GlobalKey<FormState>();
    final clientIdCtrl = TextEditingController();
    final clientNameCtrl = TextEditingController();
    String? selectedTenantId = _tenants.isNotEmpty
        ? _tenants.first['id']?.toString()
        : '00000000-0000-0000-0000-000000000001';
    final selectedScopes = <String>{'invoice:create', 'invoice:read'};

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(
            'REGISTER NEW ERP / M2M API CLIENT',
            style: AppTypography.h2(),
          ),
          content: SizedBox(
            width: 520,
            child: Form(
              key: formKey,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Issue secure machine-to-machine API credentials for ERP systems, billing software, and custom ingress connectors.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      initialValue: selectedTenantId,
                      decoration: const InputDecoration(
                        labelText: 'Target Tenant Organization *',
                      ),
                      items: _tenants.map<DropdownMenuItem<String>>((t) {
                        final id = t['id']?.toString() ?? '';
                        final name =
                            t['companyName']?.toString() ??
                            t['tradingName']?.toString() ??
                            'Tenant';
                        final tin = t['tin']?.toString() ?? '';
                        return DropdownMenuItem(
                          value: id,
                          child: Text(
                            '$name ($tin)',
                            overflow: TextOverflow.ellipsis,
                          ),
                        );
                      }).toList(),
                      onChanged: (v) =>
                          setDialogState(() => selectedTenantId = v),
                      validator: (v) =>
                          v == null ? 'Please select a tenant' : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: clientIdCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Client Identifier (X-API-Key) *',
                        hintText: 'e.g., CLIENT_SAP_PROD_01',
                      ),
                      textCapitalization: TextCapitalization.characters,
                      validator: (v) => (v == null || v.trim().isEmpty)
                          ? 'Client identifier required'
                          : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: clientNameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Client Friendly Description *',
                        hintText: 'e.g., SAP ERP Production Ingress',
                      ),
                      validator: (v) => (v == null || v.trim().isEmpty)
                          ? 'Description required'
                          : null,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Authorized Scopes:',
                      style: AppTypography.uiLabelBold(),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children:
                          [
                            'invoice:create',
                            'invoice:read',
                            'tenant:admin',
                            'offline:sync',
                            'reports:read',
                          ].map((scope) {
                            final isSelected = selectedScopes.contains(scope);
                            return FilterChip(
                              label: Text(
                                scope,
                                style: AppTypography.monoSmall(),
                              ),
                              selected: isSelected,
                              onSelected: (selected) {
                                setDialogState(() {
                                  if (selected) {
                                    selectedScopes.add(scope);
                                  } else {
                                    selectedScopes.remove(scope);
                                  }
                                });
                              },
                            );
                          }).toList(),
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            OutlinedButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                if (!formKey.currentState!.validate()) return;
                if (selectedScopes.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Please select at least one scope'),
                    ),
                  );
                  return;
                }

                final apiClient = ref.read(masterAdminApiClientProvider);
                final messenger = ScaffoldMessenger.of(context);
                try {
                  final res = await apiClient.post(
                    '/api/v1/master/api-clients',
                    data: {
                      'tenantId': selectedTenantId,
                      'clientId': clientIdCtrl.text.trim().toUpperCase(),
                      'clientName': clientNameCtrl.text.trim(),
                      'scopes': selectedScopes.join(' '),
                    },
                  );

                  if (ctx.mounted) {
                    Navigator.pop(ctx);
                  }
                  await _loadAllData();

                  if (!mounted) return;
                  if (res.statusCode == 200 || res.statusCode == 201) {
                    final rawSecret =
                        res.data['rawSecret']?.toString() ?? 'Secret Generated';
                    final cId =
                        res.data['clientId']?.toString() ??
                        clientIdCtrl.text.trim().toUpperCase();
                    _showSecretBannerDialog(cId, rawSecret);
                  }
                } catch (err) {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text('Failed to create API client: $err'),
                      backgroundColor: AppColors.red600,
                    ),
                  );
                }
              },
              child: const Text('Generate Credentials'),
            ),
          ],
        ),
      ),
    );
  }

  void _showSecretBannerDialog(String clientId, String rawSecret) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Icon(
              Icons.warning_amber_rounded,
              color: AppColors.amber600,
              size: 28,
            ),
            const SizedBox(width: 8),
            Text('API CLIENT SECRET GENERATED', style: AppTypography.h2()),
          ],
        ),
        content: SizedBox(
          width: 520,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.amber600.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(
                    color: AppColors.amber600.withValues(alpha: 0.4),
                  ),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.lock_outline,
                      color: AppColors.amber600,
                      size: 20,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'ATTENTION: This client secret is displayed ONCE. It is hashed with SHA-256 in the database and cannot be retrieved later. Store it securely in your secret manager.',
                        style: AppTypography.bodySmall(
                          color: AppColors.amber600,
                        ).copyWith(fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Client ID (X-API-Key):',
                style: AppTypography.uiLabelBold(),
              ),
              const SizedBox(height: 4),
              SelectableText(
                clientId,
                style: AppTypography.mono(weight: FontWeight.w600),
              ),
              const SizedBox(height: 12),
              Text(
                'Client Secret (X-Client-Secret):',
                style: AppTypography.uiLabelBold(),
              ),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: AppColors.paper,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppColors.rule),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: SelectableText(
                        rawSecret,
                        style: AppTypography.mono(
                          weight: FontWeight.w700,
                          color: AppColors.navy900,
                        ),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 18),
                      tooltip: 'Copy secret to clipboard',
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: rawSecret));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Client secret copied to clipboard!'),
                          ),
                        );
                      },
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('I Have Securely Saved This Secret'),
          ),
        ],
      ),
    );
  }

  void _rotateSecret(dynamic client) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('ROTATE API CLIENT SECRET', style: AppTypography.h3()),
        content: Text(
          'Are you sure you want to rotate the secret for client "${client['clientId']}"?\n\nThe existing secret will immediately stop working.',
          style: AppTypography.body(),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.red600),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Rotate Secret Now'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    final apiClient = ref.read(masterAdminApiClientProvider);
    try {
      final res = await apiClient.post(
        '/api/v1/master/api-clients/${client['id']}/rotate-secret',
      );
      if (res.statusCode == 200 && res.data != null) {
        final newSecret = res.data['rawSecret']?.toString() ?? '';
        final cId = res.data['clientId']?.toString() ?? client['clientId'];
        _showSecretBannerDialog(cId, newSecret);
      }
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to rotate secret: $err'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  void _toggleClientStatus(dynamic client) async {
    final newStatus = client['status'] == 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    final apiClient = ref.read(masterAdminApiClientProvider);
    try {
      await apiClient.put(
        '/api/v1/master/api-clients/${client['id']}/status',
        data: {'status': newStatus},
      );
      await _loadAllData();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Client status updated to $newStatus')),
        );
      }
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to update status: $err'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  void _retryDelivery(dynamic delivery) async {
    final apiClient = ref.read(masterAdminApiClientProvider);
    try {
      final res = await apiClient.post(
        '/api/v1/master/webhooks/deliveries/${delivery['id']}/retry',
      );
      await _loadAllData();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Delivery retry triggered: ${res.data['newStatus'] ?? 'Processed'}',
            ),
          ),
        );
      }
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to retry delivery: $err'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'API INTEGRATIONS & GOVERNANCE',
                      style: AppTypography.h1(),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'M2M credentials, webhook delivery oversight, and controlled public API version releases',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: _showCreateClientDialog,
                  icon: const Icon(Icons.vpn_key_outlined, size: 18),
                  label: const Text('PROVISION API CLIENT'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 14,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Tab Bar
            Container(
              decoration: const BoxDecoration(
                border: Border(
                  bottom: BorderSide(color: AppColors.rule, width: 1),
                ),
              ),
              child: TabBar(
                controller: _tabController,
                indicatorColor: AppColors.navy900,
                labelColor: AppColors.navy900,
                unselectedLabelColor: AppColors.inkMuted,
                tabs: [
                  Tab(
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.api_outlined, size: 18),
                        const SizedBox(width: 8),
                        Text('API Clients (${_apiClients.length})'),
                      ],
                    ),
                  ),
                  Tab(
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.webhook_outlined, size: 18),
                        const SizedBox(width: 8),
                        Text('Webhook Subscriptions (${_webhooks.length})'),
                      ],
                    ),
                  ),
                  Tab(
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.history_outlined, size: 18),
                        const SizedBox(width: 8),
                        Text('Delivery Logs (${_deliveries.length})'),
                      ],
                    ),
                  ),
                  Tab(
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.account_tree_outlined, size: 18),
                        const SizedBox(width: 8),
                        Text(
                          'API Versions (${_apiVersionOverview?['supportedVersions'] is List ? (_apiVersionOverview?['supportedVersions'] as List).length : 1})',
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Tab Views
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : TabBarView(
                      controller: _tabController,
                      children: [
                        _buildApiClientsTab(),
                        _buildWebhooksTab(),
                        _buildDeliveriesTab(),
                        _buildApiVersionsTab(),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildApiClientsTab() {
    final filtered = _apiClients.where((c) {
      if (_clientSearchQuery.isEmpty) return true;
      final q = _clientSearchQuery.toLowerCase();
      final cId = (c['clientId']?.toString() ?? '').toLowerCase();
      final cName = (c['clientName']?.toString() ?? '').toLowerCase();
      final tName = (c['tenantName']?.toString() ?? '').toLowerCase();
      return cId.contains(q) || cName.contains(q) || tName.contains(q);
    }).toList();

    return Column(
      children: [
        // Search & Refresh
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.paperRaised,
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: AppColors.rule),
          ),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  decoration: const InputDecoration(
                    hintText:
                        'Search by Client ID, friendly name, or tenant...',
                    prefixIcon: Icon(
                      Icons.search,
                      size: 20,
                      color: AppColors.inkMuted,
                    ),
                  ),
                  onChanged: (v) =>
                      setState(() => _clientSearchQuery = v.trim()),
                ),
              ),
              const SizedBox(width: 16),
              IconButton(
                icon: const Icon(Icons.refresh),
                tooltip: 'Reload',
                onPressed: _loadAllData,
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Expanded(
          child: Container(
            width: double.infinity,
            decoration: BoxDecoration(
              color: AppColors.paperRaised,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: AppColors.rule),
            ),
            child: filtered.isEmpty
                ? Center(
                    child: Text(
                      'No API Clients Registered',
                      style: AppTypography.h3(),
                    ),
                  )
                : SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SingleChildScrollView(
                      scrollDirection: Axis.vertical,
                      child: DataTable(
                        columns: const [
                          DataColumn(label: Text('Client ID (X-API-Key)')),
                          DataColumn(label: Text('Client Name / Tenant')),
                          DataColumn(label: Text('Authorized Scopes')),
                          DataColumn(label: Text('Status')),
                          DataColumn(label: Text('Last Used')),
                          DataColumn(label: Text('Actions')),
                        ],
                        rows: filtered.map((c) {
                          final isActive = c['status'] == 'ACTIVE';
                          return DataRow(
                            cells: [
                              DataCell(
                                Text(
                                  c['clientId']?.toString() ?? '',
                                  style: AppTypography.mono(
                                    weight: FontWeight.w700,
                                    color: AppColors.navy900,
                                  ),
                                ),
                              ),
                              DataCell(
                                Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      c['clientName']?.toString() ?? '',
                                      style: AppTypography.uiLabelBold(),
                                    ),
                                    Text(
                                      c['tenantName']?.toString() ?? '',
                                      style: AppTypography.bodySmall(
                                        color: AppColors.inkMuted,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              DataCell(
                                Text(
                                  c['scopes']?.toString() ?? '',
                                  style: AppTypography.monoSmall(),
                                ),
                              ),
                              DataCell(
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 3,
                                  ),
                                  decoration: BoxDecoration(
                                    color: isActive
                                        ? AppColors.green700.withValues(
                                            alpha: 0.08,
                                          )
                                        : AppColors.red600.withValues(
                                            alpha: 0.08,
                                          ),
                                    borderRadius: BorderRadius.circular(2),
                                    border: Border.all(
                                      color: isActive
                                          ? AppColors.green700.withValues(
                                              alpha: 0.3,
                                            )
                                          : AppColors.red600.withValues(
                                              alpha: 0.3,
                                            ),
                                    ),
                                  ),
                                  child: Text(
                                    c['status']?.toString() ?? 'ACTIVE',
                                    style: AppTypography.uiLabel(
                                      color: isActive
                                          ? AppColors.green700
                                          : AppColors.red600,
                                    ),
                                  ),
                                ),
                              ),
                              DataCell(
                                Text(
                                  c['lastUsedAt'] != null
                                      ? c['lastUsedAt']
                                            .toString()
                                            .split('T')
                                            .first
                                      : 'Never',
                                  style: AppTypography.bodySmall(),
                                ),
                              ),
                              DataCell(
                                Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      icon: const Icon(Icons.refresh, size: 18),
                                      tooltip: 'Rotate Secret',
                                      onPressed: () => _rotateSecret(c),
                                    ),
                                    IconButton(
                                      icon: Icon(
                                        isActive
                                            ? Icons.block
                                            : Icons.check_circle_outline,
                                        size: 18,
                                        color: isActive
                                            ? AppColors.red600
                                            : AppColors.green700,
                                      ),
                                      tooltip: isActive
                                          ? 'Suspend Client'
                                          : 'Activate Client',
                                      onPressed: () => _toggleClientStatus(c),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
          ),
        ),
      ],
    );
  }

  Widget _buildWebhooksTab() {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: _webhooks.isEmpty
          ? Center(
              child: Text(
                'No Webhook Subscriptions Configured',
                style: AppTypography.h3(),
              ),
            )
          : SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SingleChildScrollView(
                scrollDirection: Axis.vertical,
                child: DataTable(
                  columns: const [
                    DataColumn(label: Text('Tenant')),
                    DataColumn(label: Text('Target URL')),
                    DataColumn(label: Text('Subscribed Events')),
                    DataColumn(label: Text('Status')),
                    DataColumn(label: Text('Created At')),
                  ],
                  rows: _webhooks.map((w) {
                    final isActive = w['isActive'] == true;
                    return DataRow(
                      cells: [
                        DataCell(
                          Text(
                            w['tenantName']?.toString() ?? 'Tenant',
                            style: AppTypography.uiLabelBold(),
                          ),
                        ),
                        DataCell(
                          Text(
                            w['targetUrl']?.toString() ?? '',
                            style: AppTypography.monoSmall(),
                          ),
                        ),
                        DataCell(
                          Text(
                            w['subscribedEvents']?.toString() ?? '*',
                            style: AppTypography.monoSmall(),
                          ),
                        ),
                        DataCell(
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: isActive
                                  ? AppColors.green700.withValues(alpha: 0.08)
                                  : AppColors.inkMuted.withValues(alpha: 0.08),
                              borderRadius: BorderRadius.circular(2),
                            ),
                            child: Text(
                              isActive ? 'ACTIVE' : 'INACTIVE',
                              style: AppTypography.uiLabel(
                                color: isActive
                                    ? AppColors.green700
                                    : AppColors.inkMuted,
                              ),
                            ),
                          ),
                        ),
                        DataCell(
                          Text(
                            w['createdAt']?.toString().split('T').first ?? '',
                            style: AppTypography.bodySmall(),
                          ),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),
            ),
    );
  }

  Widget _buildDeliveriesTab() {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: _deliveries.isEmpty
          ? Center(
              child: Text(
                'No Webhook Delivery Logs Recorded',
                style: AppTypography.h3(),
              ),
            )
          : SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SingleChildScrollView(
                scrollDirection: Axis.vertical,
                child: DataTable(
                  columns: const [
                    DataColumn(label: Text('Event Type')),
                    DataColumn(label: Text('Target Endpoint')),
                    DataColumn(label: Text('Status')),
                    DataColumn(label: Text('Attempts')),
                    DataColumn(label: Text('Last Error')),
                    DataColumn(label: Text('Dispatched At')),
                    DataColumn(label: Text('Action')),
                  ],
                  rows: _deliveries.map((d) {
                    final status = d['status']?.toString() ?? 'PENDING';
                    final isDelivered = status == 'DELIVERED';
                    final isFailed =
                        status == 'FAILED' || status == 'DEAD_LETTER';

                    return DataRow(
                      cells: [
                        DataCell(
                          Text(
                            d['eventType']?.toString() ?? '',
                            style: AppTypography.mono(weight: FontWeight.w600),
                          ),
                        ),
                        DataCell(
                          Text(
                            d['targetUrl']?.toString() ?? '',
                            style: AppTypography.monoSmall(),
                          ),
                        ),
                        DataCell(
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: isDelivered
                                  ? AppColors.green700.withValues(alpha: 0.08)
                                  : isFailed
                                  ? AppColors.red600.withValues(alpha: 0.08)
                                  : AppColors.amber600.withValues(alpha: 0.08),
                              borderRadius: BorderRadius.circular(2),
                            ),
                            child: Text(
                              status,
                              style: AppTypography.uiLabel(
                                color: isDelivered
                                    ? AppColors.green700
                                    : isFailed
                                    ? AppColors.red600
                                    : AppColors.amber600,
                              ),
                            ),
                          ),
                        ),
                        DataCell(
                          Text(
                            d['attempts']?.toString() ?? '0',
                            style: AppTypography.monoSmall(),
                          ),
                        ),
                        DataCell(
                          Text(
                            d['lastError']?.toString() ?? '—',
                            style: AppTypography.bodySmall(
                              color: isFailed
                                  ? AppColors.red600
                                  : AppColors.inkMuted,
                            ),
                          ),
                        ),
                        DataCell(
                          Text(
                            d['createdAt']?.toString().split('.').first ?? '',
                            style: AppTypography.bodySmall(),
                          ),
                        ),
                        DataCell(
                          isFailed
                              ? TextButton(
                                  onPressed: () => _retryDelivery(d),
                                  child: const Text('Retry'),
                                )
                              : const Text('—'),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),
            ),
    );
  }

  Map<String, dynamic> _defaultVersionOverview() {
    return {
      'currentVersion': 'v1',
      'supportedVersions': ['v1'],
      'versions': [
        {
          'version': 'v1',
          'current': true,
          'supported': true,
          'lifecycle': 'CURRENT',
          'contractVersion': '1.0.0-RELEASE',
          'releasedAt': '2026-09-18',
          'frozen': true,
          'changePolicy': 'ADDITIVE_ONLY',
          'pathPrefix': '/api/v1',
          'openApiPath': '/v3/api-docs/v1',
          'notes':
              'Frozen fiscal API contract. Breaking changes require a new major version.',
        },
      ],
      'publicationControl': {
        'deploymentControlled': true,
        'activationEnvironmentVariable': 'API_SUPPORTED_VERSIONS',
        'dashboardPolicy':
            'Version publication is deployment-controlled. The dashboard cannot activate a version at runtime.',
        'activationRule':
            'Deploy every instance with controller, security, OpenAPI, and contract-test evidence before enabling a new major.',
      },
      'activationChecklist': [
        'Approved version RFC and compatibility assessment',
        'Dedicated versioned controllers and wire DTOs',
        'Security and contract verification for both old and new versions',
        'Blue/green or all-instance deployment with rollback plan',
      ],
      'retirementChecklist': [
        'Announced deprecation, migration guide, and sunset date',
        'Client migration tracking before disabling the old major',
      ],
    };
  }

  Widget _buildApiVersionsTab() {
    final overview = _apiVersionOverview ?? _defaultVersionOverview();
    final versions = overview['versions'] is List
        ? overview['versions'] as List<dynamic>
        : <dynamic>[];
    final activationChecklist = overview['activationChecklist'] is List
        ? overview['activationChecklist'] as List<dynamic>
        : <dynamic>[];
    final retirementChecklist = overview['retirementChecklist'] is List
        ? overview['retirementChecklist'] as List<dynamic>
        : <dynamic>[];
    final publicationControl = overview['publicationControl'] is Map
        ? Map<String, dynamic>.from(overview['publicationControl'] as Map)
        : <String, dynamic>{};
    final currentVersion = overview['currentVersion']?.toString() ?? 'v1';
    final activationVariable =
        publicationControl['activationEnvironmentVariable']?.toString() ??
        'API_SUPPORTED_VERSIONS';

    return RefreshIndicator(
      onRefresh: _loadAllData,
      child: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: AppColors.navy900,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(
                  Icons.verified_outlined,
                  color: Colors.white,
                  size: 28,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'CURRENT PUBLIC CONTRACT: ${currentVersion.toUpperCase()}',
                        style: AppTypography.h3(color: Colors.white),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'The URL path is authoritative. Existing client contracts remain stable while breaking work is introduced under a new major path.',
                        style: AppTypography.bodySmall(
                          color: Colors.white.withValues(alpha: 0.78),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        _apiVersionCatalogIsLive
                            ? 'LIVE DEPLOYED CATALOG'
                            : 'BASELINE CATALOG — live version endpoint unavailable',
                        style: AppTypography.uiLabel(
                          color: _apiVersionCatalogIsLive
                              ? AppColors.green700
                              : Colors.white.withValues(alpha: 0.78),
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.refresh, color: Colors.white),
                  tooltip: 'Refresh version catalog',
                  onPressed: _loadAllData,
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.amber600.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(4),
              border: Border.all(
                color: AppColors.amber600.withValues(alpha: 0.35),
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(
                  Icons.admin_panel_settings_outlined,
                  color: AppColors.amber600,
                  size: 22,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'DEPLOYMENT-CONTROLLED PUBLICATION',
                        style: AppTypography.uiLabelBold(
                          color: AppColors.amber600,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        publicationControl['dashboardPolicy']?.toString() ??
                            'New API majors are published through an approved deployment, not a runtime dashboard toggle.',
                        style: AppTypography.bodySmall(),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        publicationControl['activationRule']?.toString() ?? '',
                        style: AppTypography.bodySmall(
                          color: AppColors.inkMuted,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Container(
            width: double.infinity,
            decoration: BoxDecoration(
              color: AppColors.paperRaised,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: AppColors.rule),
            ),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: DataTable(
                columns: const [
                  DataColumn(label: Text('Version')),
                  DataColumn(label: Text('Lifecycle')),
                  DataColumn(label: Text('Exposure')),
                  DataColumn(label: Text('Wire Contract')),
                  DataColumn(label: Text('Published')),
                  DataColumn(label: Text('Compatibility Policy')),
                  DataColumn(label: Text('API / OpenAPI')),
                ],
                rows: versions.map((raw) {
                  final version = raw is Map
                      ? Map<String, dynamic>.from(raw)
                      : <String, dynamic>{};
                  final lifecycle = version['lifecycle']?.toString() ?? 'DRAFT';
                  final isCurrent = version['current'] == true;
                  final isSupported = version['supported'] == true;
                  final lifecycleColor =
                      lifecycle == 'CURRENT' || lifecycle == 'STABLE'
                      ? AppColors.green700
                      : lifecycle == 'DEPRECATED' || lifecycle == 'SUNSET'
                      ? AppColors.red600
                      : AppColors.amber600;
                  return DataRow(
                    cells: [
                      DataCell(
                        Text(
                          version['version']?.toString() ?? '—',
                          style: AppTypography.mono(
                            weight: FontWeight.w700,
                            color: AppColors.navy900,
                          ),
                        ),
                      ),
                      DataCell(
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: lifecycleColor.withValues(alpha: 0.10),
                            borderRadius: BorderRadius.circular(2),
                          ),
                          child: Text(
                            lifecycle,
                            style: AppTypography.uiLabel(color: lifecycleColor),
                          ),
                        ),
                      ),
                      DataCell(
                        Text(
                          isSupported
                              ? (isCurrent ? 'CURRENT ROUTE' : 'SUPPORTED')
                              : 'NOT PUBLISHED',
                          style: AppTypography.uiLabel(
                            color: isSupported
                                ? AppColors.green700
                                : AppColors.inkMuted,
                          ),
                        ),
                      ),
                      DataCell(
                        Text(
                          version['contractVersion']?.toString() ?? '—',
                          style: AppTypography.monoSmall(),
                        ),
                      ),
                      DataCell(
                        Text(
                          version['releasedAt']?.toString() ?? 'Not published',
                          style: AppTypography.bodySmall(),
                        ),
                      ),
                      DataCell(
                        Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              version['changePolicy']?.toString() ?? '—',
                              style: AppTypography.monoSmall(),
                            ),
                            if (version['frozen'] == true)
                              Text(
                                'FROZEN',
                                style: AppTypography.uiLabel(
                                  color: AppColors.amber600,
                                ),
                              ),
                          ],
                        ),
                      ),
                      DataCell(
                        Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              version['pathPrefix']?.toString() ?? '—',
                              style: AppTypography.monoSmall(),
                            ),
                            Text(
                              version['openApiPath']?.toString() ?? '—',
                              style: AppTypography.monoSmall(
                                color: AppColors.inkMuted,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  );
                }).toList(),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: _buildVersionChecklistCard(
                  'Publish a new major',
                  Icons.rocket_launch_outlined,
                  AppColors.green700,
                  activationChecklist,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: _buildVersionChecklistCard(
                  'Retire a major safely',
                  Icons.event_busy_outlined,
                  AppColors.red600,
                  retirementChecklist,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.paperRaised,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: AppColors.rule),
            ),
            child: Row(
              children: [
                const Icon(Icons.terminal_outlined, color: AppColors.navy900),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'APPROVED ACTIVATION SETTING',
                        style: AppTypography.uiLabelBold(),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        'After the release gates are complete, publish v2 with:',
                        style: AppTypography.bodySmall(
                          color: AppColors.inkMuted,
                        ),
                      ),
                      const SizedBox(height: 6),
                      SelectableText(
                        '$activationVariable=v1,v2',
                        style: AppTypography.mono(
                          weight: FontWeight.w700,
                          color: AppColors.navy900,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.copy_outlined, size: 18),
                  tooltip: 'Copy activation setting',
                  onPressed: () async {
                    await Clipboard.setData(
                      ClipboardData(text: '$activationVariable=v1,v2'),
                    );
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Activation setting copied'),
                        ),
                      );
                    }
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVersionChecklistCard(
    String title,
    IconData icon,
    Color accent,
    List<dynamic> items,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: accent, size: 20),
              const SizedBox(width: 8),
              Text(title, style: AppTypography.h3()),
            ],
          ),
          const SizedBox(height: 12),
          ...items.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 9),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.check_circle_outline, color: accent, size: 17),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      item.toString(),
                      style: AppTypography.bodySmall(),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
