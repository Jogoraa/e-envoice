import 'dart:typed_data';

Future<void> saveOrLaunchPdf(Uint8List bytes, String filename) async {
  throw UnsupportedError('Platform not supported for file save');
}

void openHtmlInNewWindow(String htmlContent, String title) {
  throw UnsupportedError('Platform not supported for HTML window');
}
