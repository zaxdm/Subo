import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';

/// El buzón de sugerencias: lo que la gente escribe desde Soporte en la app.
///
/// Se pide al servidor al abrir la pantalla, no al entrar al panel: quien solo
/// viene a editar rutas no espera por él.
class FeedbackPage extends StatefulWidget {
  const FeedbackPage({super.key, required this.api, required this.onError});

  final ApiClient api;
  final void Function(Object error) onError;

  @override
  State<FeedbackPage> createState() => _FeedbackPageState();
}

class _FeedbackPageState extends State<FeedbackPage> {
  /// `null` mientras llega la primera respuesta.
  List<Suggestion>? _items;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final items = await widget.api.feedback();
      if (mounted) setState(() => _items = items);
    } catch (error) {
      // Sin lista que enseñar, al menos no se queda cargando para siempre.
      if (mounted) setState(() => _items ??= []);
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _delete(Suggestion item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('¿Eliminar esta sugerencia?'),
        content: const Text('Se borra del buzón y no se puede recuperar.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Eliminar')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.deleteFeedback(item.id);
      if (mounted) setState(() => _items = _items?.where((other) => other.id != item.id).toList());
    } catch (error) {
      widget.onError(error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final items = _items;

    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 720;
        final padding = EdgeInsets.symmetric(horizontal: narrow ? 20 : 0);

        final heading = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('BUZÓN', style: text.labelSmall?.copyWith(color: Palette.accentText)),
            const SizedBox(height: 8),
            Text('Sugerencias', style: narrow ? text.headlineMedium : text.headlineLarge),
            const SizedBox(height: 6),
            Text('Lo que los pasajeros escriben desde Soporte en la app.',
                style: text.bodyMedium?.copyWith(color: Palette.muted)),
          ],
        );
        final refresh = OutlinedButton.icon(
          onPressed: _loading ? null : _load,
          icon: _loading
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
            if (items == null)
              const SliverFillRemaining(hasScrollBody: false, child: Center(child: CircularProgressIndicator()))
            else if (items.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text('El buzón está vacío.',
                        textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: Palette.muted)),
                  ),
                ),
              )
            else
              SliverPadding(
                padding: padding.copyWith(bottom: 40),
                sliver: SliverList.separated(
                  itemCount: items.length,
                  separatorBuilder: (context, index) => const SizedBox(height: 14),
                  itemBuilder: (context, index) =>
                      _FeedbackCard(item: items[index], onDelete: () => _delete(items[index])),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _FeedbackCard extends StatelessWidget {
  const _FeedbackCard({required this.item, required this.onDelete});

  final Suggestion item;
  final VoidCallback onDelete;

  /// "10/10/2026 16:05", en la hora de quien mira el panel.
  static String _date(DateTime date) {
    String two(int value) => value.toString().padLeft(2, '0');
    return '${two(date.day)}/${two(date.month)}/${date.year} ${two(date.hour)}:${two(date.minute)}';
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final author = [item.userName, item.userEmail].whereType<String>().join(' · ');
    final origin = [
      if (item.appVersion.isNotEmpty) 'Versión ${item.appVersion}',
      if (item.device.isNotEmpty) item.device,
    ].join(' · ');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                  decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(30)),
                  child: Text(item.subject,
                      style: const TextStyle(color: Palette.accentText, fontSize: 10, fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(_date(item.createdAt), style: text.bodySmall, overflow: TextOverflow.ellipsis)),
                IconButton(
                  onPressed: onDelete,
                  tooltip: 'Eliminar sugerencia',
                  icon: const Icon(Icons.delete_outline, size: 20, color: Palette.danger),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SelectableText(item.message, style: text.bodyMedium),
            const SizedBox(height: 12),
            Text(author.isEmpty ? 'Cuenta eliminada' : author, style: text.bodySmall),
            if (origin.isNotEmpty) Text(origin, style: text.bodySmall),
          ],
        ),
      ),
    );
  }
}
