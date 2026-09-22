import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'apps/master_admin/master_admin_app.dart';

/// Entry point for Application C — UT Invoice Master Admin (Internal Platform Operations)
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: UtMasterAdminApp()));
}
