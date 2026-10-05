import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ut_einvoice_client/features/saas_management/presentation/saas_management_shell.dart';
import 'package:ut_einvoice_client/core/authentication/multi_gateway_session.dart';

void main() {
  group('SaaS Management Enterprise Information Architecture & Navigation', () {
    testWidgets('Renders all SaaS commercial navigation items in sidebar', (tester) async {
      tester.view.physicalSize = const Size(1440, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            saasSessionProvider.overrideWith(
              (ref) => SaasManagementSessionNotifier(
                null,
                const SaasManagementSession(
                  operatorId: 'SAAS-01',
                  username: 'saas.commercial',
                  email: 'operations@utsolutionsplc.com',
                  roles: {'ROLE_SAAS_ADMIN'},
                ),
              ),
            ),
          ],
          child: const MaterialApp(
            home: SaasManagementShell(
              child: Scaffold(body: Text('SaaS Content Pane')),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Top branding & role pill
      expect(find.text('UT INVOICE'), findsOneWidget);
      expect(find.text('COMMERCIAL SaaS PORTAL'), findsOneWidget);
      expect(find.text('Sign Out'), findsAtLeastNWidgets(1));

      // Sidebar navigation items
      expect(find.text('SaaS Dashboard'), findsOneWidget);
      expect(find.text('Tenant Onboarding'), findsOneWidget);
      expect(find.text('Tenant Lifecycle'), findsOneWidget);
      expect(find.text('Plans & Subscriptions'), findsOneWidget);
      expect(find.text('Usage & Quotas'), findsOneWidget);
      expect(find.text('MoR Lifecycle Notices'), findsOneWidget);
      expect(find.text('Marketplace Merchants'), findsOneWidget);
      expect(find.text('Tenant Exit Oversight'), findsOneWidget);
      expect(find.text('Provider Tier & Bond'), findsOneWidget);
      expect(find.text('Report Templates'), findsOneWidget);
      expect(find.text('Support & Diagnostics'), findsOneWidget);
    });

    testWidgets('Toggles sidebar collapse and expand', (tester) async {
      tester.view.physicalSize = const Size(1440, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: SaasManagementShell(
              child: Scaffold(body: Text('SaaS Content Pane')),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Collapse sidebar'), findsOneWidget);
      await tester.tap(find.text('Collapse sidebar'));
      await tester.pumpAndSettle();

      // Sidebar should be collapsed
      expect(find.text('Collapse sidebar'), findsNothing);
    });
  });
}
