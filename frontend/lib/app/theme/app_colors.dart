import 'package:flutter/material.dart';

/// Exact Brand Color Tokens from UT Invoice Brand Guidelines Version 1.1
/// Sampled from the UT Solutions master logo (`logo.png`).
abstract class AppColors {
  // Brand Core Colors
  static const Color navy900 = Color(
    0xFF0A3471,
  ); // Primary brand fill, logo, dark surfaces
  static const Color navy700 = Color(
    0xFF0F4A9C,
  ); // Links, primary buttons, interactive states
  static const Color red600 = Color(
    0xFFED2124,
  ); // Sub-mark notch, one accent per screen, error state
  static const Color red700 = Color(0xFFDC1C1F);
  static const Color amber700 = Color(0xFFB35E00);
  //surfaceMuted
  static const Color surfaceMuted = Color(0xFFF1F3F5);
  // Status Semantics
  static const Color amber600 = Color(
    0xFFB3781A,
  ); // Pending / offline draft status
  static const Color green700 = Color(0xFF1C7A4C); // Synced / confirmed status

  //paperMuted
  static const Color paperMuted = Color(0xFFF7F8FA);
  // Neutral Scale (Light)
  static const Color ink = Color(0xFF101820); // Primary text on paper
  static const Color paper = Color(0xFFF4F5F6); // Application background
  static const Color paperRaised = Color(
    0xFFFFFFFF,
  ); // Cards, table surfaces, inputs
  static const Color rule = Color(0xFFDCDFE3); // Dividers, table lines, borders
  static const Color ruleLight = Color(0xFFEEF0F2);
  static const Color blue600 = Color(0xFF0F4A9C);
  static const Color inkMuted = Color(0xFF5A6675); // Secondary neutral text

  // Dark Mode Equivalents (lightened for contrast on dark surfaces)
  static const Color navy900Dark = Color(0xFF7FA6E0);
  static const Color navy700Dark = Color(0xFFA9C4EC);
  static const Color red600Dark = Color(0xFFF16A6C);
  static const Color amber600Dark = Color(0xFFE0B158);
  static const Color green700Dark = Color(0xFF6CC296);
  static const Color paperDark = Color(0xFF12151A);
  static const Color paperRaisedDark = Color(0xFF1A1E24);
  static const Color ruleDark = Color(0xFF2C3138);
  static const Color inkDark = Color(0xFFF0F4F8);
  static const Color inkMutedDark = Color(0xFF9AA7B7);
}
