import 'package:flutter/services.dart';

/// Authoritative Ethiopian Government & Ministry of Revenues (MoR)
/// Input Validation, Normalization, and Sanitization Engine.
class EthiopianInputValidators {
  // Regex Patterns
  static final RegExp _tinRegex = RegExp(r'^\d{10}$');
  static final RegExp _digitsOnlyRegex = RegExp(r'^\d+$');
  static final RegExp _vatRegex = RegExp(r'^[0-9A-Za-z]{6,32}$');
  static final RegExp _phoneEthiopianRegex = RegExp(
    r'^(?:\+251|0)(?:9|7)\d{8}$|^(?:\+251|0)[1-5]\d{7,8}$',
  );
  static final RegExp _emailRegex = RegExp(
    r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$',
  );
  static final RegExp _nameValidCharsRegex = RegExp(
    r"^[a-zA-Z0-9\u1200-\u137F\s.,&'()\/-]+$",
  );
  static final RegExp _lettersOrAmharicRegex = RegExp(
    r'[a-zA-Z\u1200-\u137F]',
  );

  /// Standard 12 Regional States + 2 Chartered Cities of Ethiopia
  static const List<String> ethiopianRegions = [
    'Addis Ababa',
    'Dire Dawa',
    'Oromia',
    'Amhara',
    'Tigray',
    'Somali',
    'Sidama',
    'Southern Ethiopia',
    'Central Ethiopia',
    'South West Ethiopia',
    'Afar',
    'Benishangul-Gumuz',
    'Gambella',
    'Harari',
  ];

  // =========================================================================
  // 1. Taxpayer Identification Number (TIN)
  // =========================================================================

  /// Enforces Ethiopian Ministry of Revenues 10-digit numeric TIN specification.
  static String? validateTin(
    String? value, {
    bool isRequired = true,
    String fieldName = 'TIN',
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return '$fieldName is mandatory (10 numeric digits required).';
      }
      return null;
    }

    final trimmed = value.trim();

    if (!_digitsOnlyRegex.hasMatch(trimmed)) {
      return '$fieldName must contain numbers only.';
    }

    if (trimmed.length != 10) {
      return '$fieldName must be exactly 10 digits (currently ${trimmed.length}).';
    }

    if (!_tinRegex.hasMatch(trimmed)) {
      return 'Invalid $fieldName format. Must be 10 numeric digits.';
    }

