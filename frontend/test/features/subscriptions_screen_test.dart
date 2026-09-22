import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/core/di/providers.dart';
import 'package:ut_einvoice_client/core/networking/saas_management_api_client.dart';
import 'package:ut_einvoice_client/core/storage/secure_storage_service.dart';
import 'package:ut_einvoice_client/features/saas_management/presentation/subscriptions_screen.dart';

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
    final data = [
      {
        'tenantId': '86906a19-0f47-4ea7-8fe7-4f681ab99ee8',
        'tenantName': 'Abyssinia Trading & Distribution PLC',
        'tin': '0011223344',
        'plan': 'ENTERPRISE_UNLIMITED',
        'status': 'ACTIVE',
        'maxBranches': 100,
        'maxPosDevices': 500,
        'monthlyInvoiceQuota': 50000000,
        'nextBillingDate': '2027-01-01T00:00:00Z',
      }
    ];
    return Response<T>(
      requestOptions: RequestOptions(path: path),
      data: data as T,
      statusCode: 200,
    );
  }
}

void main() {
  testWidgets('SubscriptionsScreen opens edit plan dialog with ENTERPRISE_UNLIMITED without assertion crash', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          saasManagementApiClientProvider.overrideWithValue(_MockSaasApiClient()),
        ],
        child: const MaterialApp(
          home: SubscriptionsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Verify tenant subscription rendered
    expect(find.text('Abyssinia Trading & Distribution PLC'), findsOneWidget);
    expect(find.text('ENTERPRISE_UNLIMITED'), findsOneWidget);

    // Tap 'Manage Plan'
    final manageButton = find.widgetWithText(ElevatedButton, 'Manage Plan');
    expect(manageButton, findsOneWidget);
    await tester.tap(manageButton);
    await tester.pumpAndSettle();

    // Verify dialog opened with DropdownButton showing ENTERPRISE_UNLIMITED
    expect(find.text('Select Commercial Tier:'), findsOneWidget);
    expect(find.byType(DropdownButton<String>), findsOneWidget);
    expect(find.text('ENTERPRISE UNLIMITED (Unlimited Quotas)'), findsOneWidget);

    // Dismiss dialog cleanly
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.text('Select Commercial Tier:'), findsNothing);
  });
}
