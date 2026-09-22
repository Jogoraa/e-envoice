import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class MasterApiManagementScreen extends ConsumerStatefulWidget {
  const MasterApiManagementScreen({super.key});

  @override
  ConsumerState<MasterApiManagementScreen> createState() => _MasterApiManagementScreenState();
}

class _MasterApiManagementScreenState extends ConsumerState<MasterApiManagementScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = false;
  List<dynamic> _apiClients = [];
  List<dynamic> _webhooks = [];
  List<dynamic> _deliveries = [];
  List<dynamic> _tenants = [];
  String _clientSearchQuery = '';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadAllData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadAllData() async {
    setState(() => _isLoading = true);
    final apiClient = ref.read(apiClientProvider);

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
      final delivRes = await apiClient.get('/api/v1/master/webhooks/deliveries?size=50');
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
            'lastUsedAt': DateTime.now().subtract(const Duration(minutes: 14)).toIso8601String(),
            'createdAt': DateTime.now().subtract(const Duration(days: 10)).toIso8601String(),
          }
        ];
      }
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
    String? selectedTenantId = _tenants.isNotEmpty ? _tenants.first['id']?.toString() : '00000000-0000-0000-0000-000000000001';
    final selectedScopes = <String>{'invoice:create', 'invoice:read'};

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('REGISTER NEW ERP / M2M API CLIENT', style: AppTypography.h2()),
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
                      decoration: const InputDecoration(labelText: 'Target Tenant Organization *'),
                      items: _tenants.map<DropdownMenuItem<String>>((t) {
                        final id = t['id']?.toString() ?? '';
                        final name = t['companyName']?.toString() ?? t['tradingName']?.toString() ?? 'Tenant';
                        final tin = t['tin']?.toString() ?? '';
                        return DropdownMenuItem(
                          value: id,
                          child: Text('$name ($tin)', overflow: TextOverflow.ellipsis),
                        );
                      }).toList(),
                      onChanged: (v) => setDialogState(() => selectedTenantId = v),
                      validator: (v) => v == null ? 'Please select a tenant' : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: clientIdCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Client Identifier (X-API-Key) *',
                        hintText: 'e.g., CLIENT_SAP_PROD_01',
                      ),
                      textCapitalization: TextCapitalization.characters,
                      validator: (v) => (v == null || v.trim().isEmpty) ? 'Client identifier required' : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: clientNameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Client Friendly Description *',
                        hintText: 'e.g., SAP ERP Production Ingress',
                      ),
                      validator: (v) => (v == null || v.trim().isEmpty) ? 'Description required' : null,
                    ),
                    const SizedBox(height: 16),
                    Text('Authorized Scopes:', style: AppTypography.uiLabelBold()),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        'invoice:create',
                        'invoice:read',
                        'tenant:admin',
                        'offline:sync',
                        'reports:read',
                      ].map((scope) {
                        final isSelected = selectedScopes.contains(scope);
                        return FilterChip(
                          label: Text(scope, style: AppTypography.monoSmall()),
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
            OutlinedButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
            ElevatedButton(
              onPressed: () async {
                if (!formKey.currentState!.validate()) return;
                if (selectedScopes.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Please select at least one scope')),
                  );
                  return;
                }

                final apiClient = ref.read(apiClientProvider);
                final messenger = ScaffoldMessenger.of(context);
                try {
                  final res = await apiClient.post('/api/v1/master/api-clients', data: {
                    'tenantId': selectedTenantId,
                    'clientId': clientIdCtrl.text.trim().toUpperCase(),
                    'clientName': clientNameCtrl.text.trim(),
                    'scopes': selectedScopes.join(' '),
                  });

                  if (ctx.mounted) {
                    Navigator.pop(ctx);
                  }
                  await _loadAllData();

                  if (!mounted) return;
                  if (res.statusCode == 200 || res.statusCode == 201) {
                    final rawSecret = res.data['rawSecret']?.toString() ?? 'Secret Generated';
                    final cId = res.data['clientId']?.toString() ?? clientIdCtrl.text.trim().toUpperCase();
                    _showSecretBannerDialog(cId, rawSecret);
                  }
                } catch (err) {
                  messenger.showSnackBar(
                    SnackBar(content: Text('Failed to create API client: $err'), backgroundColor: AppColors.red600),
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
            const Icon(Icons.warning_amber_rounded, color: AppColors.amber600, size: 28),
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
                  border: Border.all(color: AppColors.amber600.withValues(alpha: 0.4)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.lock_outline, color: AppColors.amber600, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'ATTENTION: This client secret is displayed ONCE. It is hashed with SHA-256 in the database and cannot be retrieved later. Store it securely in your secret manager.',
                        style: AppTypography.bodySmall(color: AppColors.amber600).copyWith(fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Text('Client ID (X-API-Key):', style: AppTypography.uiLabelBold()),
              const SizedBox(height: 4),
              SelectableText(clientId, style: AppTypography.mono(weight: FontWeight.w600)),
              const SizedBox(height: 12),
              Text('Client Secret (X-Client-Secret):', style: AppTypography.uiLabelBold()),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
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
                        style: AppTypography.mono(weight: FontWeight.w700, color: AppColors.navy900),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 18),
                      tooltip: 'Copy secret to clipboard',
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: rawSecret));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Client secret copied to clipboard!')),
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
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.red600),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Rotate Secret Now'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    final apiClient = ref.read(apiClientProvider);
    try {
      final res = await apiClient.post('/api/v1/master/api-clients/${client['id']}/rotate-secret');
      if (res.statusCode == 200 && res.data != null) {
        final newSecret = res.data['rawSecret']?.toString() ?? '';
        final cId = res.data['clientId']?.toString() ?? client['clientId'];
        _showSecretBannerDialog(cId, newSecret);
      }
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to rotate secret: $err'), backgroundColor: AppColors.red600),
        );
      }
    }
  }

  void _toggleClientStatus(dynamic client) async {
    final newStatus = client['status'] == 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    final apiClient = ref.read(apiClientProvider);
    try {
      await apiClient.put('/api/v1/master/api-clients/${client['id']}/status', data: {'status': newStatus});
      await _loadAllData();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Client status updated to $newStatus')),
        );
      }
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to update status: $err'), backgroundColor: AppColors.red600),
        );
      }
    }
  }

  void _retryDelivery(dynamic delivery) async {
    final apiClient = ref.read(apiClientProvider);
    try {
      final res = await apiClient.post('/api/v1/master/webhooks/deliveries/${delivery['id']}/retry');
      await _loadAllData();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Delivery retry triggered: ${res.data['newStatus'] ?? 'Processed'}')),
        );
      }
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to retry delivery: $err'), backgroundColor: AppColors.red600),
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
                    Text('ERP INTEGRATIONS & WEBHOOKS', style: AppTypography.h1()),
                    const SizedBox(height: 4),
                    Text(
                      'Machine-to-Machine Credentials (X-API-Key / Secret) & Event Dispatch Oversight',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: _showCreateClientDialog,
                  icon: const Icon(Icons.vpn_key_outlined, size: 18),
                  label: const Text('PROVISION API CLIENT'),
                  style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14)),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Tab Bar
            Container(
              decoration: const BoxDecoration(
                border: Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
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
                    hintText: 'Search by Client ID, friendly name, or tenant...',
                    prefixIcon: Icon(Icons.search, size: 20, color: AppColors.inkMuted),
                  ),
                  onChanged: (v) => setState(() => _clientSearchQuery = v.trim()),
                ),
              ),
              const SizedBox(width: 16),
              IconButton(icon: const Icon(Icons.refresh), tooltip: 'Reload', onPressed: _loadAllData),
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
                    child: Text('No API Clients Registered', style: AppTypography.h3()),
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
                                  style: AppTypography.mono(weight: FontWeight.w700, color: AppColors.navy900),
                                ),
                              ),
                              DataCell(
                                Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(c['clientName']?.toString() ?? '', style: AppTypography.uiLabelBold()),
                                    Text(c['tenantName']?.toString() ?? '', style: AppTypography.bodySmall(color: AppColors.inkMuted)),
                                  ],
                                ),
                              ),
                              DataCell(
                                Text(c['scopes']?.toString() ?? '', style: AppTypography.monoSmall()),
                              ),
                              DataCell(
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: isActive
                                        ? AppColors.green700.withValues(alpha: 0.08)
                                        : AppColors.red600.withValues(alpha: 0.08),
                                    borderRadius: BorderRadius.circular(2),
                                    border: Border.all(
                                      color: isActive
                                          ? AppColors.green700.withValues(alpha: 0.3)
                                          : AppColors.red600.withValues(alpha: 0.3),
                                    ),
                                  ),
                                  child: Text(
                                    c['status']?.toString() ?? 'ACTIVE',
                                    style: AppTypography.uiLabel(
                                      color: isActive ? AppColors.green700 : AppColors.red600,
                                    ),
                                  ),
                                ),
                              ),
                              DataCell(
                                Text(
                                  c['lastUsedAt'] != null ? c['lastUsedAt'].toString().split('T').first : 'Never',
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
                                        isActive ? Icons.block : Icons.check_circle_outline,
                                        size: 18,
                                        color: isActive ? AppColors.red600 : AppColors.green700,
                                      ),
                                      tooltip: isActive ? 'Suspend Client' : 'Activate Client',
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
              child: Text('No Webhook Subscriptions Configured', style: AppTypography.h3()),
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
                        DataCell(Text(w['tenantName']?.toString() ?? 'Tenant', style: AppTypography.uiLabelBold())),
                        DataCell(Text(w['targetUrl']?.toString() ?? '', style: AppTypography.monoSmall())),
                        DataCell(Text(w['subscribedEvents']?.toString() ?? '*', style: AppTypography.monoSmall())),
                        DataCell(
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: isActive
                                  ? AppColors.green700.withValues(alpha: 0.08)
                                  : AppColors.inkMuted.withValues(alpha: 0.08),
                              borderRadius: BorderRadius.circular(2),
                            ),
                            child: Text(
                              isActive ? 'ACTIVE' : 'INACTIVE',
                              style: AppTypography.uiLabel(color: isActive ? AppColors.green700 : AppColors.inkMuted),
                            ),
                          ),
                        ),
                        DataCell(Text(w['createdAt']?.toString().split('T').first ?? '', style: AppTypography.bodySmall())),
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
              child: Text('No Webhook Delivery Logs Recorded', style: AppTypography.h3()),
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
                    final isFailed = status == 'FAILED' || status == 'DEAD_LETTER';

                    return DataRow(
                      cells: [
                        DataCell(Text(d['eventType']?.toString() ?? '', style: AppTypography.mono(weight: FontWeight.w600))),
                        DataCell(Text(d['targetUrl']?.toString() ?? '', style: AppTypography.monoSmall())),
                        DataCell(
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
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
                        DataCell(Text(d['attempts']?.toString() ?? '0', style: AppTypography.monoSmall())),
                        DataCell(
                          Text(
                            d['lastError']?.toString() ?? '—',
                            style: AppTypography.bodySmall(color: isFailed ? AppColors.red600 : AppColors.inkMuted),
                          ),
                        ),
                        DataCell(Text(d['createdAt']?.toString().split('.').first ?? '', style: AppTypography.bodySmall())),
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
}
