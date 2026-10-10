import 'package:flutter/material.dart';

import '../theme.dart';

/// El esqueleto de una sección del panel: su encabezado, el botón de actualizar
/// y, debajo, el contenido. Mientras llega la primera respuesta enseña que está
/// cargando; si no hay nada, lo dice.
class PageFrame extends StatelessWidget {
  const PageFrame({
    super.key,
    required this.label,
    required this.title,
    required this.subtitle,
    required this.loading,
    required this.onRefresh,
    required this.slivers,
    this.ready = true,
    this.emptyMessage,
  });

  final String label;
  final String title;
  final String subtitle;

  /// Hay una petición en marcha: el botón de actualizar espera.
  final bool loading;
  final VoidCallback onRefresh;

  /// Ya llegó la primera respuesta.
  final bool ready;

  /// Qué decir cuando no hay nada que enseñar; `null` si hay contenido.
  final String? emptyMessage;

  /// El contenido, ya con su relleno lateral puesto por [padding].
  final List<Widget> Function(EdgeInsets padding) slivers;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 720;
        final padding = EdgeInsets.symmetric(horizontal: narrow ? 20 : 0);

        final heading = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: text.labelSmall?.copyWith(color: Palette.accentText)),
            const SizedBox(height: 8),
            Text(title, style: narrow ? text.headlineMedium : text.headlineLarge),
            const SizedBox(height: 6),
            Text(subtitle, style: text.bodyMedium?.copyWith(color: Palette.muted)),
          ],
        );
        final refresh = OutlinedButton.icon(
          onPressed: loading ? null : onRefresh,
          icon: loading
              ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.refresh, size: 18),
          label: const Text('Actualizar'),
        );

        return CustomScrollView(
          slivers: [
            SliverPadding(
              padding: padding.copyWith(top: 32, bottom: 24),
              sliver: SliverToBoxAdapter(
                child: narrow
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [heading, const SizedBox(height: 16), refresh],
                      )
                    : Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [Expanded(child: heading), refresh],
                      ),
              ),
            ),
            if (!ready)
              const SliverFillRemaining(hasScrollBody: false, child: Center(child: CircularProgressIndicator()))
            else if (emptyMessage != null)
              SliverFillRemaining(
                hasScrollBody: false,
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text(emptyMessage!,
                        textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: Palette.muted)),
                  ),
                ),
              )
            else
              ...slivers(padding),
          ],
        );
      },
    );
  }
}

/// "10/10/2026 16:05", en la hora de quien mira el panel.
String formatDateTime(DateTime date) {
  String two(int value) => value.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year} ${two(date.hour)}:${two(date.minute)}';
}

/// Una pastilla de color con un texto corto: un tipo, un estado.
class Pill extends StatelessWidget {
  const Pill(this.text, {super.key, this.background = Palette.accentSoft, this.foreground = Palette.accentText});

  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(30)),
      child: Text(text, style: TextStyle(color: foreground, fontSize: 10, fontWeight: FontWeight.w700)),
    );
  }
}
