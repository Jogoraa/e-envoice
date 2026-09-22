import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ut_einvoice_client/core/di/providers.dart';
import 'package:ut_einvoice_client/core/networking/master_admin_api_client.dart';
import 'package:ut_einvoice_client/features/master_admin/presentation/master_sessions_devices_screen.dart';

class MockMasterAdminApiClient extends Fake implements MasterAdminApiClient {
  @override
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    if (path == '/api/v1/master/account/sessions') {
      final dynamic sessions = [
        {
          'id': 'sess-001',
          'userId': 'usr-001',
          'username': 'platform.admin',
          'ipAddress': '196.188.12.44',
          'userAgent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
          'deviceSummary': 'Apple MacBook Pro (Admin Station)',
          'mfaAuthenticated': true,
          'createdAt': '2026-09-22T20:00:00Z',
          'lastSeenAt': '2026-09-22T22:30:00Z',
          'isCurrent': true,
        },
        {
          'id': 'sess-002',
          'userId': 'usr-001',
          'username': 'platform.admin',
          'ipAddress': '197.156.77.10',
          'userAgent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
          'deviceSummary': 'Windows Workstation (Secondary)',
          'mfaAuthenticated': false,
          'createdAt': '2026-09-22T19:00:00Z',
          'lastSeenAt': '2026-09-22T21:15:00Z',
          'isCurrent': false,
        },
      ];
      return Response<T>(
        requestOptions: RequestOptions(path: path),
        statusCode: 200,
        data: sessions as T,
      );
    }

    if (path == '/api/v1/master/audit-logs') {
      final dynamic auditLogs = [
        {
          'sequence': 35,
          'actorId': 'platform.admin',
          'action': 'SESSION_REVOKED',
          'resource': 'SECURITY_SESSION:sess-old-99',
          'currentHash': '3742bf1ace407dbc98374aef1294875b1c8e390234acfe129845cdbafe341298',
          'previousHash': '3c15df23ea47a433987162543981726354871625349871625349871625349871',
          'isSignatureValid': true,
          'timestamp': '2026-09-22T22:11:00Z',
        },
        {
          'sequence': 34,
          'actorId': 'platform.admin',
          'action': 'MFA_ENROLLED_SUCCESS',
          'resource': 'IDENTITY_ACCOUNT:usr-001',
          'currentHash': '3c15df23ea47a433987162543981726354871625349871625349871625349871',
          'previousHash': 'c639e0ee1dacbc35876125438761254387612543876125438761254387612543',
          'isSignatureValid': true,
          'timestamp': '2026-09-22T20:34:16Z',
        },
      ];
      return Response<T>(
        requestOptions: RequestOptions(path: path),
        statusCode: 200,
        data: auditLogs as T,
      );
    }

    throw UnimplementedError('Path not mocked: $path');
  }
}

void main() {
  group('Master Sessions & Immutable Audit Ledger UI Tests', () {
    testWidgets('Renders sessions and immutable audit log list clearly',
        (tester) async {
      tester.view.physicalSize = const Size(1440, 1000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final mockClient = MockMasterAdminApiClient();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            masterAdminApiClientProvider.overrideWithValue(mockClient),
          ],
          child: const MaterialApp(
            home: MasterSessionsDevicesScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Top Title and Directive compliance
      expect(find.text('Sessions & Enrolled Devices'), findsOneWidget);
      expect(find.text('DIRECTIVE 1142/2026 AUDITED'), findsOneWidget);

      // Security metrics strip
      expect(find.text('ACTIVE SESSIONS'), findsOneWidget);
      expect(find.text('2 Active'), findsOneWidget);
      expect(find.text('CRYPTOGRAPHIC AUDIT CHAIN'), findsOneWidget);
      expect(find.text('2 Ledger Events'), findsOneWidget);
      expect(find.text('CHAIN INTEGRITY STATUS'), findsOneWidget);
      expect(find.text('0 Breaks Detected'), findsOneWidget);

      // Tabs / view modes
      expect(find.text('Unified Overview'), findsOneWidget);
      expect(find.text('Active Sessions (2)'), findsOneWidget);
      expect(find.text('Immutable Audit Ledger (2)'), findsOneWidget);

      // Active sessions elements
      expect(find.text('Apple MacBook Pro (Admin Station)'), findsOneWidget);
      expect(find.text('THIS STATION (CURRENT)'), findsOneWidget);
      expect(find.text('HARDWARE MFA VERIFIED'), findsOneWidget);
      expect(find.text('Windows Workstation (Secondary)'), findsOneWidget);
      expect(find.text('PASSWORD ONLY'), findsOneWidget);

      // Immutable Audit Log Ledger elements
      expect(find.text('Cryptographic Immutable Security Audit Ledger'),
          findsOneWidget);
      expect(find.text('SHA-256 HASH CHAIN VERIFIED (0 BREAKS)'), findsOneWidget);
      expect(
          find.text(
              'IMMUTABILITY GUARANTEE: Every ledger entry is mathematically bound to its predecessor via SHA-256 HMAC hash linkage. Modifying or deleting any historical log breaks cryptographic verification for all successor entries.'),
          findsOneWidget);

      // Entries
      expect(find.text('#35'), findsOneWidget);
      expect(find.text('SESSION_REVOKED'), findsOneWidget);
      expect(find.text('#34'), findsOneWidget);
      expect(find.text('MFA_ENROLLED_SUCCESS'), findsOneWidget);
    });

    testWidgets('Cryptographic proof inspection dialog opens and shows proof',
        (tester) async {
      tester.view.physicalSize = const Size(1440, 1000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final mockClient = MockMasterAdminApiClient();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            masterAdminApiClientProvider.overrideWithValue(mockClient),
          ],
          child: const MaterialApp(
            home: MasterSessionsDevicesScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Switch to Immutable Audit Ledger tab
      await tester.tap(find.text('Immutable Audit Ledger (2)'));
      await tester.pumpAndSettle();

      // Tap on the Proof button of the first entry
      final proofBtn = find.text('Proof').first;
      await tester.tap(proofBtn);
      await tester.pumpAndSettle();

      // Verify dialog is visible
      expect(find.text('Cryptographic Proof #35'), findsOneWidget);
      expect(find.text('SHA-256 HMAC VERIFIED'), findsOneWidget);
      expect(find.text('CURRENT EVENT HASH (SHA-256)'), findsOneWidget);
      expect(find.text('PREVIOUS BLOCK HASH (CHAIN LINK)'), findsOneWidget);
      expect(find.text('Copy Proof Record'), findsOneWidget);
    });
  });
}
