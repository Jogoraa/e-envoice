import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'apps/saas_management/saas_app.dart';

/// Entry point for Application B — UT Invoice SaaS Management (Commercial SaaS Operations)
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: UtSaasManagementApp()));
}
