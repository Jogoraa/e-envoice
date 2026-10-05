import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/marketplace_service.dart';

class MarketplaceManagementScreen extends ConsumerStatefulWidget {
  const MarketplaceManagementScreen({super.key});

  @override
  ConsumerState<MarketplaceManagementScreen> createState() =>
      _MarketplaceManagementScreenState();
}

class _MarketplaceManagementScreenState
    extends ConsumerState<MarketplaceManagementScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = true;
  String? _errorMessage;
  List<MarketplaceMerchantDto> _merchants = [];

  final _dateFormat = DateFormat('dd/MM/yyyy');

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
      _errorMessage = null;
    });

    try {
      final service = ref.read(marketplaceServiceProvider);
      final merchants = await service.listMerchants();
      if (mounted) {
        setState(() {
          _merchants = merchants;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  void _showRegisterMerchantDialog() {
    showDialog(
      context: context,
      builder: (ctx) => _RegisterMerchantDialog(
        onSuccess: () {
          _loadData();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Merchant registered to multi-seller marketplace.'),
              backgroundColor: AppColors.success,
            ),
          );
        },
      ),
    );
  }

  void _showMultiSellerOrderDemo() {
    showDialog(
      context: context,
      builder: (ctx) => _MultiSellerFiscalSplitDialog(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Marketplace & Multi-Seller Operations (Art. 18)'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: [
            Tab(text: 'Merchant Registry (${_merchants.length})'),
            const Tab(text: 'Multi-Seller Fiscal Order Split (Demo)'),
          ],
        ),
      ),
      body: Column(
        children: [
          _buildDirectiveNoticeCard(),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.error_outline, size: 48, color: AppColors.error),
                            const SizedBox(height: 16),
                            Text(_errorMessage!, style: AppTypography.bodyMedium),
                            const SizedBox(height: 16),
                            ElevatedButton(
                              onPressed: _loadData,
                              child: const Text('Retry'),
                            ),
                          ],
                        ),
                      )
                    : TabBarView(
                        controller: _tabController,
                        children: [
                          _buildMerchantsList(),
                          _buildOrderSplitView(),
                        ],
                      ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showRegisterMerchantDialog,
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.store),
        label: const Text('Register Merchant'),
      ),
    );
  }

  Widget _buildDirectiveNoticeCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      color: AppColors.navy700.withValues(alpha: 0.08),
      child: Row(
        children: [
          const Icon(Icons.shopping_cart_checkout, color: AppColors.navy700, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 18 — Marketplace & Intermediary Platforms',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'An order containing items from multiple independent merchants cannot be fiscalized as a single invoice. '
                  'The platform must split the checkout into distinct fiscal invoices per registered merchant TIN plus '
                  'a separate commission invoice for the marketplace platform fee.',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMerchantsList() {
    if (_merchants.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.storefront_outlined, size: 64, color: AppColors.textTertiary),
            const SizedBox(height: 16),
            Text('No merchants registered on marketplace yet', style: AppTypography.titleMedium),
            const SizedBox(height: 8),
            Text(
              'Add third-party merchants to enable multi-seller orders and automatic tax splitting.',
              style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: _merchants.length,
      itemBuilder: (ctx, index) {
        final m = _merchants[index];
        final isActive = m.status == 'ACTIVE' && !m.authoritySuspended;

        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      m.tradeName.isNotEmpty ? m.tradeName : m.legalName,
                      style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: isActive
                            ? AppColors.success.withValues(alpha: 0.15)
                            : AppColors.error.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        m.authoritySuspended ? 'AUTHORITY SUSPENDED' : m.status,
                        style: TextStyle(
                          color: isActive ? AppColors.success : AppColors.error,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text('Legal Name: ${m.legalName}', style: AppTypography.bodySmall),
                Text('Taxpayer TIN: ${m.tin}', style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.w600)),
                Text('Registered Address: ${m.address}', style: AppTypography.caption),
                if (m.suspensionReason != null) ...[
                  const SizedBox(height: 6),
                  Text('Suspension Reason: ${m.suspensionReason}', style: AppTypography.caption.copyWith(color: AppColors.error)),
                ],
                const Divider(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Registered: ${_dateFormat.format(m.registeredAt)}', style: AppTypography.caption),
                    OutlinedButton(
                      onPressed: () {
                        // Toggle suspension action
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: isActive ? AppColors.error : AppColors.success,
                      ),
                      child: Text(isActive ? 'Suspend Merchant' : 'Reinstate'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildOrderSplitView() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ElevatedButton.icon(
            onPressed: _showMultiSellerOrderDemo,
            icon: const Icon(Icons.receipt_long),
            label: const Text('Simulate Multi-Seller Order Fiscal Split'),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primary,
              padding: const EdgeInsets.symmetric(vertical: 14),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Statutory Requirement Architecture', style: AppTypography.titleMedium),
                  const Divider(height: 24),
                  _architectureRow('Customer Checkout:', 'Single payment authorization (e.g. 5,500.00 ETB)'),
                  _architectureRow('Seller 1 (Electronics):', 'Separate Invoice • TIN 0019283746 • IRN Generated'),
                  _architectureRow('Seller 2 (Books):', 'Separate Invoice • TIN 0055443322 • IRN Generated'),
                  _architectureRow('Marketplace Operator:', 'Commission Invoice • Platform TIN • VAT Applied'),
                  _architectureRow('Customer Receipt:', 'Aggregated receipt referencing all individual IRNs'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _architectureRow(String label, String desc) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 170,
            child: Text(label, style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.bold)),
          ),
          Expanded(child: Text(desc, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary))),
        ],
      ),
    );
  }
}

class _RegisterMerchantDialog extends ConsumerStatefulWidget {
  final VoidCallback onSuccess;

  const _RegisterMerchantDialog({required this.onSuccess});

  @override
  ConsumerState<_RegisterMerchantDialog> createState() => _RegisterMerchantDialogState();
}

class _RegisterMerchantDialogState extends ConsumerState<_RegisterMerchantDialog> {
  final _formKey = GlobalKey<FormState>();
  final _tinController = TextEditingController();
  final _legalNameController = TextEditingController();
  final _tradeNameController = TextEditingController();
  final _addressController = TextEditingController();
  final _phoneController = TextEditingController();
  bool _isSubmitting = false;

  @override
  void dispose() {
    _tinController.dispose();
    _legalNameController.dispose();
    _tradeNameController.dispose();
    _addressController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(marketplaceServiceProvider);
      await service.registerMerchant({
        'tin': _tinController.text.trim(),
        'legalName': _legalNameController.text.trim(),
        'tradeName': _tradeNameController.text.trim(),
        'address': _addressController.text.trim(),
        'contactPhone': _phoneController.text.trim(),
      });

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Registration failed: $e'), backgroundColor: AppColors.error),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Register Marketplace Merchant'),
      content: SizedBox(
        width: 450,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _tinController,
                decoration: const InputDecoration(labelText: 'Merchant TIN (10 digits) *', border: OutlineInputBorder()),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _legalNameController,
                decoration: const InputDecoration(labelText: 'Legal Business Name *', border: OutlineInputBorder()),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _tradeNameController,
                decoration: const InputDecoration(labelText: 'Trade Name / Storefront', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _addressController,
                decoration: const InputDecoration(labelText: 'Business Address *', border: OutlineInputBorder()),
                validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _phoneController,
                decoration: const InputDecoration(labelText: 'Contact Phone Number', border: OutlineInputBorder()),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
          child: _isSubmitting
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Register'),
        ),
      ],
    );
  }
}

class _MultiSellerFiscalSplitDialog extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Multi-Seller Fiscal Breakdown (Simulated)'),
      content: SizedBox(
        width: 550,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Order Ref: ORD-2026-MARKET-9021', style: AppTypography.titleSmall),
            const SizedBox(height: 4),
            Text('Total Customer Paid: ETB 5,500.00', style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold)),
            const Divider(height: 20),
            _splitItem(
              title: 'Seller A: Addis Electronics PLC',
              tin: '0019283746',
              irn: 'IRN-ET-2026-SELLER-A-9012',
              amount: 'ETB 3,450.00 (Incl. 15% VAT)',
              color: AppColors.primary,
            ),
            const SizedBox(height: 8),
            _splitItem(
              title: 'Seller B: Nile Books & Stationery',
              tin: '0055443322',
              irn: 'IRN-ET-2026-SELLER-B-4410',
              amount: 'ETB 1,800.00 (Exempt/Book Rate)',
              color: AppColors.secondary,
            ),
            const SizedBox(height: 8),
            _splitItem(
              title: 'Marketplace Platform (Commission Fee)',
              tin: '0099887766',
              irn: 'IRN-ET-2026-COMMISSION-009',
              amount: 'ETB 250.00 (Commission + VAT)',
              color: AppColors.navy700,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close'),
        ),
      ],
    );
  }

  Widget _splitItem({
    required String title,
    required String tin,
    required String irn,
    required String amount,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.bold, color: color)),
          const SizedBox(height: 2),
          Text('TIN: $tin • IRN: $irn', style: AppTypography.caption),
          const SizedBox(height: 2),
          Text(amount, style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
