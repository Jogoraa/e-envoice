import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

/// Typography according to UT Invoice Brand Guidelines v1.1
/// Display & H1 in uppercase Archivo, Inter for interface/body, IBM Plex Mono for fiscal figures.
abstract class AppTypography {
  // Display: Archivo, 40 / 43, weight 800, uppercase
  static TextStyle display({Color color = AppColors.ink}) => GoogleFonts.archivo(
        fontSize: 40,
        height: 43 / 40,
        fontWeight: FontWeight.w800,
        color: color,
        letterSpacing: 0.5,
      );

  // H1: Archivo, 30 / 35, weight 700, uppercase
  static TextStyle h1({Color color = AppColors.ink}) => GoogleFonts.archivo(
        fontSize: 30,
        height: 35 / 30,
        fontWeight: FontWeight.w700,
        color: color,
        letterSpacing: 0.25,
      );

  // H2: Archivo, 21 / 26, weight 600
  static TextStyle h2({Color color = AppColors.ink}) => GoogleFonts.archivo(
        fontSize: 21,
        height: 26 / 21,
        fontWeight: FontWeight.w600,
        color: color,
      );

  // H3: Archivo, 17 / 22, weight 600
  static TextStyle h3({Color color = AppColors.ink, FontWeight fontWeight = FontWeight.w600}) =>
      GoogleFonts.archivo(
        fontSize: 17,
        height: 22 / 17,
        fontWeight: fontWeight,
        color: color,
      );

  // Body: Inter, 16 / 26, weight 400
  static TextStyle body({Color color = AppColors.ink}) => GoogleFonts.inter(
        fontSize: 16,
        height: 26 / 16,
        fontWeight: FontWeight.w400,
        color: color,
      );

  // Body Small / Secondary: Inter, 14 / 20, weight 400
  static TextStyle bodySmall({Color color = AppColors.inkMuted}) => GoogleFonts.inter(
        fontSize: 14,
        height: 20 / 14,
        fontWeight: FontWeight.w400,
        color: color,
      );

  // UI Label: Inter, 13 / 18, weight 500
  static TextStyle uiLabel({Color color = AppColors.ink}) => GoogleFonts.inter(
        fontSize: 13,
        height: 18 / 13,
        fontWeight: FontWeight.w500,
        color: color,
      );

  // UI Label Bold: Inter, 13 / 18, weight 600
  static TextStyle uiLabelBold({Color color = AppColors.ink}) => GoogleFonts.inter(
        fontSize: 13,
        height: 18 / 13,
        fontWeight: FontWeight.w600,
        color: color,
      );

  // Data / Mono: IBM Plex Mono, 15 / 20, weight 400
  // Reserved for invoice numbers, IRNs, RRNs, amounts, and TINs
  static TextStyle mono({Color color = AppColors.ink, FontWeight weight = FontWeight.w400}) =>
      GoogleFonts.ibmPlexMono(
        fontSize: 15,
        height: 20 / 15,
        fontWeight: weight,
        color: color,
      );

  // Data / Mono Small: IBM Plex Mono, 13 / 18, weight 400
  static TextStyle monoSmall({Color color = AppColors.inkMuted, FontWeight weight = FontWeight.w400}) =>
      GoogleFonts.ibmPlexMono(
        fontSize: 13,
        height: 18 / 13,
        fontWeight: weight,
        color: color,
      );
}
