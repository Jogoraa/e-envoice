import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ut_einvoice_client/features/authentication/presentation/invitation_acceptance_screen.dart';

void main() {
  testWidgets('missing invitation token never exposes the password form', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: InvitationAcceptanceScreen(token: null)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Invitation unavailable'), findsOneWidget);
    expect(find.text('New password'), findsNothing);
  });
}
