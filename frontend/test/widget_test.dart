import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/apps/tenant_client/tenant_app.dart';

void main() {
  testWidgets('UtTenantClientApp bootstrap and workspace selection smoke test', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      const ProviderScope(
        child: UtTenantClientApp(),
      ),
    );
    await tester.pumpAndSettle();

    // Verify initial route renders the Workspace Selection screen
    expect(find.text('Choose Application Workspace'), findsOneWidget);
    expect(find.text('Tenant Client'), findsOneWidget);
    expect(find.text('SaaS Management'), findsOneWidget);
    expect(find.text('Master Platform Admin'), findsOneWidget);

    // Tap "Launch Tenant Client" to navigate to Tenant Login
    final tenantBtn = find.text('Launch Tenant Client');
    expect(tenantBtn, findsOneWidget);
    await tester.tap(tenantBtn);
    await tester.pumpAndSettle();

    // Verify Tenant Login screen is rendered
    expect(find.text('Operator Sign In'), findsOneWidget);
    expect(find.text('Taxpayer TIN'), findsOneWidget);
  });
}
