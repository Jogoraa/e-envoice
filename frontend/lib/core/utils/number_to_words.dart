/// Deterministic English Birr number-to-words converter for invoice presentation.
class NumberToWords {
  static const List<String> _units = [
    '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine',
    'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
    'Seventeen', 'Eighteen', 'Nineteen'
  ];

  static const List<String> _tens = [
    '', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'
  ];

  static String convert(double amount) {
    if (amount.isNaN || amount.isInfinite) return 'Zero Birr and 00/100';

    final rounded = (amount * 100).round();
    final whole = (rounded ~/ 100).abs();
    final cents = (rounded % 100).abs();

    final wholeStr = whole == 0 ? 'Zero' : _convertWhole(whole);
    final centsStr = cents.toString().padLeft(2, '0');

    return '$wholeStr Birr and $centsStr/100';
  }

  static String _convertWhole(int n) {
    if (n == 0) return '';
    if (n < 20) return _units[n];
    if (n < 100) {
      final rem = n % 10;
      return rem == 0 ? _tens[n ~/ 10] : '${_tens[n ~/ 10]} ${_units[rem]}';
    }
    if (n < 1000) {
      final rem = n % 100;
      return rem == 0
          ? '${_units[n ~/ 100]} Hundred'
          : '${_units[n ~/ 100]} Hundred ${_convertWhole(rem)}';
    }
    if (n < 1000000) {
      final rem = n % 1000;
      return rem == 0
          ? '${_convertWhole(n ~/ 1000)} Thousand'
          : '${_convertWhole(n ~/ 1000)} Thousand ${_convertWhole(rem)}';
    }
    if (n < 1000000000) {
      final rem = n % 1000000;
      return rem == 0
          ? '${_convertWhole(n ~/ 1000000)} Million'
          : '${_convertWhole(n ~/ 1000000)} Million ${_convertWhole(rem)}';
    }
    final rem = n % 1000000000;
    return rem == 0
        ? '${_convertWhole(n ~/ 1000000000)} Billion'
        : '${_convertWhole(n ~/ 1000000000)} Billion ${_convertWhole(rem)}';
  }
}
