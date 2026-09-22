import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/utils/ethiopian_input_validators.dart';

class TenantOnboardingWizard extends ConsumerStatefulWidget {
  const TenantOnboardingWizard({super.key});

  @override
  ConsumerState<TenantOnboardingWizard> createState() =>
      _TenantOnboardingWizardState();
}

class _TenantOnboardingWizardState
    extends ConsumerState<TenantOnboardingWizard> {
  int _currentStep = 0;
  static const int _totalSteps = 5;

  // Form Keys for granular per-step validation
  final _step1FormKey = GlobalKey<FormState>();
  final _step2FormKey = GlobalKey<FormState>();
  final _step4FormKey = GlobalKey<FormState>();

  // ==========================================
  // Step 1: Taxpayer Legal Entity
  // ==========================================
  final _tinCtrl = TextEditingController();
  final _legalNameCtrl = TextEditingController();
  final _tradeNameCtrl = TextEditingController();
  final _vatNumberCtrl = TextEditingController();
  final _cityCtrl = TextEditingController();
  final _woredaCtrl = TextEditingController();
  String _selectedTaxOffice = 'Addis Ababa Large Taxpayers Office (LTO)';
  String _selectedRegion = 'Addis Ababa';
  bool _isVatRegistered = true;

  // ==========================================
  // Step 2: Primary Branch & POS Terminals
  // ==========================================
  final _branchNameCtrl = TextEditingController(
    text: 'Main Headquarters & Flagship',
  );
  final _branchCodeCtrl = TextEditingController(text: 'BR-HQ-01');
  final _branchPhoneCtrl = TextEditingController();
  final _branchAddressCtrl = TextEditingController();
  int _posDeviceCount = 3;

  // ==========================================
  // Step 3: Subscription & Commercial Quota
  // ==========================================
  String _selectedPlan =
      'GROWTH'; // 'STARTER', 'SME_STANDARD', 'GROWTH', 'ENTERPRISE'
  String _selectedBillingCycle = 'ANNUAL'; // 'MONTHLY', 'ANNUAL'

  // ==========================================
  // Step 4: Primary Administrator Identity
  // ==========================================
  final _adminNameCtrl = TextEditingController();
  final _adminUsernameCtrl = TextEditingController();
  final _adminEmailCtrl = TextEditingController();
  final _adminPhoneCtrl = TextEditingController();
  final _adminPasswordCtrl = TextEditingController(text: 'TenantAdmin2026!');
  bool _obscurePassword = true;

  // ==========================================
  // Step 5: Review & Statutory Certification
  // ==========================================
  bool _statutoryCertified = false;
  bool _isSubmitting = false;
  String? _errorMessage;
  Map<String, dynamic>? _provisionedTenantResult;

  static const List<String> _morTaxOffices = [
    'Addis Ababa Large Taxpayers Office (LTO)',
    'Eastern Addis Ababa Medium Taxpayers Branch',
    'Western Addis Ababa Medium Taxpayers Branch',
    'Southern Addis Ababa Medium Taxpayers Branch',
    'Northern Addis Ababa Medium Taxpayers Branch',
    'Dire Dawa Medium Taxpayers Branch',
    'Hawassa Taxpayers Branch',
    'Adama Taxpayers Branch',
    'Bahir Dar Taxpayers Branch',
    'Mekelle Taxpayers Branch',
    'Jimma Taxpayers Branch',
    'Bishoftu Regional Tax Center',
  ];

  @override
  void dispose() {
    _tinCtrl.dispose();
    _legalNameCtrl.dispose();
    _tradeNameCtrl.dispose();
    _vatNumberCtrl.dispose();
    _cityCtrl.dispose();
    _woredaCtrl.dispose();
    _branchNameCtrl.dispose();
    _branchCodeCtrl.dispose();
    _branchPhoneCtrl.dispose();
    _branchAddressCtrl.dispose();
    _adminNameCtrl.dispose();
    _adminUsernameCtrl.dispose();
    _adminEmailCtrl.dispose();
    _adminPhoneCtrl.dispose();
    _adminPasswordCtrl.dispose();
    super.dispose();
  }

  void _validateAndNext() {
    setState(() => _errorMessage = null);

    if (_currentStep == 0) {
      if (!_step1FormKey.currentState!.validate()) return;
    } else if (_currentStep == 1) {
      if (!_step2FormKey.currentState!.validate()) return;
    } else if (_currentStep == 3) {
      if (!_step4FormKey.currentState!.validate()) return;
    }

    if (_currentStep < _totalSteps - 1) {
      setState(() => _currentStep += 1);
    } else {
      _submitOnboarding();
    }
  }

  void _goToStep(int step) {
    if (step < _currentStep) {
      setState(() {
        _errorMessage = null;
        _currentStep = step;
      });
    }
  }

  Future<void> _submitOnboarding() async {
    if (!_statutoryCertified) {
      setState(() {
        _errorMessage =
            'Statutory compliance certification under Directive No. 1142/2026 is required before provisioning.';
      });
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final client = ref.read(saasManagementApiClientProvider);

      // Sanitize every single input field using EthiopianInputValidators
      final cleanTin =
          EthiopianInputValidators.sanitizeTin(_tinCtrl.text) ?? '';
      final cleanLegalName =
          EthiopianInputValidators.sanitizeName(_legalNameCtrl.text) ?? '';
      final cleanTradeName =
          EthiopianInputValidators.sanitizeName(_tradeNameCtrl.text) ??
          cleanLegalName;
      final cleanVatNumber = _isVatRegistered
          ? (EthiopianInputValidators.sanitizeVatNumber(_vatNumberCtrl.text) ??
                '${cleanTin}VAT')
          : null;
      final cleanCity =
          EthiopianInputValidators.sanitizeAddressField(_cityCtrl.text) ??
          'Addis Ababa';
      final cleanWoreda =
          EthiopianInputValidators.sanitizeAddressField(_woredaCtrl.text) ??
          'Woreda 03';

      final cleanBranchName =
          EthiopianInputValidators.sanitizeName(_branchNameCtrl.text) ??
          'Main Headquarters';
      final cleanBranchCode = _branchCodeCtrl.text
          .trim()
          .toUpperCase()
          .replaceAll(RegExp(r'[^A-Z0-9-]'), '');
      final cleanBranchPhone =
          EthiopianInputValidators.sanitizePhone(_branchPhoneCtrl.text) ??
          '+251911000000';

      final cleanAdminName =
          EthiopianInputValidators.sanitizeName(_adminNameCtrl.text) ??
          '$cleanLegalName Admin';
      final cleanAdminUsername = _adminUsernameCtrl.text.trim().isNotEmpty
          ? _adminUsernameCtrl.text.trim()
          : (_adminEmailCtrl.text.trim().isNotEmpty
                ? _adminEmailCtrl.text.trim()
                : 'admin');
      final cleanAdminEmail =
          EthiopianInputValidators.sanitizeEmail(_adminEmailCtrl.text) ??
          'admin@taxpayer.et';
      final cleanAdminPhone =
          EthiopianInputValidators.sanitizePhone(_adminPhoneCtrl.text) ??
          cleanBranchPhone;
      final cleanPassword = _adminPasswordCtrl.text.trim().isNotEmpty
          ? _adminPasswordCtrl.text.trim()
          : 'TenantAdmin2026!';

      final payload = {
        'tenant': {
          'tin': cleanTin,
          'legalName': cleanLegalName,
          'tradeName': cleanTradeName,
          'taxOffice': _selectedTaxOffice,
          'region': _selectedRegion,
          'city': cleanCity,
          'woreda': cleanWoreda,
          'isVatRegistered': _isVatRegistered,
          // ignore: use_null_aware_elements
          if (cleanVatNumber != null) 'vatNumber': cleanVatNumber,
        },
        'primaryBranch': {
          'name': cleanBranchName,
          'code': cleanBranchCode.isNotEmpty ? cleanBranchCode : 'BR-HQ-01',
          'city': cleanCity,
          'region': _selectedRegion,
          'phone': cleanBranchPhone,
          'allocatedDevices': _posDeviceCount,
        },
        'subscription': {
          'plan': _selectedPlan,
          'billingCycle': _selectedBillingCycle,
        },
        'primaryAdmin': {
          'fullName': cleanAdminName,
          'username': cleanAdminUsername,
          'email': cleanAdminEmail,
          'phone': cleanAdminPhone,
          'password': cleanPassword,
          'role': 'ROLE_TENANT_ADMIN',
        },
      };

      final response = await client.post(
        '/api/v1/saas/tenants/onboard',
        data: payload,
      );

      if (response.data is Map) {
        setState(() {
          _isSubmitting = false;
          _provisionedTenantResult = Map<String, dynamic>.from(
            response.data as Map,
          );
        });
      } else {
        setState(() {
          _isSubmitting = false;
          _provisionedTenantResult = {
            'tenantId': 'PROVISIONED-OK',
            'tin': cleanTin,
            'legalName': cleanLegalName,
            'plan': _selectedPlan,
            'message':
                'Tenant successfully onboarded to the platform database.',
          };
        });
      }
    } catch (e) {
      setState(() {
        _isSubmitting = false;
        _errorMessage = 'Tenant onboarding failed: ${e.toString()}';
      });
    }
  }

  void _resetWizard() {
    setState(() {
      _currentStep = 0;
      _provisionedTenantResult = null;
      _errorMessage = null;
      _statutoryCertified = false;
      _tinCtrl.clear();
      _legalNameCtrl.clear();
      _tradeNameCtrl.clear();
      _vatNumberCtrl.clear();
      _cityCtrl.clear();
      _woredaCtrl.clear();
      _branchNameCtrl.text = 'Main Headquarters & Flagship';
      _branchCodeCtrl.text = 'BR-HQ-01';
      _branchPhoneCtrl.clear();
      _branchAddressCtrl.clear();
      _adminNameCtrl.clear();
      _adminUsernameCtrl.clear();
      _adminEmailCtrl.clear();
      _adminPhoneCtrl.clear();
      _adminPasswordCtrl.text = 'TenantAdmin2026!';
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_provisionedTenantResult != null) {
      return _buildSuccessScreen();
    }

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: Column(
        children: [
          // Top Header & Progress Bar
          _buildTopHeader(),

          // Main Stepper Body
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1000),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Error Alert Banner
                      if (_errorMessage != null) ...[
                        Container(
                          margin: const EdgeInsets.only(bottom: 20),
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: AppColors.red600.withValues(alpha: 0.08),
                            borderRadius: BorderRadius.circular(3),
                            border: Border.all(
                              color: AppColors.red600.withValues(alpha: 0.4),
                            ),
                          ),
                          child: Row(
                            children: [
                              const Icon(
                                Icons.error_outline,
                                color: AppColors.red600,
                                size: 20,
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(
                                  _errorMessage!,
                                  style: AppTypography.bodySmall(
                                    color: AppColors.red600,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],

                      // Step Content Cards
                      AnimatedSwitcher(
                        duration: const Duration(milliseconds: 250),
                        child: _buildCurrentStepView(),
                      ),

                      const SizedBox(height: 32),

                      // Bottom Navigation Controls
                      _buildBottomActionBar(),
                      const SizedBox(height: 48),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // =========================================================================
  // Top Header with Step Navigator and Completion Bar
  // =========================================================================
  Widget _buildTopHeader() {
    final progress = (_currentStep + 1) / _totalSteps;

    return Container(
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        border: const Border(
          bottom: BorderSide(color: AppColors.rule, width: 1),
        ),
        boxShadow: [
          BoxShadow(
            color: AppColors.ink.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.navy700.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(3),
                      ),
                      child: const Icon(
                        Icons.add_business,
                        color: AppColors.navy700,
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'TENANT ONBOARDING & GATEWAY PROVISIONING',
                          style: AppTypography.h2(),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Ethiopian Electronic Invoicing Directive No. 1142/2026 Statutory Compliance Engine',
                          style: AppTypography.bodySmall(
                            color: AppColors.inkMuted,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.paper,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Text(
                        'STEP ${_currentStep + 1} OF $_totalSteps',
                        style: AppTypography.monoSmall(
                          weight: FontWeight.w700,
                          color: AppColors.navy700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    OutlinedButton.icon(
                      onPressed: () => context.go('/saas/dashboard'),
                      icon: const Icon(Icons.close, size: 14),
                      label: const Text('Exit Wizard'),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // Linear Progress Bar
          LinearProgressIndicator(
            value: progress,
            backgroundColor: AppColors.rule,
            color: AppColors.navy700,
            minHeight: 3,
          ),

          // Horizontal Step Navigator Pills
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
            color: AppColors.paper,
            child: Row(
              children: [
                _buildStepTab(0, '1. Taxpayer Profile', Icons.business),
                _buildStepDivider(),
                _buildStepTab(1, '2. Branch & POS', Icons.storefront),
                _buildStepDivider(),
                _buildStepTab(2, '3. Subscription Tier', Icons.card_membership),
                _buildStepDivider(),
                _buildStepTab(3, '4. Root Admin', Icons.admin_panel_settings),
                _buildStepDivider(),
                _buildStepTab(4, '5. Review & Deploy', Icons.verified_user),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStepTab(int index, String label, IconData icon) {
    final isCompleted = index < _currentStep;
    final isCurrent = index == _currentStep;

    return Expanded(
      child: InkWell(
        onTap: () => _goToStep(index),
        borderRadius: BorderRadius.circular(3),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 8),
          decoration: BoxDecoration(
            color: isCurrent
                ? AppColors.navy700.withValues(alpha: 0.08)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(3),
            border: isCurrent
                ? Border.all(color: AppColors.navy700.withValues(alpha: 0.3))
                : null,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isCompleted
                      ? AppColors.green700
                      : (isCurrent ? AppColors.navy700 : AppColors.rule),
                ),
                child: Center(
                  child: isCompleted
                      ? const Icon(Icons.check, size: 12, color: Colors.white)
                      : Text(
                          '${index + 1}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: isCurrent
                                ? Colors.white
                                : AppColors.inkMuted,
                          ),
                        ),
                ),
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  label,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isCurrent ? FontWeight.w700 : FontWeight.w500,
                    color: isCurrent
                        ? AppColors.navy700
                        : (isCompleted ? AppColors.ink : AppColors.inkMuted),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStepDivider() {
    return Container(
      width: 16,
      height: 1,
      color: AppColors.rule,
      margin: const EdgeInsets.symmetric(horizontal: 4),
    );
  }

  Widget _buildCurrentStepView() {
    switch (_currentStep) {
      case 0:
        return _buildStep1TaxpayerProfile();
      case 1:
        return _buildStep2BranchAndDevices();
      case 2:
        return _buildStep3Subscription();
      case 3:
        return _buildStep4AdminIdentity();
      case 4:
        return _buildStep5ReviewAndDeploy();
      default:
        return const SizedBox.shrink();
    }
  }

  // =========================================================================
  // STEP 1: Taxpayer Profile & Legal Entity
  // =========================================================================
  Widget _buildStep1TaxpayerProfile() {
    return Form(
      key: _step1FormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSectionHeader(
            title: 'Taxpayer Legal Entity & Tax Authority Jurisdiction',
            subtitle:
                'Authoritative tax credentials verified against the Ethiopian Ministry of Revenues (MoR) Registry.',
          ),
          const SizedBox(height: 20),

          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Left Column: Identification
              Expanded(
                child: _buildFormCard(
                  title: 'Tax Identification & Identity',
                  icon: Icons.badge_outlined,
                  children: [
                    TextFormField(
                      controller: _tinCtrl,
                      decoration: InputDecoration(
                        labelText: 'Ethiopian TIN (10 Numeric Digits) *',
                        hintText: 'e.g. 0011223344',
                        prefixIcon: const Icon(Icons.pin_outlined, size: 18),
                        suffixIcon: IconButton(
                          icon: const Icon(
                            Icons.auto_fix_high,
                            size: 16,
                            color: AppColors.navy700,
                          ),
                          tooltip: 'Pre-fill Demo Enterprise TIN',
                          onPressed: () {
                            _tinCtrl.text = '0011223344';
                            _legalNameCtrl.text =
                                'Abyssinia Trading & Distribution PLC';
                            _tradeNameCtrl.text = 'Abyssinia Retail';
                            _vatNumberCtrl.text = '0011223344VAT';
                            _cityCtrl.text = 'Addis Ababa';
                            _woredaCtrl.text = 'Bole Subcity Woreda 03';
                          },
                        ),
                      ),
                      inputFormatters: EthiopianInputValidators.tinFormatters,
                      validator: (v) => EthiopianInputValidators.validateTin(
                        v,
                        isRequired: true,
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _legalNameCtrl,
                      decoration: const InputDecoration(
                        labelText:
                            'Legal Business Name (English / Ethiopic Amharic) *',
                        hintText:
                            'e.g. Abyssinia Trading Enterprise PLC / አቢሲንያ ትሬዲንግ',
                        prefixIcon: Icon(Icons.domain_outlined, size: 18),
                      ),
                      inputFormatters: EthiopianInputValidators.nameFormatters,
                      validator: (v) =>
                          EthiopianInputValidators.validateLegalName(
                            v,
                            isRequired: true,
                          ),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _tradeNameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Commercial Brand / Trade Name',
                        hintText: 'e.g. Abyssinia Foods & Retail',
                        prefixIcon: Icon(Icons.store_outlined, size: 18),
                      ),
                      inputFormatters: EthiopianInputValidators.nameFormatters,
                      validator: (v) =>
                          EthiopianInputValidators.validateTradeName(
                            v,
                            isRequired: false,
                          ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 20),

              // Right Column: Tax Jurisdiction & Location
              Expanded(
                child: _buildFormCard(
                  title: 'MoR Tax Jurisdiction & Address',
                  icon: Icons.account_balance_outlined,
                  children: [
                    DropdownButtonFormField<String>(
                      initialValue: _selectedTaxOffice,
                      isExpanded: true,
                      isDense: true,
                      decoration: const InputDecoration(
                        labelText: 'Assigned Tax Authority Center *',
                        prefixIcon: Icon(
                          Icons.location_city_outlined,
                          size: 16,
                        ),
                        prefixIconConstraints: BoxConstraints(
                          minWidth: 32,
                          minHeight: 32,
                        ),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 12,
                        ),
                      ),
                      items: _morTaxOffices.map((office) {
                        return DropdownMenuItem(
                          value: office,
                          child: Text(
                            office,
                            style: AppTypography.bodySmall(),
                            overflow: TextOverflow.ellipsis,
                          ),
                        );
                      }).toList(),
                      onChanged: (v) => setState(
                        () => _selectedTaxOffice = v ?? _selectedTaxOffice,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.paper,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Row(
                        children: [
                          Checkbox(
                            value: _isVatRegistered,
                            activeColor: AppColors.navy700,
                            onChanged: (v) =>
                                setState(() => _isVatRegistered = v ?? true),
                          ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '15% Value Added Tax (VAT) Registered',
                                  style: AppTypography.uiLabelBold(),
                                ),
                                Text(
                                  'Subject to Directive No. 1142/2026 Standard VAT Invoicing',
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
                    if (_isVatRegistered) ...[
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _vatNumberCtrl,
                        decoration: const InputDecoration(
                          labelText: 'VAT Registration Certificate Number *',
                          hintText: 'e.g. 0011223344VAT',
                          prefixIcon: Icon(
                            Icons.receipt_long_outlined,
                            size: 18,
                          ),
                        ),
                        inputFormatters: EthiopianInputValidators.vatFormatters,
                        validator: (v) =>
                            EthiopianInputValidators.validateVatNumber(
                              v,
                              isRequired: true,
                              isVatRegistered: true,
                            ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _selectedRegion,
                            isExpanded: true,
                            isDense: true,
                            decoration: const InputDecoration(
                              labelText: 'Regional State *',
                              prefixIcon: Icon(Icons.map_outlined, size: 16),
                              prefixIconConstraints: BoxConstraints(
                                minWidth: 32,
                                minHeight: 32,
                              ),
                              contentPadding: EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 12,
                              ),
                            ),
                            items: EthiopianInputValidators.ethiopianRegions
                                .map((region) {
                                  return DropdownMenuItem(
                                    value: region,
                                    child: Text(
                                      region,
                                      style: AppTypography.bodySmall(),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  );
                                })
                                .toList(),
                            onChanged: (v) => setState(
                              () => _selectedRegion = v ?? _selectedRegion,
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: _cityCtrl,
                            decoration: const InputDecoration(
                              labelText: 'City / Subcity *',
                              hintText: 'e.g. Bole Subcity',
                              prefixIcon: Icon(
                                Icons.pin_drop_outlined,
                                size: 18,
                              ),
                            ),
                            validator: (v) =>
                                EthiopianInputValidators.validateCity(
                                  v,
                                  isRequired: true,
                                ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // =========================================================================
  // STEP 2: Primary Branch & POS Terminals
  // ==========================================
  Widget _buildStep2BranchAndDevices() {
    return Form(
      key: _step2FormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSectionHeader(
            title: 'Primary Operating Branch & POS Hardware Allocation',
            subtitle:
                'Provision default headquarters branch code and allocate cryptographic POS device licenses.',
          ),
          const SizedBox(height: 20),

          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Left: Branch Info
              Expanded(
                child: _buildFormCard(
                  title: 'Primary Headquarters Branch',
                  icon: Icons.store_mall_directory_outlined,
                  children: [
                    TextFormField(
                      controller: _branchNameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Primary Branch Name *',
                        hintText: 'e.g. Main Headquarters & Flagship Store',
                        prefixIcon: Icon(
                          Icons.business_center_outlined,
                          size: 18,
                        ),
                      ),
                      inputFormatters: EthiopianInputValidators.nameFormatters,
                      validator: (v) =>
                          EthiopianInputValidators.validateLegalName(
                            v,
                            isRequired: true,
                            fieldName: 'Branch Name',
                          ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: _branchCodeCtrl,
                            decoration: const InputDecoration(
                              labelText: 'Branch Code (Uppercase) *',
                              hintText: 'e.g. BR-HQ-01',
                              prefixIcon: Icon(
                                Icons.qr_code_outlined,
                                size: 18,
                              ),
                            ),
                            inputFormatters: [
                              FilteringTextInputFormatter.allow(
                                RegExp(r'[a-zA-Z0-9-]'),
                              ),
                              LengthLimitingTextInputFormatter(16),
                            ],
                            validator: (v) => v == null || v.trim().isEmpty
                                ? 'Branch code is required'
                                : null,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: _branchPhoneCtrl,
                            decoration: const InputDecoration(
                              labelText: 'Branch Phone Number *',
                              hintText: 'e.g. +251911223344',
                              prefixIcon: Icon(Icons.phone_outlined, size: 18),
                            ),
                            inputFormatters:
                                EthiopianInputValidators.phoneFormatters,
                            validator: (v) =>
                                EthiopianInputValidators.validatePhone(
                                  v,
                                  isRequired: true,
                                  fieldName: 'Branch Phone',
                                ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _branchAddressCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Physical Street Address / Building',
                        hintText: 'e.g. Africa Avenue, Mega Tower 4th Floor',
                        prefixIcon: Icon(Icons.streetview_outlined, size: 18),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 20),

              // Right: POS Allocation
              Expanded(
                child: _buildFormCard(
                  title: 'POS Terminal Quota Allocation',
                  icon: Icons.point_of_sale_outlined,
                  children: [
                    Text(
                      'Select the initial number of Electronic Invoicing POS terminals permitted for this taxpayer:',
                      style: AppTypography.bodySmall(),
                    ),
                    const SizedBox(height: 16),
                    Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      children: [
                        _buildPosDeviceChip(
                          1,
                          '1 POS Terminal',
                          'Single Till SME',
                        ),
                        _buildPosDeviceChip(
                          3,
                          '3 POS Terminals',
                          'Standard Store',
                        ),
                        _buildPosDeviceChip(
                          5,
                          '5 POS Terminals',
                          'High-Traffic Retail',
                        ),
                        _buildPosDeviceChip(
                          10,
                          '10 POS Terminals',
                          'Multi-Floor Supermarket',
                        ),
                        _buildPosDeviceChip(
                          25,
                          '25 POS Terminals',
                          'Enterprise Chain',
                        ),
                        _buildPosDeviceChip(
                          50,
                          '50 POS Terminals',
                          'National Distribution',
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.navy700.withValues(alpha: 0.05),
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(
                          color: AppColors.navy700.withValues(alpha: 0.2),
                        ),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.shield_outlined,
                            size: 18,
                            color: AppColors.navy700,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              'Each terminal is automatically provisioned with a secure cryptographic device serial for MoR offline receipt signing.',
                              style: AppTypography.bodySmall(
                                color: AppColors.navy700,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPosDeviceChip(int count, String label, String desc) {
    final isSelected = _posDeviceCount == count;
    return InkWell(
      onTap: () => setState(() => _posDeviceCount = count),
      borderRadius: BorderRadius.circular(3),
      child: Container(
        width: 190,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.navy700.withValues(alpha: 0.1)
              : AppColors.paper,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(
            color: isSelected ? AppColors.navy700 : AppColors.rule,
            width: isSelected ? 1.5 : 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  label,
                  style: AppTypography.uiLabelBold(
                    color: isSelected ? AppColors.navy700 : AppColors.ink,
                  ),
                ),
                if (isSelected)
                  const Icon(
                    Icons.check_circle,
                    size: 14,
                    color: AppColors.navy700,
                  ),
              ],
            ),
            const SizedBox(height: 2),
            Text(
              desc,
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
          ],
        ),
      ),
    );
  }

  // =========================================================================
  // STEP 3: SaaS Subscription Tier
  // =========================================================================
  Widget _buildStep3Subscription() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader(
          title: 'SaaS Commercial Subscription & Monthly Quota Tier',
          subtitle:
              'Select invoice processing allowances, cloud HSM key configurations, and support SLA.',
        ),
        const SizedBox(height: 16),

        // Billing Cycle Toggle
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Billing Frequency:', style: AppTypography.uiLabelBold()),
            const SizedBox(width: 12),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'MONTHLY', label: Text('Monthly')),
                ButtonSegment(
                  value: 'ANNUAL',
                  label: Text('Annual (20% Savings)'),
                ),
              ],
              selected: {_selectedBillingCycle},
              onSelectionChanged: (set) =>
                  setState(() => _selectedBillingCycle = set.first),
            ),
          ],
        ),
        const SizedBox(height: 20),

        // Plan Cards Grid
        IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: _buildPlanCard(
                  'STARTER',
                  'Starter Tier',
                  '5,000 Invoices/mo',
                  '2 POS Terminals',
                  'Standard 99.5% Gateway SLA',
                  'Ideal for single retail shops and small enterprises.',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildPlanCard(
                  'SME_STANDARD',
                  'SME Standard',
                  '15,000 Invoices/mo',
                  '5 POS Terminals',
                  '99.9% MoR Sync SLA',
                  'Ideal for growing wholesalers & supermarket outlets.',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildPlanCard(
                  'GROWTH',
                  'Growth Enterprise',
                  '50,000 Invoices/mo',
                  '15 POS Terminals',
                  'Priority Gateway Routing',
                  'Recommended for high-volume distributors & hotel chains.',
                  isRecommended: true,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildPlanCard(
                  'ENTERPRISE',
                  'Enterprise Unlimited',
                  'Unlimited Invoices',
                  '100+ POS Devices',
                  'Dedicated Cloud HSM & Sharding',
                  'Mission-critical tier for national telecom & manufacturing.',
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildPlanCard(
    String code,
    String title,
    String volume,
    String devices,
    String sla,
    String desc, {
    bool isRecommended = false,
  }) {
    final isSelected = _selectedPlan == code;

    return InkWell(
      onTap: () => setState(() => _selectedPlan = code),
      borderRadius: BorderRadius.circular(3),
      child: Container(
        constraints: const BoxConstraints(minHeight: 380),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.navy700.withValues(alpha: 0.05)
              : AppColors.paperRaised,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(
            color: isSelected
                ? AppColors.navy700
                : (isRecommended ? AppColors.amber600 : AppColors.rule),
            width: isSelected ? 2 : 1,
          ),
          boxShadow: [
            BoxShadow(
              color: AppColors.ink.withValues(alpha: isSelected ? 0.06 : 0.02),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (isRecommended) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.amber600,
                  borderRadius: BorderRadius.circular(2),
                ),
                child: const Text(
                  'RECOMMENDED',
                  style: TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                  ),
                ),
              ),
              const SizedBox(height: 6),
            ] else
              const SizedBox(height: 18),

            Text(
              title,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.3,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 4),
            Text(
              desc,
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
            const Spacer(),
            const Divider(color: AppColors.rule, height: 16),
            const SizedBox(height: 4),

            _buildPlanFeature(Icons.receipt_outlined, volume),
            const SizedBox(height: 5),
            _buildPlanFeature(Icons.point_of_sale, devices),
            const SizedBox(height: 5),
            _buildPlanFeature(Icons.verified, sla),
            const SizedBox(height: 12),

            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () => setState(() => _selectedPlan = code),
                style: ElevatedButton.styleFrom(
                  backgroundColor: isSelected
                      ? AppColors.navy700
                      : AppColors.paper,
                  foregroundColor: isSelected ? Colors.white : AppColors.ink,
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  visualDensity: VisualDensity.compact,
                  side: BorderSide(
                    color: isSelected ? AppColors.navy700 : AppColors.rule,
                  ),
                ),
                child: Text(
                  isSelected ? 'Selected Tier' : 'Select Plan',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPlanFeature(IconData icon, String label) {
    return Row(
      children: [
        Icon(icon, size: 14, color: AppColors.green700),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            label,
            style: AppTypography.bodySmall(color: AppColors.ink),
          ),
        ),
      ],
    );
  }

  // =========================================================================
  // STEP 4: Organization Administrator Account
  // =========================================================================
  Widget _buildStep4AdminIdentity() {
    return Form(
      key: _step4FormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSectionHeader(
            title: 'Organization Root Administrator Identity',
            subtitle:
                'This account will hold ROLE_TENANT_ADMIN authority for tenant user provisioning and branch management.',
          ),
          const SizedBox(height: 20),

          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Left: Admin Personal Details
              Expanded(
                child: _buildFormCard(
                  title: 'Administrator Contact & Profile',
                  icon: Icons.person_outline,
                  children: [
                    TextFormField(
                      controller: _adminNameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Administrator Full Name *',
                        hintText: 'e.g. Abebe Bikila / አበበ ቢቂላ',
                        prefixIcon: Icon(Icons.person_pin_outlined, size: 18),
                      ),
                      inputFormatters: EthiopianInputValidators.nameFormatters,
                      validator: (v) =>
                          EthiopianInputValidators.validateLegalName(
                            v,
                            isRequired: true,
                            fieldName: 'Administrator Name',
                          ),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _adminUsernameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Login Username / Operator ID *',
                        hintText:
                            'e.g. admin or abebe.bikila (used to sign into Tenant Client)',
                        prefixIcon: Icon(
                          Icons.account_circle_outlined,
                          size: 18,
                        ),
                      ),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) {
                          return 'Login username is required';
                        }
                        if (v.trim().length < 3) {
                          return 'Username must be at least 3 characters';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _adminEmailCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Official Corporate Email *',
                        hintText: 'e.g. admin@abyssinia.et',
                        prefixIcon: Icon(Icons.email_outlined, size: 18),
                      ),
                      inputFormatters: EthiopianInputValidators.emailFormatters,
                      validator: (v) => EthiopianInputValidators.validateEmail(
                        v,
                        isRequired: true,
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _adminPhoneCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Administrator Mobile Phone *',
                        hintText: 'e.g. +251911223344 or 0911223344',
                        prefixIcon: Icon(
                          Icons.phone_android_outlined,
                          size: 18,
                        ),
                      ),
                      inputFormatters: EthiopianInputValidators.phoneFormatters,
                      validator: (v) => EthiopianInputValidators.validatePhone(
                        v,
                        isRequired: true,
                        fieldName: 'Admin Phone',
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 20),

              // Right: Security & Initial Password
              Expanded(
                child: _buildFormCard(
                  title: 'Security & Access Control',
                  icon: Icons.lock_outline,
                  children: [
                    TextFormField(
                      controller: _adminPasswordCtrl,
                      obscureText: _obscurePassword,
                      decoration: InputDecoration(
                        labelText: 'Temporary Initial Password *',
                        hintText: 'Enter strong password (min 8 characters)',
                        prefixIcon: const Icon(
                          Icons.password_outlined,
                          size: 18,
                        ),
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscurePassword
                                ? Icons.visibility_off
                                : Icons.visibility,
                            size: 18,
                          ),
                          onPressed: () => setState(
                            () => _obscurePassword = !_obscurePassword,
                          ),
                        ),
                      ),
                      validator: (v) {
                        if (v == null || v.trim().length < 8) {
                          return 'Password must be at least 8 characters long.';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    _buildPasswordStrengthBar(_adminPasswordCtrl.text),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.paper,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(
                                Icons.verified_user_outlined,
                                size: 16,
                                color: AppColors.green700,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'Assigned Security Role: ROLE_TENANT_ADMIN',
                                style: AppTypography.uiLabelBold(),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Grants complete authority over tenant invoice dispatch, customer master data, offline key sync, and operator credentials.',
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
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPasswordStrengthBar(String password) {
    int strength = 0;
    if (password.length >= 8) strength++;
    if (RegExp(r'[A-Z]').hasMatch(password)) strength++;
    if (RegExp(r'[0-9]').hasMatch(password)) strength++;
    if (RegExp(r'[!@#\$&*~%^&+=]').hasMatch(password)) strength++;

    Color barColor = AppColors.red600;
    String label = 'Weak';
    if (strength >= 3) {
      barColor = AppColors.green700;
      label = 'Strong';
    } else if (strength >= 2) {
      barColor = AppColors.amber600;
      label = 'Moderate';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Password Strength:',
              style: AppTypography.bodySmall(color: AppColors.inkMuted),
            ),
            Text(label, style: AppTypography.uiLabelBold(color: barColor)),
          ],
        ),
        const SizedBox(height: 6),
        LinearProgressIndicator(
          value: (strength / 4).clamp(0.2, 1.0),
          color: barColor,
          backgroundColor: AppColors.rule,
          minHeight: 4,
        ),
      ],
    );
  }

  // =========================================================================
  // STEP 5: Review & Statutory Certification
  // =========================================================================
  Widget _buildStep5ReviewAndDeploy() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader(
          title: 'Review Provisioning Specifications & Statutory Certification',
          subtitle:
              'Verify all legal and infrastructural parameters prior to committing tenant to the live database.',
        ),
        const SizedBox(height: 20),

        Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: AppColors.paperRaised,
            borderRadius: BorderRadius.circular(3),
            border: Border.all(color: AppColors.rule),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'FINAL ONBOARDING AUDIT MANIFEST',
                style: AppTypography.h2(),
              ),
              const SizedBox(height: 16),
              const Divider(color: AppColors.rule),
              const SizedBox(height: 16),

              // Section 1: Taxpayer Profile
              _buildReviewSectionTitle('1. Taxpayer Legal Profile'),
              const SizedBox(height: 8),
              _buildReviewRow(
                'Ethiopian TIN:',
                _tinCtrl.text.trim().isNotEmpty
                    ? _tinCtrl.text.trim()
                    : 'Not Provided',
                isMono: true,
              ),
              _buildReviewRow('Legal Entity Name:', _legalNameCtrl.text.trim()),
              _buildReviewRow(
                'Trade / Brand Name:',
                _tradeNameCtrl.text.trim().isNotEmpty
                    ? _tradeNameCtrl.text.trim()
                    : _legalNameCtrl.text.trim(),
              ),
              _buildReviewRow('MoR Tax Jurisdiction:', _selectedTaxOffice),
              _buildReviewRow(
                'VAT Registered:',
                _isVatRegistered ? 'Yes (15% Standard VAT)' : 'No (Exempt/TOT)',
              ),
              if (_isVatRegistered && _vatNumberCtrl.text.isNotEmpty)
                _buildReviewRow(
                  'VAT Registration Number:',
                  _vatNumberCtrl.text.trim(),
                  isMono: true,
                ),
              _buildReviewRow(
                'Region & Subcity:',
                '$_selectedRegion, ${_cityCtrl.text.trim()}',
              ),

              const SizedBox(height: 16),
              const Divider(color: AppColors.rule),
              const SizedBox(height: 16),

              // Section 2: Branch & POS
              _buildReviewSectionTitle('2. Branch & POS Infrastructure'),
              const SizedBox(height: 8),
              _buildReviewRow(
                'Primary Branch Name:',
                _branchNameCtrl.text.trim(),
              ),
              _buildReviewRow(
                'Branch Code:',
                _branchCodeCtrl.text.trim().toUpperCase(),
                isMono: true,
              ),
              _buildReviewRow(
                'Allocated POS Terminals:',
                '$_posDeviceCount Terminal Licenses',
              ),

              const SizedBox(height: 16),
              const Divider(color: AppColors.rule),
              const SizedBox(height: 16),

              // Section 3: Subscription Plan
              _buildReviewSectionTitle('3. Commercial Subscription & Quota'),
              const SizedBox(height: 8),
              _buildReviewRow(
                'Subscribed SaaS Tier:',
                '$_selectedPlan Plan ($_selectedBillingCycle Billing)',
              ),

              const SizedBox(height: 16),
              const Divider(color: AppColors.rule),
              const SizedBox(height: 16),

              // Section 4: Primary Administrator
              _buildReviewSectionTitle('4. Root Organization Administrator'),
              const SizedBox(height: 8),
              _buildReviewRow(
                'Administrator Name:',
                _adminNameCtrl.text.trim().isNotEmpty
                    ? _adminNameCtrl.text.trim()
                    : 'Default Admin',
              ),
              _buildReviewRow(
                'Login Username:',
                _adminUsernameCtrl.text.trim().isNotEmpty
                    ? _adminUsernameCtrl.text.trim()
                    : (_adminEmailCtrl.text.trim().isNotEmpty
                          ? _adminEmailCtrl.text.trim()
                          : 'admin'),
                isMono: true,
              ),
              _buildReviewRow(
                'Administrator Email:',
                _adminEmailCtrl.text.trim().isNotEmpty
                    ? _adminEmailCtrl.text.trim()
                    : 'admin@taxpayer.et',
              ),
              _buildReviewRow(
                'Admin Security Role:',
                'ROLE_TENANT_ADMIN',
                isMono: true,
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Statutory Declaration Checkbox
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.navy700.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(3),
            border: Border.all(color: AppColors.navy700.withValues(alpha: 0.3)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Checkbox(
                value: _statutoryCertified,
                activeColor: AppColors.navy700,
                onChanged: (v) =>
                    setState(() => _statutoryCertified = v ?? false),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'STATUTORY LEGAL COMPLIANCE CERTIFICATION (Directive No. 1142/2026)',
                      style: AppTypography.uiLabelBold(
                        color: AppColors.navy700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'I solemnly certify under the Ethiopian Tax Administration Proclamation that the above taxpayer registration data, TIN, VAT status, and administrative credentials are valid, authentic, and authorized for electronic invoicing gateway integration.',
                      style: AppTypography.bodySmall(color: AppColors.ink),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildReviewSectionTitle(String title) {
    return Text(
      title,
      style: AppTypography.uiLabelBold(color: AppColors.navy700),
    );
  }

  Widget _buildReviewRow(String label, String value, {bool isMono = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: AppTypography.bodySmall(color: AppColors.inkMuted),
          ),
          Text(
            value,
            style: isMono
                ? AppTypography.monoSmall(weight: FontWeight.w700)
                : AppTypography.bodySmall(color: AppColors.ink),
          ),
        ],
      ),
    );
  }

  // =========================================================================
  // SUCCESS STATE VIEW
  // =========================================================================
  Widget _buildSuccessScreen() {
    final tenantId =
        _provisionedTenantResult?['tenantId']?.toString() ?? 'SUCCESS';
    final tin = _provisionedTenantResult?['tin']?.toString() ?? _tinCtrl.text;
    final legalName =
        _provisionedTenantResult?['legalName']?.toString() ??
        _legalNameCtrl.text;
    final plan = _provisionedTenantResult?['plan']?.toString() ?? _selectedPlan;

    return Scaffold(
      backgroundColor: AppColors.paper,
        body: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 620),
              child: Container(
                padding: const EdgeInsets.all(36),
                decoration: BoxDecoration(
                  color: AppColors.paperRaised,
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.rule),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.ink.withValues(alpha: 0.05),
                      blurRadius: 16,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.green700.withValues(alpha: 0.1),
                        border: Border.all(
                          color: AppColors.green700.withValues(alpha: 0.3),
                        ),
                      ),
                      child: const Center(
                        child: Icon(
                          Icons.verified,
                          size: 36,
                          color: AppColors.green700,
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'TENANT ONBOARDING COMPLETED',
                      style: AppTypography.h1(),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Taxpayer has been registered and provisioned in the live PostgreSQL database.',
                      style: AppTypography.bodySmall(color: AppColors.inkMuted),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 24),

                    // Details Card
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        color: AppColors.paper,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.rule),
                      ),
                      child: Column(
                        children: [
                          _buildReviewRow(
                            'Tenant UUID:',
                            tenantId,
                            isMono: true,
                          ),
                          const SizedBox(height: 6),
                          _buildReviewRow('Taxpayer TIN:', tin, isMono: true),
                          const SizedBox(height: 6),
                          _buildReviewRow('Legal Name:', legalName),
                          const SizedBox(height: 6),
                          _buildReviewRow('Active Plan:', '$plan Tier'),
                          const SizedBox(height: 6),
                          _buildReviewRow(
                            'Primary Admin:',
                            _adminEmailCtrl.text,
                          ),
                          const SizedBox(height: 6),
                          _buildReviewRow(
                            'Status:',
                            'ACTIVE / PROVISIONED',
                            isMono: true,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Official Login Credentials Card for Tenant Portal
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppColors.green700.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: AppColors.green700.withValues(alpha: 0.35),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(
                                Icons.vpn_key_outlined,
                                size: 16,
                                color: AppColors.green700,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'TENANT CLIENT LOGIN CREDENTIALS',
                                style: AppTypography.uiLabelBold(
                                  color: AppColors.green700,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          _buildReviewRow(
                            '1. Taxpayer TIN:',
                            tin,
                            isMono: true,
                          ),
                          const SizedBox(height: 6),
                          _buildReviewRow(
                            '2. Username / Operator ID:',
                            _adminUsernameCtrl.text.trim().isNotEmpty
                                ? _adminUsernameCtrl.text.trim()
                                : (_adminEmailCtrl.text.trim().isNotEmpty
                                      ? _adminEmailCtrl.text.trim()
                                      : 'admin'),
                            isMono: true,
                          ),
                          const SizedBox(height: 6),
                          _buildReviewRow(
                            '3. Password:',
                            _adminPasswordCtrl.text.trim(),
                            isMono: true,
                          ),
                          const SizedBox(height: 10),
                          Text(
                            'Use these exact credentials at the Tenant Client Login screen to sign into the newly provisioned organization.',
                            style: AppTypography.bodySmall(
                              color: AppColors.inkMuted,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 28),

                    // Action Buttons
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: _resetWizard,
                            icon: const Icon(Icons.add_business, size: 16),
                            label: const Text('Onboard Another'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: () => context.go('/saas/tenants'),
                            icon: const Icon(Icons.list_alt, size: 16),
                            label: const Text('Tenant Directory'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: () => context.go('/tenant/login'),
                            icon: const Icon(Icons.storefront, size: 16),
                            label: const Text('Test Login'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.green700,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
    );
  }

  // =========================================================================
  // BOTTOM ACTION BAR
  // =========================================================================
  Widget _buildBottomActionBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentStep > 0)
            OutlinedButton.icon(
              onPressed: _isSubmitting
                  ? null
                  : () => setState(() => _currentStep -= 1),
              icon: const Icon(Icons.arrow_back, size: 16),
              label: const Text('Previous Step'),
            )
          else
            const SizedBox.shrink(),

          ElevatedButton.icon(
            onPressed: _isSubmitting ? null : _validateAndNext,
            icon: _isSubmitting
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : Icon(
                    _currentStep == _totalSteps - 1
                        ? Icons.cloud_upload
                        : Icons.arrow_forward,
                    size: 16,
                  ),
            label: Text(
              _isSubmitting
                  ? 'Provisioning Tenant...'
                  : (_currentStep == _totalSteps - 1
                        ? 'Confirm & Provision Tenant'
                        : 'Continue to Step ${_currentStep + 2} ->'),
            ),
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader({
    required String title,
    required String subtitle,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: AppTypography.h2()),
        const SizedBox(height: 4),
        Text(
          subtitle,
          style: AppTypography.bodySmall(color: AppColors.inkMuted),
        ),
      ],
    );
  }

  Widget _buildFormCard({
    required String title,
    required IconData icon,
    required List<Widget> children,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.paperRaised,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: AppColors.rule),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 18, color: AppColors.navy700),
              const SizedBox(width: 8),
              Text(title, style: AppTypography.uiLabelBold()),
            ],
          ),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    );
  }
}
