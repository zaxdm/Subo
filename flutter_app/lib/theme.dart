import 'package:flutter/material.dart';

/// Paleta amarilla del panel. Los nombres describen el color; quién usa cada
/// uno se decide en [buildTheme] y en las pantallas.
abstract final class Palette {
  static const ink = Color(0xFF241F10);
  static const muted = Color(0xFF756D58);
  static const accent = Color(0xFFF5B800);
  static const accentDark = Color(0xFFE0A800);

  /// Ámbar oscuro: el amarillo puro no se lee como texto sobre blanco.
  static const accentText = Color(0xFF8A6500);
  static const accentSoft = Color(0xFFFBEEBC);
  static const line = Color(0xFFE8E2D0);
  static const paper = Color(0xFFFBF8EE);
  static const surface = Colors.white;
  static const sidebar = Color(0xFF1F1A0B);
  static const sidebarMuted = Color(0xFFB3A882);
  static const sidebarActive = Color(0xFF3A3115);

  // Con significado propio: no siguen la marca.
  static const success = Color(0xFF24664D);
  static const successSoft = Color(0xFFE2F0E8);
  static const warning = Color(0xFF926D20);
  static const warningSoft = Color(0xFFFFF2D6);
  static const danger = Color(0xFFA44638);
  static const dangerSoft = Color(0xFFFFF0ED);
}

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: Palette.accent,
    brightness: Brightness.light,
  ).copyWith(
    primary: Palette.accent,
    onPrimary: Palette.ink,
    secondary: Palette.accentText,
    surface: Palette.surface,
    onSurface: Palette.ink,
    onSurfaceVariant: Palette.muted,
    outline: Palette.line,
    outlineVariant: Palette.line,
    error: Palette.danger,
  );

  final border = OutlineInputBorder(
    borderRadius: BorderRadius.circular(10),
    borderSide: const BorderSide(color: Color(0xFFE3DCC6)),
  );

  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: Palette.paper,
    fontFamily: 'Roboto',
    // Letra normal: sin apretar ni separar las letras y sin negritas pesadas.
    textTheme: const TextTheme(
      headlineLarge: TextStyle(fontSize: 36, fontWeight: FontWeight.w700, letterSpacing: 0, height: 1.2),
      headlineMedium: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: 0, height: 1.25),
      titleLarge: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, letterSpacing: 0),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, letterSpacing: 0),
      titleSmall: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 0),
      bodyLarge: TextStyle(fontSize: 16, height: 1.5, letterSpacing: 0),
      bodyMedium: TextStyle(fontSize: 14, height: 1.5, letterSpacing: 0),
      bodySmall: TextStyle(fontSize: 12, height: 1.5, letterSpacing: 0, color: Palette.muted),
      labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 0),
      labelMedium: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, letterSpacing: 0),
      labelSmall: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.6),
    ).apply(bodyColor: Palette.ink, displayColor: Palette.ink),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Palette.surface,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      border: border,
      enabledBorder: border,
      focusedBorder: border.copyWith(borderSide: const BorderSide(color: Palette.accentText, width: 1.6)),
      labelStyle: const TextStyle(color: Palette.muted),
      hintStyle: const TextStyle(color: Color(0xFFA39B85)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: Palette.accent,
        foregroundColor: Palette.ink,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Palette.ink,
        backgroundColor: Palette.surface,
        side: const BorderSide(color: Palette.line),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(9)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: Palette.accentText,
        textStyle: const TextStyle(fontWeight: FontWeight.w600),
      ),
    ),
    cardTheme: CardThemeData(
      color: Palette.surface,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: const BorderSide(color: Palette.line),
      ),
    ),
    dividerTheme: const DividerThemeData(color: Palette.line, space: 1, thickness: 1),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating, width: 520),
    checkboxTheme: CheckboxThemeData(
      fillColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected) ? Palette.accentText : null,
      ),
    ),
  );
}
