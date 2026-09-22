import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/utils/ethiopian_input_validators.dart';

void main() {
  group('Ethiopian Government & MoR Input Validators Suite', () {
    test('10-digit TIN validation enforces digits only, exact length 10, and leading zero preservation', () {
      // Valid TINs
      expect(EthiopianInputValidators.validateTin('0041746204'), isNull);
      expect(EthiopianInputValidators.validateTin('0012345678'), isNull);
      expect(EthiopianInputValidators.validateTin('9000000000'), isNull);

      // Sanitization
      expect(EthiopianInputValidators.sanitizeTin(' 0041746204 '), '0041746204');
      expect(EthiopianInputValidators.sanitizeTin('00-4174-6204'), '0041746204');

      // Invalid TINs
      expect(EthiopianInputValidators.validateTin('004174620'), isNotNull); // 9 digits
      expect(EthiopianInputValidators.validateTin('00417462041'), isNotNull); // 11 digits
      expect(EthiopianInputValidators.validateTin('004174620A'), isNotNull); // Letters
      expect(EthiopianInputValidators.validateTin(''), isNotNull); // Required empty
      expect(EthiopianInputValidators.validateTin('', isRequired: false), isNull); // Optional empty
    });

    test('VAT Registration Number validates alphanumeric string and enforces requirement if VAT registered', () {
      expect(EthiopianInputValidators.validateVatNumber('0041746204', isVatRegistered: true), isNull);
      expect(EthiopianInputValidators.validateVatNumber('43256663343256663322', isVatRegistered: true), isNull);
      expect(EthiopianInputValidators.validateVatNumber('ETVAT12345', isVatRegistered: true), isNull);

      // Optional when not VAT registered
      expect(EthiopianInputValidators.validateVatNumber(null, isVatRegistered: false), isNull);
      expect(EthiopianInputValidators.validateVatNumber('', isVatRegistered: false), isNull);

      // Required when VAT registered
      expect(EthiopianInputValidators.validateVatNumber(null, isVatRegistered: true), isNotNull);
      expect(EthiopianInputValidators.validateVatNumber('', isVatRegistered: true), isNotNull);

      // Invalid symbols
      expect(EthiopianInputValidators.validateVatNumber('VAT#12345', isVatRegistered: true), isNotNull);

      // Sanitization
      expect(EthiopianInputValidators.sanitizeVatNumber(' et-vat-12345 '), 'ETVAT12345');
    });

    test('Ethiopian Phone Number validation enforces 09/07/011/+251 formats and rejects foreign/invalid formats', () {
      // Valid mobile numbers
      expect(EthiopianInputValidators.validatePhone('0911223344'), isNull);
      expect(EthiopianInputValidators.validatePhone('0711223344'), isNull);
      expect(EthiopianInputValidators.validatePhone('+251911223344'), isNull);
      expect(EthiopianInputValidators.validatePhone('+251711223344'), isNull);

      // Valid landlines
      expect(EthiopianInputValidators.validatePhone('0115512233'), isNull);
      expect(EthiopianInputValidators.validatePhone('+251115512233'), isNull);

      // Invalid formats
      expect(EthiopianInputValidators.validatePhone('12345678'), isNotNull);
      expect(EthiopianInputValidators.validatePhone('0811223344'), isNotNull); // 08 invalid prefix
      expect(EthiopianInputValidators.validatePhone('09112233'), isNotNull); // Too short
      expect(EthiopianInputValidators.validatePhone('091122334455'), isNotNull); // Too long
      expect(EthiopianInputValidators.validatePhone('abcdefghij'), isNotNull);

      // Optional handling
      expect(EthiopianInputValidators.validatePhone(null, isRequired: false), isNull);
      expect(EthiopianInputValidators.validatePhone('', isRequired: false), isNull);
      expect(EthiopianInputValidators.validatePhone('', isRequired: true), isNotNull);

      // Sanitization
      expect(EthiopianInputValidators.sanitizePhone('+251 911 22 33 44'), '+251911223344');
      expect(EthiopianInputValidators.sanitizePhone('0911-22-33-44'), '0911223344');
    });

    test('Email validator enforces RFC standards and sanitization', () {
      expect(EthiopianInputValidators.validateEmail('finance@awash.com.et'), isNull);
      expect(EthiopianInputValidators.validateEmail('user.name+tag@domain.et'), isNull);

      expect(EthiopianInputValidators.validateEmail('invalid-email'), isNotNull);
      expect(EthiopianInputValidators.validateEmail('user@'), isNotNull);
      expect(EthiopianInputValidators.validateEmail('@domain.et'), isNotNull);

      expect(EthiopianInputValidators.sanitizeEmail(' BUYER@Domain.ET '), 'buyer@domain.et');
    });

    test('Legal and Trade Names support Latin, Amharic/Ethiopic, and business punctuation', () {
      // English Latin
      expect(EthiopianInputValidators.validateLegalName('Awash Import & Export PLC'), isNull);
      expect(EthiopianInputValidators.validateLegalName('Bole Printing Enterprise (Branch-1)'), isNull);

      // Amharic / Ge'ez
      expect(EthiopianInputValidators.validateLegalName('አዋሽ አስመጪ እና ላኪ ኃ/የተ/የግ/ማ'), isNull);
      expect(EthiopianInputValidators.validateLegalName('ዩቲ ሶሉሽንስ ኃ.የተ.የግ.ማ'), isNull);

      // Invalid: Pure symbols, numbers only, or too short
      expect(EthiopianInputValidators.validateLegalName('---'), isNotNull);
      expect(EthiopianInputValidators.validateLegalName('12345'), isNotNull);
      expect(EthiopianInputValidators.validateLegalName('A'), isNotNull); // min 2 chars

      // Sanitization
      expect(EthiopianInputValidators.sanitizeName('  Awash   Export   PLC  '), 'Awash Export PLC');
    });

    test('Address fields (Region, City, Woreda, Kebele, House No) validate correctly', () {
      expect(EthiopianInputValidators.validateRegion('Addis Ababa'), isNull);
      expect(EthiopianInputValidators.validateCity('Kirkos Subcity'), isNull);
      expect(EthiopianInputValidators.validateWoreda('Woreda 03'), isNull);
      expect(EthiopianInputValidators.validateKebele('Kebele 08/09'), isNull);
      expect(EthiopianInputValidators.validateHouseNumber('House #412/B'), isNull);

      expect(EthiopianInputValidators.sanitizeAddressField('  Woreda   04  '), 'Woreda 04');
    });

    test('Numeric Invoicing formatters and validations work as expected', () {
      expect(EthiopianInputValidators.validateQuantity('10'), isNull);
      expect(EthiopianInputValidators.validateQuantity('1.5'), isNull);
      expect(EthiopianInputValidators.validateQuantity('0'), isNotNull);
      expect(EthiopianInputValidators.validateQuantity('-5'), isNotNull);
      expect(EthiopianInputValidators.validateQuantity('abc'), isNotNull);

      expect(EthiopianInputValidators.validateUnitPrice('150.00'), isNull);
      expect(EthiopianInputValidators.validateUnitPrice('0.00'), isNull);
      expect(EthiopianInputValidators.validateUnitPrice('-10'), isNotNull);

      expect(EthiopianInputValidators.validateDiscount('50.00', 100.00), isNull);
      expect(EthiopianInputValidators.validateDiscount('150.00', 100.00), isNotNull); // Exceeds line total
    });
  });
}
