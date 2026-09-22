import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class SubscriptionRecord {
  final String tenantId;
  final String tenantName;
  final String tin;
  final String plan;
  final String status;
  final int maxBranches;
  final int maxPosDevices;
  final int monthlyInvoiceQuota;
  final DateTime nextBillingDate;

  SubscriptionRecord({
    required this.tenantId,
    required this.tenantName,
    required this.tin,
    required this.plan,
    required this.status,
    required this.maxBranches,
    required this.maxPosDevices,
    required this.monthlyInvoiceQuota,
    required this.nextBillingDate,
  });

  SubscriptionRecord copyWith({
    String? plan,
    String? status,
    int? maxBranches,
    int? maxPosDevices,
    int? monthlyInvoiceQuota,
  }) {
    return SubscriptionRecord(
      tenantId: tenantId,
      tenantName: tenantName,
      tin: tin,
      plan: plan ?? this.plan,
      status: status ?? this.status,
      maxBranches: maxBranches ?? this.maxBranches,
      maxPosDevices: maxPosDevices ?? this.maxPosDevices,
      monthlyInvoiceQuota: monthlyInvoiceQuota ?? this.monthlyInvoiceQuota,
      nextBillingDate: nextBillingDate,
    );
  }
}

class SubscriptionsScreen extends ConsumerStatefulWidget {
  const SubscriptionsScreen({super.key});

  @override
  ConsumerState<SubscriptionsScreen> createState() => _SubscriptionsScreenState();
}

