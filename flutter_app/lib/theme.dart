import 'package:flutter/material.dart';

/// Paleta de Subo: amarillo, negro y crema, la misma en la web y en la app.
/// Los nombres describen el color; quién usa cada uno se decide en
/// [buildTheme] y en las pantallas.
abstract final class Palette {
  static const ink = Color(0xFF1C1917);
  static const muted = Color(0xFF6F6A5E);
  static const accent = Color(0xFFFFC400);
  static const accentDark = Color(0xFFE6B000);

  /// Casi negro, tirando a ámbar: el amarillo puro no se lee como texto sobre
  /// blanco, así que los rótulos y los iconos de acento van en este.
  static const accentText = Color(0xFF3D3000);
  static const accentSoft = Color(0xFFFFF1C2);
  static const line = Color(0xFFEFE8D6);
  static const paper = Color(0xFFFFFCF2);
  static const surface = Colors.white;

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
    borderRadius: BorderRadius.circular(14),
    borderSide: const BorderSide(color: Palette.line),
  );

  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: Palette.paper,
    fontFamily: 'Roboto',
    // Títulos gruesos y un poco apretados; el texto corrido, normal.
    textTheme: const TextTheme(
      headlineLarge: TextStyle(fontSize: 38, fontWeight: FontWeight.w700, letterSpacing: -0.8, height: 1.15),
      headlineMedium: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: -0.5, height: 1.2),
      titleLarge: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, letterSpacing: -0.2),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, letterSpacing: 0),
      titleSmall: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, letterSpacing: 0),
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
      focusedBorder: border.copyWith(borderSide: const BorderSide(color: Palette.ink, width: 1.6)),
      labelStyle: const TextStyle(color: Palette.muted),
      hintStyle: const TextStyle(color: Color(0xFFA39B85)),
    ),
    // Los botones son pastillas: el principal, negro; el secundario, blanco.
    // El amarillo se queda para los fondos y las insignias.
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: Palette.ink,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 18),
        textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
        shape: const StadiumBorder(),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Palette.ink,
        backgroundColor: Palette.surface,
        side: const BorderSide(color: Palette.line),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
        shape: const StadiumBorder(),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: Palette.ink,
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
        shape: const StadiumBorder(),
      ),
    ),
    cardTheme: CardThemeData(
      color: Palette.surface,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: const BorderSide(color: Palette.line),
      ),
    ),
    dividerTheme: const DividerThemeData(color: Palette.line, space: 1, thickness: 1),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating, width: 520),
    checkboxTheme: CheckboxThemeData(
      fillColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected) ? Palette.ink : null,
      ),
    ),
  );
}
