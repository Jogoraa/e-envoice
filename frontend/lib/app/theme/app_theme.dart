import 'package:flutter/material.dart';
import 'app_colors.dart';
import 'app_typography.dart';

/// Production ThemeData configured per UT Invoice Brand Guidelines v1.1
abstract class AppTheme {
  static const double controlRadius = 3.0;
  static final BorderRadius controlBorderRadius = BorderRadius.circular(controlRadius);

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: AppColors.paper,
      canvasColor: AppColors.paperRaised,
      cardColor: AppColors.paperRaised,
      dividerColor: AppColors.rule,
      colorScheme: const ColorScheme.light(
        primary: AppColors.navy900,
        primaryContainer: AppColors.navy700,
        secondary: AppColors.navy700,
        error: AppColors.red600,
        surface: AppColors.paperRaised,
        onPrimary: Colors.white,
        onSecondary: Colors.white,
        onError: Colors.white,
        onSurface: AppColors.ink,
      ),
      // App Bar Theme
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.paperRaised,
        foregroundColor: AppColors.ink,
        elevation: 0,
        scrolledUnderElevation: 1,
        surfaceTintColor: Colors.transparent,
        titleTextStyle: AppTypography.h2(color: AppColors.ink),
        shape: const Border(bottom: BorderSide(color: AppColors.rule, width: 1)),
      ),
      // Card Theme: Crisp 3px corners, 1px rule border, no heavy drop shadow
      cardTheme: CardThemeData(
        color: AppColors.paperRaised,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: controlBorderRadius,
          side: const BorderSide(color: AppColors.rule, width: 1),
        ),
      ),
      // Elevated Button: Primary button (Navy fill #0A3471, white text, 3px radius)
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.navy900,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: controlBorderRadius),
          textStyle: AppTypography.uiLabelBold(color: Colors.white),
        ),
      ),
      // Outlined Button: Secondary button (transparent, 1px solid rule border, ink text, 3px radius)
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          backgroundColor: Colors.transparent,
          foregroundColor: AppColors.ink,
          side: const BorderSide(color: AppColors.rule, width: 1),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: controlBorderRadius),
          textStyle: AppTypography.uiLabelBold(color: AppColors.ink),
        ),
      ),
      // Text Button
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.navy700,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          shape: RoundedRectangleBorder(borderRadius: controlBorderRadius),
          textStyle: AppTypography.uiLabel(color: AppColors.navy700),
        ),
      ),
      // Input Decoration: 1px solid rule border, 3px radius, paper-raised background
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.paperRaised,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        border: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.rule, width: 1),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.rule, width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.navy700, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.red600, width: 1),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.red600, width: 1.5),
        ),
        labelStyle: AppTypography.uiLabel(color: AppColors.inkMuted),
        hintStyle: AppTypography.uiLabel(color: AppColors.inkMuted),
        errorStyle: AppTypography.uiLabel(color: AppColors.red600),
      ),
      // Data Table Theme
      dataTableTheme: DataTableThemeData(
        headingRowColor: WidgetStateProperty.all(AppColors.paper),
        dataRowColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.hovered)) {
            return AppColors.paper.withValues(alpha: 0.5);
          }
          return AppColors.paperRaised;
        }),
        headingTextStyle: AppTypography.uiLabelBold(color: AppColors.ink),
        dataTextStyle: AppTypography.bodySmall(color: AppColors.ink),
        dividerThickness: 1,
        horizontalMargin: 16,
        columnSpacing: 24,
      ),
    );
  }

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AppColors.paperDark,
      canvasColor: AppColors.paperRaisedDark,
      cardColor: AppColors.paperRaisedDark,
      dividerColor: AppColors.ruleDark,
      colorScheme: const ColorScheme.dark(
        primary: AppColors.navy900Dark,
        primaryContainer: AppColors.navy700Dark,
        secondary: AppColors.navy700Dark,
        error: AppColors.red600Dark,
        surface: AppColors.paperRaisedDark,
        onPrimary: AppColors.ink,
        onSecondary: AppColors.ink,
        onError: Colors.white,
        onSurface: AppColors.inkDark,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.paperRaisedDark,
        foregroundColor: AppColors.inkDark,
        elevation: 0,
        scrolledUnderElevation: 1,
        surfaceTintColor: Colors.transparent,
        titleTextStyle: AppTypography.h2(color: AppColors.inkDark),
        shape: const Border(bottom: BorderSide(color: AppColors.ruleDark, width: 1)),
      ),
      cardTheme: CardThemeData(
        color: AppColors.paperRaisedDark,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: controlBorderRadius,
          side: const BorderSide(color: AppColors.ruleDark, width: 1),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.navy900,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: controlBorderRadius),
          textStyle: AppTypography.uiLabelBold(color: Colors.white),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          backgroundColor: Colors.transparent,
          foregroundColor: AppColors.inkDark,
          side: const BorderSide(color: AppColors.ruleDark, width: 1),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: controlBorderRadius),
          textStyle: AppTypography.uiLabelBold(color: AppColors.inkDark),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.paperRaisedDark,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        border: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.ruleDark, width: 1),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.ruleDark, width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.navy700Dark, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: controlBorderRadius,
          borderSide: const BorderSide(color: AppColors.red600Dark, width: 1),
        ),
        labelStyle: AppTypography.uiLabel(color: AppColors.inkMutedDark),
        hintStyle: AppTypography.uiLabel(color: AppColors.inkMutedDark),
      ),
    );
  }
}
