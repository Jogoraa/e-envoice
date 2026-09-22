import 'package:flutter/material.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';

/// UT Invoice Custom Vector Mark per Brand Guidelines v1.1:
/// Rounded navy square containing three white ruled lines (invoice body),
/// with a red circular connector tab breaking the top-right corner.
class UtInvoiceMarkPainter extends CustomPainter {
  final Color navyColor;
  final Color redColor;
  final Color lineColor;

  UtInvoiceMarkPainter({
    this.navyColor = AppColors.navy900,
    this.redColor = AppColors.red600,
    this.lineColor = Colors.white,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final double width = size.width;
    final double height = size.height;

    // Document Body: Navy rounded square
    final Paint bodyPaint = Paint()
      ..color = navyColor
      ..style = PaintingStyle.fill;

    // Document corner radius: subtle 3-4px proportioned to size
    final double docRadius = width * 0.12;
    final RRect docRRect = RRect.fromRectAndRadius(
      Rect.fromLTWH(0, height * 0.08, width * 0.88, height * 0.92),
      Radius.circular(docRadius),
    );
    canvas.drawRRect(docRRect, bodyPaint);

    // Three white ruled lines (invoice lines)
    final Paint linePaint = Paint()
      ..color = lineColor
      ..strokeWidth = height * 0.07
      ..strokeCap = StrokeCap.round;

    final double lineLeft = width * 0.18;
    final double lineRight1 = width * 0.65;
    final double lineRight2 = width * 0.72;
    final double lineRight3 = width * 0.48;

    canvas.drawLine(
      Offset(lineLeft, height * 0.38),
      Offset(lineRight1, height * 0.38),
      linePaint,
    );
    canvas.drawLine(
      Offset(lineLeft, height * 0.56),
      Offset(lineRight2, height * 0.56),
      linePaint,
    );
    canvas.drawLine(
      Offset(lineLeft, height * 0.74),
      Offset(lineRight3, height * 0.74),
      linePaint,
    );

    // Red interlocking connector tab breaking top-right corner
    final Paint connectorPaint = Paint()
      ..color = redColor
      ..style = PaintingStyle.fill;

    final double tabRadius = width * 0.22;
    final Offset tabCenter = Offset(width * 0.78, height * 0.22);
    canvas.drawCircle(tabCenter, tabRadius, connectorPaint);
  }

  @override
  bool shouldRepaint(covariant UtInvoiceMarkPainter oldDelegate) =>
      oldDelegate.navyColor != navyColor ||
      oldDelegate.redColor != redColor ||
      oldDelegate.lineColor != lineColor;
}

/// UT Invoice Brand Logo Widget with optional lockup text and parent attribution
class UtInvoiceLogo extends StatelessWidget {
  final double size;
  final bool showWordmark;
  final bool showParentCredit;
  final Color? textColor;

  const UtInvoiceLogo({
    super.key,
    this.size = 32.0,
    this.showWordmark = true,
    this.showParentCredit = true,
    this.textColor,
  });

  @override
  Widget build(BuildContext context) {
    final Widget mark = SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: UtInvoiceMarkPainter(),
      ),
    );

    if (!showWordmark) {
      return mark;
    }

    final Color textCol = textColor ?? (Theme.of(context).brightness == Brightness.dark ? AppColors.inkDark : AppColors.ink);

    return FittedBox(
      fit: BoxFit.scaleDown,
      alignment: Alignment.centerLeft,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          mark,
          const SizedBox(width: 12),
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'UT INVOICE',
                style: AppTypography.h2(color: textCol).copyWith(
                  fontSize: size * 0.55,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.8,
                ),
              ),
              if (showParentCredit)
                Text(
                  'A UT Solutions platform',
                  style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(
                    fontSize: size * 0.30,
                    height: 1.1,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
