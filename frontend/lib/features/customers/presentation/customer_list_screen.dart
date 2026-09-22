import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/utils/ethiopian_input_validators.dart';
import '../../../domain/customer/models/customer_model.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class CustomerListScreen extends ConsumerStatefulWidget {
  const CustomerListScreen({super.key});

  @override
  ConsumerState<CustomerListScreen> createState() => _CustomerListScreenState();
}

class _CustomerListScreenState extends ConsumerState<CustomerListScreen> {
  final _searchController = TextEditingController();
  List<CustomerModel> _customers = [];
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _fetchCustomers();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _fetchCustomers() async {
    setState(() {
      _isLoading = true;
    });

    final tenant = ref.read(tenantContextProvider);
    final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
    final repo = ref.read(customerRepositoryProvider);

    try {
      final results = await repo.searchCustomers(
        tenantId: tenantId,
        query: _searchController.text.trim(),
      );
      if (mounted) {
        setState(() {
          _customers = results;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  void _openAddOrEditCustomerDialog([CustomerModel? existing]) {
    final formKey = GlobalKey<FormState>();
    final tinCtrl = TextEditingController(text: existing?.tin ?? '');
    final vatCtrl = TextEditingController(text: existing?.vatNumber ?? '');
    final legalNameCtrl = TextEditingController(text: existing?.legalName ?? '');
    final tradeNameCtrl = TextEditingController(text: existing?.tradeName ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final emailCtrl = TextEditingController(text: existing?.email ?? '');
    final regionCtrl = TextEditingController(text: existing?.region ?? 'Addis Ababa');
    final cityCtrl = TextEditingController(text: existing?.city ?? 'Addis Ababa');
    final zoneCtrl = TextEditingController(text: existing?.zone ?? '');
    final woredaCtrl = TextEditingController(text: existing?.woreda ?? '');
    final kebeleCtrl = TextEditingController(text: existing?.kebele ?? '');
    final houseNoCtrl = TextEditingController(text: existing?.houseNumber ?? '');
    final buyerIdNumCtrl = TextEditingController(text: existing?.buyerIdNumber ?? '');

    String buyerIdType = existing?.buyerIdType ?? 'TIN';
    bool isVat = existing?.isVatRegistered ?? true;
    bool isSaving = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(3),
            side: const BorderSide(color: AppColors.rule),
          ),
          title: Text(
            existing == null ? 'REGISTER BUYER / CUSTOMER' : 'EDIT CUSTOMER',
            style: AppTypography.h2(color: AppColors.navy900),
          ),
          content: SizedBox(
            width: 580,
            child: Form(
              key: formKey,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'All customer records are saved locally and synced with tenant master data.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          flex: 3,
                          child: TextFormField(
                            controller: tinCtrl,
                            inputFormatters: EthiopianInputValidators.tinFormatters,
                            style: AppTypography.mono(),
                            decoration: const InputDecoration(
                              labelText: 'Ethiopian TIN (10 digits)',
                              hintText: '0012345678',
                              helperText: 'Numbers only. 10 digits required for B2B.',
                            ),
                            validator: (v) => EthiopianInputValidators.validateTin(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          flex: 2,
                          child: TextFormField(
                            controller: vatCtrl,
                            inputFormatters: EthiopianInputValidators.vatFormatters,
                            style: AppTypography.mono(),
                            decoration: const InputDecoration(
                              labelText: 'VAT Number',
                              hintText: 'e.g. 1234567890',
                              helperText: 'Alphanumeric',
                            ),
                            validator: (v) => EthiopianInputValidators.validateVatNumber(v, isVatRegistered: isVat),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: legalNameCtrl,
                      inputFormatters: EthiopianInputValidators.nameFormatters,
                      decoration: const InputDecoration(
                        labelText: 'Legal Business / Customer Name *',
                        hintText: 'e.g. Awash Import Export PLC',
                        helperText: 'Latin or Amharic (min 2 characters)',
                      ),
                      validator: (v) => EthiopianInputValidators.validateLegalName(v, isRequired: true),
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: tradeNameCtrl,
                      inputFormatters: EthiopianInputValidators.nameFormatters,
                      decoration: const InputDecoration(
                        labelText: 'Trade / Commercial Name',
                        hintText: 'e.g. Awash Wholesalers',
                      ),
                      validator: (v) => EthiopianInputValidators.validateTradeName(v, isRequired: false),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: phoneCtrl,
                            inputFormatters: EthiopianInputValidators.phoneFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Phone Number',
                              hintText: '+251 9... or 09...',
                              helperText: '09/07XXXXXXXX or +2519/7XXXXXXXX',
                            ),
                            validator: (v) => EthiopianInputValidators.validatePhone(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: emailCtrl,
                            inputFormatters: EthiopianInputValidators.emailFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Email Address',
                              hintText: 'billing@example.et',
                            ),
                            validator: (v) => EthiopianInputValidators.validateEmail(v, isRequired: false),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: regionCtrl,
                            inputFormatters: EthiopianInputValidators.cityFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Region / Admin State',
                              hintText: 'e.g. Addis Ababa, Oromia',
                            ),
                            validator: (v) => EthiopianInputValidators.validateRegion(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: cityCtrl,
                            inputFormatters: EthiopianInputValidators.cityFormatters,
                            decoration: const InputDecoration(labelText: 'City / Town'),
                            validator: (v) => EthiopianInputValidators.validateCity(v, isRequired: false),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: zoneCtrl,
                            inputFormatters: EthiopianInputValidators.cityFormatters,
                            decoration: const InputDecoration(labelText: 'Sub-city / Zone'),
                            validator: (v) => EthiopianInputValidators.validateZone(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: woredaCtrl,
                            inputFormatters: EthiopianInputValidators.woredaFormatters,
                            decoration: const InputDecoration(labelText: 'Woreda'),
                            validator: (v) => EthiopianInputValidators.validateWoreda(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: kebeleCtrl,
                            inputFormatters: EthiopianInputValidators.kebeleFormatters,
                            decoration: const InputDecoration(labelText: 'Kebele'),
                            validator: (v) => EthiopianInputValidators.validateKebele(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: houseNoCtrl,
                            inputFormatters: EthiopianInputValidators.houseNumberFormatters,
                            decoration: const InputDecoration(labelText: 'House No.'),
                            validator: (v) => EthiopianInputValidators.validateHouseNumber(v, isRequired: false),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Material(
                      type: MaterialType.transparency,
                      child: CheckboxListTile(
                        value: isVat,
                        onChanged: (v) => setModalState(() => isVat = v ?? true),
                        title: Text('VAT Registered Entity (15% Standard VAT Applicable)', style: AppTypography.uiLabel()),
                        contentPadding: EdgeInsets.zero,
                        controlAffinity: ListTileControlAffinity.leading,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSaving ? null : () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isSaving
                  ? null
                  : () async {
                      if (!formKey.currentState!.validate()) return;
                      setModalState(() => isSaving = true);

                      final tenant = ref.read(tenantContextProvider);
                      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
                      final repo = ref.read(customerRepositoryProvider);

                      final customer = CustomerModel(
                        id: existing?.id ?? '',
                        tenantId: tenantId,
                        tin: EthiopianInputValidators.sanitizeTin(tinCtrl.text),
                        vatNumber: EthiopianInputValidators.sanitizeVatNumber(vatCtrl.text),
                        legalName: EthiopianInputValidators.sanitizeName(legalNameCtrl.text) ?? legalNameCtrl.text.trim(),
                        tradeName: EthiopianInputValidators.sanitizeName(tradeNameCtrl.text),
                        phone: EthiopianInputValidators.sanitizePhone(phoneCtrl.text),
                        email: EthiopianInputValidators.sanitizeEmail(emailCtrl.text),
                        country: 'ET',
                        region: EthiopianInputValidators.sanitizeAddressField(regionCtrl.text),
                        city: EthiopianInputValidators.sanitizeAddressField(cityCtrl.text),
                        zone: EthiopianInputValidators.sanitizeAddressField(zoneCtrl.text),
                        woreda: EthiopianInputValidators.sanitizeAddressField(woredaCtrl.text),
                        kebele: EthiopianInputValidators.sanitizeAddressField(kebeleCtrl.text),
                        houseNumber: EthiopianInputValidators.sanitizeAddressField(houseNoCtrl.text),
                        buyerIdType: buyerIdType,
                        buyerIdNumber: buyerIdNumCtrl.text.trim().isNotEmpty ? buyerIdNumCtrl.text.trim() : null,
                        isVatRegistered: isVat,
                        status: 'ACTIVE',
                        createdAt: existing?.createdAt ?? DateTime.now(),
                      );

                      final scaffoldMessenger = ScaffoldMessenger.of(context);
                      final navigator = Navigator.of(ctx);

                      try {
                        if (existing == null) {
                          await repo.createCustomer(tenantId: tenantId, customer: customer);
                        } else {
                          await repo.updateCustomer(tenantId: tenantId, customer: customer);
                        }

                        if (mounted) {
                          navigator.pop();
                          _fetchCustomers();
                          scaffoldMessenger.showSnackBar(
                            SnackBar(
                              content: Text(
                                existing == null
                                    ? 'Customer registered in tenant directory and synced.'
                                    : 'Customer details updated successfully.',
                              ),
                              backgroundColor: AppColors.green700,
                            ),
                          );
                        }
                      } catch (e) {
                        setModalState(() => isSaving = false);
                        if (mounted) {
                          scaffoldMessenger.showSnackBar(
                            SnackBar(
                              content: Text('Failed to save customer: $e'),
                              backgroundColor: AppColors.red600,
                            ),
                          );
                        }
                      }
                    },
              child: isSaving
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Text(existing == null ? 'Register Customer' : 'Save Changes'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tenant = ref.watch(tenantContextProvider);
    final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
    final repo = ref.watch(customerRepositoryProvider);

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            LayoutBuilder(
              builder: (context, constraints) {
                final isCompact = constraints.maxWidth < 650;
                if (isCompact) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('CUSTOMER MASTER DIRECTORY', style: AppTypography.h1()),
                          const SizedBox(height: 4),
                          Text(
                            'Tenant-authorized B2B & B2C customer master data for electronic invoicing',
                            style: AppTypography.bodySmall(),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: ElevatedButton.icon(
                          onPressed: () => _openAddOrEditCustomerDialog(),
                          icon: const Icon(Icons.person_add_alt_1, size: 16),
                          label: const Text('Register Customer'),
                        ),
                      ),
                    ],
                  );
                }
                return Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('CUSTOMER MASTER DIRECTORY', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Tenant-authorized B2B & B2C customer master data for electronic invoicing',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                    ElevatedButton.icon(
                      onPressed: () => _openAddOrEditCustomerDialog(),
                      icon: const Icon(Icons.person_add_alt_1, size: 16),
                      label: const Text('Register Customer'),
                    ),
                  ],
                );
              },
            ),
            const SizedBox(height: 20),

            // Search Bar & Stats
            Row(
              children: [
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: AppColors.rule),
                    ),
                    child: TextField(
                      controller: _searchController,
                      decoration: const InputDecoration(
                        hintText: 'Search by legal name, trade name, 10-digit TIN, or phone number...',
                        prefixIcon: Icon(Icons.search, size: 18, color: AppColors.inkMuted),
                        border: InputBorder.none,
                        contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      ),
                      onChanged: (_) => _fetchCustomers(),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                IconButton(
                  tooltip: 'Refresh Customer List',
                  icon: const Icon(Icons.refresh, color: AppColors.navy700),
                  onPressed: _fetchCustomers,
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Customer Stream / Table
            Expanded(
              child: StreamBuilder<List<CustomerModel>>(
                stream: repo.watchCustomers(tenantId: tenantId),
                builder: (context, snapshot) {
                  final liveCustomers = snapshot.data ?? _customers;
                  final query = _searchController.text.trim().toLowerCase();
                  final filtered = liveCustomers.where((c) {
                    if (query.isEmpty) return true;
                    return c.legalName.toLowerCase().contains(query) ||
                        (c.tin != null && c.tin!.contains(query)) ||
                        (c.tradeName?.toLowerCase().contains(query) ?? false) ||
                        (c.phone?.contains(query) ?? false);
                  }).toList();

                  if (_isLoading && liveCustomers.isEmpty) {
                    return const Center(
                      child: CircularProgressIndicator(),
                    );
                  }

                  if (filtered.isEmpty) {
                    return Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.people_outline, size: 48, color: AppColors.inkMuted.withValues(alpha: 0.5)),
                          const SizedBox(height: 12),
                          Text('No customers found', style: AppTypography.h2()),
                          const SizedBox(height: 4),
                          Text(
                            query.isEmpty
                                ? 'Add your first customer to populate the tenant directory.'
                                : 'No customers match "$query".',
                            style: AppTypography.bodySmall(),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton.icon(
                            onPressed: () => _openAddOrEditCustomerDialog(),
                            icon: const Icon(Icons.add, size: 16),
                            label: const Text('Add Customer'),
                          ),
                        ],
                      ),
                    );
                  }

                  return Material(
                    color: AppColors.paperRaised,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(3),
                      side: const BorderSide(color: AppColors.rule),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: ListView.separated(
                      itemCount: filtered.length,
                      separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                      itemBuilder: (context, index) {
                        final c = filtered[index];
                        return Material(
                          type: MaterialType.transparency,
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                            title: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    c.legalName,
                                    style: AppTypography.uiLabelBold(),
                                  ),
                                ),
                                if (c.tin != null && c.tin!.isNotEmpty) ...[
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppColors.navy700.withValues(alpha: 0.08),
                                      border: Border.all(color: AppColors.navy700.withValues(alpha: 0.3)),
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                    child: Text(
                                      'TIN: ${c.tin}',
                                      style: AppTypography.monoSmall(
                                        color: AppColors.navy700,
                                        weight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                ],
                                if (c.isVatRegistered)
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppColors.green700.withValues(alpha: 0.08),
                                      border: Border.all(color: AppColors.green700.withValues(alpha: 0.3)),
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                    child: Text(
                                      'VAT',
                                      style: AppTypography.monoSmall(
                                        color: AppColors.green700,
                                        weight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            subtitle: Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Row(
                                children: [
                                  if (c.tradeName != null && c.tradeName!.isNotEmpty) ...[
                                    Text(
                                      '(${c.tradeName})  •  ',
                                      style: AppTypography.bodySmall().copyWith(fontStyle: FontStyle.italic),
                                    ),
                                  ],
                                  if (c.phone != null) ...[
                                    const Icon(Icons.phone_outlined, size: 13, color: AppColors.inkMuted),
                                    const SizedBox(width: 4),
                                    Text(c.phone!, style: AppTypography.bodySmall()),
                                    const SizedBox(width: 12),
                                  ],
                                  if (c.email != null) ...[
                                    const Icon(Icons.email_outlined, size: 13, color: AppColors.inkMuted),
                                    const SizedBox(width: 4),
                                    Text(c.email!, style: AppTypography.bodySmall()),
                                    const SizedBox(width: 12),
                                  ],
                                  const Icon(Icons.location_on_outlined, size: 13, color: AppColors.inkMuted),
                                  const SizedBox(width: 4),
                                  Expanded(
                                    child: Text(
                                      c.formattedAddress,
                                      style: AppTypography.bodySmall(),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                IconButton(
                                  icon: const Icon(Icons.edit_outlined, size: 18, color: AppColors.inkMuted),
                                  tooltip: 'Edit Customer',
                                  onPressed: () => _openAddOrEditCustomerDialog(c),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
