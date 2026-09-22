import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'apps/tenant_client/tenant_app.dart';

/// Entry point for Application A — UT Invoice Client (Taxpayer Operations)
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: UtTenantClientApp()));
}
