import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';

class TenantOversightRecord {
  final String tenantId;
  final String legalName;
  final String tin;
  final String taxOffice;
  final int branchCount;
  final int posDeviceCount;
  final bool isMoRCompliant;
  final bool hasPendingSyncErrors;
  final DateTime lastFiscalActivity;

  const TenantOversightRecord({
    required this.tenantId,
    required this.legalName,
    required this.tin,
    required this.taxOffice,
    required this.branchCount,
    required this.posDeviceCount,
    required this.isMoRCompliant,
    required this.hasPendingSyncErrors,
    required this.lastFiscalActivity,
  });
}

class TenantOversightScreen extends ConsumerStatefulWidget {
  const TenantOversightScreen({super.key});

  @override
  ConsumerState<TenantOversightScreen> createState() => _TenantOversightScreenState();
}

class _TenantOversightScreenState extends ConsumerState<TenantOversightScreen> {
  final _searchController = TextEditingController();
  final List<TenantOversightRecord> _tenants = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadOversight();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadOversight() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final client = ref.read(masterAdminApiClientProvider);
      final response = await client.get('/api/v1/master/tenants-oversight');
      if (response.data is List) {
        final List list = response.data;
        _tenants.clear();
        for (final item in list) {
          _tenants.add(TenantOversightRecord(
            tenantId: item['tenantId']?.toString() ?? '',
            legalName: item['legalName']?.toString() ?? 'Taxpayer Entity',
            tin: item['tin']?.toString() ?? '',
            taxOffice: item['taxOffice']?.toString() ?? 'Addis Ababa LTO',
            branchCount: (item['branchCount'] as num?)?.toInt() ?? 1,
            posDeviceCount: (item['posDeviceCount'] as num?)?.toInt() ?? 2,
            isMoRCompliant: item['isMoRCompliant'] == true,
            hasPendingSyncErrors: item['hasPendingSyncErrors'] == true,
            lastFiscalActivity: item['lastFiscalActivity'] != null
                ? DateTime.tryParse(item['lastFiscalActivity'].toString()) ?? DateTime.now()
                : DateTime.now(),
          ));
        }
      }
      setState(() => _isLoading = false);
    } catch (e) {
      setState(() {
        _isLoading = false;
        _error = 'Unable to fetch taxpayer oversight records from database.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final query = _searchController.text.trim().toLowerCase();
    final filtered = _tenants.where((t) {
      if (query.isEmpty) return true;
      return t.legalName.toLowerCase().contains(query) || t.tin.contains(query) || t.tenantId.toLowerCase().contains(query);
    }).toList();

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Padding(
        padding: const EdgeInsets.all(24),
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
                    Text('TENANT REGULATORY OVERSIGHT', style: AppTypography.h1()),
                    const SizedBox(height: 4),
                    Text(
                      'Live database supervisory inspection of taxpayer compliance, 72-hour synchronization, and device integrity',
                      style: AppTypography.bodySmall(),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: _loadOversight,
                  icon: const Icon(Icons.refresh, size: 16),
                  label: const Text('Refresh'),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Strict Privacy Boundary Banner (Section 1 Invariant)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.navy900.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.navy900.withValues(alpha: 0.2)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.privacy_tip_outlined, size: 24, color: AppColors.navy900),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Text(
                      'STRICT TENANT PRIVACY BOUNDARY: Master Admin privileges are limited to platform compliance, cluster health, and cryptographic verification. Unrestricted access to private tenant financial records or customer lists is prohibited by system design.',
                      style: AppTypography.bodySmall(color: AppColors.ink).copyWith(fontWeight: FontWeight.w500),
                    ),
                  ),
                ],
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.amber600.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.amber600),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.warning_amber, size: 18, color: AppColors.amber600),
                    const SizedBox(width: 8),
                    Expanded(child: Text(_error!, style: AppTypography.bodySmall(color: AppColors.ink))),
                    TextButton(onPressed: _loadOversight, child: const Text('Retry')),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 20),

            // Search Bar
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule),
              ),
              child: TextField(
                controller: _searchController,
                decoration: const InputDecoration(
                  hintText: 'Search supervised tenants by Legal Name, TIN, or ID...',
                  prefixIcon: Icon(Icons.search, size: 18),
                  isDense: true,
                ),
                onChanged: (_) => setState(() {}),
              ),
            ),
            const SizedBox(height: 16),

            // Table
            Expanded(
              child: Material(
                color: AppColors.paperRaised,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(3),
                  side: const BorderSide(color: AppColors.rule),
                ),
                clipBehavior: Clip.antiAlias,
                child: _isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : (filtered.isEmpty
                        ? Center(
                            child: Text(
                              'No registered taxpayers found in database.',
                              style: AppTypography.bodySmall(color: AppColors.inkMuted),
                            ),
                          )
                        : ListView.separated(
                            itemCount: filtered.length,
                            separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                            itemBuilder: (context, index) {
                              final t = filtered[index];
                              return Material(
                                type: MaterialType.transparency,
                                child: ListTile(
                                  contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                                  leading: Icon(
                                    t.isMoRCompliant ? Icons.verified : Icons.warning_amber,
                                    color: t.isMoRCompliant ? AppColors.green700 : AppColors.amber600,
                                  ),
                                  title: Row(
                                    children: [
                                      Text(t.legalName, style: AppTypography.uiLabelBold()),
                                      const SizedBox(width: 10),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: t.isMoRCompliant
                                              ? AppColors.green700.withValues(alpha: 0.1)
                                              : AppColors.amber600.withValues(alpha: 0.1),
                                          borderRadius: BorderRadius.circular(3),
                                        ),
                                        child: Text(
                                          t.isMoRCompliant ? 'COMPLIANT' : 'ATTENTION REQUIRED',
                                          style: AppTypography.monoSmall(
                                            color: t.isMoRCompliant ? AppColors.green700 : AppColors.amber600,
                                            weight: FontWeight.w700,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  subtitle: Padding(
                                    padding: const EdgeInsets.only(top: 4),
                                    child: Text(
                                      'TIN: ${t.tin} | Branches: ${t.branchCount} | POS Hardware: ${t.posDeviceCount} | Jurisdiction: ${t.taxOffice}',
                                      style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                    ),
                                  ),
                                  trailing: Text(
                                    'Last Activity: ${t.lastFiscalActivity.toLocal().toString().length >= 16 ? t.lastFiscalActivity.toLocal().toString().substring(0, 16) : t.lastFiscalActivity.toLocal().toString()}',
                                    style: AppTypography.monoSmall(),
                                  ),
                                ),
                              );
                            },
                          )),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
