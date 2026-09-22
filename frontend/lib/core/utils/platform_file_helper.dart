import 'dart:typed_data';
import 'platform_file_helper_stub.dart'
    if (dart.library.html) 'platform_file_helper_web.dart'
    if (dart.library.io) 'platform_file_helper_io.dart' as impl;

class PlatformFileHelper {
  static Future<void> saveOrLaunchPdf(Uint8List bytes, String filename) {
    return impl.saveOrLaunchPdf(bytes, filename);
  }

  static void openHtmlReceipt(String htmlContent, {String title = 'Official MoR Tax Invoice'}) {
    impl.openHtmlInNewWindow(htmlContent, title);
  }
}