class _SubscriptionsScreenState extends ConsumerState<SubscriptionsScreen> {
  final List<SubscriptionRecord> _subscriptions = [];
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _loadSubscriptions();
  }

  Future<void> _loadSubscriptions() async {
    setState(() => _isLoading = true);

    try {
      final client = ref.read(saasManagementApiClientProvider);
      final response = await client.get('/api/v1/saas/subscriptions');
      if (response.data is List) {
        final List list = response.data;
        _subscriptions.clear();
        for (final item in list) {
          _subscriptions.add(SubscriptionRecord(
            tenantId: item['tenantId']?.toString() ?? '',
            tenantName: item['tenantName']?.toString() ?? 'Taxpayer Entity',
            tin: item['tin']?.toString() ?? '',
            plan: item['plan']?.toString() ?? 'GROWTH',
            status: item['status']?.toString() ?? 'ACTIVE',
            maxBranches: (item['maxBranches'] as num?)?.toInt() ?? 5,
            maxPosDevices: (item['maxPosDevices'] as num?)?.toInt() ?? 10,
            monthlyInvoiceQuota: (item['monthlyInvoiceQuota'] as num?)?.toInt() ?? 50000,
            nextBillingDate: item['nextBillingDate'] != null
                ? DateTime.tryParse(item['nextBillingDate'].toString()) ?? DateTime.now().add(const Duration(days: 30))
                : DateTime.now().add(const Duration(days: 30)),
          ));
        }
      }
    } catch (_) {
      // Error handled, display real DB records or empty state
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _openEditPlanDialog(SubscriptionRecord sub) {
    final List<Map<String, String>> planOptions = [
      {'value': 'STARTER', 'label': 'STARTER (5,000 inv/mo, 1 Branch, 2 POS)'},
      {'value': 'GROWTH', 'label': 'GROWTH (50,000 inv/mo, 5 Branches, 10 POS)'},
      {'value': 'ENTERPRISE', 'label': 'ENTERPRISE (50 Branches, 100 POS)'},
      {'value': 'ENTERPRISE_UNLIMITED', 'label': 'ENTERPRISE UNLIMITED (Unlimited Quotas)'},
    ];

    final String rawPlan = sub.plan.trim();
    String selectedPlan = rawPlan.isNotEmpty ? rawPlan : 'ENTERPRISE_UNLIMITED';
    int branches = sub.maxBranches;
    int devices = sub.maxPosDevices;
    int quota = sub.monthlyInvoiceQuota;
    bool isSaving = false;

    showDialog(
      context: context,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (context, setDialogState) {
          final List<DropdownMenuItem<String>> menuItems = planOptions.map((opt) {
            return DropdownMenuItem<String>(
              value: opt['value']!,
              child: Text(opt['label']!),
            );
          }).toList();

          // Ensure selectedPlan matches exactly one item to prevent Flutter assertion failure
          if (!menuItems.any((item) => item.value == selectedPlan)) {
            menuItems.add(DropdownMenuItem<String>(
              value: selectedPlan,
              child: Text('$selectedPlan (Current Plan)'),
            ));
          }

          return AlertDialog(
            title: Text('MODIFY TENANT SUBSCRIPTION: ${sub.tenantName}', style: AppTypography.h2()),
            content: SizedBox(
              width: 480,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Tenant ID: ${sub.tenantId} | TIN: ${sub.tin}', style: AppTypography.monoSmall()),
                  const SizedBox(height: 16),
                  Text('Select Commercial Tier:', style: AppTypography.uiLabelBold()),
                  const SizedBox(height: 8),
                  DropdownButton<String>(
                    value: selectedPlan,
                    isExpanded: true,
                    items: menuItems,
                    onChanged: (v) {
                      if (v != null) {
                        setDialogState(() {
                          selectedPlan = v;
                          if (v == 'STARTER') {
                            branches = 1;
                            devices = 2;
                            quota = 5000;
                          } else if (v == 'GROWTH') {
                            branches = 5;
                            devices = 10;
                            quota = 50000;
                          } else if (v == 'ENTERPRISE') {
                            branches = 50;
                            devices = 100;
                            quota = 1000000;
                          } else if (v == 'ENTERPRISE_UNLIMITED') {
                            branches = 100;
                            devices = 500;
                            quota = 50000000;
                          }
                        });
                      }
                    },
                  ),
                const SizedBox(height: 16),
                Text('Max Branches Quota:', style: AppTypography.uiLabel()),
                TextFormField(
                  initialValue: '$branches',
                  keyboardType: TextInputType.number,
                  onChanged: (v) => branches = int.tryParse(v) ?? branches,
                  decoration: const InputDecoration(isDense: true),
                ),
                const SizedBox(height: 12),
                Text('Max POS Devices Quota:', style: AppTypography.uiLabel()),
                TextFormField(
                  initialValue: '$devices',
                  keyboardType: TextInputType.number,
                  onChanged: (v) => devices = int.tryParse(v) ?? devices,
                  decoration: const InputDecoration(isDense: true),
                ),
                const SizedBox(height: 12),
                Text('Monthly Invoice Quota:', style: AppTypography.uiLabel()),
                TextFormField(
                  initialValue: '$quota',
                  keyboardType: TextInputType.number,
                  onChanged: (v) => quota = int.tryParse(v) ?? quota,
                  decoration: const InputDecoration(isDense: true),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSaving ? null : () => Navigator.of(dialogCtx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSaving
                  ? null
                  : () async {
                      setDialogState(() => isSaving = true);
                      try {
                        final client = ref.read(saasManagementApiClientProvider);
                        await client.put('/api/v1/saas/subscriptions/${sub.tenantId}', data: {
                          'plan': selectedPlan,
                          'maxBranches': branches,
                          'maxPosDevices': devices,
                          'monthlyInvoiceQuota': quota,
                        });
                      } catch (_) {}

                      setState(() {
                        final idx = _subscriptions.indexWhere((s) => s.tenantId == sub.tenantId);
                        if (idx != -1) {
                          _subscriptions[idx] = sub.copyWith(
                            plan: selectedPlan,
                            maxBranches: branches,
                            maxPosDevices: devices,
                            monthlyInvoiceQuota: quota,
                          );
                        }
                      });

                      if (dialogCtx.mounted) {
                        Navigator.of(dialogCtx).pop();
                      }

                      if (mounted) {
                        // ignore: use_build_context_synchronously
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('Updated subscription for "${sub.tenantName}".'),
                            backgroundColor: AppColors.green700,
                          ),
                        );
                      }
                    },
              child: Text(isSaving ? 'Updating...' : 'Save Changes'),
            ),
          ],
        );
      },
    ),
  );
}

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('PLANS, SUBSCRIPTIONS & ENTITLEMENTS', style: AppTypography.h1()),
                      const SizedBox(height: 4),
                      Text(
                        'Commercial tiers, feature entitlements, and quota management for taxpayer organizations',
                        style: AppTypography.bodySmall(),
                      ),
                    ],
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: _loadSubscriptions,
                  icon: const Icon(Icons.refresh, size: 16),
                  label: const Text('Refresh'),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Subscription Tiers Grid
            LayoutBuilder(
              builder: (context, constraints) {
                final isWide = constraints.maxWidth >= 960;
                final starterCard = _buildTierCard(
                  title: 'STARTER TIER',
                  price: 'ETB 1,800',
                  period: '/ month billed annually',
                  invoices: '5,000 invoices / mo',
                  branches: '1 Branch Included',
                  devices: '2 POS Terminals',
                  features: [
                    'Basic Product & Service Catalog',
                    '72h Offline Storage Outbox',
                    'Standard MoR EIRS Sync',
                    'Email Support',
                  ],
                  color: AppColors.ink,
                );
                final growthCard = _buildTierCard(
                  title: 'GROWTH TIER',
                  price: 'ETB 4,500',
                  period: '/ month billed annually',
                  invoices: '50,000 invoices / mo',
                  branches: '5 Branches Included',
                  devices: '10 POS Terminals',
                  features: [
                    'Full Multi-Branch Stock Tracking',
                    'Automated Low-Stock Alerts',
                    'Priority EIRS Gateway Channel',
                    'Z-Report & Fiscal Ledger Export',
                    'Phone & Ticket SLA (4 Hours)',
                  ],
                  color: AppColors.navy700,
                  isPopular: true,
                );
                final enterpriseCard = _buildTierCard(
                  title: 'ENTERPRISE TIER',
                  price: 'ETB 12,000',
                  period: '/ month billed annually',
                  invoices: 'Unlimited Invoices',
                  branches: 'Unlimited Branches',
                  devices: 'Unlimited POS Terminals',
                  features: [
                    'Dedicated API Integration Gateway',
                    'Custom ERP Connector (SAP / Oracle)',
                    'Hardware POS Fleet Management',
                    '24/7 Dedicated Account Manager',
                    'Custom Regulatory Compliance Audit',
                  ],
                  color: AppColors.navy900,
                );

                if (isWide) {
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: starterCard),
                      const SizedBox(width: 16),
                      Expanded(child: growthCard),
                      const SizedBox(width: 16),
                      Expanded(child: enterpriseCard),
                    ],
                  );
                } else {
                  return Column(
                    children: [
                      starterCard,
                      const SizedBox(height: 16),
                      growthCard,
                      const SizedBox(height: 16),
                      enterpriseCard,
                    ],
                  );
                }
              },
            ),
            const SizedBox(height: 32),

            // Active Tenant Subscriptions Table
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('ACTIVE TENANT SUBSCRIPTIONS & QUOTAS', style: AppTypography.h2()),
                  const SizedBox(height: 16),
                  if (_isLoading)
                    const Center(child: Padding(padding: EdgeInsets.all(20), child: CircularProgressIndicator()))
                  else
                    ListView.separated(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: _subscriptions.length,
                      separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                      itemBuilder: (context, index) {
                        final sub = _subscriptions[index];
                        return ListTile(
                          contentPadding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
                          title: Row(
                            children: [
                              Flexible(
                                child: Text(
                                  sub.tenantName,
                                  style: AppTypography.uiLabelBold(),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppColors.navy700.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: Text(sub.plan, style: AppTypography.monoSmall(color: AppColors.navy700, weight: FontWeight.w700)),
                              ),
                              const SizedBox(width: 12),
                              Text(
                                '${sub.monthlyInvoiceQuota.toString()} inv/mo',
                                style: AppTypography.mono(color: AppColors.ink, weight: FontWeight.w600),
                              ),
                            ],
                          ),
                          subtitle: Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(
                              'TIN: ${sub.tin} | Branches Limit: ${sub.maxBranches} | POS Limit: ${sub.maxPosDevices} | Next Renewal: ${sub.nextBillingDate.toLocal().toString().split(' ')[0]}',
                              style: AppTypography.monoSmall(color: AppColors.inkMuted),
                            ),
                          ),
                          trailing: ElevatedButton(
                            onPressed: () => _openEditPlanDialog(sub),
                            style: ElevatedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                            ),
                            child: const Text('Manage Plan'),
                          ),
                        );
                      },
                    ),
                ],
              ),
            ),
            const SizedBox(height: 32),

            // Entitlements Matrix
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('ENTITLEMENTS & REGULATORY CAPABILITIES MATRIX', style: AppTypography.h2()),
                  const SizedBox(height: 16),
                  _buildEntitlementRow('Directive No. 1142/2026 EIRS Gateway Certification', 'All Tiers', true),
                  const Divider(height: 16, color: AppColors.rule),
                  _buildEntitlementRow('Drift Encrypted Offline Outbox & Idempotency Engine', 'All Tiers', true),
                  const Divider(height: 16, color: AppColors.rule),
                  _buildEntitlementRow('Multi-Branch Inventory Snapshot & Transfer Operations', 'Growth & Enterprise', true),
                  const Divider(height: 16, color: AppColors.rule),
                  _buildEntitlementRow('External ERP & Accounting System REST Webhooks', 'Enterprise Only', true),
                  const Divider(height: 16, color: AppColors.rule),
                  _buildEntitlementRow('Dedicated Hardware Security Module (HSM) Signing', 'Enterprise Only', true),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTierCard({
    required String title,
    required String price,
    required String period,
    required String invoices,
    required String branches,
    required String devices,
    required List<String> features,
    required Color color,
    bool isPopular = false,
  }) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(
          color: isPopular ? AppColors.navy700 : AppColors.rule,
          width: isPopular ? 2 : 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(title, style: AppTypography.uiLabelBold(color: color)),
              if (isPopular)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.navy700,
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Text('POPULAR', style: AppTypography.monoSmall(color: Colors.white, weight: FontWeight.w700)),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Text(price, style: AppTypography.display(color: color).copyWith(fontSize: 28)),
          Text(period, style: AppTypography.bodySmall(color: AppColors.inkMuted)),
          const Divider(height: 24, color: AppColors.rule),
          Text(invoices, style: AppTypography.uiLabelBold()),
          const SizedBox(height: 4),
          Text('$branches • $devices', style: AppTypography.bodySmall()),
          const SizedBox(height: 16),
          ...features.map(
            (f) => Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Row(
                children: [
                  const Icon(Icons.check, size: 14, color: AppColors.green700),
                  const SizedBox(width: 8),
                  Expanded(child: Text(f, style: AppTypography.bodySmall())),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEntitlementRow(String capability, String tierRequirement, bool isIncluded) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(capability, style: AppTypography.uiLabel()),
        Text(tierRequirement, style: AppTypography.mono(weight: FontWeight.w600, color: AppColors.navy700)),
      ],
    );
  }
}
