import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/utils/ethiopian_input_validators.dart';
import '../../../core/utils/tin_validator.dart';
import '../../../domain/catalog/models/catalog_models.dart';
import '../../../domain/customer/models/customer_model.dart';
import '../../../domain/invoice/invoice_type_registry.dart';
import '../../../domain/invoice/models/invoice_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';

class InvoiceCreationWizard extends ConsumerStatefulWidget {
  const InvoiceCreationWizard({super.key});

  @override
  ConsumerState<InvoiceCreationWizard> createState() => _InvoiceCreationWizardState();
}

class _InvoiceCreationWizardState extends ConsumerState<InvoiceCreationWizard> {
  final _formKey = GlobalKey<FormState>();

  InvoiceType _invoiceType = InvoiceType.b2c;
  String _paymentMode = 'CASH';
  String _currency = 'ETB';
  bool _saveCustomerToMaster = false;
  bool _isVatRegistered = true;
  bool _isEmergencyManualFallback = false;
  final _manualPaperReceiptNumberController = TextEditingController();

  // Buyer Fields
  final _customerSearchController = TextEditingController();
  final _buyerTinController = TextEditingController();
  final _buyerVatNumberController = TextEditingController();
  final _buyerNameController = TextEditingController();
  final _buyerTradeNameController = TextEditingController();
  final _buyerPhoneController = TextEditingController();
  final _buyerEmailController = TextEditingController();
  final _buyerCountryController = TextEditingController(text: 'ET');
  final _buyerRegionController = TextEditingController(text: 'Addis Ababa');
  final _buyerCityController = TextEditingController(text: 'Addis Ababa');
  final _buyerZoneController = TextEditingController();
  final _buyerWoredaController = TextEditingController();
  final _buyerKebeleController = TextEditingController();
  final _buyerHouseNoController = TextEditingController();

  CustomerModel? _selectedCustomer;
  List<CustomerModel> _customerSuggestions = [];
  bool _isSearchingCustomer = false;
  Timer? _customerSearchDebounce;

  final List<InvoiceLineDraft> _lines = [];

