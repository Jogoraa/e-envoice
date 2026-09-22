import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../app/theme/app_theme.dart';
import 'master_admin_router.dart';

class UtMasterAdminApp extends ConsumerWidget {
  const UtMasterAdminApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(masterAdminRouterProvider);

    return MaterialApp.router(
      title: 'UT Invoice — Master Admin Platform Operations',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.light,
      routerConfig: router,
    );
  }
}
