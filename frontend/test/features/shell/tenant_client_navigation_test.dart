import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ut_einvoice_client/app/localization/app_localizations.dart';
import 'package:ut_einvoice_client/features/shell/presentation/enterprise_shell.dart';
import 'package:ut_einvoice_client/domain/auth/models/auth_session.dart';
import 'package:ut_einvoice_client/domain/tenant/models/tenant_context.dart';
import 'package:ut_einvoice_client/core/authentication/multi_gateway_session.dart';

void main() {
  group('Tenant Client Enterprise Information Architecture & Navigation', () {
    testWidgets('Renders all statutory navigation items in tenant sidebar', (tester) async {
      tester.view.physicalSize = const Size(1440, 1800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      const sampleTenant = TenantInfo(
        id: 'T-01',
        organizationId: 'ORG-01',
        name: 'Alpha Trading PLC',
        tradeName: 'Alpha Store',
        tin: '0012345678',
        status: 'ACTIVE',
      );

      const sampleBranch = BranchInfo(
        id: 'B-01',
        tenantId: 'T-01',
        name: 'Main Branch',
        code: 'B001',
        isHeadOffice: true,
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authSessionProvider.overrideWith(
              (ref) => AuthSessionNotifier(
                null,
                AuthSession(
                  userId: 'USR-01',
                  username: 'tenant.admin',
                  tenantId: 'T-01',
                  roles: const {'ROLE_TENANT_ADMIN'},
                ),
              ),
            ),
            tenantContextProvider.overrideWith(
              (ref) => TenantContextNotifier(
                null,
                const TenantContextState(
                  activeTenant: sampleTenant,
                  activeBranch: sampleBranch,
                  authorizedTenants: [sampleTenant],
                  authorizedBranches: [sampleBranch],
                ),
              ),
            ),
          ],
          child: const MaterialApp(
            localizationsDelegates: [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: [Locale('en', '')],
            home: EnterpriseShell(
              child: Scaffold(body: Text('Tenant Content Pane')),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Top branding & taxpayer context
      expect(find.text('UT INVOICE'), findsOneWidget);
      expect(find.text('Alpha Trading PLC [TIN: 0012345678]'), findsOneWidget);

      // Core statutory documents & features
      expect(find.text('Dashboard'), findsOneWidget);
      expect(find.text('Invoices'), findsOneWidget);
      expect(find.text('Tax Adjustments'), findsOneWidget);
      expect(find.text('Product Catalog'), findsOneWidget);
      expect(find.text('Service Catalog'), findsOneWidget);
      expect(find.text('Item Categories'), findsOneWidget);
      expect(find.text('Branch Inventory'), findsOneWidget);
      expect(find.text('Customer Registry'), findsOneWidget);
      expect(find.text('Cash Receipts'), findsOneWidget);
      expect(find.text('Purchase Vouchers'), findsOneWidget);
      expect(find.text('Withholding Receipts'), findsOneWidget);
      expect(find.text('Credit Settlements'), findsOneWidget);
      expect(find.text('Cancellations'), findsOneWidget);
      expect(find.text('Offline Operations'), findsOneWidget);
      expect(find.text('Manual Fallback'), findsOneWidget);
      expect(find.text('mPOS & Geofence'), findsOneWidget);
      expect(find.text('MoR Credentials'), findsOneWidget);
      expect(find.text('Signature Health'), findsOneWidget);
      expect(find.text('Art. 20 Summaries'), findsOneWidget);
      expect(find.text('Retention Schedule'), findsOneWidget);
      expect(find.text('Data Portability / Exit'), findsOneWidget);
      expect(find.text('Offline queue'), findsOneWidget);
      expect(find.text('Government EIRS'), findsOneWidget);
      expect(find.text('Fiscal Reports'), findsOneWidget);
      expect(find.text('Audit trail'), findsOneWidget);
      expect(find.text('Settings'), findsOneWidget);
    });

    testWidgets('Toggles tenant sidebar collapse and expand', (tester) async {
      tester.view.physicalSize = const Size(1440, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            localizationsDelegates: [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: [Locale('en', '')],
            home: EnterpriseShell(
              child: Scaffold(body: Text('Tenant Content Pane')),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Collapse sidebar'), findsOneWidget);
      await tester.tap(find.text('Collapse sidebar'));
      await tester.pumpAndSettle();

      expect(find.text('Collapse sidebar'), findsNothing);
    });
  });
}
