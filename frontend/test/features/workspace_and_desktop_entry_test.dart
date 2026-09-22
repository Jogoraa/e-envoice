import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/apps/tenant_client/tenant_app.dart';
import 'package:ut_einvoice_client/core/authentication/multi_gateway_session.dart';
import 'package:ut_einvoice_client/core/connectivity/connectivity_service.dart';
import 'package:ut_einvoice_client/core/di/providers.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/data/local/database/app_database.dart';
import 'package:ut_einvoice_client/data/repositories/invoice_repository_impl.dart';
import 'package:ut_einvoice_client/domain/auth/models/auth_session.dart';
import 'package:ut_einvoice_client/domain/invoice/models/invoice_models.dart';
import 'package:ut_einvoice_client/domain/tenant/models/tenant_context.dart';

class _MockDelegatedSessionNotifier extends DelegatedTenantSessionNotifier {
  _MockDelegatedSessionNotifier(DelegatedTenantSession session) {
    state = session;
  }
}

class _MockConnectivityService extends ConnectivityService {
  _MockConnectivityService() : super(probeDio: Dio(), autoStart: false);

  @override
  ConnectionStatus get currentStatus => ConnectionStatus.serverAvailable;

  @override
  Stream<ConnectionStatus> get statusStream => Stream.value(ConnectionStatus.serverAvailable);
}

class _FastInvoiceRepository extends InvoiceRepositoryImpl {
  _FastInvoiceRepository(ConnectivityService conn)
      : super(
          apiClient: ApiClient(baseUrl: 'http://localhost:8080', secureStorage: SecureStorageService()),
          db: AppDatabase(),
          connectivity: conn,
        );

