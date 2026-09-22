import 'package:flutter/services.dart';

/// Ethiopian Taxpayer Identification Number (TIN) validation and input formatting utilities.
/// Enforces Ethiopian Ministry of Revenue 10-digit numeric TIN specification.
class TinValidator {
  static final RegExp _tinRegex = RegExp(r'^\d{10}$');
  static final RegExp _digitsOnlyRegex = RegExp(r'^\d+$');

  /// Validates whether [tin] is a valid 10-digit Ethiopian TIN.
  /// If [isRequired] is false and [tin] is null or empty, returns null (valid).
  static String? validate(String? tin, {bool isRequired = true, String fieldName = 'TIN'}) {
    if (tin == null || tin.trim().isEmpty) {
      if (isRequired) {
        return '$fieldName is mandatory (10 numeric digits required).';
      }
      return null;
    }

    final trimmed = tin.trim();

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

  /// Normalizes a TIN string by stripping leading/trailing whitespace.
  /// Preserves all leading zeros.
  static String? normalize(String? tin) {
    if (tin == null) return null;
    final trimmed = tin.trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  /// Returns true if [tin] is a valid 10-digit Ethiopian TIN.
  static bool isValid(String? tin) {
    if (tin == null) return false;
    return _tinRegex.hasMatch(tin.trim());
  }

  /// Formats TIN into standard readable representation: "00-4174-6204" (optional display helper).
  static String formatDisplay(String tin) {
    final clean = tin.replaceAll(RegExp(r'\D'), '');
    if (clean.length == 10) {
      return '${clean.substring(0, 2)}-${clean.substring(2, 6)}-${clean.substring(6, 10)}';
    }
    return tin;
  }

  /// Input formatters to attach to TextEditingController / TextFormField for TIN inputs.
  static List<TextInputFormatter> get inputFormatters => [
        FilteringTextInputFormatter.digitsOnly,
        LengthLimitingTextInputFormatter(10),
      ];
}
