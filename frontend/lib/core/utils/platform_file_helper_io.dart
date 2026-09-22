import 'dart:io';
import 'dart:typed_data';

Future<void> saveOrLaunchPdf(Uint8List bytes, String filename) async {
  final tempDir = Directory.systemTemp;
  final file = File('${tempDir.path}/$filename');
  await file.writeAsBytes(bytes);
  if (Platform.isWindows) {
    await Process.run('cmd', ['/c', 'start', '', file.path]);
  } else if (Platform.isMacOS) {
    await Process.run('open', [file.path]);
  } else if (Platform.isLinux) {
    await Process.run('xdg-open', [file.path]);
  }
}

void openHtmlInNewWindow(String htmlContent, String title) async {
  final tempDir = Directory.systemTemp;
  final file = File('${tempDir.path}/receipt_${DateTime.now().millisecondsSinceEpoch}.html');
  await file.writeAsString(htmlContent);
  if (Platform.isWindows) {
    await Process.run('cmd', ['/c', 'start', '', file.path]);
  } else if (Platform.isMacOS) {
    await Process.run('open', [file.path]);
  } else if (Platform.isLinux) {
    await Process.run('xdg-open', [file.path]);
  }
}