  @override
  Future<List<InvoiceModel>> listInvoices({
    required String tenantId,
    String? branchId,
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    return const [];
  }

  Future<List<InvoiceModel>> listRecentInvoices({
    required String tenantId,
    String? branchId,
    int limit = 10,
  }) async {
    return const [];
  }

  @override
  Future<InvoiceSummary> getInvoiceSummary({
    required String tenantId,
    String? branchId,
  }) async {
    return const InvoiceSummary();
  }
}

void main() {
  group('Desktop Workspace Entry & Authentication Routing Suite', () {
    testWidgets('Scenario A: Fresh Desktop Launch -> Workspace Selection -> Tenant Login', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        const ProviderScope(
          child: UtTenantClientApp(),
        ),
      );
      await tester.pumpAndSettle();

      // 1. Verify Workspace Chooser is rendered
      expect(find.text('Choose Application Workspace'), findsOneWidget);
      expect(find.text('Tenant Client'), findsOneWidget);
      expect(find.text('SaaS Management'), findsOneWidget);
      expect(find.text('Master Platform Admin'), findsOneWidget);

      // 2. Select Tenant Client
      await tester.tap(find.text('Launch Tenant Client'));
      await tester.pumpAndSettle();

      // 3. Confirm Tenant Login UI is displayed
      expect(find.text('Operator Sign In'), findsOneWidget);
      expect(find.text('Taxpayer TIN'), findsOneWidget);
      expect(find.text('Username / Operator ID'), findsOneWidget);
      expect(find.text('Password'), findsOneWidget);
    });

    testWidgets('Scenario B: Fresh Desktop Launch -> Workspace Selection -> Master Login', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        const ProviderScope(
          child: UtTenantClientApp(),
        ),
      );
      await tester.pumpAndSettle();

      // 1. Verify Workspace Chooser
      expect(find.text('Choose Application Workspace'), findsOneWidget);

      // 2. Select SaaS Portal
      await tester.tap(find.text('Launch SaaS Portal'));
      await tester.pumpAndSettle();

      // 3. Confirm Master Login UI is displayed
      expect(find.text('SaaS Operations Login'), findsOneWidget);
      expect(find.text('Operator Email'), findsOneWidget);
      expect(find.text('Password'), findsOneWidget);
      expect(find.text('Access SaaS Portal'), findsOneWidget);
    });

    testWidgets('Scenario C: Switching from Tenant Login to Master Login and back to Workspaces', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        const ProviderScope(
          child: UtTenantClientApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Open Tenant Login
      await tester.tap(find.text('Launch Tenant Client'));
      await tester.pumpAndSettle();
      expect(find.text('Operator Sign In'), findsOneWidget);

      // Tap Master Portal switcher
      await tester.tap(find.text('Master Portal'));
      await tester.pumpAndSettle();
      expect(find.text('SaaS Operations Login'), findsOneWidget);

      // Tap Tenant Client switcher
      await tester.tap(find.text('Tenant Client'));
      await tester.pumpAndSettle();
      expect(find.text('Operator Sign In'), findsOneWidget);

      // Tap Workspaces back button
      await tester.tap(find.text('Workspaces'));
      await tester.pumpAndSettle();
      expect(find.text('Choose Application Workspace'), findsOneWidget);
    });

    testWidgets('Scenario D: Startup with Existing Active Tenant Session restores Tenant Dashboard', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final mockConn = _MockConnectivityService();
      addTearDown(mockConn.dispose);

      final branch = const BranchInfo(
        id: '00000000-0000-0000-0000-000000000010',
        tenantId: '00000000-0000-0000-0000-000000000001',
        name: 'Head Office',
        code: 'HO-001',
        isHeadOffice: true,
      );

      final tenant = const TenantInfo(
        id: '00000000-0000-0000-0000-000000000001',
        organizationId: 'ORG-01',
        name: 'Abyssinia Trading PLC',
        tradeName: 'Abyssinia',
        tin: '0011223344',
        status: 'ACTIVE',
      );

      final container = ProviderContainer(
        overrides: [
          connectivityServiceProvider.overrideWithValue(mockConn),
          connectionStatusProvider.overrideWith((ref) => Stream.value(ConnectionStatus.serverAvailable)),
          invoiceRepositoryProvider.overrideWith((ref) => _FastInvoiceRepository(mockConn)),
          authSessionProvider.overrideWith((ref) => AuthSessionNotifier(
                null,
                const AuthSession(
                  userId: 'user-001',
                  username: 'Abebe Cashier',
                  tenantId: '00000000-0000-0000-0000-000000000001',
                  roles: {'ROLE_TENANT_ADMIN'},
                  scopes: {'invoice:create', 'invoice:read'},
                ),
              )),
          tenantContextProvider.overrideWith((ref) {
            final notifier = TenantContextNotifier();
            notifier.setAuthorizedContext(
              tenants: [tenant],
              activeTenant: tenant,
              branches: [branch],
              activeBranch: branch,
            );
            return notifier;
          }),
        ],
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const UtTenantClientApp(),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Must restore straight into Tenant Dashboard without asking for login
      expect(find.text('Choose Application Workspace'), findsNothing);
      expect(find.text('Operator Sign In'), findsNothing);
    });

    testWidgets('Scenario E: Startup with Delegated Tenant Session displays TENANT TEST MODE banner', (tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final mockConn = _MockConnectivityService();
      addTearDown(mockConn.dispose);

      final branch = const BranchInfo(
        id: '00000000-0000-0000-0000-000000000010',
        tenantId: '00000000-0000-0000-0000-000000000001',
        name: 'Head Office',
        code: 'HO-001',
        isHeadOffice: true,
      );

      final tenant = const TenantInfo(
        id: '00000000-0000-0000-0000-000000000001',
        organizationId: 'ORG-01',
        name: 'Abyssinia Trading PLC',
        tradeName: 'Abyssinia',
        tin: '0011223344',
        status: 'ACTIVE',
      );

      final session = DelegatedTenantSession(
        sessionId: 'del-sess-001',
        initiatingMasterUserId: 'platform.admin',
        targetTenantId: '00000000-0000-0000-0000-000000000001',
        targetTenantName: 'Abyssinia Trading PLC',
        targetTenantTin: '0011223344',
        accessType: 'TESTING',
        accessToken: 'mock-delegated-jwt',
        reason: 'Pre-production validation',
        expiresAt: DateTime.now().add(const Duration(minutes: 30)),
      );

      final container = ProviderContainer(
        overrides: [
          connectivityServiceProvider.overrideWithValue(mockConn),
          connectionStatusProvider.overrideWith((ref) => Stream.value(ConnectionStatus.serverAvailable)),
          invoiceRepositoryProvider.overrideWith((ref) => _FastInvoiceRepository(mockConn)),
          delegatedTenantSessionProvider.overrideWith((ref) => _MockDelegatedSessionNotifier(session)),
          tenantContextProvider.overrideWith((ref) {
            final notifier = TenantContextNotifier();
            notifier.setAuthorizedContext(
              tenants: [tenant],
              activeTenant: tenant,
              branches: [branch],
              activeBranch: branch,
            );
            return notifier;
          }),
        ],
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const UtTenantClientApp(),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Must display the prominent TENANT TEST MODE banner
      expect(find.textContaining('TENANT TEST MODE'), findsOneWidget);
      expect(find.text('Exit Tenant Environment'), findsOneWidget);
    });
  });
}