    return null;
  }

  /// Strips any non-digits while strictly preserving leading zeros.
  static String? sanitizeTin(String? value) {
    if (value == null) return null;
    final clean = value.replaceAll(RegExp(r'\D'), '').trim();
    return clean.isEmpty ? null : clean;
  }

  static List<TextInputFormatter> get tinFormatters => [
        FilteringTextInputFormatter.digitsOnly,
        LengthLimitingTextInputFormatter(10),
      ];

  // =========================================================================
  // 2. VAT Registration Number
  // =========================================================================

  /// Validates Ethiopian VAT Registration Number.
  static String? validateVatNumber(
    String? value, {
    bool isRequired = false,
    bool isVatRegistered = false,
  }) {
    final shouldEnforce = isRequired || isVatRegistered;
    if (value == null || value.trim().isEmpty) {
      if (shouldEnforce) {
        return 'VAT Number is required for VAT registered entities.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length < 6) {
      return 'VAT Number must be at least 6 characters.';
    }
    if (trimmed.length > 32) {
      return 'VAT Number cannot exceed 32 characters.';
    }
    if (!_vatRegex.hasMatch(trimmed)) {
      return 'VAT Number can only contain alphanumeric characters (no symbols).';
    }

    return null;
  }

  static String? sanitizeVatNumber(String? value) {
    if (value == null) return null;
    final trimmed = value.replaceAll(RegExp(r'[^0-9a-zA-Z]'), '').toUpperCase().trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  static List<TextInputFormatter> get vatFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r'[0-9a-zA-Z]')),
        LengthLimitingTextInputFormatter(32),
      ];

  // =========================================================================
  // 3. Ethiopian Phone Number
  // =========================================================================

  /// Validates Ethiopian Mobile (09.../07.../+2519.../+2517...) or Landline format.
  static String? validatePhone(
    String? value, {
    bool isRequired = false,
    String fieldName = 'Phone number',
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return '$fieldName is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim().replaceAll(RegExp(r'\s+'), '');

    if (!_phoneEthiopianRegex.hasMatch(trimmed)) {
      return 'Enter a valid Ethiopian phone (e.g., 0912345678, 0712345678, or +251912345678).';
    }

    return null;
  }

  /// Normalizes Ethiopian phone numbers (collapses spaces, preserves prefix).
  static String? sanitizePhone(String? value) {
    if (value == null) return null;
    final clean = value.replaceAll(RegExp(r'[^\d+]'), '').trim();
    return clean.isEmpty ? null : clean;
  }

  static List<TextInputFormatter> get phoneFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r'[\d+]')),
        LengthLimitingTextInputFormatter(13),
      ];

  // =========================================================================
  // 4. Email Address
  // =========================================================================

  static String? validateEmail(
    String? value, {
    bool isRequired = false,
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return 'Email address is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (!_emailRegex.hasMatch(trimmed)) {
      return 'Enter a valid email address (e.g., buyer@domain.et).';
    }

    return null;
  }

  static String? sanitizeEmail(String? value) {
    if (value == null) return null;
    final trimmed = value.trim().toLowerCase();
    return trimmed.isEmpty ? null : trimmed;
  }

  static List<TextInputFormatter> get emailFormatters => [
        FilteringTextInputFormatter.deny(RegExp(r'\s')),
        LengthLimitingTextInputFormatter(100),
      ];

  // =========================================================================
  // 5. Legal Name & Trade Name (Supports English & Amharic/Ethiopic)
  // =========================================================================

  static String? validateLegalName(
    String? value, {
    bool isRequired = true,
    String fieldName = 'Legal Name',
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return '$fieldName is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length < 2) {
      return '$fieldName must be at least 2 characters.';
    }
    if (trimmed.length > 150) {
      return '$fieldName cannot exceed 150 characters.';
    }

    if (!_nameValidCharsRegex.hasMatch(trimmed)) {
      return '$fieldName contains invalid special characters.';
    }

    if (!_lettersOrAmharicRegex.hasMatch(trimmed)) {
      return '$fieldName must contain alphabetic or Ethiopic characters.';
    }

    return null;
  }

  static String? validateTradeName(
    String? value, {
    bool isRequired = false,
  }) {
    return validateLegalName(
      value,
      isRequired: isRequired,
      fieldName: 'Trade Name / Commercial Brand',
    );
  }

  static String? sanitizeName(String? value) {
    if (value == null) return null;
    final cleaned = value.replaceAll(RegExp(r'\s+'), ' ').trim();
    return cleaned.isEmpty ? null : cleaned;
  }

  static List<TextInputFormatter> get nameFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r"[a-zA-Z0-9\u1200-\u137F\s.,&'()\/-]")),
        LengthLimitingTextInputFormatter(150),
      ];

  // =========================================================================
  // 6. Region, City, Zone, Woreda, Kebele, House Number
  // =========================================================================

  static String? validateRegion(
    String? value, {
    bool isRequired = false,
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return 'Region is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length > 64) {
      return 'Region name is too long.';
    }
    return null;
  }

  static String? validateCity(
    String? value, {
    bool isRequired = false,
    String fieldName = 'City / Subcity',
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return '$fieldName is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length > 64) {
      return '$fieldName cannot exceed 64 characters.';
    }
    return null;
  }

  static String? validateZone(String? value, {bool isRequired = false}) {
    return validateCity(value, isRequired: isRequired, fieldName: 'Zone / Subcity');
  }

  static String? validateWoreda(
    String? value, {
    bool isRequired = false,
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return 'Woreda is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length > 32) {
      return 'Woreda cannot exceed 32 characters.';
    }
    return null;
  }

  static String? validateKebele(
    String? value, {
    bool isRequired = false,
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return 'Kebele is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length > 32) {
      return 'Kebele cannot exceed 32 characters.';
    }
    return null;
  }

  static String? validateHouseNumber(
    String? value, {
    bool isRequired = false,
  }) {
    if (value == null || value.trim().isEmpty) {
      if (isRequired) {
        return 'House Number is mandatory.';
      }
      return null;
    }

    final trimmed = value.trim();
    if (trimmed.length > 32) {
      return 'House Number cannot exceed 32 characters.';
    }
    return null;
  }

  static String? sanitizeAddressField(String? value) {
    if (value == null) return null;
    final trimmed = value.replaceAll(RegExp(r'\s+'), ' ').trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  static List<TextInputFormatter> get cityFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r"[a-zA-Z0-9\u1200-\u137F\s.,\/-]")),
        LengthLimitingTextInputFormatter(64),
      ];

  static List<TextInputFormatter> get woredaFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r"[a-zA-Z0-9\u1200-\u137F\s\/-]")),
        LengthLimitingTextInputFormatter(32),
      ];

  static List<TextInputFormatter> get kebeleFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r"[a-zA-Z0-9\u1200-\u137F\s\/-]")),
        LengthLimitingTextInputFormatter(32),
      ];

  static List<TextInputFormatter> get houseNumberFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r"[a-zA-Z0-9\u1200-\u137F\s#\/-]")),
        LengthLimitingTextInputFormatter(32),
      ];

  // =========================================================================
  // 7. Numeric Invoicing Formatters & Validators (Quantity, Price, Discount)
  // =========================================================================

  static String? validateQuantity(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Quantity is required.';
    }
    final parsed = double.tryParse(value.trim());
    if (parsed == null || parsed <= 0) {
      return 'Quantity must be greater than 0.';
    }
    if (parsed > 1000000) {
      return 'Quantity exceeds maximum allowable limit.';
    }
    return null;
  }

  static String? validateUnitPrice(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Unit price is required.';
    }
    final parsed = double.tryParse(value.trim());
    if (parsed == null || parsed < 0) {
      return 'Unit price cannot be negative.';
    }
    if (parsed > 1000000000) {
      return 'Unit price exceeds maximum allowable limit.';
    }
    return null;
  }

  static String? validateDiscount(String? value, double preTaxTotal) {
    if (value == null || value.trim().isEmpty) return null;
    final parsed = double.tryParse(value.trim());
    if (parsed == null || parsed < 0) {
      return 'Discount cannot be negative.';
    }
    if (parsed > preTaxTotal) {
      return 'Discount cannot exceed line amount (ETB ${preTaxTotal.toStringAsFixed(2)}).';
    }
    return null;
  }

  static List<TextInputFormatter> get numericAmountFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,2}')),
      ];

  static List<TextInputFormatter> get numericQuantityFormatters => [
        FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,4}')),
      ];
}
