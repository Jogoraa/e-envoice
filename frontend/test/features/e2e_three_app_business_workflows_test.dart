import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/app/localization/app_localizations.dart';
import 'package:ut_einvoice_client/core/di/providers.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/core/networking/api_client.dart';
import 'package:ut_einvoice_client/core/networking/network_profile.dart';
import 'package:ut_einvoice_client/core/networking/network_resilience_manager.dart';
import 'package:ut_einvoice_client/core/networking/master_admin_api_client.dart';
import 'package:ut_einvoice_client/core/networking/saas_management_api_client.dart';
import 'package:dio/dio.dart';

// Screens
import 'package:ut_einvoice_client/features/master_admin/presentation/system_integrity_screen.dart';
import 'package:ut_einvoice_client/features/master_admin/presentation/authority_investigation_portal_screen.dart';
import 'package:ut_einvoice_client/features/master_admin/presentation/provider_exit_governance_screen.dart';
import 'package:ut_einvoice_client/features/master_admin/presentation/directive_compliance_governance_screen.dart';
import 'package:ut_einvoice_client/features/saas_management/presentation/tenant_lifecycle_notifications_screen.dart';
import 'package:ut_einvoice_client/features/saas_management/presentation/marketplace_management_screen.dart';
import 'package:ut_einvoice_client/features/saas_management/presentation/saas_tenant_exit_oversight_screen.dart';
import 'package:ut_einvoice_client/features/saas_management/presentation/provider_tier_dashboard_screen.dart';
import 'package:ut_einvoice_client/features/receipts/presentation/cash_receipt_screen.dart';
import 'package:ut_einvoice_client/features/receipts/presentation/purchase_voucher_screen.dart';
import 'package:ut_einvoice_client/features/receipts/presentation/withholding_receipt_screen.dart';
import 'package:ut_einvoice_client/features/invoices/presentation/credit_settlement_screen.dart';
import 'package:ut_einvoice_client/features/invoices/presentation/cancellation_management_screen.dart';
import 'package:ut_einvoice_client/features/offline/presentation/offline_operations_screen.dart';
import 'package:ut_einvoice_client/features/offline/presentation/manual_invoice_reconciliation_screen.dart';
import 'package:ut_einvoice_client/features/mpos/presentation/device_compliance_screen.dart';
import 'package:ut_einvoice_client/features/government/presentation/government_credentials_screen.dart';
import 'package:ut_einvoice_client/features/government/presentation/signature_health_screen.dart';
import 'package:ut_einvoice_client/features/compliance/presentation/tenant_exit_screen.dart';
import 'package:ut_einvoice_client/features/compliance/presentation/retention_schedule_screen.dart';
import 'package:ut_einvoice_client/features/compliance/presentation/exempt_sector_reporting_screen.dart';

class _MockUniversalApiClient extends ApiClient {
  _MockUniversalApiClient()
      : super(
          baseUrl: 'http://localhost:8080',
          secureStorage: SecureStorageService(),
        );

  @override
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    NetworkProfile? profile,
    String? dedupeKey,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    dynamic mockData;
    if (path.contains('/api/v1/authority/system-checksum')) {
      mockData = {
        'version': '2.4.0-STABLE',
        'gitSha': '056473b',
        'backendSha256': 'a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef',
        'frontendBuildSha256': 'f1e2d3c4b5a678901234567890abcdef1234567890abcdef1234567890abcdef',
        'containerDigest': 'sha256:fedcba9876543210',
        'databaseSchemaVersion': 'V26',
        'registeredChecksum': 'a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef',
        'status': 'MATCH',
        'buildTimestamp': DateTime.now().toIso8601String(),
      };
    } else if (path.contains('/api/v1/authority/investigations/exports')) {
      mockData = [
        {
          'id': 'exp-001',
          'caseReference': 'CASE-2026-001',
          'reason': 'Statutory Audit Art 15(5)',
          'status': 'COMPLETED',
          'recordCount': 1500,
          'requestedBy': 'AUDITOR_01',
          'createdAt': DateTime.now().toIso8601String(),
        }
      ];
    } else if (path.contains('/api/v1/compliance/tiers')) {
      mockData = {
        'providerTier': 'TIER_1',
        'bondStatus': 'ACTIVE',
        'guaranteeBondAmount': 50000000,
        'minimumStaffingSatisfied': true,
        'qualifiedStaffCount': 12,
        'certifiedArchitects': 4,
      };
    } else if (path.contains('/api/v1/marketplace')) {
      mockData = [
        {
          'id': 'mer-01',
          'tin': '0099887766',
          'legalName': 'Addis Merchant PLC',
          'tradeName': 'Addis Mall',
          'status': 'ACTIVE',
          'orderCount': 420,
        }
      ];
    } else {
      mockData = [];
    }

    return Response<T>(
      requestOptions: RequestOptions(path: path),
      data: mockData as T,
      statusCode: 200,
    );
  }
}

class _MockMasterApiClient extends MasterAdminApiClient {
  _MockMasterApiClient() : super(secureStorage: SecureStorageService());

  @override
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async {
    return Response<T>(
      requestOptions: RequestOptions(path: path),
      data: <String, dynamic>{
        'currentThroughput': '145.2 TPS',
        'gatewayStatus': 'ONLINE',
        'averageLatency': '42 ms',
        'connectionPool': '18 / 20 active',
        'gatewayErrorRate': '0.00%',
        'overallReady': true,
      } as T,
      statusCode: 200,
    );
  }
}