  bool _isSubmitting = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _customerSearchController.addListener(_onCustomerSearchChanged);
  }

  @override
  void dispose() {
    _customerSearchDebounce?.cancel();
    _customerSearchController.dispose();
    _buyerTinController.dispose();
    _buyerVatNumberController.dispose();
    _buyerNameController.dispose();
    _buyerTradeNameController.dispose();
    _buyerPhoneController.dispose();
    _buyerEmailController.dispose();
    _buyerCountryController.dispose();
    _buyerRegionController.dispose();
    _buyerCityController.dispose();
    _buyerZoneController.dispose();
    _buyerWoredaController.dispose();
    _buyerKebeleController.dispose();
    _buyerHouseNoController.dispose();
    _manualPaperReceiptNumberController.dispose();
    super.dispose();
  }

  void _onCustomerSearchChanged() {
    final query = _customerSearchController.text.trim();
    if (query.isEmpty) {
      if (mounted) setState(() => _customerSuggestions = []);
      return;
    }

    _customerSearchDebounce?.cancel();
    _customerSearchDebounce = Timer(const Duration(milliseconds: 250), () async {
      final tenant = ref.read(tenantContextProvider);
      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final repo = ref.read(customerRepositoryProvider);

      setState(() => _isSearchingCustomer = true);
      try {
        final results = await repo.searchCustomers(tenantId: tenantId, query: query, size: 8);
        if (mounted) {
          setState(() {
            _customerSuggestions = results;
            _isSearchingCustomer = false;
          });
        }
      } catch (_) {
        if (mounted) setState(() => _isSearchingCustomer = false);
      }
    });
  }

  void _selectCustomer(CustomerModel customer) {
    setState(() {
      _selectedCustomer = customer;
      _customerSuggestions = [];
      _customerSearchController.text = customer.legalName;

      _buyerTinController.text = customer.tin ?? '';
      _buyerVatNumberController.text = customer.vatNumber ?? '';
      _buyerNameController.text = customer.legalName;
      _buyerTradeNameController.text = customer.tradeName ?? '';
      _buyerPhoneController.text = customer.phone ?? '';
      _buyerEmailController.text = customer.email ?? '';
      _buyerCountryController.text = customer.country;
      _buyerRegionController.text = customer.region ?? 'Addis Ababa';
      _buyerCityController.text = customer.city ?? 'Addis Ababa';
      _buyerZoneController.text = customer.zone ?? '';
      _buyerWoredaController.text = customer.woreda ?? '';
      _buyerKebeleController.text = customer.kebele ?? '';
      _buyerHouseNoController.text = customer.houseNumber ?? '';
      _isVatRegistered = customer.isVatRegistered;
      _saveCustomerToMaster = false; // Already in directory
    });
  }

  void _clearSelectedCustomer() {
    setState(() {
      _selectedCustomer = null;
      _customerSearchController.clear();
      _buyerTinController.clear();
      _buyerVatNumberController.clear();
      _buyerNameController.clear();
      _buyerTradeNameController.clear();
      _buyerPhoneController.clear();
      _buyerEmailController.clear();
      _buyerCountryController.text = 'ET';
      _buyerRegionController.text = 'Addis Ababa';
      _buyerCityController.text = 'Addis Ababa';
      _buyerZoneController.clear();
      _buyerWoredaController.clear();
      _buyerKebeleController.clear();
      _buyerHouseNoController.clear();
      _saveCustomerToMaster = true;
    });
  }

  void _removeLine(int index) {
    setState(() {
      _lines.removeAt(index);
    });
  }

  /// Opens the Multi-Select Catalog Picker Modal
  void _openCatalogPicker() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _CatalogSelectionDialog(
        initialLines: _lines,
        onItemsSelected: (selectedItems) {
          setState(() {
            for (final newItem in selectedItems) {
              final existingIndex = _lines.indexWhere((l) => l.itemCode == newItem.itemCode);
              if (existingIndex >= 0) {
                _lines[existingIndex].quantity += newItem.quantity;
              } else {
                _lines.add(newItem);
              }
            }
          });
        },
      ),
    );
  }

  Future<void> _submitInvoice() async {
    if (!_formKey.currentState!.validate()) return;
    if (_lines.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('At least one catalog-bound item must be added before submitting an invoice.'),
          backgroundColor: AppColors.red600,
        ),
      );
      return;
    }

    // Strict validation: Verify every line has a catalog reference
    for (final line in _lines) {
      if (line.catalogItemId == null || line.catalogItemId!.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Line item "${line.itemCode}" is not linked to an authorized catalog entity.'),
            backgroundColor: AppColors.red600,
          ),
        );
        return;
      }
    }

    // Schema-driven validation
    final typeDef = InvoiceTypeRegistry.getDefinition(_invoiceType);
    if (typeDef.isBuyerTinRequired) {
      final tinError = TinValidator.validate(_buyerTinController.text, isRequired: true);
      if (tinError != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(tinError), backgroundColor: AppColors.red600),
        );
        return;
      }
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final tenantState = ref.read(tenantContextProvider);
      final tenantId = tenantState.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final branchId = tenantState.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';

      BuyerModel? buyer;
      final rawTin = _buyerTinController.text.trim();
      final rawName = _buyerNameController.text.trim();

      if (rawTin.isNotEmpty || rawName.isNotEmpty || typeDef.isBuyerTinRequired) {
        buyer = BuyerModel(
          legalName: EthiopianInputValidators.sanitizeName(rawName) ??
              (_invoiceType == InvoiceType.b2c ? 'Retail Customer' : 'Commercial Buyer'),
          tin: EthiopianInputValidators.sanitizeTin(rawTin) ?? (typeDef.isBuyerTinRequired ? '' : '9999999999'),
          vatNumber: EthiopianInputValidators.sanitizeVatNumber(_buyerVatNumberController.text),
          phone: EthiopianInputValidators.sanitizePhone(_buyerPhoneController.text),
          email: EthiopianInputValidators.sanitizeEmail(_buyerEmailController.text),
          country: _buyerCountryController.text.trim().isNotEmpty ? _buyerCountryController.text.trim() : 'ET',
          region: EthiopianInputValidators.sanitizeAddressField(_buyerRegionController.text),
          city: EthiopianInputValidators.sanitizeAddressField(_buyerCityController.text),
          zone: EthiopianInputValidators.sanitizeAddressField(_buyerZoneController.text),
          woreda: EthiopianInputValidators.sanitizeAddressField(_buyerWoredaController.text),
          kebele: EthiopianInputValidators.sanitizeAddressField(_buyerKebeleController.text),
          houseNumber: EthiopianInputValidators.sanitizeAddressField(_buyerHouseNoController.text),
        );
      }

      if (_isEmergencyManualFallback && _manualPaperReceiptNumberController.text.trim().isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Pre-printed paper receipt / book number is mandatory for manual fallback entry.'),
            backgroundColor: AppColors.red600,
          ),
        );
        return;
      }

      final repo = ref.read(invoiceRepositoryProvider);
      final result = await repo.submitOrQueueInvoice(
        tenantId: tenantId,
        branchId: branchId,
        transactionType: _invoiceType.code,
        paymentMode: _paymentMode,
        buyer: buyer,
        items: _lines,
        saveCustomerToMaster: _saveCustomerToMaster,
        customDocumentNumber: _isEmergencyManualFallback
            ? _manualPaperReceiptNumberController.text.trim()
            : null,
      );

      if (mounted) {
        context.go('/invoices/${result.id}');
      }
    } catch (e) {
      setState(() {
        _errorMessage = e.toString();
        _isSubmitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final totals = ClientTaxEstimator.estimateTotals(_lines);
    final loc = AppLocalizations.of(context);
    final currentTypeDef = InvoiceTypeRegistry.getDefinition(_invoiceType);

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Form(
          key: _formKey,
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
                        Text('ISSUE ELECTRONIC INVOICE', style: AppTypography.h1()),
                        const SizedBox(height: 4),
                        Text(
                          'Direct registration with Ministry of Revenues EIRS (Directive No. 1142/2026)',
                          style: AppTypography.bodySmall(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  OutlinedButton.icon(
                    onPressed: () => context.go('/invoices'),
                    icon: const Icon(Icons.close, size: 16),
                    label: const Text('Cancel'),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Tax Estimate Disclaimer Banner
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.amber600.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.amber600.withValues(alpha: 0.4), width: 1),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.info_outline, size: 22, color: AppColors.amber600),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        loc.get('tax_estimate_disclaimer'),
                        style: AppTypography.bodySmall(color: AppColors.ink).copyWith(
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              if (_errorMessage != null) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.red600.withValues(alpha: 0.08),
                    border: Border.all(color: AppColors.red600.withValues(alpha: 0.3)),
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, size: 18, color: AppColors.red600),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _errorMessage!,
                          style: AppTypography.bodySmall(color: AppColors.red600),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
              ],

              // 1. INVOICE TYPE & TAX CLASSIFICATION CARD
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text('INVOICE TYPE & TAX REGIME', style: AppTypography.h2()),
                        const SizedBox(width: 12),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: AppColors.navy700.withValues(alpha: 0.08),
                            border: Border.all(color: AppColors.navy700.withValues(alpha: 0.3)),
                            borderRadius: BorderRadius.circular(3),
                          ),
                          child: Text(
                            currentTypeDef.badgeText,
                            style: AppTypography.monoSmall(
                              color: AppColors.navy700,
                              weight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      currentTypeDef.description,
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                    ),
                    const SizedBox(height: 16),
                    LayoutBuilder(
                      builder: (context, constraints) {
                        final isCompact = constraints.maxWidth < 650;
                        if (isCompact) {
                          return Column(
                            children: [
                              DropdownButtonFormField<InvoiceType>(
                                initialValue: _invoiceType,
                                decoration: const InputDecoration(labelText: 'Invoice Type / Category *'),
                                items: InvoiceType.values.map((t) {
                                  return DropdownMenuItem(
                                    value: t,
                                    child: Text(t.label),
                                  );
                                }).toList(),
                                onChanged: (v) {
                                  if (v != null) setState(() => _invoiceType = v);
                                },
                              ),
                              const SizedBox(height: 12),
                              DropdownButtonFormField<String>(
                                initialValue: _paymentMode,
                                decoration: const InputDecoration(labelText: 'Payment Mode *'),
                                items: const [
                                  DropdownMenuItem(value: 'CASH', child: Text('CASH (ጥሬ ገንዘብ)')),
                                  DropdownMenuItem(value: 'TELEBIRR', child: Text('telebirr (ቴሌብር)')),
                                  DropdownMenuItem(value: 'CBE_BIRR', child: Text('CBE Birr (ሲቢኢ ብር)')),
                                  DropdownMenuItem(value: 'BANK_TRANSFER', child: Text('Bank Transfer (ባንክ)')),
                                  DropdownMenuItem(value: 'CREDIT', child: Text('Credit / On Account (ብድር)')),
                                ],
                                onChanged: (v) => setState(() => _paymentMode = v ?? 'CASH'),
                              ),
                              if (_invoiceType == InvoiceType.export) ...[
                                const SizedBox(height: 12),
                                DropdownButtonFormField<String>(
                                  initialValue: _currency,
                                  decoration: const InputDecoration(labelText: 'Invoice Currency *'),
                                  items: currentTypeDef.supportedCurrencies.map((c) {
                                    return DropdownMenuItem(value: c, child: Text(c));
                                  }).toList(),
                                  onChanged: (v) => setState(() => _currency = v ?? 'USD'),
                                ),
                              ],
                            ],
                          );
                        }
                        return Row(
                          children: [
                            Expanded(
                              flex: 2,
                              child: DropdownButtonFormField<InvoiceType>(
                                initialValue: _invoiceType,
                                decoration: const InputDecoration(labelText: 'Invoice Type / Category *'),
                                items: InvoiceType.values.map((t) {
                                  return DropdownMenuItem(
                                    value: t,
                                    child: Text(t.label),
                                  );
                                }).toList(),
                                onChanged: (v) {
                                  if (v != null) setState(() => _invoiceType = v);
                                },
                              ),
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              flex: 2,
                              child: DropdownButtonFormField<String>(
                                initialValue: _paymentMode,
                                decoration: const InputDecoration(labelText: 'Payment Mode *'),
                                items: const [
                                  DropdownMenuItem(value: 'CASH', child: Text('CASH (ጥሬ ገንዘብ)')),
                                  DropdownMenuItem(value: 'TELEBIRR', child: Text('telebirr (ቴሌብር)')),
                                  DropdownMenuItem(value: 'CBE_BIRR', child: Text('CBE Birr (ሲቢኢ ብር)')),
                                  DropdownMenuItem(value: 'BANK_TRANSFER', child: Text('Bank Transfer (ባንክ)')),
                                  DropdownMenuItem(value: 'CREDIT', child: Text('Credit / On Account (ብድር)')),
                                ],
                                onChanged: (v) => setState(() => _paymentMode = v ?? 'CASH'),
                              ),
                            ),
                            if (_invoiceType == InvoiceType.export) ...[
                              const SizedBox(width: 16),
                              Expanded(
                                flex: 1,
                                child: DropdownButtonFormField<String>(
                                  initialValue: _currency,
                                  decoration: const InputDecoration(labelText: 'Currency *'),
                                  items: currentTypeDef.supportedCurrencies.map((c) {
                                    return DropdownMenuItem(value: c, child: Text(c));
                                  }).toList(),
                                  onChanged: (v) => setState(() => _currency = v ?? 'USD'),
                                ),
                              ),
                            ],
                          ],
                        );
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Emergency Manual Paper Receipt Fallback Card (Directive No. 1142/2026 Art. 22)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: _isEmergencyManualFallback
                      ? AppColors.amber600.withValues(alpha: 0.08)
                      : AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(
                    color: _isEmergencyManualFallback ? AppColors.amber600 : AppColors.rule,
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Checkbox(
                          value: _isEmergencyManualFallback,
                          activeColor: AppColors.amber600,
                          onChanged: (val) {
                            setState(() => _isEmergencyManualFallback = val ?? false);
                          },
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Emergency Manual Paper Receipt Fallback Entry (Directive No. 1142/2026 Art. 22)',
                                style: AppTypography.uiLabelBold(
                                  color: _isEmergencyManualFallback ? AppColors.navy900 : AppColors.ink,
                                ),
                              ),
                              Text(
                                'Post-outage retrospective registration for transactions recorded on pre-printed paper receipts during system downtime.',
                                style: AppTypography.bodySmall(color: AppColors.inkMuted),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    if (_isEmergencyManualFallback) ...[
                      const SizedBox(height: 14),
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppColors.amber600.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(2),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.info_outline, size: 16, color: AppColors.amber600),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Mandatory 72-hour retrospective synchronization window. Document will reference original paper receipt number.',
                                style: AppTypography.bodySmall(color: AppColors.navy900).copyWith(fontSize: 11),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _manualPaperReceiptNumberController,
                        decoration: const InputDecoration(
                          labelText: 'Pre-Printed Manual Paper Receipt / Pad Number *',
                          hintText: 'e.g. MOR-PAD-882910',
                          border: OutlineInputBorder(),
                          prefixIcon: Icon(Icons.receipt, size: 18),
                        ),
                        validator: (val) {
                          if (_isEmergencyManualFallback && (val == null || val.trim().isEmpty)) {
                            return 'Manual paper receipt number is required';
                          }
                          return null;
                        },
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // 2. BUYER & CUSTOMER MASTER AUTOCOMPLETE CARD
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('BUYER / CUSTOMER MASTER DATA', style: AppTypography.h2()),
                            const SizedBox(height: 4),
                            Text(
                              currentTypeDef.isBuyerTinRequired
                                  ? '10-digit Buyer TIN and legal business details are strictly MANDATORY for B2B tax compliance.'
                                  : 'Select existing registered buyer or enter customer details.',
                              style: AppTypography.bodySmall(
                                color: currentTypeDef.isBuyerTinRequired ? AppColors.navy700 : AppColors.inkMuted,
                              ),
                            ),
                          ],
                        ),
                        if (_selectedCustomer != null)
                          TextButton.icon(
                            onPressed: _clearSelectedCustomer,
                            icon: const Icon(Icons.clear, size: 16),
                            label: const Text('Clear / New Customer'),
                          ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Customer Autocomplete Search Field
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        TextField(
                          controller: _customerSearchController,
                          decoration: InputDecoration(
                            labelText: 'Search Customer Directory (Name, TIN, Phone)',
                            hintText: 'Type to search registered customers or enter new below...',
                            prefixIcon: const Icon(Icons.person_search_outlined, size: 20),
                            suffixIcon: _isSearchingCustomer
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: Center(
                                      child: CircularProgressIndicator(strokeWidth: 2),
                                    ),
                                  )
                                : null,
                          ),
                        ),
                        if (_customerSuggestions.isNotEmpty)
                          Material(
                            color: AppColors.paperRaised,
                            elevation: 4,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(3),
                              side: BorderSide(color: AppColors.navy700.withValues(alpha: 0.3)),
                            ),
                            clipBehavior: Clip.antiAlias,
                            child: ListView.separated(
                              shrinkWrap: true,
                              physics: const NeverScrollableScrollPhysics(),
                              itemCount: _customerSuggestions.length,
                              separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                              itemBuilder: (context, idx) {
                                final cust = _customerSuggestions[idx];
                                return Material(
                                  type: MaterialType.transparency,
                                  child: ListTile(
                                    dense: true,
                                    title: Row(
                                      children: [
                                        Expanded(
                                          child: Text(cust.legalName, style: AppTypography.uiLabelBold()),
                                        ),
                                        if (cust.tin != null && cust.tin!.isNotEmpty)
                                          Container(
                                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                                            decoration: BoxDecoration(
                                              color: AppColors.navy700.withValues(alpha: 0.08),
                                              borderRadius: BorderRadius.circular(2),
                                            ),
                                            child: Text(
                                              'TIN: ${cust.tin}',
                                              style: AppTypography.monoSmall(color: AppColors.navy700, weight: FontWeight.bold),
                                            ),
                                          ),
                                      ],
                                    ),
                                    subtitle: Text(
                                      '${cust.phone ?? 'No phone'} • ${cust.formattedAddress}',
                                      style: AppTypography.bodySmall(),
                                    ),
                                    onTap: () => _selectCustomer(cust),
                                  ),
                                );
                              },
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Active Customer Form Fields
                    Row(
                      children: [
                        Expanded(
                          flex: 3,
                          child: TextFormField(
                            controller: _buyerTinController,
                            inputFormatters: EthiopianInputValidators.tinFormatters,
                            style: AppTypography.mono(),
                            decoration: InputDecoration(
                              labelText: currentTypeDef.isBuyerTinRequired
                                  ? '10-Digit Buyer TIN (MANDATORY) *'
                                  : 'Buyer TIN (Optional for B2C)',
                              hintText: '0012345678',
                              helperText: '10 numeric digits only. Leading zeros preserved.',
                            ),
                            validator: (v) => EthiopianInputValidators.validateTin(
                              v,
                              isRequired: currentTypeDef.isBuyerTinRequired,
                              fieldName: 'Buyer TIN',
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          flex: 2,
                          child: TextFormField(
                            controller: _buyerVatNumberController,
                            inputFormatters: EthiopianInputValidators.vatFormatters,
                            style: AppTypography.mono(),
                            decoration: const InputDecoration(
                              labelText: 'VAT Number',
                              hintText: 'e.g. 1234567890',
                              helperText: 'Alphanumeric',
                            ),
                            validator: (v) => EthiopianInputValidators.validateVatNumber(
                              v,
                              isVatRegistered: _isVatRegistered,
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          flex: 4,
                          child: TextFormField(
                            controller: _buyerNameController,
                            inputFormatters: EthiopianInputValidators.nameFormatters,
                            decoration: InputDecoration(
                              labelText: currentTypeDef.isBuyerNameRequired
                                  ? 'Buyer Legal Name *'
                                  : 'Buyer Name (Walk-in Retail Default)',
                              hintText: _invoiceType == InvoiceType.b2c ? 'Retail Customer' : 'Company PLC / Enterprise',
                              helperText: 'Latin or Amharic (min 2 characters)',
                            ),
                            validator: (v) => EthiopianInputValidators.validateLegalName(
                              v,
                              isRequired: currentTypeDef.isBuyerNameRequired,
                              fieldName: 'Buyer Legal Name',
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: _buyerTradeNameController,
                            inputFormatters: EthiopianInputValidators.nameFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Trade / Brand Name',
                              hintText: 'e.g. Commercial Brand Name',
                            ),
                            validator: (v) => EthiopianInputValidators.validateTradeName(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            controller: _buyerPhoneController,
                            inputFormatters: EthiopianInputValidators.phoneFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Phone Number',
                              hintText: '+251 9... or 09...',
                              helperText: '09/07XXXXXXXX or +2519/7XXXXXXXX',
                            ),
                            validator: (v) => EthiopianInputValidators.validatePhone(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            controller: _buyerEmailController,
                            inputFormatters: EthiopianInputValidators.emailFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Email Address',
                              hintText: 'finance@company.et',
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
                            controller: _buyerRegionController,
                            inputFormatters: EthiopianInputValidators.cityFormatters,
                            decoration: const InputDecoration(
                              labelText: 'Region / Admin State',
                              hintText: 'Addis Ababa / Oromia / Amhara...',
                            ),
                            validator: (v) => EthiopianInputValidators.validateRegion(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            controller: _buyerCityController,
                            inputFormatters: EthiopianInputValidators.cityFormatters,
                            decoration: const InputDecoration(
                              labelText: 'City / Town',
                              hintText: 'Addis Ababa / Hawassa / Dire Dawa...',
                            ),
                            validator: (v) => EthiopianInputValidators.validateCity(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            controller: _buyerZoneController,
                            inputFormatters: EthiopianInputValidators.cityFormatters,
                            decoration: const InputDecoration(labelText: 'Sub-city / Zone', hintText: 'Bole / Kirkos...'),
                            validator: (v) => EthiopianInputValidators.validateZone(v, isRequired: false),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: _buyerWoredaController,
                            inputFormatters: EthiopianInputValidators.woredaFormatters,
                            decoration: const InputDecoration(labelText: 'Woreda', hintText: 'Woreda 03...'),
                            validator: (v) => EthiopianInputValidators.validateWoreda(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            controller: _buyerKebeleController,
                            inputFormatters: EthiopianInputValidators.kebeleFormatters,
                            decoration: const InputDecoration(labelText: 'Kebele', hintText: 'Kebele 08...'),
                            validator: (v) => EthiopianInputValidators.validateKebele(v, isRequired: false),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            controller: _buyerHouseNoController,
                            inputFormatters: EthiopianInputValidators.houseNumberFormatters,
                            decoration: const InputDecoration(labelText: 'House No.', hintText: 'House 412...'),
                            validator: (v) => EthiopianInputValidators.validateHouseNumber(v, isRequired: false),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    Row(
                      children: [
                        Expanded(
                          child: Material(
                            type: MaterialType.transparency,
                            child: CheckboxListTile(
                              value: _saveCustomerToMaster,
                              onChanged: (v) => setState(() => _saveCustomerToMaster = v ?? false),
                              title: Text(
                                'Save / Update this customer in Tenant Master Directory',
                                style: AppTypography.uiLabel(),
                              ),
                              contentPadding: EdgeInsets.zero,
                              controlAffinity: ListTileControlAffinity.leading,
                            ),
                          ),
                        ),
                        Expanded(
                          child: Material(
                            type: MaterialType.transparency,
                            child: CheckboxListTile(
                              value: _isVatRegistered,
                              onChanged: (v) => setState(() => _isVatRegistered = v ?? true),
                              title: Text(
                                'Buyer is VAT Registered Entity',
                                style: AppTypography.uiLabel(),
                              ),
                              contentPadding: EdgeInsets.zero,
                              controlAffinity: ListTileControlAffinity.leading,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // 3. CATALOG-BOUND LINE ITEMS CARD
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule, width: 1),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('AUTHORIZED CATALOG LINE ITEMS', style: AppTypography.h2()),
                            const SizedBox(height: 4),
                            Text(
                              'Multi-select products or services from the registered catalog.',
                              style: AppTypography.bodySmall(color: AppColors.inkMuted),
                            ),
                          ],
                        ),
                        ElevatedButton.icon(
                          onPressed: _openCatalogPicker,
                          icon: const Icon(Icons.add_shopping_cart, size: 16),
                          label: const Text('Add Items from Catalog'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    if (_lines.isEmpty)
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
                        decoration: BoxDecoration(
                          color: AppColors.paper,
                          border: Border.all(color: AppColors.rule, style: BorderStyle.solid),
                          borderRadius: BorderRadius.circular(3),
                        ),
                        child: Column(
                          children: [
                            const Icon(Icons.inventory_2_outlined, size: 40, color: AppColors.inkMuted),
                            const SizedBox(height: 12),
                            Text('No Catalog Items Added Yet', style: AppTypography.h2()),
                            const SizedBox(height: 4),
                            Text(
                              'Select one or multiple authorized products/services to add to this invoice.',
                              style: AppTypography.bodySmall(color: AppColors.inkMuted),
                            ),
                            const SizedBox(height: 16),
                            OutlinedButton.icon(
                              onPressed: _openCatalogPicker,
                              icon: const Icon(Icons.search, size: 16),
                              label: const Text('Browse Catalog or Scan Barcode'),
                            ),
                          ],
                        ),
                      )
                    else
                      LayoutBuilder(
                        builder: (context, constraints) {
                          final itemTableWidth = constraints.maxWidth < 880 ? 880.0 : constraints.maxWidth;
                          return SingleChildScrollView(
                            scrollDirection: Axis.horizontal,
                            child: SizedBox(
                              width: itemTableWidth,
                              child: ListView.separated(
                                shrinkWrap: true,
                                physics: const NeverScrollableScrollPhysics(),
                                itemCount: _lines.length,
                                separatorBuilder: (context, index) => const Divider(height: 24, color: AppColors.rule),
                                itemBuilder: (context, index) {
                                  final item = _lines[index];
                                  final lineSubtotal = (item.quantity * item.unitPrice) - item.discount;

                                  return Row(
                                    crossAxisAlignment: CrossAxisAlignment.center,
                                    children: [
                                      Text(
                                        '${index + 1}.',
                                        style: AppTypography.mono(color: AppColors.inkMuted),
                                      ),
                                      const SizedBox(width: 12),
                                      // Item Type Badge
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: item.itemType == 'SERVICE'
                                              ? AppColors.amber600.withValues(alpha: 0.1)
                                              : AppColors.navy700.withValues(alpha: 0.08),
                                          borderRadius: BorderRadius.circular(2),
                                        ),
                                        child: Text(
                                          item.itemType,
                                          style: AppTypography.monoSmall(
                                            color: item.itemType == 'SERVICE' ? AppColors.amber600 : AppColors.navy700,
                                            weight: FontWeight.bold,
                                          ),
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      // Description & Code
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              item.productDescription,
                                              style: AppTypography.uiLabelBold(),
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                            Text(
                                              'Code: ${item.itemCode} | Unit: ${item.unit} | Tax: ${item.taxCode}',
                                              style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                            ),
                                          ],
                                        ),
                                      ),
                                      const SizedBox(width: 16),
                                      // Quantity Stepper
                                      Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          IconButton(
                                            icon: const Icon(Icons.remove_circle_outline, size: 20),
                                            onPressed: item.quantity > 1
                                                ? () => setState(() => item.quantity -= 1.0)
                                                : () => _removeLine(index),
                                          ),
                                          SizedBox(
                                            width: 60,
                                            child: TextFormField(
                                              key: ValueKey('qty_${item.itemCode}_${item.quantity}'),
                                              initialValue: item.quantity.toStringAsFixed(0),
                                              inputFormatters: EthiopianInputValidators.numericQuantityFormatters,
                                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                              textAlign: TextAlign.center,
                                              style: AppTypography.mono(),
                                              decoration: const InputDecoration(isDense: true, contentPadding: EdgeInsets.all(6)),
                                              onChanged: (v) {
                                                final parsed = double.tryParse(v);
                                                if (parsed != null && parsed > 0) {
                                                  setState(() => item.quantity = parsed);
                                                }
                                              },
                                            ),
                                          ),
                                          IconButton(
                                            icon: const Icon(Icons.add_circle_outline, size: 20),
                                            onPressed: () => setState(() => item.quantity += 1.0),
                                          ),
                                        ],
                                      ),
                                      const SizedBox(width: 12),
                                      // Unit Price
                                      SizedBox(
                                        width: 110,
                                        child: TextFormField(
                                          key: ValueKey('price_${item.itemCode}_${item.unitPrice}'),
                                          initialValue: item.unitPrice.toStringAsFixed(2),
                                          inputFormatters: EthiopianInputValidators.numericAmountFormatters,
                                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                          style: AppTypography.mono(),
                                          decoration: const InputDecoration(labelText: 'Price', isDense: true),
                                          onChanged: (v) {
                                            setState(() {
                                              item.unitPrice = double.tryParse(v) ?? 0.0;
                                            });
                                          },
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      // Discount
                                      SizedBox(
                                        width: 80,
                                        child: TextFormField(
                                          key: ValueKey('disc_${item.itemCode}_${item.discount}'),
                                          initialValue: item.discount.toStringAsFixed(2),
                                          inputFormatters: EthiopianInputValidators.numericAmountFormatters,
                                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                          style: AppTypography.mono(),
                                          decoration: const InputDecoration(labelText: 'Disc.', isDense: true),
                                          onChanged: (v) {
                                            setState(() {
                                              item.discount = double.tryParse(v) ?? 0.0;
                                            });
                                          },
                                        ),
                                      ),
                                      const SizedBox(width: 16),
                                      // Line Subtotal
                                      Container(
                                        width: 120,
                                        alignment: Alignment.centerRight,
                                        child: Text(
                                          '$_currency ${lineSubtotal.toStringAsFixed(2)}',
                                          style: AppTypography.mono(
                                            color: AppColors.ink,
                                            weight: FontWeight.w600,
                                          ),
                                        ),
                                      ),
                                      IconButton(
                                        icon: const Icon(Icons.delete_outline, size: 18, color: AppColors.red600),
                                        onPressed: () => _removeLine(index),
                                        tooltip: 'Remove Item',
                                      ),
                                    ],
                                  );
                                },
                              ),
                            ),
                          );
                        },
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // 4. SUMMARY & SUBMISSION CARD
              Align(
                alignment: Alignment.centerRight,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 420),
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: AppColors.rule, width: 1),
                    ),
                    child: Column(
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(loc.get('subtotal'), style: AppTypography.uiLabel()),
                            Text(
                              '$_currency ${totals.preTaxTotal.toStringAsFixed(2)}',
                              style: AppTypography.mono(),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              _invoiceType == InvoiceType.export ? 'VAT (Zero-Rated 0%)' : loc.get('vat'),
                              style: AppTypography.uiLabel(),
                            ),
                            Text(
                              '$_currency ${totals.taxTotal.toStringAsFixed(2)}',
                              style: AppTypography.mono(),
                            ),
                          ],
                        ),
                        const Divider(height: 24, color: AppColors.rule),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(loc.get('grand_total'), style: AppTypography.uiLabelBold()),
                            Text(
                              '$_currency ${totals.grandTotal.toStringAsFixed(2)}',
                              style: AppTypography.mono(
                                color: AppColors.navy900,
                                weight: FontWeight.w700,
                              ).copyWith(fontSize: 18),
                            ),
                          ],
                        ),
                        const SizedBox(height: 20),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _isSubmitting ? null : _submitInvoice,
                            child: _isSubmitting
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      valueColor: AlwaysStoppedAnimation(Colors.white),
                                    ),
                                  )
                                : Text(loc.get('submit_invoice')),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Enhanced Multi-Select Catalog Cart Dialog:
/// - Select multiple products & services without modal closing
/// - Quantity increment/decrement within modal
/// - Real-time total calculation
/// - Barcode lookup
/// - Quick registration
class _CatalogSelectionDialog extends ConsumerStatefulWidget {
  final List<InvoiceLineDraft> initialLines;
  final void Function(List<InvoiceLineDraft> drafts) onItemsSelected;

  const _CatalogSelectionDialog({
    required this.initialLines,
    required this.onItemsSelected,
  });

  @override
  ConsumerState<_CatalogSelectionDialog> createState() => _CatalogSelectionDialogState();
}

class _CatalogSelectionDialogState extends ConsumerState<_CatalogSelectionDialog>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _searchController = TextEditingController();
  final _barcodeController = TextEditingController();

  List<ProductModel> _products = [];
  List<ServiceModel> _services = [];
  final Map<String, InvoiceLineDraft> _selectedCart = {};
  bool _isLoading = false;
  String? _barcodeLookupError;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadItems();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    _barcodeController.dispose();
    super.dispose();
  }

  Future<void> _loadItems() async {
    setState(() => _isLoading = true);
    try {
      final tenant = ref.read(tenantContextProvider);
      final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
      final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';
      final repo = ref.read(catalogRepositoryProvider);

      final query = _searchController.text.trim();
      final prods = await repo.getProducts(tenantId: tenantId, branchId: branchId, query: query);
      final servs = await repo.getServices(tenantId: tenantId, branchId: branchId, query: query);

      if (mounted) {
        setState(() {
          _products = prods.where((p) => p.isActive).toList();
          _services = servs.where((s) => s.isActive).toList();
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _toggleProduct(ProductModel p) {
    setState(() {
      if (_selectedCart.containsKey(p.itemCode)) {
        _selectedCart.remove(p.itemCode);
      } else {
        _selectedCart[p.itemCode] = InvoiceLineDraft(
          itemCode: p.itemCode,
          productDescription: p.description,
          quantity: 1.0,
          unit: p.unit,
          unitPrice: p.unitPrice,
          discount: 0.0,
          taxCode: p.taxClassification.code,
          itemType: 'PRODUCT',
          catalogItemId: p.id,
        );
      }
    });
  }

  void _toggleService(ServiceModel s) {
    setState(() {
      if (_selectedCart.containsKey(s.serviceCode)) {
        _selectedCart.remove(s.serviceCode);
      } else {
        _selectedCart[s.serviceCode] = InvoiceLineDraft(
          itemCode: s.serviceCode,
          productDescription: s.name,
          quantity: 1.0,
          unit: s.unit,
          unitPrice: s.unitPrice,
          discount: 0.0,
          taxCode: s.taxClassification.code,
          itemType: 'SERVICE',
          catalogItemId: s.id,
        );
      }
    });
  }

  void _updateQuantity(String code, double delta) {
    setState(() {
      if (_selectedCart.containsKey(code)) {
        final current = _selectedCart[code]!;
        final newQty = current.quantity + delta;
        if (newQty <= 0) {
          _selectedCart.remove(code);
        } else {
          current.quantity = newQty;
        }
      }
    });
  }

  Future<void> _lookupBarcode() async {
    final barcode = _barcodeController.text.trim();
    if (barcode.isEmpty) return;

    setState(() => _barcodeLookupError = null);

    final match = _products.where((p) => p.barcode != null && p.barcode == barcode).toList();
    if (match.isNotEmpty) {
      final product = match.first;
      setState(() {
        if (_selectedCart.containsKey(product.itemCode)) {
          _selectedCart[product.itemCode]!.quantity += 1.0;
        } else {
          _selectedCart[product.itemCode] = InvoiceLineDraft(
            itemCode: product.itemCode,
            productDescription: product.description,
            quantity: 1.0,
            unit: product.unit,
            unitPrice: product.unitPrice,
            discount: 0.0,
            taxCode: product.taxClassification.code,
            itemType: 'PRODUCT',
            catalogItemId: product.id,
          );
        }
        _barcodeController.clear();
      });
    } else {
      setState(() {
        _barcodeLookupError = 'Barcode "$barcode" not registered. Quick register item to proceed.';
      });
    }
  }

  void _openQuickRegistration() {
    final isProduct = _tabController.index == 0;
    final formKey = GlobalKey<FormState>();
    final codeCtrl = TextEditingController(text: isProduct && _barcodeController.text.isNotEmpty ? 'SKU-${_barcodeController.text}' : '');
    final nameCtrl = TextEditingController();
    final priceCtrl = TextEditingController();
    final barcodeCtrl = TextEditingController(text: _barcodeController.text);

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.paperRaised,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(3),
          side: const BorderSide(color: AppColors.rule),
        ),
        title: Text(
          isProduct ? 'QUICK REGISTER PRODUCT' : 'QUICK REGISTER SERVICE',
          style: AppTypography.h2(color: AppColors.navy900),
        ),
        content: SizedBox(
          width: 440,
          child: Form(
            key: formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  controller: codeCtrl,
                  decoration: InputDecoration(
                    labelText: isProduct ? 'Item Code / SKU *' : 'Service Code *',
                  ),
                  validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'Name / Description *'),
                  validator: (v) => v == null || v.isEmpty ? 'Required' : null,
                ),
                const SizedBox(height: 12),
                if (isProduct) ...[
                  TextFormField(
                    controller: barcodeCtrl,
                    decoration: const InputDecoration(labelText: 'Barcode'),
                  ),
                  const SizedBox(height: 12),
                ],
                TextFormField(
                  controller: priceCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Unit Price / Rate (ETB) *'),
                  validator: (v) => (double.tryParse(v ?? '') == null) ? 'Valid number required' : null,
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () async {
              if (!formKey.currentState!.validate()) return;
              final tenant = ref.read(tenantContextProvider);
              final tenantId = tenant.activeTenant?.id ?? '00000000-0000-0000-0000-000000000001';
              final branchId = tenant.activeBranch?.id ?? '00000000-0000-0000-0000-000000000010';
              final repo = ref.read(catalogRepositoryProvider);

              try {
                if (isProduct) {
                  final newProd = await repo.registerProduct(
                    tenantId: tenantId,
                    branchId: branchId,
                    product: ProductModel(
                      id: 'prod-${DateTime.now().millisecondsSinceEpoch}',
                      tenantId: tenantId,
                      branchId: branchId,
                      itemCode: codeCtrl.text.trim(),
                      sku: codeCtrl.text.trim(),
                      barcode: barcodeCtrl.text.trim().isNotEmpty ? barcodeCtrl.text.trim() : null,
                      description: nameCtrl.text.trim(),
                      category: 'GENERAL',
                      unit: 'PCS',
                      unitPrice: double.parse(priceCtrl.text.trim()),
                      taxClassification: TaxClassification.vat15,
                      trackStock: true,
                    ),
                  );
                  if (ctx.mounted) Navigator.of(ctx).pop();
                  setState(() {
                    _selectedCart[newProd.itemCode] = InvoiceLineDraft(
                      itemCode: newProd.itemCode,
                      productDescription: newProd.description,
                      quantity: 1.0,
                      unit: newProd.unit,
                      unitPrice: newProd.unitPrice,
                      discount: 0.0,
                      taxCode: newProd.taxClassification.code,
                      itemType: 'PRODUCT',
                      catalogItemId: newProd.id,
                    );
                  });
                  _loadItems();
                } else {
                  final newServ = await repo.registerService(
                    tenantId: tenantId,
                    branchId: branchId,
                    service: ServiceModel(
                      id: 'serv-${DateTime.now().millisecondsSinceEpoch}',
                      tenantId: tenantId,
                      branchId: branchId,
                      serviceCode: codeCtrl.text.trim(),
                      name: nameCtrl.text.trim(),
                      description: nameCtrl.text.trim(),
                      category: 'PROFESSIONAL',
                      unit: 'HOUR',
                      unitPrice: double.parse(priceCtrl.text.trim()),
                      taxClassification: TaxClassification.vat15,
                    ),
                  );
                  if (ctx.mounted) Navigator.of(ctx).pop();
                  setState(() {
                    _selectedCart[newServ.serviceCode] = InvoiceLineDraft(
                      itemCode: newServ.serviceCode,
                      productDescription: newServ.name,
                      quantity: 1.0,
                      unit: newServ.unit,
                      unitPrice: newServ.unitPrice,
                      discount: 0.0,
                      taxCode: newServ.taxClassification.code,
                      itemType: 'SERVICE',
                      catalogItemId: newServ.id,
                    );
                  });
                  _loadItems();
                }
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Registration failed: $e'), backgroundColor: AppColors.red600),
                  );
                }
              }
            },
            child: const Text('Authorize & Select'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final totalSelectedQty = _selectedCart.values.fold<double>(0.0, (acc, item) => acc + item.quantity);
    final totalSelectedAmount = _selectedCart.values.fold<double>(0.0, (acc, item) => acc + (item.quantity * item.unitPrice));

    return AlertDialog(
      backgroundColor: AppColors.paperRaised,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(3),
        side: const BorderSide(color: AppColors.rule),
      ),
      titlePadding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
      contentPadding: const EdgeInsets.all(20),
      title: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Row(
              children: [
                Text('CATALOG SELECTION MODAL', style: AppTypography.h2()),
                if (_selectedCart.isNotEmpty) ...[
                  const SizedBox(width: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.navy900,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      '${_selectedCart.length} Selected',
                      style: AppTypography.monoSmall(color: Colors.white, weight: FontWeight.bold),
                    ),
                  ),
                ],
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 20),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
      content: SizedBox(
        width: 780,
        height: 540,
        child: Column(
          children: [
            // Barcode Scanner / Lookup Bar
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.paper,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule),
              ),
              child: Row(
                children: [
                  const Icon(Icons.qr_code_scanner, size: 22, color: AppColors.navy900),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: _barcodeController,
                      decoration: const InputDecoration(
                        hintText: 'Scan barcode to instantly add item to selection cart...',
                        isDense: true,
                        border: InputBorder.none,
                      ),
                      onSubmitted: (_) => _lookupBarcode(),
                    ),
                  ),
                  ElevatedButton(
                    onPressed: _lookupBarcode,
                    child: const Text('Add by Barcode'),
                  ),
                ],
              ),
            ),
            if (_barcodeLookupError != null) ...[
              const SizedBox(height: 8),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: AppColors.red600.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.red600.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.warning_amber, size: 16, color: AppColors.red600),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _barcodeLookupError!,
                        style: AppTypography.bodySmall(color: AppColors.red600),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 12),

            // Search and Register Bar
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchController,
                    decoration: const InputDecoration(
                      hintText: 'Search catalog by name, item code, SKU...',
                      prefixIcon: Icon(Icons.search, size: 18),
                      isDense: true,
                    ),
                    onChanged: (_) => _loadItems(),
                  ),
                ),
                const SizedBox(width: 12),
                OutlinedButton.icon(
                  onPressed: _openQuickRegistration,
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('Register Item'),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Tabs: Products vs Services
            TabBar(
              controller: _tabController,
              indicatorColor: AppColors.navy900,
              labelColor: AppColors.navy900,
              unselectedLabelColor: AppColors.inkMuted,
              tabs: [
                Tab(text: 'PRODUCTS (${_products.length})'),
                Tab(text: 'SERVICES (${_services.length})'),
              ],
            ),
            const SizedBox(height: 8),

            // Tab View
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : TabBarView(
                      controller: _tabController,
                      children: [
                        // Products List
                        _products.isEmpty
                            ? Center(
                                child: Text('No matching active products found.', style: AppTypography.bodySmall()),
                              )
                            : ListView.separated(
                                itemCount: _products.length,
                                separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                                itemBuilder: (context, idx) {
                                  final p = _products[idx];
                                  final isSelected = _selectedCart.containsKey(p.itemCode);
                                  final selectedQty = _selectedCart[p.itemCode]?.quantity ?? 0.0;

                                  return Material(
                                    type: MaterialType.transparency,
                                    child: ListTile(
                                      dense: true,
                                      selected: isSelected,
                                      selectedTileColor: AppColors.navy700.withValues(alpha: 0.04),
                                      leading: Checkbox(
                                        value: isSelected,
                                        onChanged: (_) => _toggleProduct(p),
                                      ),
                                      title: Text(p.description, style: AppTypography.uiLabelBold()),
                                      subtitle: Text(
                                        'Code: ${p.itemCode} | SKU: ${p.sku} | Unit: ${p.unit} | Tax: ${p.taxClassification.label}',
                                        style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                      ),
                                      trailing: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Text(
                                            'ETB ${p.unitPrice.toStringAsFixed(2)}',
                                            style: AppTypography.mono(weight: FontWeight.w600, color: AppColors.navy900),
                                          ),
                                          const SizedBox(width: 12),
                                          if (isSelected) ...[
                                            IconButton(
                                              icon: const Icon(Icons.remove_circle_outline, size: 20),
                                              onPressed: () => _updateQuantity(p.itemCode, -1.0),
                                            ),
                                            Text(
                                              selectedQty.toStringAsFixed(0),
                                              style: AppTypography.mono(weight: FontWeight.bold),
                                            ),
                                            IconButton(
                                              icon: const Icon(Icons.add_circle_outline, size: 20),
                                              onPressed: () => _updateQuantity(p.itemCode, 1.0),
                                            ),
                                          ] else ...[
                                            OutlinedButton(
                                              onPressed: () => _toggleProduct(p),
                                              style: OutlinedButton.styleFrom(
                                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                              ),
                                              child: const Text('Add'),
                                            ),
                                          ],
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              ),
                        // Services List
                        _services.isEmpty
                            ? Center(
                                child: Text('No matching active services found.', style: AppTypography.bodySmall()),
                              )
                            : ListView.separated(
                                itemCount: _services.length,
                                separatorBuilder: (context, index) => const Divider(height: 1, color: AppColors.rule),
                                itemBuilder: (context, idx) {
                                  final s = _services[idx];
                                  final isSelected = _selectedCart.containsKey(s.serviceCode);
                                  final selectedQty = _selectedCart[s.serviceCode]?.quantity ?? 0.0;

                                  return Material(
                                    type: MaterialType.transparency,
                                    child: ListTile(
                                      dense: true,
                                      selected: isSelected,
                                      selectedTileColor: AppColors.navy700.withValues(alpha: 0.04),
                                      leading: Checkbox(
                                        value: isSelected,
                                        onChanged: (_) => _toggleService(s),
                                      ),
                                      title: Text(s.name, style: AppTypography.uiLabelBold()),
                                      subtitle: Text(
                                        'Code: ${s.serviceCode} | Unit: ${s.unit} | Tax: ${s.taxClassification.label}',
                                        style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                      ),
                                      trailing: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Text(
                                            'ETB ${s.unitPrice.toStringAsFixed(2)}',
                                            style: AppTypography.mono(weight: FontWeight.w600, color: AppColors.navy900),
                                          ),
                                          const SizedBox(width: 12),
                                          if (isSelected) ...[
                                            IconButton(
                                              icon: const Icon(Icons.remove_circle_outline, size: 20),
                                              onPressed: () => _updateQuantity(s.serviceCode, -1.0),
                                            ),
                                            Text(
                                              selectedQty.toStringAsFixed(0),
                                              style: AppTypography.mono(weight: FontWeight.bold),
                                            ),
                                            IconButton(
                                              icon: const Icon(Icons.add_circle_outline, size: 20),
                                              onPressed: () => _updateQuantity(s.serviceCode, 1.0),
                                            ),
                                          ] else ...[
                                            OutlinedButton(
                                              onPressed: () => _toggleService(s),
                                              style: OutlinedButton.styleFrom(
                                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                              ),
                                              child: const Text('Add'),
                                            ),
                                          ],
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              ),
                      ],
                    ),
            ),
            const SizedBox(height: 12),

            // Bottom Confirmation Bar
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.paper,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    _selectedCart.isEmpty
                        ? 'No items selected'
                        : '${totalSelectedQty.toStringAsFixed(0)} items (ETB ${totalSelectedAmount.toStringAsFixed(2)})',
                    style: AppTypography.uiLabelBold(color: AppColors.navy900),
                  ),
                  Row(
                    children: [
                      if (_selectedCart.isNotEmpty)
                        TextButton(
                          onPressed: () => setState(() => _selectedCart.clear()),
                          child: const Text('Clear Selection'),
                        ),
                      const SizedBox(width: 8),
                      ElevatedButton.icon(
                        onPressed: _selectedCart.isEmpty
                            ? null
                            : () {
                                widget.onItemsSelected(_selectedCart.values.toList());
                                Navigator.of(context).pop();
                              },
                        icon: const Icon(Icons.check, size: 16),
                        label: Text('Add ${_selectedCart.length} Selected Items'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
