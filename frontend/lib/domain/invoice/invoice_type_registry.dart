/// Ethiopian Ministry of Revenue Schema-Driven Invoice Type Registry.
/// Dynamically governs validation rules, mandatory buyer fields, and tax policies per invoice type.
library;

enum InvoiceType {
  b2c('B2C', 'Standard Retail Invoice (B2C)'),
  b2b('B2B', 'Commercial Tax Invoice (B2B)'),
  export('EXPORT', 'Export Invoice');

  final String code;
  final String label;

  const InvoiceType(this.code, this.label);

  static InvoiceType fromCode(String code) {
    return InvoiceType.values.firstWhere(
      (t) => t.code.toUpperCase() == code.toUpperCase(),
      orElse: () => InvoiceType.b2c,
    );
  }
}

class InvoiceTypeDefinition {
  final InvoiceType type;
  final String title;
  final String description;
  final String badgeText;
  final bool isBuyerTinRequired;
  final bool isBuyerNameRequired;
  final bool isBuyerAddressRequired;
  final bool isExport;
  final List<String> supportedCurrencies;
  final String defaultTaxCode;

  const InvoiceTypeDefinition({
    required this.type,
    required this.title,
    required this.description,
    required this.badgeText,
    required this.isBuyerTinRequired,
    required this.isBuyerNameRequired,
    required this.isBuyerAddressRequired,
    this.isExport = false,
    this.supportedCurrencies = const ['ETB'],
    this.defaultTaxCode = 'VAT15',
  });
}

class InvoiceTypeRegistry {
  static const Map<InvoiceType, InvoiceTypeDefinition> _definitions = {
    InvoiceType.b2c: InvoiceTypeDefinition(
      type: InvoiceType.b2c,
      title: 'Standard Retail (B2C)',
      description: 'For consumer sales. Buyer TIN is optional; standard 15% VAT or TOT applies.',
      badgeText: 'B2C',
      isBuyerTinRequired: false,
      isBuyerNameRequired: false,
      isBuyerAddressRequired: false,
      isExport: false,
      supportedCurrencies: ['ETB'],
      defaultTaxCode: 'VAT15',
    ),
    InvoiceType.b2b: InvoiceTypeDefinition(
      type: InvoiceType.b2b,
      title: 'Commercial Tax Invoice (B2B)',
      description: 'For business-to-business transactions. 10-digit Buyer TIN and legal business name are strictly mandatory.',
      badgeText: 'B2B Mandatory TIN',
      isBuyerTinRequired: true,
      isBuyerNameRequired: true,
      isBuyerAddressRequired: true,
      isExport: false,
      supportedCurrencies: ['ETB'],
      defaultTaxCode: 'VAT15',
    ),
    InvoiceType.export: InvoiceTypeDefinition(
      type: InvoiceType.export,
      title: 'Export Invoice',
      description: 'For cross-border commercial exports. Zero-rated VAT (0%), multi-currency support, foreign buyer destination.',
      badgeText: 'Zero-Rated Export',
      isBuyerTinRequired: false,
      isBuyerNameRequired: true,
      isBuyerAddressRequired: true,
      isExport: true,
      supportedCurrencies: ['USD', 'EUR', 'GBP', 'ETB', 'AED', 'CNY'],
      defaultTaxCode: 'ZERO_RATED',
    ),
  };

  static InvoiceTypeDefinition getDefinition(InvoiceType type) {
    return _definitions[type] ?? _definitions[InvoiceType.b2c]!;
  }

  static List<InvoiceTypeDefinition> getAll() => _definitions.values.toList();
}