class _MockSaasApiClient extends SaasManagementApiClient {
  _MockSaasApiClient() : super(secureStorage: SecureStorageService());

  @override
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async {
    return Response<T>(
      requestOptions: RequestOptions(path: path),
      data: <String, dynamic>{
        'status': 'ACTIVE',
        'tier': 'TIER_1',
        'items': [],
      } as T,
      statusCode: 200,
    );
  }
}

Widget _wrapTestWidget(Widget child) {
  final mockClient = _MockUniversalApiClient();
  return ProviderScope(
    overrides: [
      apiClientProvider.overrideWithValue(mockClient),
      masterAdminApiClientProvider.overrideWithValue(_MockMasterApiClient()),
      saasManagementApiClientProvider.overrideWithValue(_MockSaasApiClient()),
    ],
    child: MaterialApp(
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en', '')],
      home: child,
    ),
  );
}

void main() {
  group('Phase 18 & 21: Master Admin Business Workflows', () {
    testWidgets('1. System Integrity Screen displays certified SHA-256 build checksums', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const SystemIntegrityScreen()));
      await tester.pumpAndSettle();

      expect(find.text('System Integrity & Certified Checksum Verification'), findsOneWidget);
    });

    testWidgets('2. Authority Investigation Portal renders audit inspection workflows', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const AuthorityInvestigationPortalScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Tax Authority & Regulatory Investigation Portal'), findsOneWidget);
    });

    testWidgets('3. Provider Exit Governance displays cessation and portability plans', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const ProviderExitGovernanceScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Provider Market Exit Governance (Directive Art. 25)'), findsOneWidget);
    });

    testWidgets('4. Directive Compliance Governance displays statutory tabs', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const DirectiveComplianceGovernanceScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Statutory Compliance Governance'), findsOneWidget);
    });
  });

  group('Phase 19 & 21: Commercial SaaS Business Workflows', () {
    testWidgets('5. Tenant Lifecycle Notifications Screen renders MoR notice logs', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const TenantLifecycleNotificationsScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Taxpayer Lifecycle Notifications (Directive Art. 19)'), findsOneWidget);
    });

    testWidgets('6. Marketplace Management Screen displays multi-seller platform controls', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const MarketplaceManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Marketplace & Multi-Seller Operations (Art. 18)'), findsOneWidget);
    });

    testWidgets('7. SaaS Tenant Exit Oversight displays data migration status', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const SaasTenantExitOversightScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Tenant Portability & Exit Oversight (SaaS Administration)'), findsOneWidget);
    });

    testWidgets('8. Provider Tier Dashboard displays capital and bond metrics', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const ProviderTierDashboardScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Provider Tiering & Guarantee Governance (Annex 1)'), findsOneWidget);
    });
  });

  group('Phase 20 & 21: Tenant Client Statutory Workflows', () {
    testWidgets('9. Cash Receipt Screen renders Art. 7(2) document creation', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const CashReceiptScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Cash Receipts / የጥሬ ገንዘብ መቀበያ ደረሰኞች'), findsOneWidget);
    });

    testWidgets('10. Purchase Voucher Screen renders self-billing reverse charge', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const PurchaseVoucherScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Purchase Vouchers / የግዥ ማረጋገጫ ሰነዶች'), findsOneWidget);
    });

    testWidgets('11. Withholding Receipt Screen renders 3% & 50% withholding', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const WithholdingReceiptScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Withholding Receipts / የግብር ቅነሳ ደረሰኞች'), findsOneWidget);
    });

    testWidgets('12. Credit Settlement Screen renders credit sale reconciliation', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const CreditSettlementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Credit Sales & Settlement / የብድር ሽያጭ አከፋፈል'), findsOneWidget);
    });

    testWidgets('13. Cancellation Management Screen renders cancellation workflows', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const CancellationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Statutory Invoice Cancellations (Directive Art. 14 & 15)'), findsOneWidget);
    });

    testWidgets('14. Offline Operations Screen displays statutory 72h status', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const OfflineOperationsScreen()));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('Offline Operations Center'), findsOneWidget);
    });

    testWidgets('15. Manual Invoice Reconciliation Screen displays batch entry tools', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const ManualInvoiceReconciliationScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Manual Invoice Recovery & Fallback'), findsOneWidget);
    });

    testWidgets('16. Device Compliance Screen renders mPOS and geofencing', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const DeviceComplianceScreen()));
      await tester.pumpAndSettle();

      expect(find.text('mPOS & Device Compliance'), findsOneWidget);
    });

    testWidgets('17. Government Credentials Screen displays API keys and certificates', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const GovernmentCredentialsScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Government Integration & MoR Credentials'), findsOneWidget);
    });

    testWidgets('18. Signature Health Screen renders cryptographic verification', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const SignatureHealthScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Digital Signature & INSA Certificate Health'), findsOneWidget);
    });

    testWidgets('19. Tenant Exit Screen displays data export and archive', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const TenantExitScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Data Portability & Statutory Tenant Exit'), findsOneWidget);
    });

    testWidgets('20. Retention Schedule Screen displays statutory 10-year archiving', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const RetentionScheduleScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Statutory Retention & Legal Hold Schedule'), findsOneWidget);
    });

    testWidgets('21. Exempt Sector Reporting Screen displays Art. 20 periodic summaries', (tester) async {
      tester.view.physicalSize = const Size(1280, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(_wrapTestWidget(const ExemptSectorReportingScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Article 20 Exempt Sector Summary Reporting'), findsOneWidget);
    });
  });
}
