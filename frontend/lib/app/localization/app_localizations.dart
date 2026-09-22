import 'package:flutter/material.dart';

class AppLocalizations {
  final Locale locale;

  AppLocalizations(this.locale);

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations) ??
        AppLocalizations(const Locale('en'));
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  static final Map<String, Map<String, String>> _localizedValues = {
    'en': {
      'app_title': 'UT Electronic Invoicing',
      'tagline': 'A UT Solutions platform',
      'dashboard': 'Dashboard',
      'invoices': 'Invoices',
      'new_invoice': 'New invoice',
      'submit_invoice': 'Submit invoice',
      'save_draft': 'Save draft',
      'offline_queue': 'Offline queue',
      'cancellations': 'Cancellations',
      'adjustments': 'Adjustments',
      'receipts': 'Receipts',
      'audit_trail': 'Audit trail',
      'reports': 'Reports',
      'settings': 'Settings',
      'tenant': 'Tenant',
      'branch': 'Branch',
      'operator': 'Operator',
      'online': 'Online',
      'offline': 'Offline',
      'synced': 'Synced',
      'syncing': 'Syncing',
      'offline_draft': 'Offline draft',
      'sync_error': 'Sync error',
      'tin': 'Taxpayer TIN',
      'buyer_name': 'Buyer legal name',
      'item_description': 'Product description',
      'quantity': 'Qty',
      'unit_price': 'Unit price',
      'discount': 'Discount',
      'tax_rate': 'Tax code',
      'total': 'Total',
      'subtotal': 'Pre-tax subtotal',
      'vat': 'VAT (15%)',
      'grand_total': 'Grand total',
      'irn': 'Invoice Reference Number (IRN)',
      'rrn': 'Registration Reference Number (RRN)',
      'document_number': 'Document number',
      'date': 'Invoice date',
      'status': 'Status',
      'actions': 'Actions',
      'view_receipt': 'View receipt',
      'reprint_duplicate': 'Reprint duplicate',
      'tax_estimate_disclaimer': 'Tax values shown are client estimates. Authoritative VAT is computed by the Ministry-certified server upon registration.',
      'offline_continuity_notice': 'Offline Business Continuity Mode (Directive No. 1142/2026 Art. 4(4)). Stored in encrypted local storage. 72-hour sync required upon connection.',
      'sync_now': 'Sync now',
      'retry': 'Retry',
      'search': 'Search by IRN, Document No, or Buyer...',
      'logout': 'Sign out',
    },
    'am': {
      'app_title': 'ዩቲ ኤሌክትሮኒክ ደረሰኝ',
      'tagline': 'የዩቲ ሶሉሽንስ ፕላትፎርም',
      'dashboard': 'ዳሽቦርድ',
      'invoices': 'ደረሰኞች',
      'new_invoice': 'አዲስ ደረሰኝ',
      'submit_invoice': 'ደረሰኝ መዝግብ',
      'save_draft': 'ረቂቅ አስቀምጥ',
      'offline_queue': 'የመስመር ውጪ ወረፋ',
      'cancellations': 'ስረዛዎች',
      'adjustments': 'የታክስ ማስተካከያዎች',
      'receipts': 'ደረሰኞችና ክፍያዎች',
      'audit_trail': 'የኦዲት መዝገብ',
      'reports': 'ሪፖርቶች',
      'settings': 'ቅንብሮች',
      'tenant': 'ግብር ከፋይ ድርጅት',
      'branch': 'ቅርንጫፍ',
      'operator': 'ተጠቃሚ (ካሸር)',
      'online': 'መስመር ላይ (Online)',
      'offline': 'ከመስመር ውጭ (Offline)',
      'synced': 'ተመሳስሏል',
      'syncing': 'በማመሳሰል ላይ',
      'offline_draft': 'የመስመር ውጪ ረቂቅ',
      'sync_error': 'የማመሳሰል ስህተት',
      'tin': 'የግብር ከፋይ መለያ ቁጥር (TIN)',
      'buyer_name': 'የገዢው ህጋዊ ስም',
      'item_description': 'የእቃው / አገልግሎቱ ዝርዝር',
      'quantity': 'ብዛት',
      'unit_price': 'ነጠላ ዋጋ',
      'discount': 'ቅናሽ',
      'tax_rate': 'የታክስ አይነት',
      'total': 'ድምር',
      'subtotal': 'ከታክስ በፊት ድምር',
      'vat': 'ተጨማሪ እሴት ታክስ (ተእታ 15%)',
      'grand_total': 'ጠቅላላ ድምር',
      'irn': 'የደረሰኝ ማመሳከሪያ ቁጥር (IRN)',
      'rrn': 'የምዝገባ ማመሳከሪያ ቁጥር (RRN)',
      'document_number': 'የሰነድ ቁጥር',
      'date': 'የደረሰኝ ቀን',
      'status': 'ሁኔታ',
      'actions': 'ተግባራት',
      'view_receipt': 'ደረሰኝ እይ',
      'reprint_duplicate': 'እንደገና አትም (ግልባጭ)',
      'tax_estimate_disclaimer': 'የሚታዩት የታክስ መጠኖች የደንበኛ ግምቶች ናቸው። ትክክለኛው ተእታ በማዕከላዊ አገልጋዩ ሲመዘገብ ይሰላል።',
      'offline_continuity_notice': 'የመስመር ውጪ ቀጣይነት ስርዓት (መመሪያ ቁጥር 1142/2018 ዓ.ም አንቀጽ 4(4))። በ72 ሰዓታት ውስጥ መመሳሰል አለበት።',
      'sync_now': 'አሁን አመሳስል',
      'retry': 'እንደገና ሞክር',
      'search': 'በIRN፣ ሰነድ ቁጥር ወይም ገዢ ፈልግ...',
      'logout': 'ውጣ',
    },
  };

  String get(String key) {
    return _localizedValues[locale.languageCode]?[key] ??
        _localizedValues['en']?[key] ??
        key;
  }
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => ['en', 'am'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async {
    return AppLocalizations(locale);
  }

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}
