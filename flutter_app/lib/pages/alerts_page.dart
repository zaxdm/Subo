import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/page_frame.dart';

/// Moderación: las alertas que están a la vista ahora mismo y quién las puso.
///
/// Desde aquí se borra la que sea falsa u ofensiva y se bloquea a quien abusa.
/// Una cuenta bloqueada sigue entrando, pero no publica y lo suyo deja de verse.
class AlertsPage extends StatefulWidget {
  const AlertsPage({super.key, required this.api, required this.onError});

  final ApiClient api;
  final void Function(Object error) onError;

  @override
  State<AlertsPage> createState() => _AlertsPageState();
}

class _AlertsPageState extends State<AlertsPage> {
  /// `null` mientras llega la primera respuesta.
  List<ModerationAlert>? _alerts;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final alerts = await widget.api.moderationAlerts();
      if (mounted) setState(() => _alerts = alerts);
    } catch (error) {
      if (mounted) setState(() => _alerts ??= []);
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<bool> _confirm(String title, String message, String action) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(action)),
        ],
      ),
    );
    return confirmed == true;
  }

  Future<void> _delete(ModerationAlert item) async {
    if (!await _confirm('¿Eliminar esta alerta?', 'Desaparece del mapa de todos y no se puede recuperar.', 'Eliminar')) {
      return;
    }
    try {
      await widget.api.deleteAlert(item.alert.id);
      if (mounted) setState(() => _alerts = _alerts?.where((other) => other.alert.id != item.alert.id).toList());
    } catch (error) {
      widget.onError(error);
    }
  }

  Future<void> _toggleBlock(ModerationAlert item) async {
    final block = !item.authorBlocked;
    final who = item.authorName ?? item.authorEmail ?? 'esta cuenta';
    if (block &&
        !await _confirm(
          '¿Bloquear a $who?',
          'No podrá publicar alertas, fotos, calificaciones ni sugerencias, y lo que ya publicó dejará de verse. '
              'Se puede desbloquear después.',
          'Bloquear',
        )) {
      return;
    }
    try {
      await widget.api.blockUser(item.authorId, blocked: block);
      if (!mounted) return;
      // El bloqueo es de la cuenta: vale para todas sus alertas de la lista.
      setState(() {
        for (final other in _alerts ?? const <ModerationAlert>[]) {
          if (other.authorId == item.authorId) other.authorBlocked = block;
        }
      });
    } catch (error) {
      widget.onError(error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final alerts = _alerts;
    return PageFrame(
      label: 'MODERACIÓN',
      title: 'Alertas',
      subtitle: 'Lo que la gente tiene publicado ahora mismo en el mapa.',
      loading: _loading,
      onRefresh: _load,
      ready: alerts != null,
      emptyMessage: alerts != null && alerts.isEmpty ? 'No hay alertas vigentes.' : null,
      slivers: (padding) => [
        SliverPadding(
          padding: padding.copyWith(bottom: 40),
          sliver: SliverList.separated(
            itemCount: alerts!.length,
            separatorBuilder: (context, index) => const SizedBox(height: 14),
            itemBuilder: (context, index) => _AlertCard(
              item: alerts[index],
              onDelete: () => _delete(alerts[index]),
              onToggleBlock: () => _toggleBlock(alerts[index]),
            ),
          ),
        ),
      ],
    );
  }
}

class _AlertCard extends StatelessWidget {
  const _AlertCard({required this.item, required this.onDelete, required this.onToggleBlock});

  final ModerationAlert item;
  final VoidCallback onDelete;
  final VoidCallback onToggleBlock;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final alert = item.alert;
    final author = [item.authorName, item.authorEmail].whereType<String>().join(' · ');
    final photo = Uri.tryParse(item.imageUrl ?? '');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Pill(alert.label),
                if (item.reputation == 'TRUSTED')
                  const Pill('Suele acertar', background: Palette.successSoft, foreground: Palette.success),
                if (item.reputation == 'UNRELIABLE')
                  const Pill('Suele fallar', background: Palette.warningSoft, foreground: Palette.warning),
                if (item.authorBlocked)
                  const Pill('Cuenta bloqueada', background: Palette.dangerSoft, foreground: Palette.danger),
                Text(formatDateTime(item.createdAt), style: text.bodySmall),
              ],
            ),
            const SizedBox(height: 10),
            Text(alert.placeName ?? 'Lugar sin nombre', style: text.titleMedium),
            if ((alert.comment ?? '').isNotEmpty) ...[
              const SizedBox(height: 4),
              SelectableText(alert.comment!, style: text.bodyMedium),
            ],
            if (photo != null && photo.scheme == 'https') ...[
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: Image.network(
                  item.imageUrl!,
                  height: 180,
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stack) => const SizedBox.shrink(),
                ),
              ),
            ],
            const SizedBox(height: 12),
            Text(author.isEmpty ? 'Cuenta sin datos' : author, style: text.bodySmall),
            Text('Sigue ahí: ${alert.confirmations} · Ya no está: ${item.dismissals}', style: text.bodySmall),
            const SizedBox(height: 12),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                OutlinedButton.icon(
                  onPressed: onDelete,
                  icon: const Icon(Icons.delete_outline, size: 16, color: Palette.danger),
                  label: const Text('Eliminar alerta'),
                ),
                OutlinedButton.icon(
                  onPressed: onToggleBlock,
                  icon: Icon(item.authorBlocked ? Icons.lock_open : Icons.block, size: 16),
                  label: Text(item.authorBlocked ? 'Desbloquear cuenta' : 'Bloquear cuenta'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
